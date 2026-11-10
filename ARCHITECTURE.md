# Architecture — GlassTube

How the app is put together and why. Behaviour is in `BEHAVIORS.md`; state and next steps are in
`ROADMAP.md`. Where this file and the code disagree, the code wins and this file gets fixed.

## Quick facts

- Native Android, Kotlin, Jetpack Compose, Material 3. Package `com.presaince.glasstube`.
- `minSdk` 33, `targetSdk` 36, `compileSdk` 36. No core-library desugaring: the floor is 33 because
  NewPipeExtractor calls JDK methods that only exist from Android 13.
- Room `version = 11`, `exportSchema = true` (`app/schemas/`), `fallbackToDestructiveMigration`.
  Nothing has shipped. Migrations are still destructive until the schema is released.
- Version `0.3.0`, `versionCode` 3. Personal release only; no store listing.
- GPLv3. NewPipeExtractor is a dependency, never vendored.
- Offline-first for everything the app owns; the network is only for YouTube, SponsorBlock, an
  optional model endpoint, and the bug drop box.

Dependencies (all deliberate): Compose BOM 2024.09, Activity Compose, Lifecycle, Media3 1.11.1
(exoplayer, ui, session), OkHttp 4.12, kotlinx-coroutines, Room 2.8.5 (KSP), WorkManager 2.10.1,
Coil 3.0.4 (compose + okhttp), NewPipeExtractor v0.27.0.

Permissions: INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK, POST_NOTIFICATIONS, RECORD_AUDIO (dictation),
FOREGROUND_SERVICE (media playback), REQUEST_INSTALL_PACKAGES (self-update installer), plus a
`<queries>` entry for `android.speech.RecognitionService`.

## Package structure

```
app/src/main/java/com/presaince/glasstube/
  core/        AppResult, AppLog, BugReport/BugShot/BugUpload, Html, Sort, Time
  data/        model, db, source, library, discover, resync, sponsorblock, importer,
               backup (settings schema, restore, one combined file, found files), auth, secure,
               cache, update (self-update)
  playback/    PlaybackController (ExoPlayer + MediaSession + prefs), PlaybackService
  feature/     one package per screen: player, feed, search, library, collections, discover,
               channel, comments, notes, posting, settings, resync, about, onboarding
  ui/          theme (schemes, glass), nav (GlassTubeNav), component (scaffold, mini player,
               rows, flags, scroll-to-top)
  di/          AppContainer — the manual composition root
  MainActivity.kt, GlassTubeApp.kt
```

## System overview

One Activity, one Compose tree, one playback session.

- `MainActivity` creates the `ThemeController` and wraps `GlassTubeNav` in `GlassTubeTheme`.
- `GlassTubeApp` owns the `AppContainer`; the container is the only place singletons are built.
- `GlassTubeNav` holds the route stack and the tab pager. It is the whole navigation model. On a
  fresh install it renders `OnboardingScreen` instead of the scaffold, before any tab is composed.
- `PlaybackController` owns the `ExoPlayer` and the media session. Every screen reads its
  `StateFlow<PlaybackState>`; nothing else touches the player.
- `ContentRepository` is the only writer to the content tables in Room.
- `VideoSource` is the only boundary that touches NewPipeExtractor.

## Core ownership

| State | Owner | Rule |
|---|---|---|
| ExoPlayer, speed, volume, PiP | `PlaybackController` | Only it may call the player. Screens send intentions. |
| Media notification | `PlaybackService` + session | Declared in the manifest, foreground type mediaPlayback. |
| Route stack, selected tab | `GlassTubeNav` (`remember`) | Not persisted; a cold start is always Feed. |
| Theme, accent, glass base, daily accent | `ThemeController` | Live state; never recreates the Activity. |
| Feed, videos, history, progress, notes | Room via `ContentRepository` / DAOs | UI never writes content tables directly. |
| Settings | `shared_prefs/glasstube.xml` | Keys are read at the point of use; no settings singleton. |
| API keys | `SecurePrefs` (Android Keystore) | Never in shared prefs, never in logs. |

## Data flow

**Feed refresh** (`ContentRepository.refreshFeed`, driven by `FeedWorker`):

1. Read subscriptions from Room.
2. Four channels at a time: `source.channelUploads(url)` + `source.channelInfo(url)`.
3. Upsert videos. A video seen LIVE stays LIVE for a 3-hour grace (`liveSeenAt`) so two listings
   that disagree cannot make the badge blink.
4. Upsert channels; prune videos older than 30 days; log start, finish, failures, duration.

**Screens** observe Room (`Flow`) and render; there is no view-state cache between screens. Search
and channel pages go straight to `VideoSource`, because they are about the network, not the library.

**Comments** are paged by continuation token (`Page.getId()`), cached for an hour in
`comment_cache`, and the total is stored on the video row.

**Playback bytes** are written through a Media3 `SimpleCache` (2 GB default, LRU, user setting)
keyed by video id + itag, so a seek back inside what you have watched, or reopening a video,
reads the disk before it touches the network. Only the key is stable across fetches; the signed
URL never is.

**Resync** is opt-in: a transcript is fetched, cue hashes computed, and a model is asked to align a
comment's timestamp. Results are cached per video + comment + cue hash.

**Bug reports** are built in memory (`BugReport.bundle`), redacted, optionally with a JPEG screenshot,
and POSTed to the drop box. A filed report appends one `bug:` line to the log, and the reports
themselves are read back with `scripts/reports.mjs`, which prints the note and, on request, only the
warn and error lines of one bundle. `docs/reports.md` is the append-only index of what was filed and
what came of it.

## Module notes

- `data/source/NewPipeSource` — one class, one job: turn extractor objects into `AppResult` models.
  It translates `LinkageError` and extractor exceptions into messages, never leaks extractor types.
- `data/source/ContentRegion` — the country the extractor presents; stored as an ISO code, applied
  when the source is built.
- `data/source/ChannelFeed` — the free per-channel upload feed (`feeds/videos.xml`), keyless and
  quoteless, cached for thirty minutes. It returns the latest fifteen with no duration; a caller
  that filters on length treats that as unknown.
- `playback/PlaybackController` — speed, quality ceiling, audio track preference (ORIGINAL, then
  locale, then bitrate), PiP flag, notification restart flag, resume-after-dictation. It is the only
  object that knows the player exists.
- `data/discover/DiscoverEngine` — `picks()` returns one page from one of three sources: one
  search for the recipe's query, the latest uploads from followed channels (through `ChannelFeed`,
  the extractor as fallback), or one level of related videos from seeds in the library. It scores,
  returns a path, and falls back to the stored library when the network source fails; the recipe is
  the entire state.
- `data/resync/ResyncEngine` — 15-second tail after a comment's cue; one model call per batch.
- `ui/theme/Theme.kt` — schemes, the glass aurora, `ThemeBackdrop` (the opaque floor under a pushed
  screen), and the mode resolver. Glass inherits the last ordinary theme.
- `ui/nav/GlassTubeNav` — a `List<Route>` plus a `HorizontalPager` that is never disposed: pushed
  screens draw over it. The pager is not swipeable while anything is on top.
- `ui/component/MiniPlayer` — a second `PlayerView` bound to the same `ExoPlayer`; only one surface
  is composed at a time (mini or full).
- `ui/component/FlagBackground` — maps an ISO code to one of the generated `flag_*.xml` vectors.
  The sources and the converter live in `~/.config/opencode/assets/flags`.
- `ui/component/ScrollToTop` — the one back-to-top control, taking either a `LazyListState` or a
  `LazyGridState`; it appears after the list has actually travelled.
- `core/Sort.kt` — `SortControl` (word plus arrow, opens a menu) for list headers; `SortRow` keeps
  the chip row for in-player lists, where every option has to be visible at once.

## Key invariants

1. **The UI never touches NewPipeExtractor.** Everything goes through `VideoSource`.
2. **Stream URLs are never persisted.** Persist the video id and the preferred itag.
3. **Expected failures return `AppResult.Err(message)`.** Programmer errors throw. `LinkageError`
   from a dependency is the one exception worth catching.
4. **One video surface at a time.** Full player or mini card, never both bound.
5. **One theme, live.** Theme changes never recreate the Activity (`configChanges` plus
   `ThemeController`).
6. **Secrets never reach a log or a clipboard.** `BugReport.SECRET` redacts on the way out.
7. **The stack is the truth for navigation.** The pager follows a tab tap; a settle may update the
   stack, but only when it is a single tab.

## Ownership boundaries and layering

```
feature/*  →  playback, data/*, core, ui/*
ui/*       →  core, ui/theme (never data)
data/*     →  core, data/db, data/source
playback   →  core
di         →  everything (composition root only)
```

No feature package imports another feature package; navigation is the go-between.

## Deliberate tradeoffs and risks

- **Manual DI, no Hilt.** One container, explicit lazy singletons. Cost: more wiring; benefit: no
  code generation and no framework to learn.
- **No navigation-compose.** A `List<Route>` is the whole stack. Cost: no deep links or saved state;
  benefit: it fits in a file and the backflip animation can own the transition.
- **Layered navigation.** The pager stays composed under pushed screens, so back is instant. Cost:
  the overlay must be opaque (`ThemeBackdrop`) and the pager must not be swipeable underneath.
- **Glass without blur.** A static aurora is drawn behind translucent surfaces; no RenderEffect, no
  per-frame cost. Cost: it is a styled pane, not a true blur.
- **Minification off.** An untested R8 pass is a crash waiting to happen. Cost: 32 MB APKs.
- **Release signing from env or `keystore.properties`.** Gradle applies the signing config when
  either provides a key; otherwise the APK is unsigned. The manual `apksigner` path remains (see Build).
- **Destructive migrations while pre-release.** Fast schema changes; must be removed before release.

## Failure modes

- **Network down** — `VideoSource` returns `Err`; screens show the message and a retry; the feed
  serves the database.
- **Extractor breaks** — logged with the class and message; the UI shows a readable failure. The
  opt-in canary test is the early warning.
- **Channel gone or empty** — logged per channel; the refresh continues and reports `n of m failed`.
- **Player error** — surfaced in the player with retry; the session keeps its state. The retry is
  the controller's: it fetches fresh streams and resumes at the last position.
- **A stream that stops delivering bytes** — the player has its own OkHttp client (`playerHttp`):
  same pool and DNS cache as the extractor, 8 s connect and read instead of the extractor's 30 s,
  because a silent socket held for half a minute is what "huge queue time" was. A wait past 20 s is
  published as a stall and spends one of the same two retries an error gets. Both retries live in
  `PlaybackController`, so they fire with the screen gone, and the budget belongs to the video, not
  to a visit.
- **Cache trouble** — `FLAG_IGNORE_CACHE_ON_ERROR` keeps a cleared, full or corrupt media cache a
  re-download rather than a dead stream.
- **Seeking back** — a 15 s tail is retained behind the playhead from the previous keyframe
  (`setBackBuffer`), because ExoPlayer's default of zero makes every backward seek a read. Time
  targets outrank the byte heuristic (`setPrioritizeTimeOverSizeThresholds`), otherwise the 30 s
  forward target is clipped at 1080p and above.
- **Bug upload failure** — sealed state machine with a reason; 429 sets a cooldown.
- **Model failure (resync)** — falls back to "no alignment" for that batch; timestamps stay raw.

## Testing

- Pure JVM (JUnit 4): chapters, settings search, importers, time, sorting, the `plural`/`AppResult`
  contract, discover scoring and engine, feed repository, update-manager predicates, bug-upload
  state mapping, thumbnail URLs.
- Robolectric, SDK 35 (`src/test/resources/robolectric.properties`): `cleanHtml`, the settings
  export and restore, in-memory Room queries and Compose UI. Tests run against `TestApp`, not
  `GlassTubeApp`, because the real application schedules the feed worker and WorkManager is not
  initialised on the JVM.
- `ExtractorCanaryTest` is opt-in (`-Dglasstube.canary=true`) and hits the live extractor.
- The suite is never attached to `assembleDebug`: a compile check stays a compile check.
- The suite runs green; 263 tests at the time of writing, 2 skipped (the canary). Added 2026-11-09: `BackupReaderTest` (the file is read by
  content, not by name), `SettingsImportTest` (round trip, unknown keys refused, AI gated on a
  key), the rewritten `SettingsExportTest` (v2 shape, no secret) and recipe `describe` cases. Before
  that: `ContentRepositoryTest` (refresh preserves learned counts), `DiscoverEngineTest` (seen
  filtering and named empty reasons), `BugUploadTest` (429 cooldown, bad replies), importer skipped
  counts, `FeedUiStateTest`, `SortRowTest` (the one Compose test, proving the Robolectric path).
- Room schema v11 is exported and committed under `app/schemas/`. Migration tests still need
  instrumentation; the DAO queries are covered in memory.
- Everything else is a hand check on the Pixel 7 (`28221FDH20001G`). A pass is only as wide as the
  device it ran on, and the owner decides whether it passed.

## Build and release

```
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Release (direct build): `assembleRelease` is unsigned when neither `GLASSTUBE_*` env vars nor an
untracked `app/keystore.properties` provide a key. The key lives in one place outside both app
trees: `C:\Users\Bodi\.config\opencode\keys\presaince-release.keystore`, the same certificate the
Oko release uses. `app/keystore.properties` is untracked and points `GLASSTUBE_KEYSTORE` at its
absolute path; this tree keeps no copy of the key itself. A release built on another machine needs
that keystore and properties supplied. The manual `apksigner` path remains the Oko-compatible
fallback. The APK is copied to `app-release.apk` and uploaded with:

```
node C:\Users\Bodi\.config\opencode\scripts\deploy\deploy.mjs -C D:\Desktop\parallax --config=deploy-apk.properties
```

That uploads to `/other_apps/glasstube/`, where `upload.php` also lives. The portfolio links to that
URL and ships no binaries.

The folder also serves the download page: `web/index.html` and `web/img/*.webp` are uploaded beside
the APK by the config's `extra=` lines. It is one static HTML file with its own CSS, no build step,
wearing the app's own Glass theme — the same near-black base and three accent-adjacent aurora glows
as `glassBlobs`, fixed behind the page, with translucent cards over them — and it reads
`version.json` at load, so the version, notes and hash cannot go stale. The copy is shared with
the portfolio's `parallaxCopy.ts` and kept identical by hand, heading for heading; the only lines
that differ are download-page-specific — the Builds list with the sha256, the footer link, and the
absence of a back button. The captures are copies of the portfolio's `parallax*.webp`, because the
download folder must not depend on another deploy's paths.

A copy-only change ships without a signed release on disk:

```
node C:\Users\Bodi\.config\opencode\scripts\deploy\deploy.mjs -C D:\Desktop\parallax --config=deploy-page.properties
```

That config is `dist=none` with the same four `extra=` entries, so it uploads the page and images and
touches neither the APK nor `version.json`.

Self-update: after signing, `node scripts/make-version-json.mjs --notes "..."` writes `version.json`
beside the signed APK (sha256 of the signed bytes, versionCode/versionName from
`output-metadata.json`). `deploy-apk.properties` allows both files and defers `version.json`, so
the APK goes up first and the manifest second. Bump `versionCode` in `app/build.gradle.kts` before
each release; the client only offers the server build when it is newer.

The bump is a script, not a habit: `node scripts/bump-version.mjs --apply` reads the live manifest,
moves `versionCode` one past the highest number either side knows and the patch of `versionName`
one up, and writes both into `app/build.gradle.kts`. `make-version-json.mjs` refuses to write a
manifest for a code that is already published, so the failure lands before the upload rather than
in every installed app. `node --test scripts/bump-version.test.mjs` covers the pure parts.

## Performance notes

- Gesture state (`minimizeProgress`, offsets) is `mutableFloatStateOf` read only by draw/layout, so
  dragging does not recompose the player.
- The speed dial and the seek pill use a gate, not a second gesture tree: the picture detector is
  disabled while the speed control owns the gesture.
- The picture gesture detector sits on a box that is never transformed. The surface that follows a
  minimise drag is a child inside it, so pointer positions stay in screen space.
- `SurfaceTapZones` is the list of regions on the surface that belong to a control (back, bug).
  It is compared in surface fractions, so it needs no layout measurement.
- Card styling is a revision counter (`CardStyle.revision`) so composed lists repaint only when the
  style changes, not on every recomposition.
- Thumbnails share the app's `OkHttpClient` through Coil.
- The theme is applied through a stable wrapper: switching it cannot remount the navigation.

## Known gaps

- Descriptions do not yet match YouTube's spacing/emphasis; the renderer keeps line breaks only.
- No in-app subscribe: subscriptions arrive only by import (NewPipe JSON, LibreTube JSON, Takeout
  CSV). Entries without a URL are now counted as skipped and named in the import message.
- Bored Mode empty picks are now named: no related videos, everything seen, all subscribed, or
  filters. The cause is in the error message instead of a generic empty page.
- Room is v11 with the schema exported and committed. Before release: real migrations and drop the
  destructive fallback; migration tests need instrumentation.
- No git remote; the repository is local only.
- The direct build's signing step is manual and unverified by a clean install.
- `PlayerScreen.kt` is 1,918 lines with no tests. `PlaybackController`'s pure rules are tested
  (`PlaybackGateTest`, `PlaybackStallTest`, `PlaybackRecoveryTest`); the class itself and every
  ViewModel besides the feed state are not. The player regressions in the UI passes all live in
  that file.
