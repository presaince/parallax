# Parallax roadmap

Native Android client for YouTube, GPLv3, local-first. The full build plan is
`C:\Users\Bodi\.opencode\plan\parallax-plan.md`; this file tracks state.

Status: M0 gated; M1–M5 built, no gate run yet. M6 scaffolded, needs an OAuth client. M7 tooling in
place. The shell and the second UI pass landed 2026-11-05 (bottom navigation, thumbnails,
inline comments, mini-player, pull-to-refresh); the device check for that pass is still pending.
No git repo — Bodi tests a build for a few days first.

## Phases, 2026-11-07 (third)

The current batch of asks, cut into phases so nothing is dropped and each phase ends on a build.
Phases 1, 2, 3 and 6 are built and installed; the owner's check is what decides them.

1. **About**: the version status is the same text element as the version, not a line below it. Done.
2. **Settings shape**: Surfaces becomes the Glass button itself (top Glass, bottom Solid, split by a
   line, translucent, remembering its last half); Accent moves to second; the layout buttons sit
   above a fixed-height preview so the card cannot move under the finger; "While a video loads"
   becomes its own subsection; Playback's Gentle/Normal/Quick labels lose the stray accented
   character; video quality becomes a horizontal slider with Auto pinned left, 4K, 6K and an
   "AI sim" end stop that is not a number. Done.
3. **Player**: chapters as a row with the time in the far right corner at 90% opacity over the
   text, and no chapters block in the description when they are shown above — built; the notes mic
   row and checkmark no longer shift (spacing fixed); the progress bar's touch band is 44dp while
   swipe-to-seek stays the micro gesture — built; description collapse hides all text — built;
   comments (1) with no comments — count suppressed until a page loads. Still open: rotation, the
   spontaneous stop, and the owner's device check.
4. **Super-minimised player panel**: translucent, a hairline above the navigation bar, and the X
   becomes a restore icon that returns to the full panel instead of closing it.
5. **Settings reorg**: one Accounts & keys page for YouTube and LLM keys, Posting keeps a shortcut;
   Data redesigned into cards with subscriptions as one card and two buttons; logs shown inline in
   Diagnostics instead of one tap away; SponsorBlock loses the subtext under Categories and gains a
   live preview section, planned from the best existing implementation.
6. **Content region flags**: each card's region gets its flag as a full-height background, faint.
   Done with the real 4:3 SVGs from lipis/flag-icons, converted to vector drawables (about 400KB
   of XML) rather than hand-drawn bands, which lost Canada's leaf, Korea's taegeuk and the US
   stars. Sources and converter are kept in `~/.config/opencode/assets/flags`.
7. **Build**: the release signing config never picks up the environment; read an untracked
   `keystore.properties` at configuration time instead, and drop the `-x validateSigningRelease`
   workaround.

## Fourth pass, 2026-11-07

- **History is a view of the feed**: pull to refresh there switches to Feed and refreshes it.
- **Bored recipes are Deep, Long, Short, Fresh**, so a chip cannot wrap. Title is "Bored", not
  "Bored Mode".
- **Why these? is a card**: collapsed shows only the title, the fold arrow is at the bottom under a
  divider, and "Back to the recipe" is gone. Sliders are shorter-labelled and their captions move
  with the thumb; the fetch still waits for finger-up.
- **Search**: a horizontal swipe changes tab; a long press on a recent-search pill deletes just that
  pill; Subscriptions with no matches gets a large centred sentence; Bored searches the typed words
  before walking; the same query no longer refetches and shuffles the list.
- **Minimal thumbnails** put the duration 4dp from the corner instead of 8dp.
- **Multi-region**: up to three content regions, first tapped is main; search runs one extraction
  pass per region and merges by video id. Feed and channel pages stay on the main region, because a
  channel's uploads do not change by country.
- **Bug report switches**: an "Include logs" toggle like oko's, on by default. Logs carry a redacted
  settings dump; off sends the note alone (plus the screenshot if on).
- **From the FTP feedback**: play hides the overlay at once; brightness ignores a paused picture;
  the playing chapter is highlighted; recipe chips do not stay selected; the Feed tab always
  refreshes; a sort change keeps the reader's place.
- **Second FTP batch**: deleting one note offers Undo instead of asking, and cancelling an edit asks
  when there are words to lose; a comment is author and words until tapped, then the likes, age and
  open-in-YouTube appear; the bug screen has one Send and a clear "Sent" state (Copy and Share are
  gone); Bored skips subscribed channels by default behind a gear switch; the player's channel name
  opens the channel page with the video's facts on the right; a seek over two seconds offers Undo on
  the picture; Settings is a grid of three-card rows; chapter timestamps accept brackets, bullets and
  three-digit minutes.
- **2026-11-08 feedback** (13 reports): region cards are 3-per-row so flags are not stretched; the
  full log window lives on Diagnostics; the quality stop labels are buttons so 480 is reachable; the
  Feed tab switches from History without a network refresh; "Send another" is a full-width outlined
  button; Glass has an eight-level slider in the same tile; the player shows Queueing/Broken status;
  the shuffle chip reads "Focus"; super-minimised is reached by swipe up; likes and views show under
  the title; the channel name resolves through the video detail and subscriptions.
- **Player load order**: one staged pipeline — video to the first frame, then chapters, then
  comments, then "Up next" — with the AI-chapter fallback and the first comments page also gated
  on the first frame, tested by `PlayerLoadStageTest`.
- **Player fixes 2026-11-08**: the share timestamp is locked while seeking and only moves on finger
  lift; haptics go through the vibrator as touch usage, gated by a master switch plus per-feature
  switches in the new Feedback settings section and by the system setting; home means zero
  displacement — the seek cancel snap and the return to 1.0× — with chapter-boundary milestones
  and volume/brightness clamps, all tested by `TicksTest`; home is detected as a crossing of zero,
  never equality, since a drag jumps over it; fullscreen uses
  fixed landscape and portrait is restored by the nav, not by the player's dispose.
- **Solid Glass is still Glass**: it raises the card opacity over the same aurora instead of
  falling back to the ordinary Dark/Light schemes. The aurora is now strong (0.52 dark / 0.34
  light) and Glass fill thin (0.42 / 0.66), so the theme reads as glass on a real page; Solid is
  the same theme with the aurora quiet and the cards nearly closed. Tiles that used
  `copy(alpha = x)` now use `faded(x)` so they no longer erase the theme's transparency.
  "Go to end" in the share panel seeks to the actual end.
- **Open question: multi-region.** Built: up to three regions, search fans out, feed stays on the
  main one.

## Settings pass, 2026-11-07 (second)

- **Surfaces is one control, cut across**: Glass on top, Solid underneath, in a single block. Two
  chips side by side read as two options, not as one setting with two positions.
- **Shuffle is a row of four chips**: Shuffle, Daily, Hourly, Open. The master sits beside its
  rhythms; switching it off mutes the other three without moving them.
- **"On open" is now "Open"** everywhere.
- **Loading gains Random**: it picks Kinetic or Still once per video, so the wait is not the same
  loop every time.
- **Preview is the second section**, in a card of its own: the full-width preview, then the three
  layouts (Side, Classic, Minimal), then the six facts as three rows of two tap buttons. Everything
  that changes the preview sits under the preview.
- **Card layouts are real**: `CardStyle.layout` drives `VideoRow`, so Classic is the full-width
  picture with the words below, Minimal is a short scanning row, Side stays the default.
- **Watch history is out of Diagnostics.** It duplicated Library → History, where the same plays are
  already listed. `HistorySection.kt` is still in the tree, uncalled, for a decision on whether to
  delete it.
- **About**: Version and Check for updates share a line with the button on the right edge, and
  "Made by Presaince" closes the page, centred.

## Queue time, 2026-11-09

- **The cause was the shared OkHttp client, not the extractor.** One client with a 30 s read
  timeout served the extractor and the player alike, so a googlevideo edge that stopped sending
  held the first frame for the whole timeout; ExoPlayer's own retry then succeeded silently, which
  is why no error ever reached the screen. The 11:45 report logs `ready in 30567ms` against
  2149 ms for the first video of the same session. The player now has `playerHttp`: the same pool
  and DNS cache, 8 s connect and read.
- **Silence is timed.** `PlaybackController.tick()` measures buffering (`stalledForMs`), logs one
  line at 15 s with the network label, and `play()`/`stop()` reset the stopwatch so a retry re-arms.
- **The pill states one fact.** `playerStatus()` replaces the four booleans folded into
  "Queueing…": Fetching, Loading video, Buffering, Stalled, Broken. A stall arms the same two quiet
  retries an error does, and past them the pill is pressable instead of a dead word.
- Verified by the suite (11 new tests across `PlayerStatusTest` and `PlaybackStallTest`, 0 failures)
  and a clean install. The 30 s figure is read off the owner's log; no hand check has been done yet.

## Cache and retries, 2026-11-09 (second)

- **The disk cache was already there; the size was not.** Media3 `SimpleCache` keyed video + itag
  had been written with a 100 MB default, which is about three minutes of 720p. Default is now
  2 GB with Off / 1GB / 2GB / 4GB, so "seek back a few minutes" is a cache hit and a two-hour
  podcast at 720p fits whole. It is an LRU, so the ceiling is the ceiling; the system clearing
  `cacheDir` is the one thing that empties it.
- **The memory buffer came down to 60 s.** At 1080p and above the old 90 s held hundreds of MB of
  a byte the disk now has; 30 s ahead is what keeps a jittery connection fed.
- **A broken cache costs a download, not the picture.** `FLAG_IGNORE_CACHE_ON_ERROR`, because a
  span can vanish under a playing stream when Android clears the cache directory.
- **The quiet retries moved into `PlaybackController`.** They were a `LaunchedEffect`, so a stall
  on the mini-player or with the activity gone was never retried, and the budget reset on every
  screen visit. Now: a `refreshStreams` lambda wired in `AppContainer`, a pure `shouldRecover`
  rule, two tries per video, and a retry that never starts a video the viewer paused. The pill's
  tap is `retryNow()` and resets the budget on purpose.
- **The cache index is timed, not guessed.** One log line (`media cache N keys, XMB, indexed in
  Yms`) on first use answers whether a 2 GB folder is a 30 ms or a 1 s scan; the construction
  stays lazy until a log says otherwise.
- **Seeking back two seconds was two seconds of loading.** ExoPlayer's `DEFAULT_BACK_BUFFER_DURATION_MS`
  is 0: nothing behind the playhead is retained, so every backward seek left the buffer and paid a
  read. With the cache off (the stored `stream_cache_mb` was 0 on the owner's phone) that read was
  the network. The player now keeps a 15 s tail from the previous keyframe, and the time targets
  outrank the byte heuristic, which had been clipping the 30 s forward buffer at 1080p and above.
- **Off moved out of the first chip** in the cache row, where a thumb reaching for the setting
  could switch off the thing that makes seeking cheap.
- Verified by `:app:assembleDebug` and `:app:testDebugUnitTest` (5 new tests in
  `PlaybackRecoveryTest`), 0 failures. No hand check yet: the seek-back and reopen paths are the
  owner's to judge.

## Asked, not done (second FTP batch, 2026-11-09)

- **A pasted or clipboard YouTube link should open the video** instead of being searched as words.
- **Comments expand on a row tap only.** The chevron closes them; tapping a reply must not collapse
  the thread.

## Asked, not done (2026-11-07)

What has been asked for and is still outstanding, so it cannot be quietly dropped:

- [x] **Card border as a signal** — watch progress only (2dp primary when >5%), removed 2026-11-08.
- [x] **The AI page** — stub page on the Resync key (summary/ask/blurbs announced) 2026-11-08.
- [ ] **Voice-note count on cards**, an icon rail in the thumbnail corner, and smart placement that
      samples the frame for dark or empty regions.
- [x] **Download integrity** — already in UpdateInfo (sha256 + version.json) 2026-11-08.
- [x] **The cache TTLs from the plan** — metadata 6h, related 12h, uploads 30m in NewPipeSource;
      comments 1h in ContentRepository 2026-11-08.
- [x] **Room before release** — schema v11 exported, destructive fallback dropped 2026-11-08.
- [ ] **Device gates** — M1–M5 have never had a run on the Pixel; M6 needs an OAuth client.

## UI pass, 2026-11-07

- **The minimise drag actually starts.** `resolveAxis` returned `None` for a vertical drag in the
  centre, so the gesture the player was written to handle never reached it. Every zone now resolves
  vertical: volume left, brightness right, minimise centre.
- **The drag survives the surface moving.** The pointer detector sits on a box that does not
  transform, so travel is read in screen space; a vertical gesture is no longer abandoned when the
  finger crosses the picture's old edge. That was the "5% then stops".
- **Tap dead zones are a registry.** `SurfaceTapZones` names rectangles in surface fractions; the
  back arrow and bug button own the top corners, and a tap there no longer toggles the controls.
  The next control is one entry.
- **Description collapse is the first four lines**, counted from the top, not a fixed-height slice
  that landed on a random paragraph.
- **Shuffle is a master with a cadence**: Daily, Hourly or On open. Solid surfaces is a two-button
  choice of Glass or Solid instead of a lone switch.
- **The loading wait is the thumbnail itself**: it breathes and a band of light crosses it.
  Kinetic, Still or None, under an animations master.
- **Video cards are sideways** — picture left, words right — so roughly twice as many fit on
  screen. The Appearance preview draws the same shape.
- **History is a real button**: a named pill on the feed header and a full-width row in Library;
  the sort row has its own line; clear history is three named buttons instead of three unlabelled
  icons.
- **A mid-video stream failure retries twice** at the position it reached before the error is
  shown, and the log now names an unexpected `IDLE`.

## UI pass, 2026-11-06 (third)

- **Search lives on Feed and Library only.** The magnifier is no longer offered on Bored Mode,
  Settings, a channel page or anywhere else: search is a library action, not a global one.
- **Watch history is a section.** Settings → Diagnostics now has a collapsible "Watch history"
  block: every play, newest first, with how long ago it was, the date and time, the video's length,
  and the position it was left at. 103 events were on the phone at the time of writing. Tapping one
  reopens that video.
- **Comments are collapsible.** The heading — with the count — opens and closes the list, like the
  description.
- **Two reference files exist now.** `ARCHITECTURE.md` (packages, ownership, data flow, invariants,
  tradeoffs, build, known gaps) and `BEHAVIORS.md` (one rule per surface, with its edge cases and the
  file that enforces it) were written from the code, not from the plan. A new skill, `project-docs`,
  produces and maintains them; `AGENTS.md` names it.
- **Glass has no light/dark chips.** It takes its tone from the last ordinary theme chosen: tap
  Dark, then Glass, and the glass is dark; Light first and it is light; System first and it follows
  the phone. That is the same answer with one row fewer, and the base is remembered
  (`glass_base_mode`) so reopening the picker does not change the look.
- **The mini player can be waved away.** A sideways swipe slides the card out and it stays gone
  while the audio keeps playing; a swipe up still reopens the player, and the X is the one control
  that actually stops. The card comes back for a different video, or when the full player is
  opened again.
- **Swipe to seek has a rate, not a ratio.** It used to map finger travel to a share of the whole
  video, so a short swipe on a long video jumped minutes — worst from a standstill. It now moves in
  seconds: the first tenth of the screen is worth the chosen base (Settings → Playback → Swipe to
  seek: 2, 5 or 10 s), and the rate climbs up to eight times as the finger keeps travelling, so the
  far end of a long video is still a few swipes away. The far edge no longer needs a microscope.
- **Share is one panel.** The share pill opens a sheet with two modes: "This moment" (the link
  carries the playhead) and "A clip" (the link carries the start, the end travels in the text). The
  bar carries a thin playhead line that follows the video while the sheet is open, either end can
  be played to, and "Go to end" stops two seconds early so the last moment is still ahead of you.
  "Sync to now" pulls the end — or the moment — to wherever the video is, which is the precise way
  to place a short clip in a long video. A long press on the pill skips the panel and shares the
  plain link, which is the fastest path for the thing people send most.
- **Bug reports have a drop box.** The bug screen gained a note field and a Send button that POSTs
  one JSON bundle to `https://odesaplay.com.ua/other_apps/parallax/upload.php` — Oko's path, moved
  into Parallax's own folder. The bundle carries the build, the device, the last 300 log lines
  (redacted), the note, and the screenshot when the switch is on. Reports land in
  `other_apps/parallax_reports/<device>_<versionCode>_<timestamp>.json`, beside the app's web
  folder; an `.htaccess` there answers 403 over HTTP, so they are readable only over FTP. Verified
  from the Pixel: the server replied with the stored filename, and a GET of that file is 403.
- **Navigation is layered.** The tab pager is the floor of the app and every pushed route (player,
  channel, settings, search, collections come later) is drawn over it instead of replacing it. Back
  reveals a feed that never left composition, so the minimise gesture no longer waits for a refetch
  and settings can be left without the list rebuilding behind it. This is the suspected cause of the
  "endless settings back loop", now structurally impossible to hit that way.
- **Speed, rebuilt rather than patched.** One gate disables the picture gesture while the panel is
  open *and* while the dial is held, so a drag on the panel can never also be a brightness swipe.
  The panel fills from a `BoxWithConstraints`, so the fill edge is a fraction of the panel's own
  height instead of the screen's. The hold flag is cleared in a `finally`, so an interrupted gesture
  cannot leave the player deaf.
- **Selectable text.** Description (collapsed and expanded) and comment and reply bodies are in
  `SelectionContainer`s, one per card so a drag does not try to select across the list. Timestamps
  inside the description keep their own tap-to-seek.
- **Notes:** the timestamp, copy and delete sit in one row under the text, right-aligned; the
  microphone moved into the header row; the empty row above the field is gone; copy, delete and the
  word "All" share one small bordered capsule. Saving a dictated note resumes the video that the
  microphone paused.
- **Collections.** The old Presets screen is gone as a route. Its four rules are now a third reading
  of the Library, beside Grid and By channel, under a chip row, renamed Collections.
- **Glass tone:** System / Dark / Light, because glass is a transparency and what shows through it
  can be either. **Shuffle daily** picks a new accent once a day, from the same 18-hue field, and
  remembers the date it last moved.
- **Clip share.** A range icon beside the share pill opens a sheet with a two-handle timeline. The
  link carries the start; the range travels in the text, because a YouTube URL cannot hold both.
- **About:** the "Parallax" heading wears the same accent gradient as the wordmark in the top bar.
- **Card preview reacts.** The Appearance preview's card is drawn from the same seven switches: the
  title, the assembled meta line and the duration badge appear and vanish as they are toggled. The
  style also bumps a revision, so cards already composed underneath Settings repaint when the
  switches move — layered navigation otherwise leaves them holding the old style until a restart.
- **About is one page.** The About section was a single row that pushed a second About screen; the
  section now *is* that screen. Version moved under the tagline, and the pushed route is gone.
- **Two regressions the layering exposed, fixed.** Leaving fullscreen went to `UNSPECIFIED`, which
  hands the choice back to a phone still held sideways, so the whole app stayed landscape — it now
  restores `PORTRAIT`. And because Glass paints a transparent background, the pager underneath a
  pushed screen showed through its cards; `ThemeBackdrop` paints the aurora (or the theme background)
  as the floor of every overlay.
- **The direct build.** A signed release APK for handing to people, built with the release key via
  `apksigner` (32 MB, minification still off). Under AGP 9.4.1 the release signing config in
  `app/build.gradle.kts` is not applied: `assembleRelease` writes an unsigned APK even with the
  `PARALLAX_*` variables set. Packaging plus a manual `apksigner` pass is the working path; worth
  fixing before the build is automated.
- **Live pill** is sticky for three hours (`liveSeenAt`, DB v10): two listings that disagree can no
  longer make it blink.

## UI pass, 2026-11-05 (second)

- Shell: bottom navigation, top bar with a menu, real insets; per-screen headers removed.
- Thumbnails via Coil on the shared OkHttp client, with an `i.ytimg.com` fallback; one `VideoRow`
  used by feed, search, library, channel and the walk.
- Feed refreshes on a downward pull, not a header button.
- Player page: description with a collapse chevron, comments inline, no SponsorBlock toggle, no
  Post. Notes renamed **My notes**.
- Comments: aligned timestamps appear inline; a small icon opens the comment in YouTube; posting
  and replies appear only when enabled and signed in.
- Mini-player: live video above the navigation bar while the player screen is closed; tap to
  expand, buttons to pause and close.
- Notification taps resume the activity through the media session's session activity.

## Testing, 2026-11-08 — what "fix them" produced

- **Feed refresh no longer erases learned counts.** `ContentRepository` now loads `learnedCounts()`
  once per refresh and carries `likeCount`/`commentCount` forward across the REPLACE upsert.
  `ContentRepositoryTest` proves it against an in-memory Room with a fake source.
- **`DiscoverEngine` has a seam.** A `DiscoverStore` interface replaces the concrete `ParallaxDb`;
  `RoomDiscoverStore` is the production adapter. `DiscoverEngineTest` pins seen filtering, and the
  empty pick set now returns a named error (no related, all seen, all subscribed, or filters)
  instead of a silent empty page.
- **`BugUpload` contract is pure.** `stateFor()` maps code, headers and body to a state without a
  socket. `BugUploadTest` covers 429 with and without `Retry-After`, clamping to 5–300s, bad
  replies and non-2xx.
- **Importer now counts skips.** Entries without a URL are counted and reported as
  "N skipped (no URL)" in both Library and Settings, so a 39-entry file landing 38 rows names the
  missing entry. No longer silent.
- **Release signing reads `app/keystore.properties`.** Env vars win; the untracked properties file
  is the local fallback. Unsigned without either, which is the documented path.
- **Room schema v11 exported.** `exportSchema = true`, schemas committed under `app/schemas/`,
  KSP wired with `room.schemaLocation`. Migration tests still need instrumentation.
- **`plural` handles the regular rules.** Sibilants take -es, consonant+y takes -ies; `"3 watchs"`
  is no longer reachable through the default.
- **One Compose test** (`SortRowTest`) so the Robolectric UI path is exercised, plus
  `FeedUiStateTest` for the pure feed state.
- **Spontaneous stop is diagnosable, not invented-fixed.** `PlaybackService` now logs when it
  stops itself at ENDED with the playhead. Without a device repro the root cause is still open.
- 163 tests run, 2 skipped (the canary). `assembleDebug` and `assembleRelease` pass (release
  unsigned without a key, as documented).

Not fixed, deliberately: `PlayerScreen.kt` unit tests (1,918 lines, needs device-first slicing),
`PlaybackController` ExoPlayer tests, migration instrumentation, and every ViewModel beyond the
feed state. Each is listed in ARCHITECTURE "Known gaps".

## Testing, 2026-11-07

- **The suite has a second tier.** Robolectric 4.17 (SDK 35) joins JUnit 4, so `cleanHtml`, the
  settings export, in-memory Room and Compose can be tested on the JVM. `TestApp` replaces
  `ParallaxApp` in tests: the real one schedules the feed worker, and WorkManager is not initialised
  there.
- **`UpdateManagerTest` is ported from Oko.** Eleven tests over the six predicates that decide
  whether an APK is trusted, installable and uncorrupted. It found nothing wrong, which is the
  point of porting a test that already exists.
- **`cleanHtml` had a real bug, now fixed.** Entities without a single tag ("I&amp;apos;m") skipped
  the parser and reached the screen verbatim. `HtmlTest` fails without the fix.
- **Four more files**: `SortTest` (the comparator five lists share), `ContractTest` (`plural`,
  `attempt`), `SettingsExportTest` (no key or token leaves the device), `DbSmokeTest` (the
  hand-written SQL).
- **Two things the tests found and did not fix**, listed under Asked, not done: a feed refresh
  erases the learned like and comment counts, and the discover engine has no seam to test through.
- 115 tests run, 1 skipped (the canary).

## Locked decisions

- Kotlin, Compose, Material 3, ViewModel + StateFlow, Media3, Room, WorkManager.
- NewPipeExtractor as a dependency, never vendored. Licence GPLv3. minSdk 33.
- Package `com.presaince.parallax` (changeable until first publish).
- Accent is Presaince brand cyan; the gradient is for wordmark, splash and About only.
- No debate transport, no clip payload, no P2P sync, no third-party skins, no Slack Circuit.
- Download is a single simple path and comes last.
- Manual dependency injection; ask before adding a library.

## Queued

- `ui/nav/ParallaxNav.kt` - `prefetchStreams` fires on every Player route entry, including re-entering the video that is already loaded and about to be reused, so a warm restart pays one extractor fetch for nothing. Cost: a needless request on a shared client; found 2026-11-09 while moving the retries.
- `data/library/ContentRepository.kt` - `refreshFeed` spins `while (playbackBusy()) delay(400)` with no ceiling, so a buffering player can hold a refresh open indefinitely. Cost: a refresh that reads as a hang. Found 2026-11-09 while fixing the stall; not changed in that patch.
- `data/source/SelfRepair.kt` — data-driven extractor (JSON path model) is not built; LLM repair covers search query and region only. Cost of leaving it: field renames still need a code change instead of a model publish.
- `feature/**` — about thirty call sites still paint accent *text* with `colorScheme.primary` instead of `accentTextColour()`/`LocalAccentInk` (nav labels, chips and Settings are converted; feed, player, comments, library are not). Cost of leaving it: at AA and AAA those labels keep their raw accent, so a magenta or blue accent still sits near its own tinted surface. Found 2026-11-09 while adding the Contrast card.

## Milestones

- [x] **M0** scaffold, extractor wrapper, search, playback. Gate: a search result plays.
      Built 2026-11-05: builds clean, unit tests pass, APK 15.1 MB. Gate verified 2026-11-05 on
      the Pixel 7: query "lofi" returned results, a result played past 84 s with media_session
      reporting PLAYING.
- [ ] **M1** player service, background audio, gestures, SponsorBlock, A-B loop. Gate: a week of daily use.
      Verified: service foreground notification, double-tap ±10 s, search→player navigation.
      Unverified after the deadline rewrite: long-press 2x (expect media_session speed 2.0 while
      held), edge-band behaviour, boost restore after a manual speed change.
- [ ] **M2** library, subscription import, feed, comment reader, history. Gate: the YouTube app stops being opened.
      Built 2026-11-05: `channelUploads`/`comments`/`related` on `VideoSource`; `ContentRepository`
      with a ±4 h self-rescheduling feed worker; comment paging with an on-disk cache; `&lc=` jump
      out to YouTube; continue-watching and history in Library; Library and Feed reachable from
      search, comments and notes from the player.
- [ ] **M3** local debate deck with timestamped notes and voice-to-text.
      Built 2026-11-05: notes table, per-video deck, note at the current position, edit before
      save, dictation via `SpeechRecognizer` with no audio file written. Keep-audio toggle is not
      built, because there is no audio to keep.
- [ ] **M4** Timestamp Resync, BYOK, off by default.
      Built 2026-11-05: VTT/SRT/TTML parsing, 5-minute-before window with a 5 s tail, one call per
      batch (10 comments, 60k cue characters), reply validation that refuses a later second,
      cache keyed by video + comment + cue-slice hash, key encrypted with the Android Keystore.
- [ ] **M5** Bored Mode and the recipe format.
      Built 2026-11-05: strict recipe validator (unknown keys, ranges, vocabulary), four built-in
      JSON recipes in assets, optional user import, bounded walk that keeps the path and never
      revisits a node. Score uses views and recency only — see the deviation note below.
- [ ] **M6** optional OAuth for posting (hidden until enabled).
      Built 2026-11-05: PKCE authorization-code flow with a loopback redirect against a Desktop-app
      OAuth client of the user's own, token refresh, `commentThreads.insert`. Nothing is initialised
      unless posting is on. Needs a real client id to run the gate.
- [ ] **M7** release: signed APK via GitHub Actions, F-Droid metadata, GPLv3 headers.
      In place 2026-11-05: build/canary/release workflows, release signing from env or secrets,
      README, `docs/PRIVACY.md`, fastlane metadata, GPLv3 headers on every Kotlin file.
      Not done: keystore secrets, a device run of the release variant, store screenshots.

## UI pass, 2026-11-05 (third)

- Feed and History are tabs of one destination; History shows thumbnails and watch progress.
- Player: three tabs (Description, Comments, My notes) under the video; the top bar no longer
  repeats the title. Transport is a seek bar plus back-10, play/pause, forward-10 and an overflow
  menu where A-B loop now lives. Vertical brightness/volume gestures are off until there is a
  fullscreen mode - they were stealing scrolls.
- Library: a grid of channel avatars, and a "By channel" view that lists each subscription with
  its stored videos. Toggle in the header.
- Comments: HTML entities decoded (twice, because YouTube double-encodes), replies expand in place.
- Mini-player: swipe up restores the full player, swipe down closes it; tap still works.
- Search moved out of the bottom bar into a top-bar action.
- DB v6 adds thumbnails to progress and history, with a real migration so subscriptions survive.
- 37 of 38 channel avatars fill on refresh.

## UI pass, 2026-11-05 (fourth)

- Feed top bar shows the Presaince mark and wordmark, copied from the portfolio favicon.
- Player controls live on the video, fade after three seconds, and toggle with a single tap.
  Speed is 0.1 steps with 0.8/1.0/1.2/1.5/2.0 presets, remembered across videos.
- Fullscreen toggle in the overlay: landscape, system bars hidden.
- Settings regrouped: Appearance (theme), Playback (SponsorBlock master plus eight categories),
  Resync, Posting, Diagnostics, About.
- Theme: System / Dark / Light, with a light scheme; switching is live and does not recreate the
  activity, so Settings stays open.
- Accent dots: cyan, violet, amber, green, rose. The Parallax wordmark follows the accent; the
  Presaince wordmark on About stays on the brand ramp.
- Import and export live in Settings only: NewPipe zip or JSON, LibreTube JSON, Takeout CSV in;
  subscriptions out as JSON, settings out as JSON without keys.
- Logs screen under Settings: plain text, 48 hours, capped, local. Player errors log there.
- Search: focused on open, no floating label, tabs for all of YouTube, subscriptions only, and a
  walk out of the best match; local search history.

## UI pass, 2026-11-06

- Vertical swipes on the video: left is volume, right is brightness, with the HUD label while
  dragging. Downward drag past a fifth of the height minimises.
- Double-tap seek shows a "» 10s" / "10s «" chip for 600ms.
- Landscape is treated as fullscreen: system bars hide and the tabs drop, so rotating and pressing
  the fullscreen button agree.
- Mini-player has a 2dp full-length progress line.
- Pburger is tinted with the primary role.
- Bug report is reachable from the player overlay as well as the top bar.
- Library → Presets: gathering dust, almost done, they're back, never opened, with an AI panel
  reserved above them.
- Skills: `glassmorphism` added (RenderEffect on 31+, scrim fallback, contrast helper);
  `material-design-3-ui` gained tap-off dismissal, scrim-blocks-taps and the checkbox rule.
- `LEDGER.md` in the OpenCode config holds bugs, upgrades and features by project.
- Oko's AGENTS.md now points at Parallax's settings for convergence.

## UI pass, 2026-11-06 (tenth)

- Speed panel: same capsule shape as the other readouts, with the speed as a rising fill edge
  instead of a track and thumb.
- Library: history icon in its header too, which switches to the feed with history already on.
- Loading a video: the thumbnail behind the sine river instead of a spinner.
- Comments: a person mark on reply authors; an aligned comment's card takes a primary tint.
- Notes: the tools (copy-all, delete-all, count) sit on the left, appear only with two notes and
  only while no draft exists — the delete-all that ate a list cannot be reached mid-edit. Rows are
  newest first and slide in. Tapping the field stops the microphone and frees playback.
- Playback: video quality (Auto/1080/720/480/360/240) as a ceiling for new videos, honoured by the
  extractor; the chosen value is shown beside the speed as a readout.
- Bored Mode: one-level pick list with a refresh, river empty state, import in Settings.

## UI pass, 2026-11-06 (ninth)

- Settings → Playback opens with a gesture cheat sheet: tap, slide to seek, left/right slides for
  volume and brightness, speed hold-slide, flick down. The speed list is now called "Speeds in the
  slider", and 3.0, 4.0 and 5.0 each start their own line under the 0.1 ladder.
- Speed panel: flush with the left edge, 90% of the picture tall, with the track and its fill
  pinned to that edge. Tap a value to pick it and close; slide anywhere on the panel to preview,
  the number changes as you go, and the speed applies on release.
- Description: when collapsed, the text itself is the expand target, not just the header; links
  still open and, once expanded, selection returns.
- Share: tap sends the plain link, long tap sends it with the current timestamp. After three taps
  a one-time toast teaches the long tap.

## UI pass, 2026-11-06 (eighth)

- Media notification: Settings → Playback → "Dictate from the notification". When on, the media
  session drops the seek-to-previous command (the restart button) and publishes a microphone
  custom command with our own icon. Tapping it opens the player with the notes card expanded and
  dictation started; speech recognition stays in the UI, because a background service may not use
  the microphone and may not start an activity on its own. Applies from the next playback session.
- Player: leaving the player while landscape no longer leaves the whole app sideways —
  `requestedOrientation` is reset on dispose.
- Player: the ±10s arrows are gone (tap-slide and the pill replaced them).

## UI pass, 2026-11-06 (seventh)

- Player: the seek bar is gone for good. A 2dp progress line sits on the bottom edge of the
  picture, in portrait and fullscreen; Settings → Playback has "Progress line in fullscreen" to
  remove it there.
- Player: no more double tap. A tap is a tap (so the controls appear immediately), and seeking is
  a horizontal slide. The pill shows the distance as the headline (`+12s`, `−8s`) and the target
  time underneath, with the video's progress inside its bottom edge, placed above the finger that
  made it (below when the finger is near the top).
- Player: the ±10s buttons and the backflip stay; the disc can no longer be placed under the
  control strip — a tap below 72% of the height puts it at the centre instead.
- Player: speed is press, hold, slide in one motion, with the readout stuck to the left edge at
  the finger's height. A press without movement opens the panel. The control consumes its own
  drags so the picture no longer reads them as brightness.
- Player: the share action is a muted pill with the live position; tapping shares the link at that
  second, flashes the pill and raises a toast.
- Player: while loading, the video's thumbnail sits behind a 64dp spinner instead of a black frame.
- Notes: the save tick only exists while the microphone is idle. The field clears focus after a
  save, so no blinking cursor behind a closed keyboard. Each note has copy and delete icons and
  its text is the edit control; the copy-all/delete-all icons appear only at two notes.
- Notes: "heard nothing" no longer exists — a quiet room is not an error — and the river is still
  when not recording, faster and thicker while recording, in three hues around the accent.
- Settings: Appearance gains a real preview card (Bitcoin copy, a comment with a joke); the
  placeholder is "talk or type what you're thinking".

## UI pass, 2026-11-06 (sixth)

- Feed: the Feed/History tabs are gone. One column, with a history icon at the far right of the
  sort row that toggles the list; the icon takes the selected treatment (primary tint on a disc)
  while history is showing. Swiping between the two went with the tabs.
- Sort: the direction arrow is pinned at the start of the row, outside the scrolling chips, so it
  can no longer scroll out of sight. Each list offers only the sorts it can honour: the feed shows
  Date, Duration, Views and A-Z; history shows Date, Watches, Progress and A-Z.
- Collapsible headers: tapping "Description" or "My notes" anywhere on the header row opens the
  section, not just the chevron. The description animates its height; the notes card expands with
  `expandVertically` + `fadeIn`. Rule added to the material-design-3-ui skill.
- Notes: the dictation wave moved into the text field — three sine layers in the accent colour
  crossing the box like a river, faster and taller while recording, dimmed to near-silent when the
  field has focus. The microphone is now a plain button.
- Settings → Playback: "Pop out when you leave the app" turns the PiP detour off.
  `onUserLeaveHint` reads it before entering PiP.
- Playback: audio language. The original track now wins over a dub (or an audio-description
  track); a video with several language tracks is merged from video-only + the chosen audio
  instead of taking the muxed file's default.
- Notes: "Copied 1 note" is singular.

## UI pass, 2026-11-06 (fifth)

- Notes: the text field is always on the card again. Typing the first character starts a note at
  the playhead; the microphone fills the same field, so there is one note and two ways in.
- Notes: each row deletes with a trash icon instead of the word "Delete".
- Notes: the list gets its own bar — the count on the left, copy (all notes as `[time] text`
  lines, then a "Copied N notes" line) and delete-all on the right. Deleting everything asks
  first and offers "Keep".
- Notes: "No notes yet." is gone; the empty field already says the card is empty.

## UI pass, 2026-11-06 (fourth)

- Media notification: the shade and lock screen now carry the video's thumbnail. The artwork URI
  goes on the `MediaMetadata` (the history thumbnail, or `hqdefault` when the model has none), and
  media3's bitmap loader fetches it.
- Notes: the expanded card no longer repeats "My notes"; the section header above already says it,
  so the card is the time, the microphone and the list.
- Speed panel: opens above the control that was tapped (bottom-right) instead of bottom-left, with
  a 2dp vertical track and a thumb showing the current speed. Tapping a preset picks it and closes
  immediately; a drag previews the speed and only applies it on release.

## UI pass, 2026-11-06 (third)

- Player: a video opens bare. Controls start hidden, and the first tap reveals them with the
  play/pause disc at the point you tapped instead of pinned to the centre. Tapping a control no
  longer also toggles the overlay, because the gesture loop now ignores a press a child consumed.
- Player: volume and brightness readouts lost their text. The icon and the fill are the whole
  message.
- Player: volume accumulates in fractional steps instead of rounding each event to an Int, which
  is why a slow swipe could sit still.
- Player: a fast downward flick minimises even when it starts on a volume or brightness band; slow
  side drags still belong to the level. The gesture layer hands the drag to the centre once the
  downward speed passes about 450 dp/s.
- Player: repeated double taps build a burst: each ±10s adds to the position the burst started
  from, so three taps move 30 seconds, not three races against the player's last known position.
- Player: the scrub readout is a wider pill at the top of the picture, or at the bottom when the
  finger is in the top fifth, with the time and a 5dp progress bar inside its bottom edge.
- Player: Share and Open in the browser moved to the right of the action row.
- Comments: the open-in-YouTube control is the YouTube mark, on comments and replies. With no
  YouTube app the link opens the watch URL at the timestamp and says that a browser cannot jump to
  a comment.
- Comments: replies now load from the item's own continuation token, confirmed by a probe test
  against the live extractor (2 replies for the test video). A cached comment page that claims
  replies but carries no token is refetched instead of serving a dead button, and a missing token
  says so rather than doing nothing.

## UI pass, 2026-11-06 (second)

- Player: play/pause moved to the dead centre of the picture, shown and faded with the controls and
  hidden while a gesture readout is up. The bottom row is time, ±10s, speed and fullscreen.
- Player: the Material `Slider` is gone. `SeekBar` draws a 3dp track and a 12dp thumb, with the
  44dp touch band kept invisible, and commits the seek on release.
- Player: volume and brightness gestures show a centred capsule — icon, percentage and a fill that
  rises with the level — instead of a bare word. Verified on the phone at 44%.
- Player: minimise now follows the finger. The picture scales to ~38% and slides toward the mini
  card's corner while dragging, then the player closes.
- Player: speed is one control. Tapping opens a thin vertical panel at the bottom left; drag it for
  0.1 steps or tap a preset. The hold-and-slide dial is gone.
- Notes: tapping the mic no longer crashes. The notes card sat inside the player's `LazyColumn` and
  nested a `LazyColumn` of its own, which Compose rejects with infinite height. It is a plain
  column now. One microphone only: the collapsed card's mic disappears while the card is expanded.
- Settings rebuilt as oko's shape: a root page with a persistent search field and eight section
  rows (icon, title, live summary, chevron). Each section is its own route, so Back returns to the
  list and the top bar owns the title.
- Settings search: curated keywords per row, every query word must hit, prefixes and substrings
  count, and a single typo is tolerated from four letters (`sponsr`, `apearance`, `commnets`).
  Results name their section and open it. Unit-tested in `SettingsSearchTest`.
- Appearance: theme as three labelled pills; accent as a 6×3 circle grid, selection ringed.
  Accents now have spoken names ("Emerald") instead of the internal `h6`.

## UI pass, 2026-11-05 (fifth)

- Top bar: Search, Bug, and the Pburger (pburger1 from portfolio/p-burger) which opens Settings
  directly. The popup menu is gone.
- Live and upcoming videos carry a pill in the thumbnail corner (LIVE in red, UPCOMING in dark),
  detected from the extractor's stream type and its "Premieres in…" text. A Settings switch hides
  them from the feed, search and channel pages.
- Bug report captures a screenshot with PixelCopy on the way in, previews it, and attaches it to
  the share. Verified on the phone: the preview and the log both appear.
- Logs are actually written now: feed refresh start/finish with counts and duration, per-channel
  failures, play/stop, player errors, source errors. The first real entry was a premiere that
  cannot play ("LIVE_STREAM_OFFLINE: Premieres in 27 hours").
- Swipe between destinations: top-level pager for Feed/Library/Bored, inner pager for Feed/History.
  The navigation bar stays visible; swiping is an addition, not a replacement.
- History: red remove icon on the thumbnail corner; clear today / this week / all as icon buttons
  under one "Clear history" label, each confirmed.
- Sorting: one `SortRow` and `SortState` shared by lists — date, likes, watches, progress, duration,
  views, title, with an ascending/descending toggle. Wired into Feed and History. Likes are stored
  per video (DB v7) the first time a video is opened.
- Skill: `material-design-3-ui` now says a group of small actions gets one label and icon buttons
  with tooltips, never repeated words on every button.

## Deviations from the plan, with reasons

- **Feed scheduling** uses one-time work that reschedules itself with ±30 min jitter, not
  `PeriodicWorkRequest`: WorkManager has no jitter for periodic work.
- **No DataStore.** Settings still live in `SharedPreferences`. The plan lists DataStore, but there
  is one small prefs file and moving it buys nothing yet. Revisit when settings grow a UI section.
- **No Coil.** Rows render title, channel, duration and age; thumbnails are in the model but not
  drawn. Adding an image loader is a dependency decision for later.
- **Recipe schema is smaller than the sketch.** `maxSubs`, `language`, `likePerView` and
  `commentPerView` are absent because related items carry none of that without an extra extractor
  call per candidate. Every field in the shipping schema is implemented and validated.
- **Minification is off** in release. An R8 pass that has never run on a device is a crash waiting
  for the first user; `proguard-rules.pro` is ready for the day it is enabled.
- **`exportSchema = false`.** Turn it on and commit the schema before the first release, or the
  first migration is written blind. The DB currently bumps versions with a destructive fallback
  because nothing has shipped.

## Open

- [ ] Buy `presaince.com.ua` (also in the master roadmap, 2026-11-05).
- [x] App mark: layered P approved 2026-11-05. Reuses the favicon path. Splash and About only.
- [ ] Parallax is a Presaince product, so the `brand` skill applies in full. The layered P is the
      first mark built for the app rather than copied; the wordmark stays "Presaince".
- [x] Personal release only, for the foreseeable future. F-Droid and public-release items are out.
- [x] Resync tail: 15 seconds past the timestamp, confirmed 2026-11-05.
- [ ] M1 gate: a week of daily use. Long-press 2x, edge bands, PiP and boost restore are still
      unverified on the phone.
- [ ] M6 gate needs a Google OAuth client (Desktop app type, YouTube Data API v3) supplied by Bodi.
- [ ] Remove `fallbackToDestructiveMigration` and enable schema export before the first release.
- [ ] `implementation_plan(1).md` sits in the repo root; decide whether it stays.
