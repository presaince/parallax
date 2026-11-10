# Parallax roadmap

Native Android client for YouTube, GPLv3, local-first. The full build plan is
`C:\Users\Bodi\.opencode\plan\parallax-plan.md`; this file tracks state.

Status: M0 gated; M1–M5 built, no gate run yet. M6 scaffolded, needs an OAuth client. M7 tooling in
place. The shell and the second UI pass landed 2026-11-05 (bottom navigation, thumbnails,
inline comments, mini-player, pull-to-refresh); the device check for that pass is still pending.
No git repo — Bodi tests a build for a few days first.

## Brightness handback, 2026-11-10

The owner's report again: "can't change OS brightness, toast says it's locked by top app". The
player's brightness swipe wrote `window.attributes.screenBrightness` and nothing ever cleared it, so
the single Activity carried the swiped level onto every screen behind the player, and the shade
refused its own slider while Parallax was on top. `restoreSystemBrightness` now runs from the
player's `DisposableEffect(Unit)` dispose and sets `BRIGHTNESS_OVERRIDE_NONE`, leaving the rest of
the window attributes alone. Verified: `BrightnessHandbackTest` (the override clears, `dimAmount`
survives), `:app:assembleDebug`, installed on the Pixel 7. Whether the shade's slider is free
again after a gesture-and-back is the owner's check.

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

- `data/backup/Backup.kt` - the file carries subscriptions, settings and the reader's recipes, but
  not notes or watch history. Cost of leaving it: moving phones loses the deck and every resume
  point, which are the two things this app made itself. Found 2026-11-09 while writing the backup;
  a third section in the same file is the fix, not a second format.
- `ui/nav/ParallaxNav.kt` - `prefetchStreams` fires on every Player route entry, including re-entering the video that is already loaded and about to be reused, so a warm restart pays one extractor fetch for nothing. Cost: a needless request on a shared client; found 2026-11-09 while moving the retries.
- `data/library/ContentRepository.kt` - `refreshFeed` spins `while (playbackBusy()) delay(400)` with no ceiling, so a buffering player can hold a refresh open indefinitely. Cost: a refresh that reads as a hang. Found 2026-11-09 while fixing the stall; not changed in that patch.
- `data/source/SelfRepair.kt` — data-driven extractor (JSON path model) is not built; LLM repair covers search query and region only. Cost of leaving it: field renames still need a code change instead of a model publish.
- `data/library/ContentRepository.kt` — the feed refresh still opens a videos tab per subscription, although `ChannelFeed` now reads the same latest uploads for free from RSS. Cost: a refresh pays an extractor page per channel when a keyless feed would do; RSS has no duration, and the feed cards show one, which is the reason it was not swapped blind. Found 2026-11-09 while adding the uploads source.
- `data/discover/DiscoverEngine.kt` — a search recipe reads one page. `VideoSource.search` has no continuation, so `count` above the first page changes nothing and there is no "more" on Bored. Cost: the pool is one page per tap; the fix is a search-page token on the source, not a bigger count. Found 2026-11-09.
- `feature/**` - about thirty call sites still paint accent *text* with `colorScheme.primary` instead of `accentTextColour()`/`LocalAccentInk` (nav labels, chips and Settings are converted; feed, player, comments, library are not). Cost of leaving it: at AA and AAA those labels keep their raw accent, so a magenta or blue accent still sits near its own tinted surface. Found 2026-11-09 while adding the Contrast card.
- `ui/component/ScrollToTop.kt` - the button is a stock `SmallFloatingActionButton`, whose container is the `surfaceContainerHigh` role, and `glassScheme` overrides only surface, surfaceVariant, background and outlineVariant. In Glass it is therefore opaque M3 baseline grey, not the accent-tinted pane everything else sits on. Cost: one grey button in a themed app; the fix is `containerColor = MaterialTheme.colorScheme.surface` plus the usual hairline. Found 2026-11-10 while giving the top bar its fill and seam.
- `app/build.gradle.kts` - `assembleRelease` fails `validateSigningRelease` with "Keystore file not
  set" even when `keystore.properties` is present and `signingConfigs.release.storeFile` resolves to
  the file. Packaging works with `-x validateSigningRelease` followed by `apksigner sign`. Cost:
  every release needs a manual signing step. Found 2026-11-09 while installing the wizard build.
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
