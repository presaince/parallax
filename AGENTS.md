# GlassTube — project rules

Native Android YouTube client. Kotlin, Compose, Material 3, Media3, Room, WorkManager.
Local-first, GPLv3. Package `com.presaince.glasstube`.

State lives in `ROADMAP.md`; the design is `C:\Users\Bodi\.opencode\plan\parallax-plan.md`.
Structure is `ARCHITECTURE.md`; the product's rules are `BEHAVIORS.md`. Where any of them disagree
with the code, the code wins and the doc is corrected in the same pass.

## Skills for this project

- **`project-docs`** — run it when a milestone changes the structure or behaviour, or when either
  reference has gone stale. It writes `ARCHITECTURE.md` and `BEHAVIORS.md` from the code.

- **`material-design-3-ui`** — load before building or changing any screen or shell. This is a
  Material app; the scaffold, insets, navigation bar and top bar are not optional decoration.
- **`design-taste`** — load before choosing a look, and when a screen reads as a wireframe.
- **`brand`** — wordmark, gradient, splash, About. The P mark needs an explicit yes before a new
  variant is made.
- **`engineering-prompts`** — review and audit modes, plus **Test Pass** (16) and **Debug Pass**
  (17) and the Definition of Done. Use it when writing tests or chasing a bug.
- Not applicable: `deploy` (no server), `seo` (no public pages), `git-push` (no repo yet).

The failure that made this file necessary: a whole milestone set was built as bare `Column` +
`Text`, with content under the status bar, because the platform was never stated anywhere the
agent looked. Start from the shell, then the screens.

## Hard rules

- **Modern devices only: minSdk 33.** No core library desugaring. NewPipeExtractor uses JDK APIs
  that only exist from API 33; that is why the floor is 33.
- **Pixel 7 only** for device work (serial `28221FDH20001G`, `$env:ANDROID_HOME\platform-tools\adb.exe`).
  Other phones are not targets.
- **Ask before adding a dependency.** Manual injection, no Hilt, no Retrofit, no Ktor. Coil is
  approved. `material-icons-extended` is not — copy the vectors instead.
- **No Piped fallback** for now. It returns only if the canary job fails for real.
- **Don't run builds or tests in a loop.** One build to prove a change compiles. One unit-test run
  when the change touches `core/`, `data/` or a ViewModel. The gate is still a hand check on the
  Pixel: a green Gradle log proves the code, not the app.
- **Minimal patches.** Edit, don't rewrite. Files over ~300 lines get edited, not re-sent.

## Clean as you go

Four rules for every change. They are cheap enough to obey, which is the only reason they survive.

- **A new pure function or a written invariant gets one test, or a named reason there is none.**
  The reason is a comment or a line in `ROADMAP.md`, never silence.
- **A bug fix starts with the failing test**, named after the bug. If it cannot be reproduced in a
  test, say what the repro needs before fixing it.
- **Deferred debt gets one line under `ROADMAP.md` → Queued**: the file, what is wrong, what it
  costs to leave it. No new debt file.
- **Never call a change verified because it compiled.** Name what was checked, and on what.

Tests are JUnit 4 in `app/src/test/java/…`, one behaviour per test, named in plain words. Pure logic
runs on the JVM; anything touching `Context`, SharedPreferences or Room runs under Robolectric (SDK
35, pinned in `src/test/resources/robolectric.properties`). The suite is never attached to
`assembleDebug`. Modes 16 and 17 of `engineering-prompts` are the full procedure.

## Invariants

- The UI never touches NewPipeExtractor. Everything goes through `VideoSource`.
- Stream URLs are never persisted. Persist the video id and the preferred itag.
- Expected failures return `AppResult.Err` with a message. Programmer errors throw. Don't catch
  `Throwable`; `LinkageError` from a dependency is the one exception worth considering.
- **One is one.** Counts in UI copy go through `plural(count, "like")`; nobody hand-rolls an `s`.
  "1 replies" and "1 likes" must be impossible to produce.
- Tone: plain sentences in UI copy and comments. No exclamation marks, no "oops".

## Data

- Room is at version 13 with `exportSchema = true` and schemas 11–13 committed under `app/schemas/`.
  There is no destructive fallback: every version bump ships a hand-written migration plus a case in
  `data/db/MigrationTest.kt`, which builds a file from the previous exported schema and opens it at
  the new version. A migration that cannot be tested that way needs a reason in the commit, not a
  skip.
- Cache TTLs from the plan: metadata 6h, comments 1h, related 12h, channel uploads 30m. Only
  comments are cached today; the rest is still TODO.
- Timestamp Resync: off by default, user's own key in the Android Keystore, 5-minute window before
  the timestamp plus a 15-second tail, one call per batch, cached by video + comment + cue hash.
- Notes are transcript-only. Speech is transcribed to text; no audio file is ever written, which
  is why there is no keep-audio toggle.

## Release versioning

- `versionCode` and `versionName` move together, always through
  `node scripts/bump-version.mjs --apply` before a release build. It reads the live `version.json`
  first, so a tree that was never bumped cannot reuse a code the server already serves.
- `scripts/make-version-json.mjs` refuses to write a manifest for a code that is already published.
  When it refuses, bump and rebuild; never edit `version.json` by hand.
- The page serves a signed release, never the debug APK. The two are signed with different keys and
  cannot install over each other, so a tester moving between them uninstalls once and loses the
  local database.

## Layout

The shell is `GlassTubeScaffold`: top app bar with the screen title, a hamburger menu (Settings,
About), and a bottom `NavigationBar` with four destinations — Feed, Search, Library, Bored. Every
screen renders inside it and receives the scaffold padding. Do not add per-screen headers or
"Back" text; the top bar owns both.

```
app/src/main/java/com/presaince/glasstube/
  core/        AppResult, time formatting
  data/        model, source (extractor boundary), db, library (feed), resync, discover,
               auth, secure, cache
  playback/    PlaybackController, PlaybackService
  feature/     one package per screen
  ui/          theme, nav, component (scaffold, tabs)
```

## Bug reports

Reports are filed from the app to `other_apps/glasstube_reports/` on the FTP host and are readable
only over FTP. Never read a bundle whole: they are up to 40KB of HTTP chatter each.

```
node scripts/reports.mjs                    last 3 hours, one block per report
node scripts/reports.mjs --hours=24          wider window
node scripts/reports.mjs --open              what nobody has answered
node scripts/reports.mjs --log=223004        that report's warn and error lines only
node scripts/reports.mjs --set "223004=fixed in PlayerScreen.kt, 2026-11-09"
```

`docs/reports.md` is the record: one line per report, append-only, and a line that has a status is
never rewritten. `ROADMAP.md` holds the grouped reading of the open ones. Credentials come from
`~/.ftp/accounts.properties` and are never printed.

The log itself follows one rule: an entry marks something that happened, not that something was
attempted. `HttpTrace` is where the HTTP shape of that rule lives.

## Build

```
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Canary (network, opt-in): `.\gradlew.bat :app:testDebugUnitTest --tests '*ExtractorCanaryTest*' -Dglasstube.canary=true`

"Deploy" in this project means install the debug build on the phone. It does not mean an upload.
The public release goes up only when a release upload is asked for by name.

## Gates

Complete means built, installed and reported. Whether it works is the owner's call: the agent
never marks a milestone done, and build-green is not a gate pass. One device proves one device, so
a pass is only ever as wide as the device it ran on.

Release is personal only, for the foreseeable future. No F-Droid listing, no public support
promise.

This is a beta with one user: the owner, on the owner's Pixel. Nobody else has installed it. A
regression costs the owner's time and nothing else, so structural churn needs no audience
protection — say so instead of hedging. The release page and live `version.json` exist but nobody
has used them; that stops being true the first time a URL is shared, so say which it is when it
matters.
