# GlassTube roadmap

Native Android client for YouTube, GPLv3, local-first. The full build plan is
`C:\Users\Bodi\.opencode\plan\parallax-plan.md`; this file tracks state.

Status: M0 gated; M1–M5 built, no gate run yet. M6 scaffolded, needs an OAuth client. M7 tooling in
place. The shell and the second UI pass landed 2026-11-05 (bottom navigation, thumbnails,
inline comments, mini-player, pull-to-refresh); the device check for that pass is still pending.
No git repo — Bodi tests a build for a few days first.

## The stall the ladder could not see, 2026-11-12

Owner: `110306`, "loading video buffers then stops then reverts to previous video". He would not file
another report, so the answer came off the log on the phone instead of from waiting for one.

- **Two rules in two files, and neither could see the other.** The quality ladder lived in
  `PlayerScreen` and counted only `choppyForMs`; choppy needs `STATE_READY`, and a hard stall is
  `STATE_BUFFERING`, so the stall he complained about could never count as an episode. The recovery
  budget lived in `PlaybackController` and spent both quiet tries at the same height. The log says it
  line for line: `choppy runway=2602ms` at `458981ms`, `stalled 15085ms`, `stall recovery 1/2 …
  target=720p`, `stalled 15063ms`, `stall recovery 2/2 … target=720p`, then silence — dead at 720p
  with the ladder still at two of its three steps.
- **One owner, one rule.** `playback/PlaybackTick.kt` holds `tickAction`: no video, no connection or
  a retry already in flight does nothing; an error goes straight to a fresh extract, because a
  rejected URL is not a height problem; a full 20 s stall steps one rung down **before** any quiet
  try is spent; then a quiet try; then one `gave up` line instead of buffering in silence. A choppy
  picture that never fully stalls still has to meet the tolerance, and with nowhere to step it is
  left alone rather than spending a retry on a stream that is merely slow.
- **A stall is conclusive on its own.** The tolerance window is 30 s and stalls arrive 20 s apart, so
  three of them can never fit inside it — waiting for the count was waiting for the quiet tries to
  run out, which is exactly how the log ends. A stall is still stamped as an episode, so a mixed run
  of choppy and stalled spells reads as one bad connection.
- **The step down is the session's, not the reader's.** It writes `KEY_QUALITY_SESSION`, and the
  picker clears it, so a bad ten minutes on a train does not rewrite the ceiling they chose; the next
  video starts at their pick. `qualityInForce` is the one-line rule and `NewPipeSource` reads it
  before `snapToLadder`.
- **The screen keeps only the sentence.** `degradedTo` drives the notice and the picker calls
  `playback.setQuality()`, so the ladder now acts with the screen gone or on the mini-player.
- **`110114` was marked done and was not.** It claimed a video that keeps stalling drops one stop;
  only a picture that was still playing could reach that rule. Corrected in `docs/reports.md`.
- **Migration tests, without a phone.** `data/db/MigrationTest.kt` builds a version-11 file from the
  exported `createSql`, seeds a history row and a subscription, and opens it at 13 through the real
  `Room.databaseBuilder`, checking both survived and that the hidden set arrived writable. Hand-rolled
  because `androidx.room:room-testing` wants an `androidTest` source set; 11 → 13 only, because 5–10
  were never exported.
- **The player's arithmetic left the screen.** `PlayerFormat.kt` holds `segmentFrameMs`,
  `segmentThumbUrl`, `scrubProgress` and `volumeFraction`, with `PlayerFormatTest` on them. Only what
  was branchy and untested moved; the rest of the 3,514-line screen is unchanged.
- **A filed report's log line carries the note** (`112509`, asked three times). The line was
  `filed <name>` — a file name nobody chose — so Diagnostics was a list of things sent rather than a
  history of what the reader said. It now reads `filed <name>: <note>`, flattened and capped at 120
  characters, because one entry has to stay one readable line. `filedLine` is pure and has four
  cases.
- Verified: `:app:testDebugUnitTest` green, 540 tests in 87 files, 0 failures, including
  `PlaybackTickTest` (14), `PlayerFormatTest` (10), a `qualityInForce` case and `MigrationTest` (3);
  `:app:assembleDebug`, installed on the Pixel 7 over the existing build. **The gate is the hand
  check**: a video that buffers should now log `degrade to …` and keep playing.

## The offer in plain words, 2026-11-12

Owner: the hero was AI slop; self-repair should be a key feature; the screen-off paragraph and its
card were pointless; PiP is table stakes; "playhead" is jargon.

- **Hero is `YouTube, without the account.`** and the sub says what it costs: no account, the
  database is on the phone, audio survives the screen going dark, and the demo is the app.
- **The screen-off section is gone**, with its notification card. The hero sentence carries the
  claim; the picture was decoration.
- **PiP and Resync tiles are gone**; two replace them: one file for the whole phone (subscriptions,
  settings, recipes, notes, history, resume points — `Backup.kt`), and AI as an optional mode on
  the reader's own key, off until added.
- **Self-repair is its own block**, and it is honest: a reset and one retry on an extractor break,
  a refetch and two retries on a stalled stream (20 s, `STALL_LIMIT_MS`), and a re-asked search. It
  does not claim to patch a changed YouTube page shape — that needs a new build, which About
  installs from the page. The old clients waited for a person to ship a version.
- **The share sample is a 5:00 video** (1:36–2:06), so the two handles sit 10% apart instead of
  overlapping; the sheet's own rule is unchanged.
- **"Playhead" is gone from the copy**: the notes tile says "the time you were at".
- **One demo, not two.** The portfolio's GlassTube page no longer rebuilds the demo or the install
  sheet: the three interactive mocks are deleted, its CTA is "Try it in the browser" to the download
  page, and its showcase rows show the app's own screenshots. Its "On the phone" section went with
  them, because it repeated the same captures. The two pages now share one demo, one install flow
  and one hero.
- **The install sheet is gone.** The CTA is a plain APK link and the hero's one line about Android
  asking is the whole instruction; the dialog, its script, its CSS and the version wiring all went.
- **The "Everything stays on the phone" tile was a lie.** It is now "The library is a file on the
  phone": the database is local, the app talks to YouTube directly, there is no GlassTube account or
  server, and the AI mode talks only to the model the reader points it at. The portfolio's copy got
  the same sentence.
- **Search repair is no longer named.** It was a specific fallback nobody meets; the self-repair
  block now says "several layers" (reset, stall refetch, search retry) and keeps the honest limit.

## The page is the demo, 2026-11-11

Owner: "all the controls should be the CTA" — the page should read as "try a mock of this app in
the browser", not a marketing scroll with three mocks buried in it.

- **One demo surface in the hero**, not a phone on a desk: it fills its column (460–580 px tall,
  no bezel) and becomes edge to edge on a phone. Three tabs: Gestures, Share, Appearance.
- **Gestures is the first scene and the real control**: the app's own zones — 24 px system edge,
  then thirds, volume left, brightness right, seek in the middle — and the app's own pills: a level
  pill (fill, percentage, icon) for volume and brightness, a named pill for the seek.
- **Glass is the app's model, not an approximation**: three aurora blobs at the accent, +52 and −46
  degrees, at the app's peak, and `auroraScale` and `maxGlassLevel` ported, so Standard and High dim
  the glow, clamp the dial, and the card can say "Glow at 95% keeps the page readable". The
  percentage on the tile is the clamped level, and the chips, tiles and contrast card use the glass
  variant fill, so the level visibly moves them.
- **One state paints the whole demo**: picking Light on the Appearance tab turns the shell, the
  cards and the sheet light; Glass paints the aurora behind the screen, so the translucent cards have
  something to be translucent over.
- **Feed and Player are gone.** Feed showed content, not a feature; the player's SponsorBlock, speed
  and caption claims are feature tiles, and the card the system draws is a static section ("The
  screen goes off. The audio does not.") with the honest note that the lock screen is Android's.
- **Not claimed: a custom lock screen.** It would need a `showWhenLocked` activity over the keyguard,
  which hides the reader's lock screen and only works while the app is in front.
- Verified in the browser: three scenes, the volume and brightness level pills in the right zones,
  the seek pill naming the jump, the aurora dimmed 95% at Standard and 65% at High, the glass fill
  at `--glass-a` 0.300 at 100, the variant fill and panel on the same alphas, the "nothing here can
  reach it" sentence at light glass High (the accent-fill pair), and no console errors.

## The download opens the sheet, 2026-11-11

Owner: the "card repaints" caption was in a horrible spot, and the page should have one download
moment rather than four.

- **The caption is gone.** The widget's pressed states are the instruction; a footnote 300 px below
  the card was doing nothing.
- **The install card became the sheet the CTA opens** — `dialog.sheet`, drawn as the app's own
  bottom sheet (28 px top corners, grip, `#101014`, the app's inks), with the three steps and the
  version-labelled button last, so the steps are read before the file leaves the page. The hero's
  button and the "If it breaks" link open it; a modifier click, or no JS, still downloads the file.
  Builds keeps its per-build links.
- **The sheet closes on its button, the backdrop, Esc or the X**, and focus returns to whatever
  opened it. `version.json` fills the hero label, the sheet's label and its version line.
- Verified in the browser: open, scroll lock and focus; close by X, backdrop and `cancel`; a
  ctrl-click passes through untouched; three steps in one column at the app's colours.
- The portfolio page carries the same sheet (`GlassTubeBeta.tsx`, a `dialog` opened from its CTA),
  so all three URLs offer the steps before the file.

## The page shows the app's own controls, 2026-11-11

Owner's note: "the marketing page's controls don't match precisely the app's" — especially the
preview card and the share card.

- **The Appearance demo is `PreviewSection` now.** The size chips (Small/Medium/Large — a setting the
  app does not have) are gone; the three layouts (Side, Classic, Minimal) and ten fact buttons
  (Title, Channel, How long ago, Views, Likes, Duration, Watch count, Comments, Progress, My notes)
  replace them, with the meta line built in `PreviewCard`'s order and joined with the same no-break
  dot. The card wears the app's scheme colours, the progress line sits over the thumbnail at 35 %,
  and the notes and length badges are where the app draws them.
- **The accent wheel is the real one**: 18 hues at 20° steps with the brand cyan in the 200° slot,
  six per row, plus the System row. The eight hand-picked hexes were not the app's colours.
- **Glass is a tile with a level**, dragged vertically, default 0.42, and it takes its light or dark
  from the last ordinary theme picked, as `glassDarkFor` does. Shuffle (master + Daily, Hourly,
  Focus) and Contrast (Off, Standard, High) are in the widget; the ratio is measured from the card's
  own colours, not asserted.
- **The share card is `ShareSheet`.** The invented "This moment / A clip" chips are gone; the end
  handle is the clip. Sync to now is in the header, the six nudge buttons, Go to start / Go to end
  and the Share button are all there, the bar is 6/8/13/3 px as the canvas draws it, and the labels
  are derived from the handle positions. Dragging the end onto the start makes it a moment
  (`at 6:12`), which is the app's rule.
- **The lock-screen card carries four transport buttons and the channel name.** `notification_restart`
  is on by default, and the notification's second line is `setArtist`, not a playing state.
- **The copy stops saying the end goes in the text.** The app puts `&end=` in the same link;
  YouTube ignores it and GlassTube reads it.
- **The portfolio page is the second copy** (`GlassTubeBeta.tsx` `ClipMock`/`AppearanceWidget` and
  `glasstubeCopy.ts`) and moved with it: two pages that sell one app should show the same controls.
- Verified in the browser on both pages: every theme repaints the card (`#101014`, `#fff`, and the
  rgba fills at the level), the 18 swatches and the System row select, the layouts produce 40/60,
  26/74 and full-width columns, the fact buttons switch their own part (title, views, duration,
  notes and progress driven one by one; the six meta facts share one builder), the share labels
  follow the handles and collapse to `at 6:12`, and `tsc -b` is clean on the portfolio. Not
  installed, not deployed.

- **Needs the owner**: whether the page's Glass-tile drag and the share bar feel right on a phone
  browser; the site is checked with a mouse so far.

- **Queued below**: the app's own `PreviewCard` still disagrees with `VideoRow`/`Thumbnail`.

## The gesture lesson is the player's gestures, 2026-11-11

Owner's note: the lesson "should look and behave identical". It had its own pointer loop and its
own pills, and taught three gestures the player does not answer to.

- **One detector.** The lesson's 140-line `pointerInput` is gone; the stage runs
  `Modifier.playbackGestures`. So it now has the player's touch slop and 1.5x axis bias, `zoneFor`'s
  24 dp system edge and thirds, the 80 % seek start, the finger leaving the picture cancelling a
  seek, and the bottom 44 dp band moving the playhead with no pill.
- **One readout.** `Hud`, `HudPill`, `ScrubPill` and `LevelTravel` moved out of `PlayerScreen` into
  `ui/component/GestureHud.kt`, and both screens draw from them. The fixed 130x40 top-centre pill is
  gone; the lesson's seek now shows distance, target time, progress edge, and follows the finger.
  The wireframe picture keeps a stand-in seven-minute playhead so the pill has a target to name.
- **One ladder rule.** `gesture/LadderStep.kt` owns the step decision — 0.22 of the height, or a
  0.03 flick inside 260 ms — and the player and the lesson both call it. The lesson used to step on
  any 12 % drag at release, which is why a slow nudge minimised there and did nothing in the player.
- **Kept**: the wireframe stage, the dashed targets, the step line, the four-column progress, the
  mini and bar mock cards, the hints, Restart tutorial. Those are teaching, not imitation.
- **The page holds still while the finger is down.** Consuming the drag was not enough — an
  enclosing `verticalScroll` sees the event before the stage and scrolls anyway, so the page slid
  under the finger. `ui/component/PageScrollLock.kt` is provided by `Page` in Settings and by the
  wizard's demo page; the lesson freezes the scroll for the length of the press.
- Verified: `:app:compileDebugKotlin` clean, `LadderStepTest` (6) and `GestureLessonTest` (4) green.
  Not installed: the Pixel detached before the install.
- **Needs the phone**: the lesson stepping exactly when the player would, the seek pill following
  the finger instead of sitting at the top, and a drag on the lesson never moving the page.

## Captions, and sound that is shaped on request, 2026-11-11

The two reports that had been open since the 0.3.11 batch: `112435` (captions) and `215202` (an
audio stabiliser and maybe an EQ). Two builds and two installs, one feature each, so a phone check
can blame the right one.

- **Captions are the video's own tracks, drawn by the app** (`112435`). `VideoSource` grew
  `captionTracks` and `captions(id, languageTag)`; `subtitles` is now `captions(id, null)`, so the
  resync and AI-chapter paths are untouched. The list comes from the cached `StreamInfo`, so it
  costs no request of its own; the chosen track is fetched after the first frame, on the same gate
  SponsorBlock segments use.
- **A CC control in the picture's top row**, beside speed and quality, so it exists in portrait and
  fullscreen. It appears only when the video has tracks. Off, then one row per track, human before
  machine, with the stored language preselected.
- **Cues are read off the playback tick.** `cueAt` binary-searches the track and joins overlapping
  cues; an end is exclusive, so a line does not linger for a tick. `pickCaptionTrack`,
  `captionPick` and `CaptionSize` hold the rules worth testing, and `CaptionsTest` pins them.
- **A track tap is the ask.** The first build stored the language and fetched the cues but never
  set `captionsOn`, so picking a language changed nothing anyone could see — the CC label and the
  menu's own highlight are gated on the same flag. `captionPick` now returns the language and the
  on/off together, `chooseCaptionTrack` is the menu's only entry point, and the screen calls
  `fetchCuesIfOn` so switching captions on from the Playback setting also reaches an open video.
- **Text sits above the bar and lifts when the controls fade in.** One `CaptionOverlay` in both
  shells, on a per-caption scrim rather than a permanent band, at Small, Medium or Large, and it
  takes no pointer input, so the gestures work through it.
- **Sound is a new Settings page**: an equaliser with four shapes on five fixed bands (60, 230, 910,
  3600, 14000 Hz), a level slider to +12 dB, and "even out volume" (`215202`). `playback/Sound.kt`
  owns `DynamicsProcessing` and `LoudnessEnhancer`, attached to an audio session the controller
  chooses and hands to the player, because Media3 has no getter for the one it would generate. The
  limiter at -1 dB is what keeps a boosted band or level from clipping.
- **Even out volume is a compressor, and it is named that way on purpose.** Nothing in the Android
  audio APIs measures a track's loudness, so this is a one-band compressor with makeup gain plus the
  limiter, not normalisation. The summary line on the page says what it does instead of borrowing a
  word it cannot honour.
- **A refused effect costs the shaping, never the video.** Every attach and write is its own
  `runCatching` with one log line, so a device without the hardware still plays. `AudioTuning` has
  no unit test on purpose: it is hardware effects behind those guards, and a Robolectric pass would
  only prove the shadow exists, not that the phone shapes sound.
- Verified: `:app:testDebugUnitTest` (471 tests, 0 failures, 3 skipped, including the 20 new) and
  `:app:assembleDebug`, installed over the previous build.
- **Needs the phone**: a video with captions showing them after one tap on CC, the language
  surviving into the next video, the sizes, and the Sound page changing the timbre of a video that
  is already playing.
- **Still open, and why**: `110306` (a picture that reverts to the previous video), `113155` (a back
  swipe that does nothing).

## Look, feed and behaviour split into their own pages, 2026-11-11

Sixth pass on the 0.3.11 batch, from the owner's note that some Playback rows are really
Appearance.

- **Appearance owns what anything looks like.** Five rows left Playback: the fullscreen progress
  bar, the notification's restart button, the tint from the video, the chapter style, the video
  page order, chapter timestamps and the section labels. They sit under two new captions — *The
  player* and *The video page* — after Motion. Playback is now gestures, seek base, quality,
  cache and picture-in-picture: the page is what its name says.
- **Feed is a fourth page.** Hide live and upcoming, the live-tab fetch, the watch threshold, the
  hide-watched slider, Related at the end and the Bored skip switch were rows on Playback (or, for
  Bored, only behind a switch on the Bored screen). One `FEED` section, and the index is ten cards
  three across. Playback's summary lost the "live shown" half; Feed gained it.
- **Search was missing eleven rows.** Chapters, page order, chapter timestamps, section labels,
  progress bar, restart button, tint, animations, loading style, stream cache, feed live, watched,
  watch threshold and related had no entry, and four entries named a row that did not exist under
  that name. Every row on every page now has one entry whose title is the words on screen
  (`SettingsRegistry.kt`), and `SettingsSearchTest` pins each moved row to its section.
- **A row's own words now beat a keyword.** The ranker treated title tokens and keywords as equal,
  so "chapters" answered with the AI entry, which merely mentions chapters. A keyword costs
  `KEYWORD_PENALTY` more than the same word in the title.
- **Solid surfaces is gone.** `chooseSolidSurfaces(true)` was never called and `SurfaceHalf` was an
  unused composable, so the split Glass/Solid tile `BEHAVIORS.md` promised had not existed for
  passes. Removed from `Theme.kt` (fourteen `solid` parameters), `ThemeController`, the backup
  schema, `MainActivity` and the contrast test; the Glass dial is the only transparency control.
- **The live-fetch switch never did anything until a restart.** The row wrote `feed_live` and never
  called `ContentRepository.setFeedLive`, which `AppContainer` had already read once. It now tells
  the repository in the same tap, and the live tab is not asked at all while live is hidden — the
  rows would be filtered out at render. The Feed copy says so.
- **A search result can start something.** "Run setup again" opened About, which has no such
  control. `SettingsEntry.runs` marks it as an action: the result launches the wizard from the
  index, and its subtitle says "Starts the setup".
- **The Bored switch follows the stored key** through a preference listener, so the gear and
  Settings → Feed cannot show different values while both screens are composed.
- Verified: `:app:testDebugUnitTest` (437 tests, 0 failures, 3 skipped) and `:app:assembleDebug`.
  Not opened on a phone, not installed.
- **Needs the phone**: the ten-card index, the Appearance page's two new captions, the Feed page,
  a search for "chapters" landing on Appearance, and the Bored gear matching Settings after a
  change.

## A menu the picture cannot take away, 2026-11-12

The owner reported the CC list closing while a finger was on it. It was not the list: the controls
hold it, the idle timer hid the controls three seconds after they appeared, and Compose dismissed a
`DropdownMenu` whose anchor had gone. The speed panel was the only popup the timer knew about, and
the clock measured "since the controls appeared", not "since the last touch".

- **One overlay value, not five booleans.** `PlayerOverlay` (`None | Speed | Quality | Captions`) is
  owned by `PlayerScreen` and set by the two menus in `ControlsOverlay`; `speedPanelOpen` and the
  two local `…MenuOpen` states are gone.
- **The idle rule is pure.** `shouldHideControls(visible, playing, overlay)` returns true only for a
  visible, playing picture with nothing open; `ControlsIdleTest` pins the pause case, all three
  popups, and the already-hidden case. The effect keys on `interaction`, which every tap bumps, so a
  menu opened two seconds late still gets the full `CONTROLS_TIMEOUT_MS`.
- **A tap on the picture with a popup open closes the popup**, not the controls, so the menu cannot
  be dismissed into a bare picture by the same finger that just opened it.
- Verified: `:app:testDebugUnitTest` (540 tests, 0 failures) and `:app:assembleDebug`, installed on
  the Pixel 7.
- **Needs the phone**: open CC, wait past three seconds, pick a track; open the quality menu and
  wait; tap the picture with a menu open.

## A chapter's own picture, and the marks that were arriving as numbers, 2026-11-12

Three reports that were each one small glyph being wrong.

- **A chapter draws the frame it starts on** (`111049`). The row showed the upload's thumbnail
  repeated, and the roadmap had chapter thumbnails queued as a feature. `PreviewFrames.cellAt`
  answers with the page image that holds a moment plus the cell's rectangle **in that image's
  pixels**; `StoryboardTile` asks Coil for that page at its own size and draws the rectangle with
  `drawImage(src, dst)`. The first attempt — a box scaled to the grid and offset — was a jumble on
  the owner's phone: a box-sized request hands Coil a twenty-five cell sheet at about twelve pixels
  a cell, then blows it up. `PreviewTile` is now a thin wrapper for the sponsor row's 64×36
  bordered case, and `ChapterFrame` falls back to the upload thumbnail when a video has no
  storyboard or the moment's page is not in the list.
- **The storyboard arithmetic was wrong twice.** `tileAt` read the column as
  `relative % framesPerPageY`, so a grid that is not square drew the row below; and a frame past
  the reported count walked one cell past the end. Both are gone with `cellAt`, which also refuses
  a page the url list does not have instead of stretching the last one. `PreviewFramesTest` pins a
  4×5 grid, the page boundary, the past-the-end case, a short page list and a broken grid.
- **The chapter block says what it has** — one line per open chapter row: `storyboard pages=…
  grid=… frames=… eachMs=…` — so "the app has no frames" and "the row asked for a page that is not
  there" stop looking the same from a report.
- **A caption's apostrophe was a number** (`115549`). YouTube writes `&#39;` in a cue and the
  parser kept it. `core/Entities.kt` decodes the entity table without Android — the caption parser
  runs in `data/`, where a JVM test should not need the platform — and `SubtitleParser.clean` uses
  it. Unknown names, a bare `&` and a broken number all stay as they arrived.
- **A mark that arrives as an image is now the mark** (`121704`). `Html.fromHtml` drops an `<img>`
  with no `ImageGetter`, so the description's emoji vanished. `cleanHtml` replaces an image tag with
  its alt text before parsing. No test needed for the platform parser itself; the two new cases in
  `HtmlTest` are about the rescue, not the parser.
- Verified: `:app:testDebugUnitTest` (502 tests, 0 failures, 3 skipped) and `:app:assembleDebug`,
  installed on the Pixel 7. The first test run failed on a file lock from a stale Gradle daemon
  (`bundleDebugClassesToCompileJar/classes.jar`); `gradlew --stop` cleared it.
- **Needs the phone**: a video with chapters, each tile a different picture; a caption with an
  apostrophe; the description's Instagram line showing the mark.

## Two sentences and a toggle, 2026-11-11

The two reports from tonight's batch that had no unknown in them. The streaming fault behind them
is `C:\Users\Bodi\.opencode\plan\glasstube-streaming-repair.md`.

- **A stream that has not begun says so.** `192409` and `195831` both open on `EW9uZkrXBOA`, a
  premiere. The extractor answers with the same generic unavailability it uses for a removed video
  — `LIVE_STREAM_OFFLINE: "This live event will begin in a few moments."` — so `toSourceError` fell
  through to `PrivateOrRemoved`: the overlay read "private or has been removed", offered **Hide
  this video**, and would have written a premiere into Hidden as a dead video. `SourceError.NotStarted`
  is recognised by wording, like `MembersOnly` already is, and is deliberately **not** permanent, so
  the screen keeps its Retry: the video plays once it starts. The wording match is narrow on
  purpose — "has ended" is still private-or-removed, and a test pins that.
- **The hamburger is a toggle.** `201231`: it called `onOpenSettings`, which brings Settings forward
  and stops, so a press while Settings was open did nothing at all. A press now calls `back()` when
  Settings is current — out of the section first, then out of Settings — and `menuOpen` and the two
  unused `DropdownMenu` imports are gone. There is no dropdown menu: the mark is the Settings
  button, which AGENTS.md still described as a menu with an About entry that has no route.
- Verified: `:app:testDebugUnitTest` (473 tests, 0 failures, 3 skipped) and `:app:assembleDebug`,
  installed on the Pixel 7 over the existing build.
- **Needs the phone**: a press on the hamburger while in Settings closes it; a press inside a
  Settings section leaves the section first.
- **Not fixed, deliberately**: `195831`'s other half — the player still spends both quiet tries on
  a live URL that returns no bytes. That is the repair ladder, not a sentence.

## A refresh with a number, and a feed you can leave things out of, 2026-11-11

Fifth pass on the 0.3.11 batch, from what the owner found while using the last two builds.

- **The refresh says where it is.** `ContentRepository.feedProgress` is a `StateFlow<FeedProgress?>`
  published per channel — `12 of 47 · 26% · Hard Reset` — and cleared in a `finally`, so a cancelled
  refresh cannot leave a counter on screen. `FeedProgress` and `refreshLabel` are in `core/`, and
  `FeedProgressTest` pins the flooring (47 of 48 is 97%, never 100%) and the blank-name case.
- **Long press a card to leave it out of the feed.** Hide is a Room table (`hidden_videos`, v13,
  `MIGRATION_12_13`, `schemas/13.json`), not a column: a refresh REPLACEs every video row, so a flag
  there would be wiped on the next fetch. A hidden row copies the title and picture, so the Hidden
  list still names a video that has left the feed.
- **Two scopes, one table.** `hide this video`, and — on a live stream — `hide this channel's live
  streams`, keyed `live:<channelUrl>` and applied only to `LIVE` rows of that channel, because a
  perm-live channel's uploads are still worth having. `visibleVideos` is the pure filter and
  `HiddenTest` covers both kinds, the overlap, and the keys staying distinct.
- **Hidden is a mode, not a setting.** The header's single History pill became `Feed · History ·
  Hidden`, and the Hidden list shows the title, the reason ("private or removed", "hidden by you")
  and one Unhide. A hide you cannot find again is a delete.
- **A permanent failure can hide itself.** The player's dead-stream screen now offers **Hide this
  video**, which writes the row with the extractor's own reason. That is the answer to the live
  stream that opens to "private" — a members-only stream cannot play here, so the fix is to stop
  the feed offering it, one tap, with a record of why.
- **The player's Up next card had the wrong inner padding** (16dp all round against 16/4 in every
  other card), which made it taller than its neighbours for no reason. Now 16/4 like the rest.
- **One progress line, drawn three ways.** The bar had been split 50/50 by construction — segments
  on the top half, playhead on the bottom half — which read as two stacked lines. Segments now fill
  the whole 6dp strip, and `SponsorBarStyle` (Settings → SponsorBlock → Progress bar) picks how the
  playhead shares it: **Covered** (accent covers what is watched, the default), **Tinted** (an
  accent wash over the bands), **Hairline** (a 2dp accent line on the centre line). The choice is a
  preference key, read into `PlaybackState`, so it redraws on the open video; `SponsorBarStyleTest`
  pins the default and the key round-trip.
- **One type scale for cards.** The player page was carrying three section headers at 13sp
  (`labelMedium`) and three at 15sp (`titleSmall`) in one column, and the mini-player's title was
  13sp where every video card's is 15sp. `ui/component/CardText.kt` is now the five-part scale —
  header 15sp accent, title 15sp, meta 13sp, body 15sp, action 12sp — and VideoRow, MiniPlayer,
  PlayerScreen's six section headers, the comment row and the note row all read from it. No test:
  it is a table of styles, and a rendered assertion would only pin the theme back at itself.
- **Up next walks, one hop per tap.** The refresh button only reshuffled the same related list:
  `relatedFresh` refetched a fixed list and the screen shuffled it, so the list was recycled in a
  different order. It now opens one video from the pool — `RelatedWalk.hop`, the traversal Bored
  already used, pulled out of `DiscoverEngine` so both screens share it. The pool shows the whole
  related page and grows to sixty, seeded from the shallowest unopened video (**Wider**) or the
  newest (**Deeper**), a two-state `ValueRow` under Settings → Playback → Recommendations whose
  subtext names the mode in force. `relatedFresh` and its cache-bypass branch are gone.
  `RelatedWalkTest` and `RelatedPoolTest` pin the hop and the frontier; Bored's own suite is
  unchanged and still green, which is how the extraction is proved to be behaviour-preserving.
- Verified: `:app:testDebugUnitTest` (422 tests, 0 failures, 3 skipped) and `:app:assembleDebug`,
  installed on the Pixel 7 over the existing build.
- **Needs the phone**: a refresh that counts up as it goes; a long press that hides a card and a
  live stream; the Hidden list unhiding one; and the Up next card matching the others.
- **Not built, deliberately**: hidden rows do not filter Bored or Search. Those are "find me
  something" surfaces, and a matching video is a true answer there.

## Surfaces, the bug sheet, and a quality that steps down, 2026-11-11Fourth pass on the 0.3.11 batch. It starts with the two chrome moves the owner asked for while the
last build was going in, then takes everything on the list that had no unknown in it.

- **Speed, quality and fullscreen are the picture's top row.** The bar owns the bottom edge, so the
  strip moved to `TopCenter` with 48dp of start padding for the back arrow. The sponsor chips and
  the clip banner dropped to 64dp so they clear it, and the status pill moved to 40dp above the
  bar.
- **The bug button is on the left of the action row**, share and open-in-browser on the right, so a
  mis-aimed thumb does not file a report.
- **A tap on a comment opens the most of what is hidden** (`105921`): the text if it was cut off,
  otherwise its replies. Unfolding a comment whose words already fit used to be a tap that did
  nothing visible. A reply's body is now the same size as the comment's (`110831`); the indent and
  the name are what make it quieter.
- **The player's sections share one card grammar** (`114914`) through
  `ui/component/SectionCard.kt`: the card is the header (8dp margin, 16dp radius, 16dp inner), and
  every body flows under it on the page at the same 24dp text edge. It was `Modifier.playerCard()`
  in `PlayerScreen.kt` and three other containers — comments had no card, the notes body had its
  own at a different radius, and Up next had a card inside a card, so the left edge drifted 24 to
  32dp down the page. Comments are rows with dividers now, not bubbles; an aligned comment keeps
  its tint as a band behind the row; My notes has no surface and its microphone is the field's
  trailing icon, which took ~44dp off the section.
- **The scrub pill follows the finger** (`111834`) instead of centring itself on the picture, using
  the same clamp the level readouts use.
- **The speed panel and the dial stop where the presets stop** (`111118`). The panel used to walk
  0.3–5.0 regardless of the reader's list.
- **The bug sheet keeps its draft** (`110857`) in a small object outside the composition, cleared
  only when the server has the report, and **the log below shows the filed line** (`113229`) because
  the snapshot is retaken when a report lands.
- **A video that keeps stalling drops one stop** (`110114`). `lowerStop` is the pure rule
  (Auto steps to 720, the smallest stop has nowhere to go) and the pick was written to the ladder in
  force. **It only worked for a picture that was still playing** — see "The stall the ladder could not
  see, 2026-11-12", which moved the rule into `PlaybackController` and made a hard stall count.
  `111445` is answered rather than fixed: a lower stop is a
  different file, so there is nothing buffered to reuse — the pill now says **"Switching quality…"**
  instead of reading like a stall.
- **The buffering pill carries its percentage** (`120655`): `PlaybackState.bufferedPercent`, sampled
  only while the picture is waiting.
- **The feed can hide what you have watched** (`205752`): it already could, from the Feed section's
  slider; this pass only removed a duplicate filter it briefly grew. **Live streams now reach the
  feed** (`104716`): the uploads list never carries a stream that is happening now, so a refresh
  asks each channel's Live tab too — but only for channels whose own tab list reports one, because
  asking a channel without it is a failure the repair path would log as a fault. `feed_live` turns
  it off, which halves the requests per refresh.
- **Auto starts every video at 720p** (`115657`). On Wi-Fi Auto used to be uncapped, which on this
  phone is Full HD video-only plus a separate audio track: two connections and twice the bytes
  before the first frame, and the reports show fifteen-second first frames. `firstLoadHeight` is now
  one rule for both connections, the pill still reports what actually played ("Auto (720p)"), and an
  explicit stop is still honoured, so the ladder above 720 is one tap away rather than the default.
  `lowerStop` steps down from Auto to 720 when a video keeps stalling.
- Verified: `:app:testDebugUnitTest` (411 tests, 0 failures, 3 skipped) and `:app:assembleDebug`,
  installed on the Pixel 7 over the existing build.
- **Needs the phone**: the top strip against the bar at the bottom, a comment that opens its
  replies on one tap, the cards, the panel stopping at 2.0x if that is where the presets stop, and
  a report that is still there after backing out and returning.
- **Still open, and why**: `110306` is now explained by "The stall the ladder could not see,
  2026-11-12" and needs the hand check, not more code; `112435` (captions), `215202` (audio EQ),
  `113155` (a back swipe that does nothing).

## The bar at the bottom, a band the finger can reach, and a page in the reader's order, 2026-11-11

Third pass on the 0.3.11 batch. The reports were all note-only except `115657`, and two of them
were about the same fault from different ends.

- **The progress bar is the picture's bottom edge, with a time at each end.** `111214` ("move the
  progress bar back to bottom of video") reverses the top placement from yesterday, and `112240`
  says how: the bar belongs *between* the elapsed time and the length, not beside a "0:42 / 12:07"
  cell. `progressLine` is a Row of time, track, length now, the playhead dot sits on the track's
  centre line instead of straddling it, and the control strip moved up 34dp to clear it. The strip
  lost its own copy of the times, because the bar carries them.
- **The bar's touch band followed it to the bottom** (`inBarBand`, `BAR_BAND_DP` up from the bottom)
  and the two corner exclusions went with the bar: there is one dead zone on the picture now, the
  back arrow's.
- **A level behaves on a band, and the ends are a magnet.** `core/LevelBand.kt` is the rule: the
  strip starts 44dp below the top of the picture and 12dp above the bottom, a finger outside it
  moves nothing (so a drag can no longer creep a level while the thumb grinds at the status bar),
  and entering the last 12dp at either end means that end. This is the answer to `111118`'s "my
  finger has to swipe to uncomfy top screen pos to try and reach 100": the rate is unchanged, but
  the last few percent no longer need centimetres the screen does not have. A drag that *starts*
  inside an end zone is disarmed for that end on purpose — otherwise a swipe that began at 95% of
  the height would jump the volume to full on its first pixel. `LevelBandTest` pins all of it,
  including the short-picture case where the magnets would meet.
- **The readout takes the band as well** (`readoutTop`'s `bandTop`), so the pill cannot drift into
  the chrome the finger cannot reach.
- **The description keeps its timestamps.** `descriptionBody` was filtering every chapter line out
  once the parser found three, on the argument that the same list twice is noise. The text version
  is the only one that is selectable and copyable, so the lines stay, and the duplication became a
  setting: `chapters_horizontal` (a bool) is `chapters_style` (Row, List, Hidden). A pref written
  before this pass restores to Row.
- **Description is the first card.** The page's order is now data: `PlayerSection` is the one name
  per section, `resolveSectionOrder` makes a stored order safe (unknown names dropped, missing
  sections appended in their default place, duplicates keep their first position), and the player
  emits its items with `for (section in sectionOrder) when (section)`. Settings → Playback →
  **Video page order** lists the six sections and swaps them on a long-press drag: a fixed 52dp row
  and a swap at half a row, because there is no drag-and-drop dependency and six rows do not need
  one. `PlayerSectionTest` covers the three ways a stored order can rot.
- **A title that is cut off is a control.** Two lines, then a tap shows the whole thing; no chevron,
  because the ellipsis is the affordance. The tap only exists when the collapsed layout actually
  overflowed, so a short title is not a dead target.
- **The bug button left the picture.** It sat in the top-right corner of the video, which is a
  button in the way of the thing being watched. It is in the page's action row now, beside share and
  open-in-browser. Cost: no bug button in fullscreen, which is fine for a temporary one.
- Verified: `:app:testDebugUnitTest` (410 tests, 0 failures, 3 skipped) and `:app:assembleDebug`.
- **Not installed: the phone left `adb` while this was being built** (`adb devices` empty). The APK
  is at `app\build\outputs\apk\debug\app-debug.apk`; the install is one `adb install -r`.
- **Needs the phone**: the bar at the bottom with a time at each end, a level that reaches 100 from
  a start near the top, a description that still lists its chapters, Description above Chapters,
  and a drag in Video page order that survives the page scrolling.
- Not done, and next: **`114914`** (the sections as rounded cards with a chevron), **`113229`**
  (a filed report staying in the log), **`110857`** (the bug draft surviving a close), **`105921`**
  (smart expand), **`110831`** (reply type size), **`111118`**'s second half (the speed panel should
  offer only the presets the reader kept).


## Two ladders, and sound with the screen off, 2026-11-10

- **Quality is per connection.** `video_quality` is the Wi-Fi stop and `video_quality_cellular` the
  mobile-data one; `qualityKey`/`qualityFor` in `core/Quality.kt` are the only rule, and everything
  not cellular — Wi-Fi, Ethernet, a VPN that reports no transport, no network — reads the Wi-Fi
  stop. Settings edits one ladder at a time behind a Wi-Fi / Mobile data chip, the player's menu
  writes the ladder in force and names it in the menu, and the backup carries both fields.
- **The ladder is YouTube's, and Auto is the top of it.** Stops are 144, 240, 360, 480, 720, 1080,
  1440, 2160 and then Auto; 6K, 8K and the "AI sim" joke stop are gone, because this phone cannot
  decode what they asked the server for. Auto is uncapped on Wi-Fi and capped at 720p on mobile
  data (`firstLoadHeight` takes the transport), so "the best this phone can decode" is what it now
  does on the connection where a slow first frame costs nothing.
- **The ladder above 720p was dead, and now it is not.** The old pick came from `videoStreams`, the
  progressive set, which tops out at 720p — so Full HD, QHD, 4K and AI sim all played the same file
  on any video with one audio track. `streams()` now takes `videoOnlyStreams` + `audioStreams`
  whenever both exist, and falls back to muxed only when there is no split set (a live stream).
- **The ladder stops at the panel.** `qualityStops(panelShortSidePx)` cuts the list at the screen's
  short side — on the Pixel's 1080p panel that is 144 through Full HD, then Auto — so QHD and 4K are
  not offered anywhere, in Settings or in the player's menu. Auto's ceiling is the same panel
  width. `snapToLadder` moves a stored stop above the cut down to the tallest one offered, and the
  Settings screen rewrites the stored value once, so a 4K choice made before the cap does not keep
  fetching 4K in the background.
- **The readout stopped lying.** A 4K tap on a video with no 4K rendition used to log
  `target=2160p`, take the warm-restart path on the identical URL, and still say "4K". `pick()` now
  returns the stream, `streams()` logs the height it actually chose, and `qualityLabel` appends the
  resolved height whenever it differs: "Full HD (720p)", "Auto (1440p)".
- **The quality slider never dragged.** `WaveQualitySlider` keyed its `pointerInput` on `index`, so
  the first step of a drag recomposed and cancelled the gesture — one stop per touch, which reads
  as tap-only. It keys on `count` and reads the stop through `rememberUpdatedState`, and the track
  and its label row are inset 16dp so the end stops are off the screen edge.
- **Split is what makes the screen-off claim real.** `PlaybackController` listens for
  `ACTION_SCREEN_OFF`/`ACTION_SCREEN_ON` and disables the video track while the screen is dark: the
  video source stops being read, the audio source is never re-prepared, so the bytes stop and the
  sound does not gap. The player takes `C.WAKE_MODE_NETWORK` as well, because with no decoder
  running there is nothing else holding the CPU up between buffers.
- **Screen-off ran 54 s on the Pixel with no gap.** The log shows `video track off`, then 54 s of
  nothing, then `video track on`: no error, no stall, no re-fetch. The picture came back without a
  visible freeze because the surface holds the last frame and the keyframe was already on disk.
- **The SponsorBlock prefetch warms only the audio source while the screen is off**, for the same
  reason: video bytes fetched behind a locked screen are never seen.
- **Not verified for the new ladder and slider.** Built and unit-tested only; the gate is a hand
  check on the Pixel: slide the wave end to end, tap Auto, and read the pill after picking 4K and
  Full HD on a video that has neither.

## The feed's own cut, 2026-11-10

- **A watched video can leave the feed.** `205752` asked for a setting from 1% to 100%, and the
  cut is separate from the history threshold: that one decides when an open counts as a watch,
  this one decides when a watched video stops being offered. `Playback → Feed` is a slider with
  **0 = off** as the factory default, because a feed that hides things on its own is a surprise.
  Moving it repaints the feed with no fetch: `ContentRepository.hideWatchedPercent` is a
  `StateFlow`, and `observeFeed()` switches between two DAO queries on it.
- **Two ways a video counts as finished**: the resume point is past the percentage, or history has
  already marked it watched. The second one is why the setting cannot make a finished video come
  back, which is stated in the copy rather than left to be discovered.
- The stored value is `feed_hide_watched_percent`, in `SettingsSchema`, so a backup carries it and
  the container seeds the repository from it on the first read.
- Verified: `DbSmokeTest` covers both rules against a real in-memory database (40% hidden at 25%
  and shown at 75%; a history-watched video hidden at 100%), the suite is green at 396 tests, and
  the debug build is installed on the Pixel 7.
- **Needs the phone**: the slider under Playback → Feed, and the feed dropping a video you watched
  to the end once the cut is set below that point.

## A minute of dictation, and the channel page's two blank spots, 2026-11-10

Third pass. Two of these were only legible from the screenshots in the bundles, which is why the
notes alone had stalled.

- **Dictation keeps the minute.** `220547` is `VoiceInput` doing one utterance per start and every
  partial *replacing* the note, so a minute of talking survived as its last second and then the
  session ended on `ERROR_CLIENT`. A finished utterance is now committed, the next one is started
  while the session is live (200ms apart, budget 300 segments), and a partial can only ever extend
  what is there — `mergeTranscript` is the rule, with `TranscriptMergeTest` including the minute of
  speech in every state the screen shows. Silence (`ERROR_NO_MATCH`, `SPEECH_TIMEOUT`) and a
  dropped service (`ERROR_CLIENT`, `ERROR_SERVER`, network, busy) restart instead of ending.
  `stop()` keeps the partial that was on screen when the finger arrived.
- **The channel's Watched rows were text only.** `205625`'s screenshot is a bare title and "4h
  ago": the row carried `thumbUrl` and never drew it. It draws the same `Thumbnail` the lists use,
  at 112dp.
- **The channel's About text was plain.** `205649`'s screenshot is a description with
  `https://www.bitcoinuniversity.com/` printed as words. It is a `LinkifiedText` now, so a URL in
  it opens. **A bare `@handle` stays words**: the first version guessed YouTube and the owner
  landed on `youtube.com/@mattkratter` from a line that said "Follow me on Twitter". The sentence
  names the network and the app cannot know it, so guessing is worse than not linking. That is the
  rule `LinkifiedTextTest` now pins.
- **A reply's likes and age are right-aligned** (`205539`), so the footer ends where the reply's
  name above it ends.
- Verified: `:app:testDebugUnitTest` (395 tests, 0 failures, 3 skipped) and `:app:assembleDebug`,
  installed on the Pixel 7.
- **Needs the phone**: a minute of dictation that keeps its first sentence and does not exit; a
  channel's About tab opening the site and the handle; a Watched tab with pictures in it.
- Seen and not changed: the channel header still says "Subscribed" beside the name *and* on the
  button. The word was added deliberately in the `131926` pass, so it is the owner's call whether
  the button makes it redundant.

## The picture's geometry, 2026-11-10

Second pass on the 0.3.10 batch. Two reports about the same thing: where the controls are, and
what a finger means when it moves.

- **The progress bar is the top edge of the picture.** `213728` asked for it and the bar's touch
  band moved with it: `barBandHit` measures 44dp down from the top, and the bar itself sits 4dp in
  with the playhead dot straddling it. The band is a function, not a constant, because the back
  arrow and the bug button live in that band — a press on either is still a press, and a drag from
  either is not a playhead drag. `GestureStateTest` pins the four cases.
- **The top row moved down to clear it**: the sponsor chips and the clip banner now sit at 20dp
  instead of 12dp, so the bar does not run through them.
- **The level readouts sit 40dp above the finger**, not 24dp. A pill 24dp over the thumb was still
  under the thumb. The below-the-finger case went to 72dp for the same reason.
- **The speed panel opens on the finger.** It was anchored to the speed button's top-left and
  offset upward from there, so it appeared beside the tap. It now takes the tap point in overlay
  coordinates and uses the same `readoutLeft`/`readoutTop` rules as the level pills, including the
  clamp to the picture — the hardcoded `320dp`/`800dp` limits are gone.
- **A sideways drift no longer starts a seek.** `222730` is a swipe up whose first frame past the
  slop happened to move further in x than in y; the axis locked horizontal and the seek pill came
  up. `resolveAxis` now needs x to be `AXIS_BIAS` (1.5×) the vertical travel before the gesture is
  a seek, so a near-vertical swipe is vertical however it starts.
- Verified: `:app:testDebugUnitTest` green and `:app:assembleDebug`, installed on the Pixel 7.
  `LevelReadoutTest` updated for the wider gap, and `GestureStateTest` has the bias and the band.
- **Needs the phone**: the bar at the top in both shells, a swipe up from the middle of the picture
  that must not raise the seek pill, and the speed panel landing under the finger that opened it.

## A press that does something, 2026-11-10

The 0.3.10 batch, first pass. Three faults that all read as "the app stopped working and said
nothing".

- **A play press on an idle player did nothing, so nothing was logged.** `210828` and `210851`
  ("it won't give error toast or play or log … restart app fixed it") are `STATE_IDLE` with the
  media still loaded: `play()` on an idle player is ignored, because it is waiting to be prepared.
  `togglePlayPause` now goes through `playAction(state, hasMedia, hasError, isPlaying)`, which
  re-prepares on idle or on a failed stream, restarts at the end, and logs which of those it did.
  `PlayActionTest` pins the five cases.
- **Both quiet retries were spent while the phone was offline.** `204259`'s log is
  `error recovery 1/2 … net=none`, then `2/2 … net=none`, and then a dead picture with no budget
  left for the moment the connection came back. `shouldRecover` takes `networkDown` and returns
  false while there is nothing to reach; the error is still on the player when the network returns,
  so the retry happens then. `PlaybackRecoveryTest` has the two new cases.
- **The connection is now a value, not a guess.** `core/Network.kt` grew `NetworkMonitor`
  (`VALIDATED` capability, registered for the process) and `PlaybackState.networkDown`; the pill
  says "No connection — playing will retry" for a drop mid-playback, not only for a failed fetch.
  One log line per transition, never one per retry: the old shape put two failed-retry lines in
  every report and read like the app's fault.
- **A death is now recorded, not inferred.** `163203`/`163241` are a "force close" with no
  exception in our log, which is what a native crash, an ANR and a low-memory kill all look like
  from inside. `core/ExitLog.kt` reads `ActivityManager.getHistoricalProcessExitReasons` on the next
  start and writes the newest unrecorded one, with up to twelve trace lines for a crash or an ANR;
  `GlassTubeApp` also installs an uncaught-exception handler, so a Java crash names itself. One
  line per exit, keyed on the last recorded timestamp (`ExitLogTest`).
- **The idle line says what the player was doing**: `items=… wantPlay=…` beside the video, position
  and time. It is the only clue a stream left on its own leaves behind, and it appears in six of
  these reports.
- Verified: `:app:testDebugUnitTest` (382 tests, 0 failures, 3 skipped) and `:app:assembleDebug`,
  installed on the Pixel 7 over the existing build.
- **Needs the phone**: a video that has gone quiet overnight plays on the first press; a play with
  the phone in flight mode says "No connection" and starts on its own when the network returns; and
  after a force close, Diagnostics shows an `exit` line naming the reason.
- Not fixed, and not claimed: the DNS drop itself is the phone's or the network's, and the dead
  `Peruvian Bull` channel still fails every refresh (Queued, needs Room v12).

## A first screen with something on it, 2026-11-10

- **A fresh install landed on a dead end.** With nothing subscribed the wizard's last button was
  "Start with nothing", and that landed on an empty Feed whose only action was a trip to an equally
  empty Library ("0 subscriptions", no import control there). Import lived in the wizard and in
  Settings only.
- **The empty feed is two states, not one.** Nothing subscribed now gets the import picker inline
  and a "Try Bored" button; subscribed-but-empty says the fetch found nothing and to pull down. The
  count is the live subscription table, so the import flips the sentence without a restart.
- **"Try Bored" starts a mode.** Bored is the only destination that works with nothing subscribed,
  so the button does not open on four names the reader has not met: the first visit starts Short
  (`firstPickName`, `FirstPickTest`), and a renamed built-in falls back to whatever the row holds.
  The request is one-shot in the nav, cleared by the screen that honours it.
- **The shared link previewed the old portfolio card.** og:image was
  `/other_apps/portfolio/screenshots/parallax-og.png`, the pre-rename Parallax card, on both stubs.
  `web/img/og.png` is new (1200x630, the GlassTube tile and wordmark on the glass field), the app
  page and both stubs point at it, and the app page now has an Open Graph card and a favicon of its
  own for the first time. The stubs redirect to `/other_apps/glasstube/` instead of the portfolio
  view.
- **`/glasstube` without the slash is the portfolio.** The OdesaPlay Express app answered every
  unknown path with its SPA, so the short URL a chat drops the slash from previewed that site.
  Fixed on 2026-11-11 in `odesaplay/server.js`: `/glasstube` and `/parallax` now serve their own
  stub file (not a redirect — crawlers read only the first response). nginx still answers the
  slashed URLs from disk; both shapes carry the GlassTube card.

## A restore that restores, a feed that fetches, a skip that does not rebuffer, 2026-11-10

- **The imported file was telling the truth.** `/sdcard/Download/Newpipe/parallax-backup (1).json`
  says `"cards": {"layout": "MINIMAL"}` and the phone's prefs said the same, alongside the file's
  accent h220, speed 1.1 and threshold 25%. The export predated the switch to Classic; the restore
  wrote exactly what it was given. The fault was the age of the file, not the code.
- **The export only carried choices that had been written.** `SettingsExport` returned null for a
  BOOL/INT/FLOAT whose key was absent, so every setting left at its factory default was missing,
  and a restore kept the new phone's own value — a wizard pick could survive the thing meant to
  replace it. `SettingsSchema` now holds a default per field, taken from the literal each reader
  uses, and the export always writes it. Lists stay additive: an empty search history is left out
  rather than clearing the new phone's terms.
- **Watch history and notes were missing from those files, not from the code.** Both are written by
  `Backup.json` and were added after the Nov 9 export. Resume position (`progress`) was genuinely
  absent and is now in the file; a row replaces one here only when it is newer, so a restore cannot
  rewind a video watched since.
- **`speed_presets` was in no table at all**, so a reader's custom speeds never travelled.
- **The feed never heard about an import.** Its only triggers were the first composition and the
  pull, and Settings stays on top of a composed feed. `ContentRepository` now carries a refresh
  counter, an import that added channels raises it, and `FeedViewModel` collects it; a request that
  lands during a running refresh re-arms rather than being dropped.
- **A restore now repaints.** Only the theme was told about the write, while the player's skip
  switch, categories, speed and threshold, the card style and the extractor's country were read
  once and kept. One effect on the Data page calls `reload()` on each and re-initialises the
  region.
- **A long SponsorBlock block rebuffered at its far side.** The skip is a seek past the ~60 s the
  player holds. `SponsorPrefetch` warms 15 s after the block through a throwaway player on the same
  media source and the same `SimpleCache`, started up to two minutes ahead and cancelled when the
  video closes or skipping is turned off. The decision is a pure function with a test; the fetch
  itself needs the Pixel.
- **A swipe out of recents left the process playing.** `PlaybackService` had no `onTaskRemoved`, so
  the mediaPlayback service outlived its task and only a force-stop ended it. It now stops playback
  (which also clears the notification and the service) and calls `stopSelf`.
- **“Found” listed one backup twice.** The folder row is a tree-document URI and the remembered
  export is a plain document URI; the merge keyed on the whole URI. It now keys on the SAF document
  id, so one file is one row and two same-named files in two folders stay two. `BackupFilesTest`
  builds both URI shapes.
- **The update path had nothing on it.** FTP `other_apps/glasstube/` holds `index.html` and `img/`
  only: no `app-release.apk`, no `version.json`. The download page is still served, so the directory
  is live; a missing file falls through to the portfolio's SPA and answers 200 with a 2 848-byte web
  page, which the app was parsing as JSON. The release config is correct (`remoteDir`, APK first,
  manifest deferred, verify on), so the fix is a release run, not a code change. The updater now
  refuses a body that is not JSON and says “No version.json at … — the server answered with a web
  page”. Published as 0.3.10 (code 13) on 2026-11-10 — see *The rename* below.
- **The Settings entry is three lines.** The PBurger is retired: `ic_menu.xml` draws three rounded
  strokes on the same brand ramp (`#f8fafc → #a5e4f5 52% → #4cc2e8`), untinted, and the removed
  drawable had no other caller. The wordmark is unchanged.

## Screen off, as the sentence that sells it, 2026-11-10

- **The claim was checked before it was written.** `PlaybackService` is a `mediaPlayback` foreground
  service, the manifest holds `WAKE_LOCK` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and nothing on the
  activity's stop path calls `pause()`, so a locked phone is just an activity that went away. The
  lock-screen card is media3's own, built from the item's metadata, which already carries the video's
  thumbnail (`PlaybackController` `setMediaMetadata`).
- **Every page that sells it says it.** `web/index.html` (hero, the new first showcase row, meta
  description), the portfolio's `glasstubeCopy.ts` plus `GlassTubeBeta.tsx` (`LockMock`) and the
  GlassTube row's `why` in `apps.ts`, the `/glasstube` share stub's Open Graph card, `README.md`, and
  the two fastlane files. Hero and showcase carry the same sentences on both pages, because the two
  are meant to say the same words.
- **The row is first, not last.** Clip sharing, Appearance and Bored Mode moved down one place and
  the portfolio roadmap's "leads with three things" is now four.
- **The frame is the client's own, not the paywall.** The first draft wrote "YouTube's own app only
  does this with Premium", which the owner cut: it measures the app against a subscription instead of
  saying what it is. The people this is for already live with the workaround — in the official app
  you leave the screen on to keep the audio, or you let the ads play — so the copy names that
  experience plainly ("the screen does not have to stay on"), and the listing says "listening with
  the phone put away". Same claim, no opponent in the sentence.
- **The Appearance row is a working demo now.** The three rows of chips became a small widget: four
  theme buttons set one attribute on it, eight accent buttons set one custom property, three size
  chips (Small, Medium, Large) set the card's padding, thumbnail share and type sizes, and four
  switches (Duration, Channel, Views and age, Progress) hide the matching part of the card. The card
  is styled from those alone, so there is no second copy of a colour to drift. The same shape lives
  on the download page (plain markup and one listener) and in `AppearanceWidget` on the portfolio
  page. The card uses the app's own colours inline rather than the site's tokens, because the point
  is to show the app, not the page.
- **The showcase's paragraph rule leaks into the widget unless it is fenced off.**
  `.showcase p` is `(0,1,1)`, so it repainted the preview's title with the page's muted colour and
  its 15px size — in the Light theme the title would have been light grey on a light card. The
  widget's text rules are now `.aw p` resets plus `.aw .aw-title`-style scoping, verified by reading
  the computed colour in both themes.
- **Not claimed: battery saving, and not "audio only".** The player still decodes video with the
  screen off, so the pages say the audio keeps going, not that the video stops. If an audio-only
  mode is built later, this is the paragraph to revise.
- Verified, live: the download page (6/6), the portfolio on both hosts (3/3 each, old bundles
  pruned) and the stub earlier. The two pages were then driven in a browser against the live URLs:
  every theme repaints the card (`#10161e` dark, `#eef2f6` light, `rgba(10,14,20,0.42)` glass),
  every accent moves the thumb gradient, the progress fill and the pressed ring, Small/Medium/Large
  change the padding (8/12/16px), the thumbnail share (34/40/46%) and the type (10.5/12/14px), and
  each switch hides its part of the card; neither page logs an error. Read back over HTTPS: the hero
  carries "no ads in the way", the mock ends on "The screen is off. The audio is not.", and the
  widget's markup and script are in the served page. The app itself did not change, so no APK was
  rebuilt or uploaded.

## The rename, 2026-11-10

The tree was renamed to GlassTube while this copy was being written: package
`com.presaince.glasstube`, app label `GlassTube`, AGENTS.md, and the deploy configs already pointed at
`other_apps/glasstube/`. The marketing copy now follows:

- **Both pages say GlassTube**: the title, meta description, hero, install steps, the "stream stalls"
  tile, the footer, the GitHub label and the download button's version label. `README.md`, the
  fastlane files and the portfolio row *do* agree.
- **The portfolio's app id is `glasstube`**, so its row links `?app=glasstube`; `?app=parallax` is
  still accepted and renders the same page, because those links are in chats. The component and its
  copy file are `GlassTubeBeta.tsx` and `glasstubeCopy.ts`.
- **The short link is `/glasstube`** (`glasstube-link/`, `deploy-glasstube-link.properties`). The old
  `/parallax` stub stays, retitled, and forwards to the same page, because a shared link cannot be
  corrected.
- **The release went up as 0.3.10 (code 13).** `assembleRelease` clean, signed with the same key as
  0.3.9 (`CN=Ukraine Drones`), notes "The app is now GlassTube.", sha256 `ff550853d199…`, uploaded to
  `other_apps/glasstube/` with its `version.json`. The updater finally has a manifest to read; the
  section above about the empty folder is closed by this upload.
- **The links followed the binary.** The page's three download links and its manifest are relative
  again (they resolve inside the glasstube folder), and `APK_URL` and the portfolio's version fetch
  name `/other_apps/glasstube/`. Read back: manifest 0.3.10/13, APK 33,680,760 bytes,
  `application/vnd.android.package-archive`.
- **The old folder is gone, deliberately.** It briefly held a redirect plus a code-12 tombstone
  `version.json`, so an installed 0.3.9 would stop failing its check. Both were removed on
  2026-11-10: there is no legacy site to keep alive, `other_apps/parallax/` 404s into the host's
  stub, and the last Parallax install is expected to be uninstalled rather than updated.
- **Still says the old word, on purpose:** the GitHub URL (`presaince/parallax`, kept as the repo
  name), the `parallax1..5.webp` screenshot filenames, and `BackupReader`, which accepts
  `"app":"parallax"` so a backup written before the rename still imports.
- Verified: `tsc` clean, both pages driven in a browser, then deployed: the page (6/6), the portfolio
  to both hosts (2/2 each: the bundle only), and the release (2/2). Read back over HTTPS: the page
  and the portfolio button read "Download GlassTube 0.3.10", the manifest's build row shows code 13
  and the note, the portfolio bundle carries the glasstube APK URL and no parallax one, and both
  stubs forward to `?app=glasstube`.

## Brightness handback, 2026-11-10

The owner's report again: "can't change OS brightness, toast says it's locked by top app". The
player's brightness swipe wrote `window.attributes.screenBrightness` and nothing ever cleared it, so
the single Activity carried the swiped level onto every screen behind the player, and the shade
refused its own slider while Parallax was on top. `restoreSystemBrightness` now runs from the
player's `DisposableEffect(Unit)` dispose and sets `BRIGHTNESS_OVERRIDE_NONE`, leaving the rest of
the window attributes alone. Verified: `BrightnessHandbackTest` (the override clears, `dimAmount`
survives), `:app:assembleDebug`, installed on the Pixel 7. Whether the shade's slider is free
again after a gesture-and-back is the owner's check.

## Card meta, section labels, and the second wrap, 2026-11-10

- **The wrap was my layout, not the spacing.** The report's screenshot (`161450`) shows the card
  meta as `… · 1.2K` / `likes · 164 comments`: the channel name and the facts were two weighted
  children of one Row, so they split the width down the middle and the facts were squeezed into a
  narrow column of their own. The card now gives the channel its own line and puts the facts on the
  line below, right-aligned and full width, which is the arrangement the owner asked for two passes
  ago and I read as "same row, opposite ends".
- **`timeAgo` keeps its no-break space**; that part was right and stays.
- **Player section labels have a setting**: `player_labels_right` (Playback → "Section labels on the
  right"). Off, which is now the default, puts Description, Chapters, My notes, Comments and Up next
  on the left in line with the text they name; on, they sit on the right as before. Backed up with
  the other preferences.
- Verified: `:app:assembleDebug`, unit suite green, installed on the Pixel 7.


## History and the age wrap, 2026-11-10

- **`timeAgo` cannot break any more.** "5h ago" was the last string in the app still built with an
  ordinary space, so a card that wrapped put "5h" at the end of one line and "ago" at the start of
  the next — the report `160919` screenshot. Every unit now joins with a no-break space, which fixes
  the cards, the history rows, the player's facts and the feed's "Updated …" label in one place.
- **History shows how much was watched, not when.** The row said "watched 5h ago"; it now says
  "42% watched" (plus the watch count), which is the same question at any video length. The same
  change went into Library's Continue watching, which showed an elapsed duration.
- **Both lists are right-aligned**, via a `metaEnd` flag on `VideoRow`: the figures line up in a
  column instead of ending wherever the channel name happens to stop.
- Verified: `:app:assembleDebug`, 347 tests, 0 failures, installed on the Pixel 7. `TimeTest` and
  `FeedUiStateTest` updated for the no-break space.

## The shade's fallback text, 2026-11-10

`110556`: after a mid-stream failure that repaired itself, the notification read "parallax is
running" — the system's placeholder for a foreground service with nothing to show.

- **The media item was being cleared while the service stayed up.** A recovery calls `play()`
  again, and `play()` did `player.stop()` then `player.clearMediaItems()` before building the new
  source. Between those, the media session had no metadata, so media3 posted its fallback
  notification. `setMediaSource` replaces the item on its own, so the clear is gone: the old title
  stays until the new one takes over.
- **The service starts once there is something to show.** `startService()` used to be the first line
  of `play()`; a first load that failed therefore left a foreground service with no item. It now
  runs immediately before `prepare()`, after the metadata is attached.
- **An explicit close stops the service**, not only the player: `stop()` cleared the item and left
  the service foreground, which is the same fallback text after pressing the mini-player's X.
- Verified: `:app:assembleDebug`, unit suite green, installed on the Pixel 7. No unit test: the
  behaviour lives in one `ExoPlayer` call sequence that needs a device to observe (the session, the
  notification and the service lifecycle all have to be real), and there is no seam for it in
  `PlaybackRecoveryTest`, which covers the retry predicates.
- **Needs the phone**: a mid-stream failure followed by a recovery, and a deliberate close, both
  without the placeholder text appearing.

## Seventh pass: seek chip, dot, Keep going, Up next, 2026-11-10

- **The undo chip names the distance only**: "Seeked +12s", not "Seeked +12s to 1:00". The reader
  is already on the picture at the new time; the target was restating the screen.
- **The playhead is a dot again.** The handle was `GlassTube`, the brand mark with a play glyph in
  it, which read as decoration on a control that is dragged a hundred times a session. 14dp circle
  in the accent colour.
- **"Keep going" starts the video** when the clip banner is dismissed and the picture is paused.
- **Up next's refresh was instant and looped.** It rotated a ten-video window over the *cached*
  pool, so a pool of twelve showed the same twelve in a circle, with no fetch. `relatedFresh()` is
  new on `VideoSource` (default: no cache bypass, so test fakes did not change); the button now
  makes a real call and shuffles what comes back, and says "Only N suggestions exist" when the pool
  is no bigger than the window.
- Verified: `:app:assembleDebug` and the unit suite green, with `TimeTest` updated to the shorter
  chip. Installed on the Pixel 7 on the owner's `adb` (data kept).


- **The player's facts line cannot split a fact.** "4h ago" was arriving as "4h" then "ago" because
  the line was built with ordinary spaces and allowed to wrap. Each fact now holds together with
  no-break spaces, the line is one line with an ellipsis, right-aligned, and the age is last so the
  far right always answers "when". The channel name keeps the left, ellipsised.
- **A subscribed channel's name is the accent colour on every card**; a channel you do not follow
  stays in the ordinary text colour. `subscribedChannels(db)` matches by name, because a listing and
  a subscription can write the same channel three ways (`/c/`, `/channel/`, a handle). Wired through
  feed, history, search, Bored, library, collections, channel and Up next.
- **Bored cards lost the repeated `Search: "…"` line** — the recipe says it once. A card now shows
  only the hop that led to it, or its ordinary facts when there was no hop. The panel is called
  **"How these were picked"** instead of "Why these?".
- **Comments are items of the player's list, not one tall block.** Appending a page used to re-sort
  everything inside a single non-lazy item, so rows could land above the one being read with nothing
  to hold the place. Keyed items (`comment-<id>`) mean Compose keeps what is on screen where it was.
  This is the fix for "tapping more comments loses my place".
- **A reply page that fails now says so on the card that asked**, not in a notice at the top of a
  section the reader is far below. An empty reply page says YouTube counted replies and sent none,
  which is the honest answer for a comment whose replies are held or hidden.
- Queued, not built: **a playlists screen** (the owner asked for it; it is the next feature) and
  **chapter thumbnails from the storyboard** (see Queued for the approach).
- Verified: `:app:assembleDebug`, the unit suite green, installed on the Pixel 7.
- **Needs the phone**: the two-line title block, whether the subscription colour is findable, the
  lazy comments keeping their place across "More comments", and the reworded panel title.


The owner's reading of the fourth install, plus `110003`. Open reports: 8 → 7.

- **Replies are never truncated.** A reply that hides its last clause hides the answer, so the six
  line cap is gone from `ReplyRow`.
- **The chevron belongs to comments that need one, bottom left.** It appears only when the words
  run past six lines, sits before the reply count, and survives opening so the card can close
  again — `onOverflow` only updates while folded.
- **Replies the page came without a token for say "N replies · YouTube"** and go there. The count
  is no longer a control that looks local and behaves like a link.
- **The player's fact row lost the comment count and the length**: the comments section says how
  many and the picture shows how long. Views, likes, age and the watch count stay.
- **The channel header is two lines**: the name on its own with a "Subscribed" word beside it, then
  the subscriber count and the Subscribe button underneath. The name no longer competes with a
  button for the same row.
- **`110003`**: a lost network is `SourceError.NetworkFailure`, which `withSourceRepair` was logging
  as a repair warning on every call — 203 lines in one report for what was a DNS drop. It is now one
  info line, and the pill reads **"No connection — playing will retry"** instead of "Stream broken",
  which sent the reader looking for a fault in the app.
- Verified: `:app:assembleDebug`, 348 tests, 0 failures, installed on the Pixel 7. New test: a lost
  network is `Offline`, not `Broken`.
- **Needs the phone**: whether the 14-reply comment's YouTube hop is acceptable as the answer, the
  two-line channel header, and whether the chevron's arrival on long comments is where the eye goes.


Batch 3, plus the four things the owner found after the third install. Open reports: 10 → 8.

- **The channel page is a page now.** Header with avatar, name, subscriber count and a working
  subscribe button (`SubscriptionDao`, so it is a row on this phone, not an account). Chips for the
  tabs the channel actually has — `ChannelTab` reads the extractor's own filter names, so a channel
  with no Shorts never offers Shorts — then Sort by date, views or length, and two local tabs:
  **Watched**, this channel's videos from the history table, and **About**, its description. The bare
  name label that made `131926` a duplicate is gone; the header replaces it with facts.
- **`VideoSource.channelTab(url, filter, page)`** is new with a default that serves uploads, so a
  source that cannot switch tabs still answers and the fakes in tests did not change.
  `channelUploads` is now one call to it, and its cache is keyed by tab.
- **The YouTube mark is gone from comments entirely.** Replies without a token — the pinned comment
  with fourteen of them — keep their count and carry a small "opens elsewhere" arrow instead of the
  brand, and the expanded detail row no longer offers the mark at all.
- **A reply's name is its own control**, primary-coloured and wired to the commenter's channel; the
  card's expand click no longer swallows it.
- **The reply count moved to the left of the footer line**, likes and age to the right: three facts
  in one column crowded the row.
- **Up next reshuffles.** One `related()` call per video returns the same set, so the button now
  slides a ten-video window over the pool the source did give, and says "This video only has N
  suggestions." when there is nothing to slide. Pinned comments stay first under either comments
  sort, which is what the owner asked to confirm.
- Not built, deliberately: a Playlists tab. There is no playlist route to land on, and a chip that
  opens nothing is worse than an absent one. The extractor's playlists tab is already reported in
  `Channel.tabs`, so the day a playlist page exists the chip is one line.
- Verified: `:app:assembleDebug`, 347 tests, 0 failures, installed on the Pixel 7. New tests:
  `ChannelPageTest` (which chips a channel gets, and the three sorts).
- **Needs the phone**: the header's proportions, whether the sub count reads right for a channel
  that hides it, the tab chips wrapping on a narrow screen, and the subscribe button's round trip.


Everything the owner found after the second install, plus `114655` once he explained it. Open
reports: 11 → 10.

- **The comments sort was the cache, not the code.** A comment date and a commenter URL both exist
  in the extractor — the new canary test prints them and requires at least one real date per page —
  but every page written before this build was stored without a date, so sorting by date reordered
  nothing. `CommentCodec` now writes `"v": 2` and refuses a page of any other version.
- **A comment with replies but no continuation token is not a dead end.** That is the pinned comment
  with fourteen replies: the extractor gives a count and no token, so the app could not fetch them.
  The footer now shows the YouTube mark in place of the chevron for exactly those comments and
  opens them in the app, where replies do expand.
- **A comment that has nothing to reveal no longer expands.** Short text, no replies, no posting:
  tapping did nothing but unfold a lone YouTube mark. Expansion now needs a reply count, a Reply
  button, or text running past the six lines — the last is why `LinkifiedText` grew `onOverflow`.
- **Names are primary-coloured and open the channel** (`Comment.authorUrl`, from `uploaderUrl`, and
  cached with the page). The app has no profile screen, so it leaves for the browser.
- **Up next collapses**, keeping the app's header idiom: title, spinner while fetching, refresh,
  chevron. **The refresh says when it has nothing to add** — one `related()` call per video returns
  the same set, so "Nothing new from this source." is the honest answer instead of a dead button.
- **Chapter titles get three lines** in the horizontal card; the description keeps its own
  timestamps, which are full-length and the reason the list is there.
- **A channel name on a video card is a control**, not a word in the meta line: its own `Text`, in
  the primary role, wired through `onOpenChannel` from feed, search, Bored, library and Up next.
- **A fact cannot split across lines.** `metaFor` builds "50\u00A0comments" with a no-break space and
  joins facts with a no-break dot, so a count never ends a line with its noun on the next.
- **History rows swipe over their own shape**: the dismiss backdrop was a plain `Box` filling the
  row, which squared off the card's rounded corners. Same radius, same inset.
- **"My notes" is right-aligned with the microphone to its left**, matching the comments header.
- **`114655`** — a pushed screen never owned the window: layout does not block touches, so a tap in
  the gap between the bug screen's rows fell through to the list underneath, and the screenshot
  itself passed taps too. The overlay `Box` now consumes what no child claims. This fixes every
  pushed screen, not just Bug.
- Verified: `:app:assembleDebug`, 343 tests, 0 failures (3 skipped), installed on the Pixel 7. New
  tests: a page of another version is ignored, the commenter's channel survives the cache, and a
  canary case that fails the night comments stop carrying a date.
- **Still needs the phone**: whether the date sort now visibly reorders, the tokenless-replies
  YouTube hop, the collapse and refresh on Up next, and channel names in the card grid.


The owner read the first comments pass on the phone and asked for changes, then the batch that was
queued behind it. Open reports: 20 → 11.

- **Comments are a footer line now.** Likes and age moved out of the expanded detail row and onto
  the one line that is always visible, with the reply count and the chevron at its end: the same
  facts, once, in the place the eye already is. The chevron exists only when `replyCount > 0` and it
  belongs to the replies, not to the card — which is also the fix for "tapping to open sub replies
  does not work": the thing that looked like an opener was the chevron, and it was opening the
  comment's text instead. Replies now open from the folded card as well.
- **Reply author names are on the right** like the comment's, and the reply row is its own
  `ReplyRow` rather than a second copy of the layout.
- **Comments sort by date or likes**, above the first comment, on the left, using the app's existing
  `SortRow`: two chips and the direction arrow pinned at the start. Tapping the chip already in use
  flips the direction; picking the other one starts newest-first. A pinned comment stays on top of
  both orders, and the ordering is only ever over the pages already loaded.
- **Sorting needed a date, so comments carry one.** `Comment.publishedAtMs` is read from the
  extractor's `uploadDate`, encoded in the comment cache (a page written before this reads as null
  and sorts last), and never shown: the card still says "2 days ago".
- **`111135`** — the chapter time came off the frame, where white text over a bright thumbnail was
  unreadable. It is part of the caption now ("1:23 · The middle part"), and a new Playback switch,
  **Chapter timestamps**, drops it there and in the plain list. The description keeps its own
  timestamps either way. The switch is in `SettingsSchema`, so a backup carries it.
- **`114709`** — Up next renders whenever the list exists, not only in the last second of the video.
  One `related()` call per video is still the whole cost; it now happens when the comments settle
  instead of at three quarters through.
- **`112116`** — `ic_bug.xml` was a mangled `bug_report` path (the legs spliced into the body and
  ending in garbage). Replaced with the stock Material vector.
- **`114820`** — the card border hugged the picture and the words: `padding(vertical = …)` had no
  horizontal half. 8dp inside the border on Classic and Side, 6dp on Minimal.
- **`112225`** — the card switches say "My notes", matching the player's section.
- **`114655` stays open**: "bug screen has space near toggles that touch the video player" has no
  screenshot in the bundle and no obvious fault in the layout, so it needs the owner's words or a
  picture rather than a guess. The owner explained it the same evening; the fix is in the third pass.
- Verified: `:app:assembleDebug`, the whole suite green (340 tests, 2 skipped), installed on the
  Pixel 7. New tests: `CommentSortTest` (five cases, pinned stays first, an undated comment sorts
  last), `ChapterCaption`, and the codec's "a page cached before comments carried a date still
  reads". `ResyncPromptTest` failed first on a stale compiled class, not on code: `--rerun-tasks`
  cleared it, which is worth knowing the next time a model gains a field.
- **Not checked on the phone**: the footer line's density, the sort chips under the comments header,
  whether Up next appearing early feels like clutter, and the redrawn bug icon.

The first comments pass, before the owner's second reading, is where the linkified bodies and the
long-press reload came from: `101720` (comment and reply bodies open links, and a tap anywhere else
still opens the comment), `102432` (author right, the per-reply YouTube mark gone) and `134240`
(`ContentRepository.comments` takes `refresh`, which skips a cache page still inside its hour;
`refreshComments()` keeps the list on screen while it fetches and the header shows a 14dp spinner).
`LinkifiedText` gained `style`, `colour`, `onOverflow` and `onTapElsewhere` for it; the description
keeps the defaults it already had.

## Bored sources, 2026-11-09 (eighth)



Bored was capped at one `related()` call per seed, about twelve candidates each, so the pool was
the same videos in a different order. Three sources now feed it, and the built-ins use the two new
ones:

- **`from.kind: "search"` is real.** A search recipe carries a `query`; `picks()` makes one
  `search()` call, filters and scores the page, and never touches the related graph. Long, Short
  and Deep are now curated queries ("podcast", "short film", "documentary") plus the filters they
  already had. `walk.hops` and `walk.branch` are rejected for search and uploads, because one page
  has nothing to hop; `walk` itself is optional and only `wildcard` remains for them.
- **`from.kind: "uploads"` is Fresh.** `ChannelFeed` reads YouTube's free per-channel XML feed
  (`feeds/videos.xml?channel_id=…`, latest fifteen, no key, no quota) for up to `count` followed
  channels in parallel, cached for thirty minutes. A `/channel/UC…` URL is read directly; a handle
  or `/c/` name still goes through `channelUploads`. Fresh ignores the skip-subscribed switch by
  design, and that switch is hidden on its panel instead of being a control that does nothing.
- **The library is the offline page.** A failed search is matched against stored titles and
  channel names on the query's words; failed channel feeds fall back to the stored feed. The empty
  state still names which cause applied.
- **Not built: the official Data API and AI prompt-to-recipe.** Google's `search.list` is 100
  quota units per call (about one call a day on the free bucket) and needs a BYOK key plus an audit
  for more, which breaks the no-key stance for a page the extractor already serves. An LLM would
  only translate a sentence into the `query` this format now accepts; there is no Bored AI mode to
  put it behind, so the recipe JSON is the interface for now.
- Verified: `:app:compileDebugKotlin`; `RecipeTest`, `DiscoverEngineTest` and the new
  `ChannelFeedTest` green (search does not call related, failed search uses the library, uploads
  reads the feed under skip-subscribed, handle falls back to the extractor, offline uses the
  stored feed, XML fields and entities pinned). **Not checked on the Pixel** — the search queries'
  hit rate, the RSS path and the empty-state wording are the owner's to judge.

## Screenshots and copy, 2026-11-09 (seventh)

- **Five captures replace the three.** `parallax1..5.webp` in the portfolio, and
  `web/img/feed|player|appearance|bored|library.webp` on the download page, are new stills of the
  current build at 1080×2400 (exactly 9:20, so the phone frames never crop them): the feed in Glass
  with brand cyan, the player with the chapter row, Appearance with the theme tiles and the live
  card preview, Bored with Why these? open, and the Library grid. The demo-mode status bar is used
  (9:41, full battery, wifi only); Oko's alert icon is the one system icon left, because killing a
  live air-raid app to tidy a screenshot is not a trade worth making.
- **The captures are from the phone, not a mock.** `parallax4.webp` is the Bored panel with its
  filters visible rather than the older "Back to the recipe" screen, and `parallax2.webp` is a video
  that actually plays, so the shot shows chapters and comments after the first frame.
- **`onboarding_feed.webp` was regenerated** from the new feed at 540×1200, so the welcome page and
  the marketing page cannot show different builds.
- **Copy: the slogans went.** "Belongs to you", "on your terms", "dial in", "local-first by
  construction" and "when the source breaks" are gone from both pages; the showcase and feature
  titles now say what happens ("Share the range, not the whole video", "Speed in 0.1 steps",
  "When YouTube changes"). The hero is "A YouTube client that does the things YouTube won't". The
  install and limits copy lost their duplicate "no Play Store listing" sentence, so it is said once.
- **The two pages still say the same words**, including the three captions under "On the phone".
- Verified: `npm run build` in the portfolio (check-assets: 53 screenshots present), and a Playwright
  pass over both pages measuring the decoded images: five per page, every one 1080×2400 at ratio
  0.450, no broken image, one `h1`, captions matching the copy. Deployed: portfolio to
  `bodiklamph.com` (7 uploaded, 7 verified) and `odesaplay.com.ua/other_apps/portfolio/` (8 uploaded,
  8 verified), download page via `deploy-page.properties` (6 uploaded, 6 verified). `parallax4` and
  `parallax5` had been 404 and the host's 200 HTML stub respectively because the earlier portfolio
  upload ran before the two files existed; both now read back at their exact byte sizes from both
  hosts, and the download page's five images do too.
- Installed over the existing debug build on the Pixel 7 (`install -r`, data kept, 23:40). The new
  welcome picture only shows through Settings → Run setup again, because the wizard is gated on
  `firstInstallTime == lastUpdateTime`. Hand check pending.

## Settings and System accent, 2026-11-09



- **Settings is one surface.** `Route.SettingsPage` is gone; the open section is state in
  `ParallaxNav`, so the Pburger can never push Settings on top of itself and Back can only leave
  the section or leave Settings. One `back` lambda serves the gesture and the top-bar arrow.
- **System accent.** `Accents.SYSTEM` stores the name, `readAccent` resolves the hue from
  `dynamicDarkColorScheme(context).primary` on every read, and `systemAccentOf` keeps the app's own
  saturation and lightness. A grey wallpaper falls back to the brand hue; the picker draws the real
  primary in its own row under the hue field.
- **Shipped 0.3.7 (code 10)** to `https://odesaplay.com.ua/other_apps/parallax/`, signed with
  Oko's release key (cert SHA-256 `289dfe1c…1f31`, the same as 0.3.6), `version.json` read back
  from the server with code 10 and sha256 `546402dc…dbdf`.
- Checked: `SystemAccentTest` (2 tests, hue and the grey fallback), `assembleDebug`,
  `assembleRelease`, `apksigner verify`. **Not checked on the Pixel** — no device attached, so
  neither the settings back behaviour nor the System swatch has had a hand run.

## Download page, 2026-11-09

- `web/index.html` now wears the app's own Glass theme instead of the portfolio's flat dark page:
  the three aurora glows from `glassBlobs` on the `glassBase` near-black, fixed behind the page,
  with `backdrop-filter` cards, a hairline border and an inner top highlight. Nothing animates and
  the aurora does not scroll, which is the "static aurora" the copy promises. The page now mirrors
  the portfolio's ParallaxBeta copy: the three showcase rows alternate a rebuilt panel (the share
  card, the Appearance screen reduced to theme, accent shuffle and card preview) or a phone shot
  with the argument and its note, and the features keep the portfolio's lucide icon tiles over a
  hairline rule instead of becoming eight more cards. The copy in the two places is identical and
  the portfolio reads `version.json` beside the APK, so the version cannot go stale in either.
  The download page still carries the Builds list with the sha256, which the portfolio page has
  no reason to show.
- Checked in Chromium at 375, 656, 820 and 1440: no horizontal overflow, the card blur and fill
  resolve, the generated builds row fits, and muted text measures 4.7:1 over the brightest blob
  and 7:1 on a card. Not opened on a phone, not deployed.


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

## Startup wizard, 2026-11-09 (second look)

- **Four pages, fresh installs only.** Look, Ads, Player, Start. The region page was cut after the
  first hand look: the phone's country stays the default through `readContentRegions`, and changing
  it is Settings' job. The gate is `firstInstallTime == lastUpdateTime`, so an update never rewrites
  an answered setup; Settings → About → Run setup again is the way back.
- **The welcome is a real capture**: `web/img/feed.webp` scaled to 540×1200 and shipped as
  `drawable-nodpi/onboarding_feed.webp`, in a 112 dp phone frame with an accent shadow and a 5 dp
  float. The drawn card field is gone; it read as random squares.
- **The look page is panels**, not paragraphs: Theme, Accent and Card preview on the Settings card
  fill. Theme is four icon tiles — System first, then Glass, Dark, Light — and the preview card is
  selectable: Side, Classic and Minimal write `CardStyle` and the card repaints. The tagline is
  "No ads, better features, no tracking."
- **Ads keeps both answers on screen**: two radio rows, starting from the current SponsorBlock
  state. No replaces-the-buttons UX and no change-my-answer. The line under them says YouTube ads
  are not in the stream.
- **The player page** is the same gesture lesson at full width with titleMedium/bodyLarge type;
  Settings keeps the compact one. Each step draws its target on the stage in dashes: a tap ring,
  a seek bar with arrows, and volume and brightness pills. The fourth is the real three-stop
  ladder — normal, mini, super-mini — with the card shrinking inside a fixed stage and a dashed
  outline holding its place; the step completes only after the bar and the return. Four dots fill
  as the gestures land, and all four plus a check is the finished state.
- **Start** is a panel with an import icon and one format line, and the card itself opens the
  picker; the bottom primary is only Start with nothing, then Open feed after an import. The
  in-page duplicate was removed.
- **About** says "Visit product page", not "Download the APK".
- **Verified**: `assembleDebug` clean; the whole unit suite green, with `OnboardingFlowTest` now
  four pages plus `ThemeDefaultsTest` and the `toggleRegion` cases. The debug APK was installed over
  the existing app on the Pixel 7 with no data loss; the hand check has not run yet.

## Gesture lesson and type, 2026-11-09 (third look)

- **The invitation moved onto the picture**: "Gesture tutorial" / "Touch me". The header no longer
  says "Tap the card to start".
- **The lesson is full width** in Settings and the wizard, with one centered step line, a centered
  hint, and the seek hint naming the speed and the base step in Settings.
- **Progress is a four-column line** on the bottom edge of the picture; no dots and no checkmark.
  At the end it is full and Restart tutorial replaces the hint.
- **Volume and brightness switch without lifting**: the active control follows the finger across the
  middle. The pair sits where the finger went down — active 40x96, partner 28x64 dimmed 8dp beside
  it — not on the far edges. The player does the same: `PlaybackGestures` passes the down point and
  the current half during a side drag, and the HUD draws both levels. A drag that starts in the
  centre stays on the ladder, and a fast flick still minimises.
- **The lesson's pair matches**: the live pair appears at the down point, and the dashed step-2
  target is the same pair in the middle, not two edge pills.
- **Seek matches Settings**: `SeekRate` is shared by the player and the lesson. The first tenth is
  exactly the 2/5/10 s base and the ramp reaches 8x at 40% of the width. The lesson's target is a
  2.5dp line and its readout is the signed delta only; the fake 210–720 s target the finger could
  never reach is gone.
- **The type scale is one step up** from Material's defaults in `AppTypography` (body 15/17, labels
  12/13/15, titles 15/17/23), with line heights to match. It still follows the system font scale.
- **Verified**: `assembleDebug` clean with the pair and `SeekRate`. The unit suite could not finish:
  an unrelated in-progress `PostingScreen.kt` edit broke the compile when it started. The Pixel was
  detached, so this build is not installed; the hand check is pending.

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
- **Not built, on purpose: the cache-first reopen.** A video watched two days ago still pays the
  stream resolve (about a second) before the first frame, because the player needs a `StreamSet`
  to build a media source even when every byte is already on disk. Making that instant needs a
  persisted `videoId -> itag` descriptor, a synthetic cache URI, and an upstream that fails at the
  first hole — three moving parts for one second. Revisit only if a hand check says the resolve is
  what makes reopening feel slow; the fix would then belong to the resolve, not the player.
- Verified by `:app:assembleDebug` and `:app:testDebugUnitTest` (5 new tests in
  `PlaybackRecoveryTest`), 0 failures. No hand check yet: the seek-back and reopen paths are the
  owner's to judge.

## Download page, 2026-11-09 (third)

- **About gets a download link**, pointed at the published folder rather than the raw file: the page
  there carries the version, notes and hash, and "Check for updates" still installs in place.
- **The page wears the portfolio beta page's clothes**, not its own: the same tokens (surface,
  accent `#4cc2e8`, 3xs/2xs/eyebrow scale), the gradient wordmark, the accent install card with three
  numbered steps, and the phone frame. One static file, its CSS inline, no build step and no shared
  bundle; it reads `version.json` at load, so a release never edits a page.
- **The captures ship with it.** `web/img/feed|bored|about.webp` are copies of the portfolio's
  `parallax*.webp`, because the portfolio's screenshots path on this host answers with the link stub
  (HTML, 200) rather than the image. Linking out would have been a broken frame that still "passed".
- **Two deploy configs, one page.** `deploy-apk.properties` uploads the page and images with the APK;
  `deploy-page.properties` (`dist=none`, same `extra=` list) ships a copy change without needing a
  signed release on disk. The four `extra=` entries live in both and must stay in step.
- **One row is the honest answer.** `app-release.apk` is overwritten every release, so there is no
  second build to list. Keeping older APKs — a versioned filename per release and a manifest per
  build — is the change if history is ever wanted; nothing about the page has to change first.
- Verified live at <https://odesaplay.com.ua/other_apps/parallax/>: the tab title, the h1, the CTA
  reading "Download Parallax 0.3.0" from the manifest, one build row with the 6-line notes and the
  sha256, all three images decoded at 1080 wide, `#0a0a0a` background, no console errors. The Android
  side reuses `UPDATE_BASE_URL` and adds no logic, so there is no new test; the page has none.

## Versioning, 2026-11-09 (fourth)

- **0.3.1, versionCode 4.** The published manifest was still 0.3.0 / code 3, so the check in every
  installed build answered "up to date" after a week of changes. The number had been edited by hand
  and forgotten twice.
- **One script owns the pair.** `scripts/bump-version.mjs --apply` reads the live manifest, sets
  `versionCode` one past whichever number is higher, and moves the patch. The report mode exits 1
  when the tree is not newer, so it can gate a release without writing anything.
- **The manifest generator refuses a used code.** `make-version-json.mjs` fetches the published
  `version.json` after signing and throws if the new code is not above it. The failure lands on the
  release machine, not in "nothing new" on every phone.
- **The signature answer.** The live APK is release-signed (`CN=Ukraine Drones`, same certificate
  as Oko's release, keystore in Oko's `app/`); cable builds carry the Android debug key. Android
  will not install either over the other, which is why the page's APK "would not install" on a
  phone that had the debug build. One uninstall moves a device across.
- Tests: `node --test scripts/bump-version.test.mjs`, 5 passing (patch bump, carry at 100, short
  version, next code beats both sides, read/write round-trip). No device check: nothing rebuilt or
  uploaded yet.

## Updates, 2026-11-09

- **The second question is gone.** "Ready to install" asked what the download had already answered,
  and the system installer asked it again a moment later. A verified archive now goes straight to
  the installer; the About button becomes Install, and a cancelled system sheet leaves it that way
  instead of reverting to Check for updates. One automatic attempt per download, so a cancel cannot
  become a loop.

## Settings backup and tiles, 2026-11-09 (fifth)

- **One backup, one file, one button each.** `parallax-backup.json` carries subscriptions, the
  settings and the reader's own Bored recipes. The subscription half is the same NewPipe-shaped
  object the exporter already wrote, so a restore goes back through the importer that was already
  tested; settings and recipes keep their own objects. A backup is additive: it merges, never wipes.
- **Settings can now be imported.** Until this pass `SettingsExport` was write-only — a settings
  file fed to the subscription picker answered "Could not read that file". `SettingsImport` is the
  other direction, and the two walk one table (`SettingsSchema`) so a field cannot be exported and
  forgotten by the restore. It covers the stored choices, not only the six the old file named:
  theme, Glass base and level, accent, room, contrast, shuffle, card layout and its ten switches,
  quality, cache size, swipe-to-seek, PiP, restart, chapters, hide-live, haptics and its four
  per-feature switches, Bored's skip-subscribed, regions and search history.
- **The file is read by content, not by name.** `BackupReader` decides between a combined backup,
  a settings object, a subscription list (NewPipe zip/JSON, LibreTube JSON, Takeout CSV) and a
  single recipe from what is inside. Four buttons used to ask the reader to know which one they
  were holding; there are two now, and "could not read that file" only appears when the content
  really is unrecognised. A settings file can no longer be mistaken for a recipe.
- **AI never comes back on without a key.** The endpoint and model restore, the switch does not
  unless a Resync key is already in the Keystore, and the receipt says so. An enabled switch with
  no key is a dead control.
- **The Backup page is two large tiles; the Settings index is untouched.** The old Backup rows were
  four paragraphs that explained file formats nobody reads at the moment of tapping. They are two
  tiles now, Export and Import, and the export says what it wrote ("Saved 38 subscriptions, 34
  settings and 2 recipes"). `SettingsTile` is that shape and only that page uses it; the root index
  keeps its three-across cards and their summary line, because the ask was bigger cards on the
  import/export page, not a new front page.
- **Bored's recipes have a shelf.** The chip row keeps the fast path; a menu at its end opens a
  sheet listing every recipe with a letter plate, the name and one line of what the walk does
  (`describe`, assembled from the filters unless the recipe wrote its own `description`, ≤120
  characters). User recipes delete; built-ins do not, and the query refuses them by source anyway.
  Recipe import moved here from Settings. The chips follow the Room table, so a restore made in
  Settings cannot leave Bored holding a stale list.
- Verified: `:app:assembleDebug` clean; the whole unit suite green, 0 failures (263 tests at the
  time of writing), including `BackupReaderTest`, `SettingsImportTest`, `BackupReceiptTest` and the
  rewritten `SettingsExportTest`. No hand check: the tile sizes, the receipt wording on a real
  export and the recipe sheet are the owner's to judge.

## Naming and region, 2026-11-09 (sixth)

- **Feedback is Haptics.** The page only ever held vibration switches, and "Feedback" reads as a
  request for a review. Title changed; every keyword ("vibration", "buzz", "feedback") still
  finds it.
- **Backup is Data again**, because that is what it is. The page now says what it reads — a
  Parallax backup, a NewPipe zip or JSON, a LibreTube JSON, a Takeout CSV, or one Bored recipe —
  and lists what it can see: the backups in a folder chosen once through the system picker, plus
  the last few files this app wrote, deduplicated by URI, folder first then newest, capped at
  eight. Tapping a row imports it through the same path as the picker. SAF tree grant, no blanket
  storage permission, only `.json`, `.zip` and `.csv` offered.
- **Run setup again left About** for a full-width outlined button at the bottom of the settings
  index. A wizard re-run is a settings errand, not a fact about the app.
- **The United States is no longer a default.** Both hardcoded `"US"` literals are gone, and the
  region picker opens with a **System** row: ask as the phone's locale country, or send no country
  at all when the phone has none. Picking a country replaces System, System replaces the countries,
  and tapping the last country off lands back on System instead of refusing, because "no country"
  is a real answer.
- Verified: `:app:assembleDebug` clean; the suite green, 274 tests, 0 failures, including
  `BackupFilesTest` and the rewritten `ContentRegionsTest`. The empty-country path
  (`ContentCountry("")`) is the one thing only the Pixel can settle.

## Third FTP batch, 2026-11-09

Thirteen reports, grouped in `docs/reports.md`. Eight of them turned into code:

- **Members-only is its own state.** The extractor reports it as a plain unavailability, so
  `SourceErrors` reads the wording and `SourceError.MembersOnly` carries its own sentence. A
  permanent failure (`members`, `private`, `age`, `geo`, `sign-in`) now draws the black frame with
  no Retry button: pressing it could only fail again. The "cat video" line that stood where the
  message belonged is gone.
- **A flick either way belongs to the ladder.** `flickBelongsToLadder` hands a fast upward swipe
  to the minimise/fullscreen gesture instead of the volume band under the finger, which is what
  made a swipe up for fullscreen turn the volume up on the way.
- **Rotating works after leaving fullscreen.** Exit restores `SENSOR`, and holds the portrait
  decision only while the phone is still sideways, so turning the phone goes back to fullscreen.
- **The speed pill resets.** A tap is 1.0x, and the pill says so under the number while it is not.
- **Seek undo names the seconds**: "Seeked +12s to 1:00". `seekUndoLabel` is in `core/Time.kt`.
- **The share panel seeks.** Six step buttons (−30, −10, −2, +2, +10, +30 s) move the start and
  the picture, because a handle on a two-hour video cannot land on a second.
- **Up next refreshes**, one call per tap and none per recomposition; a failed refresh keeps the
  list rather than pretending nothing follows the video.
- **Bored explains itself.** The empty screen lists the modes with their `describe` sentence
  instead of saying "Choose a mode" over four names nobody has met.

Verified: `:app:assembleDebug` clean, 299 unit tests 0 failures (new: `flickBelongsToLadder`,
members-only classification, `permanent`, `seekUndoLabel`), installed on the Pixel 7. **The hand
check has not run** — the gesture, the rotation and the sheet are the owner's to judge.

## Asked, not done (2026-11-09, FTP)

The per-report record is `docs/reports.md`: one line per filed report, with a status that survives
`node scripts/reports.mjs`. This is the grouped reading of what is still open.

- **Buffering that lasts longer than the retry** (212007). The log shows one 15 s stall on
  `c1JGheQBPH4` at 94 s, a recovery, `ready in 2514ms`, and `net=wifi`: the retry worked and the
  note still says "still is". Two candidates, neither proven: the pill keeps saying *Buffering*
  while ExoPlayer is in fact playing, or a second stall follows the first and the two retries are
  spent. Needs a device repro with the log open, not a guess.
- **Bored, enhanced** (222224). "Good idea, enhance it" names no behaviour. The next step is a
  decision about what the mode is for — a recommendation engine, a playlist builder, or a faster
  path into long-form — before any code.
- **The portfolio's Parallax page** (222407) needs another pass, with no target named. That is the
  portfolio project's work, not this one's.
- **A pasted or clipboard YouTube link should open the video** instead of being searched as words.
- **Comments expand on a row tap only.** The chevron closes them; tapping a reply must not collapse
  the thread.
- **Voice-note count on cards**, an icon rail in the thumbnail corner, and smart placement that
  samples the frame for dark or empty regions.
- **A dead subscription keeps its slot.** 212007's log names `Peruvian Bull`,
  `This channel does not exist`, on every refresh. Retiring it needs a failure count per
  subscription, which is a Room v12 column and a decision about auto-removing something the person
  asked for. See Queued.

## Process, 2026-11-09

- **Reports are read by a script, not by eye.** `node scripts/reports.mjs` pulls the last three
  hours over FTP and prints a note and a version per report; `--log=<id>` prints only the warn and
  error lines of one; `--open` lists what nobody has answered; `--set "223004=…"` answers one. It
  never prints credentials and never rewrites a line that already has a status. Twelve 40KB bundles
  used to be read in full to find five sentences.
- **The log window is for events, not for chatter.** `HttpTrace` silences a request that succeeded
  in good time, so a report's 300 entries cover a session instead of ten minutes; a non-2xx, an
  empty 200 and a call over 8 s still log. `HttpTraceTest` pins the three exceptions.
- **A filed report leaves a line behind.** `BugUpload` writes `bug: filed <name>` to the log, so
  Diagnostics is the history of what was sent (223316, 223324).

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
  `apksigner` (32 MB, minification still off). The Gradle signing config is gone: under AGP 9.4.1 it
  validated and never applied, so it only made the build look signed. `scripts/release.ps1` is the
  one path — bump, assemble, sign, manifest, deploy, verify.
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
- **Release signing has one path.** `scripts/release.ps1` runs bump → assembleRelease → apksigner
  → version.json → deploy → verify. It reads `app/keystore.properties`; there is no Gradle signing
  config, because AGP 9.4.1 validated it and never applied it.
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
- Package `com.presaince.glasstube` (was `com.presaince.parallax`; the rename on 2026-11-10 made it
  a new app id, so an installed Parallax cannot be updated in place).
- Accent is Presaince brand cyan; the gradient is for wordmark, splash and About only.
- No debate transport, no clip payload, no P2P sync, no third-party skins, no Slack Circuit.
- Download is a single simple path and comes last.
- Manual dependency injection; ask before adding a library.

## Queued

- **The player's ladder is tested as a rule, not as a player** — `tickAction` is pure and has 14
  cases, but `degrade()`, `giveUp()` and the session preference are only reachable through a real
  ExoPlayer, so the wiring between the rule and the stream is checked by hand. Cost to leave it: a
  mistake in `degrade()` — a reload that resumes at 0, a step that does not reach the extractor —
  looks exactly like the bug it was meant to fix. Found 2026-11-12.

- **The app's own preview card and the list card disagree** - `PreviewCard`
  (`SettingsSections.kt`) joins the channel into the meta line, rounds the thumbnail at 10 dp and
  draws progress as a bare 35 % line, while `VideoRow`/`Thumbnail` give the channel its own line,
  round at 12 dp and draw the line on a translucent track. The Settings preview's own comment says
  it is "the same card the lists draw". Cost to leave it: the preview teaches a card the feed does
  not show, and the marketing page can only copy one of the two. Found 2026-11-11.

- **A VPN over mobile data reads the Wi-Fi quality stop** — `activeTransport` sees only
  `TRANSPORT_VPN`, so `video_quality` (the higher ladder) applies while the bytes are in fact
  metered. Cost to leave it: a VPN user gets their Wi-Fi ceiling on a phone plan. The fix is to walk
  `ConnectivityManager.allNetworks` for the underlying transport, which is a binder call per pick.

- **The app root is the document root** - Plesk's application root for odesaplay.com.ua is
  `httpdocs`, which nginx also serves, so `server.js`, `admin-routes.js` and `package.json` are
  downloadable at their own paths. No secret is in any of them (the admin token is a Plesk
  environment variable), but the source should not be published. This profile has no
  nginx-directives box, so `location = /server.js { return 404; }` is not available. Two options:
  `chmod 600` the three files (nginx runs as another user and gets 403, Passenger reads them as
  the subscription user; Plesk's permission repair can undo it), or split the roots - application
  root `/odesaplay-app`, document root `/odesaplay-app/public` inside it, which is the shape
  Plesk's warning asks for. The split was tried on 2026-11-11 and reverted: the FTP account is
  chrooted at `httpdocs`, so a sibling app root puts `server.js` outside every deploy config, and
  aiming `remoteDir` at `httpdocs` from there created `httpdocs/httpdocs/`. Doing it properly
  means re-homing the FTP account to the subscription root and prefixing
  `odesaplay-app/public/` onto every config that targets odesaplay.com.ua (portfolio, binsy, oko,
  both stubs, the glasstube page/APK/reports configs). Cost to leave it: source disclosure only.

- **`npm install` on the host dies on a quota** - Plesk's NPM install fails with
  `npm ERR! code Unknown system error -122` / `syscall write` / `errno -122`. That number is
  `EDQUOT`: the subscription's disk quota, which is not the same number as free space and is read
  in Plesk -> subscription -> Disk usage, not with `df`. Small writes still succeed - npm wrote
  its own debug log before dying - and large extraction does not, which is exactly how the error
  presents. The app runs and needs no install unless `package.json` changes; a `node_modules`
  built on the PC and uploaded over FTP is the workaround. Do not delete `node_modules` to make
  room: if the reinstall hits the same wall, the next restart dies at `[BOOT] Missing deps`. Safe
  reclaim, in order: `.npm/_cacache`, `.npm/_logs`, locally stored Plesk backups. Cost to leave
  it: dependencies cannot be changed on the host until the quota is raised. Found 2026-11-11.

- **Playlists screen** - the owner asked for it on 2026-11-10 and it is the agreed next feature.
  Nothing exists today: no route, no `VideoSource.playlist`, no view. Needed pieces: an extractor
  call for a playlist's items (NewPipe's `PlaylistInfo`/`PlaylistExtractor`), a `Route.Playlist`,
  a list screen with the same `VideoRow` and sort, and two entry points — a Playlists tab on the
  channel page (the extractor already reports that filter in `Channel.tabs`) and any saved playlist
  later. Cost of leaving it: the channel page cannot show a channel's playlists at all, which is one
  of the things `102750` asked for.
- **Chapter thumbnails from the storyboard** — done 2026-11-12, see the top of this file.

- `data/backup/Backup.kt` - the file carries subscriptions, settings and the reader's recipes, but
  not notes or watch history. Cost of leaving it: moving phones loses the deck and every resume
  point, which are the two things this app made itself. Found 2026-11-09 while writing the backup;
  a third section in the same file is the fix, not a second format.
- `ui/nav/ParallaxNav.kt` - `prefetchStreams` fires on every Player route entry, including re-entering the video that is already loaded and about to be reused, so a warm restart pays one extractor fetch for nothing. Cost: a needless request on a shared client; found 2026-11-09 while moving the retries.
- `data/library/ContentRepository.kt` - `refreshFeed` spins `while (playbackBusy()) delay(400)` with no ceiling, so a buffering player can hold a refresh open indefinitely. Cost: a refresh that reads as a hang. Found 2026-11-09 while fixing the stall; not changed in that patch.
- `data/source/SelfRepair.kt` — data-driven extractor (JSON path model) is not built; LLM repair covers search query and region only. Cost of leaving it: field renames still need a code change instead of a model publish.
- `data/library/ContentRepository.kt` — the feed refresh still opens a videos tab per subscription, although `ChannelFeed` now reads the same latest uploads for free from RSS. Cost: a refresh pays an extractor page per channel when a keyless feed would do; RSS has no duration, and the feed cards show one, which is the reason it was not swapped blind. Found 2026-11-09 while adding the uploads source.
- `data/discover/DiscoverEngine.kt` — a search recipe reads one page. `VideoSource.search` has no continuation, so `count` above the first page changes nothing and there is no "more" on Bored. Cost: the pool is one page per tap; the fix is a search-page token on the source, not a bigger count. Found 2026-11-09.
- Google sign-in for subscription import - designed but not built: an app-owned read-only OAuth
  client (`youtube.readonly`), `subscriptions.list(mine=true)` paged 50 at a time into the same
  subscription rows, tokens in `SecurePrefs`. Blocked on a Google Cloud client published to
  Production, because Testing-mode refresh tokens last 7 days. Cost of leaving it: people arriving
  from Premium must export from Google Takeout or another client first. The wizard's Start page
  therefore shows import or empty only, with no dead sign-in row. Decided 2026-11-09.
- `web/index.html` - the captures are full-screen stills and argue with screenshots what the app does in motion. A silent 12 s MP4 in the phone frame would show the feed swipe, player, mini player, Bored and the accent on reopen: screenrecord 1080×2400, encode 720×1600 H.264 CRF 26 `-an` `+faststart`, ≤2.5 MB, poster frame, `preload="none"`, tap to play. Not shot yet. Side quest: engine/self-repair as a 4 s rebuilt card, because it is invisible when it works.
- `data/library/ContentRepository.kt` - a channel that no longer exists (`Peruvian Bull` in the 212007 log) fails on every refresh and keeps its subscription slot. Retiring it needs a failure count per subscription (Room v12 and a migration) and a decision about removing something the person asked for. Cost of leaving it: one wasted extractor call per refresh per dead channel, and a line in every bug report. Found 2026-11-09 by triaging that batch.

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
- **Schema export is on, and the destructive fallback is gone.** Schemas 11–13 are committed under
  `app/schemas/`, every bump carries a hand-written migration, and `data/db/MigrationTest.kt` opens a
  file from the previous version at the current one. A file older than 11 is the one case with no
  test, because 5–10 were never exported.

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
- [x] Remove `fallbackToDestructiveMigration` and enable schema export before the first release
      (done 2026-11-12: v13, schemas 11–13 committed, `MigrationTest` over 11 → 13).
- [ ] `implementation_plan(1).md` sits in the repo root; decide whether it stays.
