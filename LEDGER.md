# Ledger — bugs, upgrades, features, decisions

Four lists, kept apart so each can be published where it belongs. Nothing here is a promise; it is
a record of what was found or decided while working, so it is not lost with a session.

Rules:

- One line per item. **Project — what — where.**
- A bug says how it manifests and whether it is fixed elsewhere.
- An upgrade says what got better and in which project.
- A feature says what it is and how far it got.
- A decision says what was ruled out or chosen, and why, so it is not reopened by accident.
- When something is published (a commit, an issue, a release note), mark it `[published]`.
- Do not duplicate: a project's own `ROADMAP.md` holds its state; this file holds cross-project
  findings that would otherwise be forgotten.

---

## Bugs

- Parallax — swipe-down to minimise moved a few percent and stopped; two causes. `resolveAxis` returned `None` for a vertical drag in the centre, so the minimise branch was unreachable, and the pointer detector sat on the node that the drag transformed, so the deltas warped and the "finger left the video" check fired. Fixed 2026-11-07: every zone resolves vertical, and the detector is on an untransformed box. Gate: Pixel 7.
- Parallax — a tap on the picture still toggled the controls under the back arrow and bug button. Fixed 2026-11-07 with `SurfaceTapZones`, a named list of rectangles in surface fractions; add one entry per new control.
- Parallax — a collapsed description showed a random section: it clipped a fixed 72dp height. Fixed 2026-11-07: the first four lines of real text, counted from the top.
- Parallax — the direct-download APK was reported corrupt after installing 0.2.0. Not reproduced here; the next build logs the served size and sha256 and checks the digest before offering the installer.
- Parallax — a stream that died mid-video showed an error and stayed there. Now retried twice at the position it reached; the log also names an unexpected `IDLE`.
- Parallax — shuffle was one "daily" switch and solid surfaces was one lone Material switch. Both are now choices: shuffle is a master with Daily, Hourly or On open; surfaces is a Glass/Solid button pair.
- Parallax — video cards were full-width pictures with text underneath, so a screen held only a few. Now sideways: picture left, words right. The Appearance preview draws the same shape.
- Parallax — History was a 40dp icon beside a scrolling sort row. Now a named pill on the feed header and a full-width row in Library; clear history is three named buttons.
- Parallax — loading a video drew a spinner/river over a dimmed thumbnail. Now the thumbnail is the animation: it breathes under a soft light band, with Kinetic, Still or None and an animations master.
- Parallax — the preference page buried the preview under Motion, so the card being changed was never on screen with the controls that change it. Fixed 2026-11-07: Preview is the second section, in its own card, full width, with the layout modes and the six facts directly under it.
- Parallax — surfaces were two chips and shuffle was a switch plus three chips, which read as separate settings. Fixed 2026-11-07: Surfaces is one block cut across (Glass over Solid), and Shuffle is a four-chip row (Shuffle, Daily, Hourly, Open) whose master mutes the rest.
- Parallax — watch history lived in Diagnostics and duplicated Library → History. Removed from Diagnostics 2026-11-07; `HistorySection.kt` is uncalled and can be deleted on a word.
- Oko — settings screen alignment drift between groups and rows; worth a pass before the next
  release. `com.odesaplay.oko` settings.
- Oko — feedback sheet duplicates the screenshot capture that Parallax now implements in
  `BugShot`; unify when either is touched next.
- Oko — `AGENTS.md` first line has a mojibake en dash.
- Parallax — a premiere video opens to a dead player; the extractor error is
  `LIVE_STREAM_OFFLINE`. Now logged and shown with Retry, but the list should mark it UPCOMING
  before you tap. Partly fixed.
- Parallax — history duplicated the same video; fixed by grouping and a watch count.
- Parallax — dictation never opened the microphone on Android 11+; fixed with a `<queries>` entry
  for `android.speech.RecognitionService`.
- Parallax — theme changes recreated the activity and dropped the user out of Settings; fixed with
  a live `ThemeController`.
- Parallax — the top bar swallowed taps behind the video; fixed by using a TextureView.
- Parallax — resume sought before the duration was known, so it clamped to zero. Not fixed yet.
- Parallax — replies expand but nothing loads; the replies page token needs checking on a device.
- Parallax — tapping the notes mic force-closed the player: the notes card nested a `LazyColumn`
  inside the player's `LazyColumn` item, which Compose rejects with an infinite-height constraint.
  Fixed 2026-11-06; the notes list is a plain column.
- Parallax — every control on the video also fired the parent tap gesture, so a tap on Back was as
  likely to hide the controls as to leave the player. Fixed 2026-11-06: the loop ignores a press a
  child consumed.
- Parallax — comment replies looked like a dead button: a cached comment page from before the token
  fix was served for up to an hour, and items without a token returned silently. The cache is now
  rejected when replies are claimed without a token, and a missing token says so.
- Parallax — AI chapters: when a description carries no chapters and the model features are on, the transcript is sampled, sent to the model, and the result is cached in a new `ai_chapters` table (Room v11). The Resync settings summary now says so.
- Parallax — AI chapters: when a video's description has no chapters and the Resync model settings are on, the transcript is sampled and sent to the model; the chapters are cached in `ai_chapters` (Room v11, migration 10→11). The Resync settings summary now says it also unlocks AI chapters.
- Parallax — the description's "Show more" label is gone. Collapsed is the preview with the chevron, expanded is everything, and nothing in between.
- Parallax — 0.2.0 (versionCode 2) released live with `version.json` (32,604,909 bytes): AI chapters, rotation, the new speed and quality controls, bar swipe, comment/reply chevrons, the mini-player strip, and the About self-update check. The phone still runs the debug build, disconnected at deploy time.
- Parallax — self-update ported from Oko: About checks `version.json` on the server, downloads and validates the APK (sha256, signing key, package, not-older-than-installed) and hands it to the system installer. `scripts/make-version-json.mjs` writes the manifest at release, and the deploy defers it after the APK. The debug build on the phone has the same signing key as the release APK, so the update works even for it.
- Parallax — rotating the phone in the player goes fullscreen again. The manifest no longer locks the orientation; MainActivity sets portrait by default, the player asks for landscape on its own.
- Parallax — the video subtitle row ("watched now 5d ago, 1 reply") wraps to two lines instead of an ellipsis.
- Parallax — dragging the speed pill shows the speed list as a thin column just under the finger (flips above if it would leave the screen), with no fill. The tap-open speed panel is thin too, with no fill and no scroll. Video quality is tappable: a menu picks it and reloads the video at the current position.
- Parallax — the mini card no longer disappears on a sideways swipe. It folds to a strip (title, tap or swipe up to open, X to stop) and swipes back to the picture. A tap on the media notification opens the playing video's full player.
- Parallax — the description collapses by measured height (72dp), not by line count, so three long paragraphs now get the chevron and "Show more" too.
- Parallax — chapters are a collapsible section under the video. Settings → Playback → "Chapters as a scrolling row" switches between a horizontal row of small cards and a plain list with a small thumb per row. Every chapter shows the video's thumbnail, since no per-chapter frame is available.
- Parallax — a video paused after one second of play because a 1% swipe of a long video moved
  minutes; the swipe now moves in seconds (2 / 5 / 10 per tenth of the screen, rising to eight
  times with distance) and the gesture is cancelled if the finger leaves the video, so a swipe that
  starts on the picture cannot be finished below it.
- Parallax — swiping sideways on a pushed screen (Settings, Search, a channel page) moved the tab
  pager underneath and popped the screen the person was on. The pager's swipe is now off while
  anything is on top of it.
- Parallax — subscriptions only ever arrive by file import: there is no subscribe action anywhere, and
  neither search results nor the player lead to a channel page. Found 2026-11-06, after a list went
  stale: the phone held 38 channels from a NewPipe export dated 2025-04-21 (the file has 39, and
  "The Bitcoin Layer" never made it into the database), with nothing subscribed since.
- Parallax — the comment count lived in two places at once: a "Comment count" switch in the video
  card toggles and a bare "Comments" heading. The switch is gone from the cards; the heading now
  reads "Comments (n)" with the total from the first page, matching how the notes card shows its
  count.
- Parallax — the description was one blob because `cleanHtml` parsed twice: the second `Html.fromHtml`
  pass turned the newlines that `<br>` and `<p>` produced into spaces. One pass now, with a single
  entity un-escape, so the line breaks survive. The collapseable chevron was already there; it only
  appeared once the text had more than three lines.
- Parallax — "Made by Presaince" is one line in About, not two.: two listings disagree about whether a
  stream is live. Fixed 2026-11-06 by remembering when LIVE was first seen and holding the badge for
  three hours (DB v10, `MIGRATION_9_10`). The cost is a badge that can outlive a stream by three
  hours.
- Parallax — the speed panel's drag also moved brightness, and its fill was measured against the
  screen rather than the panel. Both rebuilt 2026-11-06; unconfirmed on the phone.
- Parallax — cards kept an old style after the Appearance switches changed it, because layered
  navigation leaves the list composed under Settings. Fixed with a style revision counter that
  composed cards observe.
- Parallax — leaving fullscreen in landscape left the whole app sideways: `UNSPECIFIED` does not
  restore a manifest portrait lock. Fixed 2026-11-06 with `SCREEN_ORIENTATION_PORTRAIT`.
- Parallax — with Glass on, a pushed screen let the tab pager's cards show through its transparent
  background. Fixed 2026-11-06 with `ThemeBackdrop`, which paints the aurora or theme background as
  the floor of every overlay.
- Parallax — a tap meant to hide the controls moved the play disc to the new tap point on its way
  out, reading as a second play button instead of a hide. Fixed 2026-11-06: only a tap that reveals
  the controls chooses where the disc sits; a hiding tap changes nothing but the visibility.
- Parallax — tapping Bored from Library landed part-way: the pager stopped with a sliver of Library
  on the left. The stack was being synced from `currentPage`, which flips mid-animation, so the
  animation toward Bored was rewritten to Library and cancelled; and the follow-up animation was
  skipped because `currentPage` already claimed to be there. Fixed 2026-11-06 by syncing on
  `settledPage` and by deciding whether to animate from `settledPage` too.
- Parallax — a screen could vanish without a back press, and the tab bar could highlight one tab
  while another tab's content stayed on screen. The pager stayed composed and swipeable under an
  overlay, so a horizontal gesture on empty space moved a page and the page handler reset the stack
  to that tab. Fixed 2026-11-06: `userScrollEnabled = isTab`, and a page change only rewrites a
  single-tab stack.

## Upgrades

- Search is only offered on Feed and Library; Bored Mode, Settings and pushed screens no longer
  show the magnifier.
- Settings → Diagnostics carries a watch-history section: every play as an event, with ago, date,
  duration and the position left. Comments on the player are collapsible, with the count in the
  heading.
- Project docs — new skill `project-docs` writes `ARCHITECTURE.md` and `BEHAVIORS.md` from the code;
  `web-app-builder` now ends with it, and Parallax has both files.
- Parallax — navigation: the tab pager stays composed under every pushed screen, so going back never
  refetches and never loses scroll. Lays the ground for the instant reveal the minimise gesture
  needed.
- Parallax — Library has three readings under one chip row: Grid, By channel, Collections.
- Parallax — Collections replaces the pushed Presets screen; same four rules, names unchanged, now
  inside the library they describe.
- Parallax — Appearance: Glass tone (System/Dark/Light) and Shuffle daily accent.
- Parallax — player: selectable description and comments per card.
- Parallax — player: a clip sheet with two draggable ends; link carries the start, range in the text.
- Parallax — About: the app name wears the accent gradient, like the top-bar wordmark, and the
  section is the page instead of a row that pushes another one.
- Parallax — the direct build: a signed release APK (32 MB, minification off) for sideloading. Signed
  with the existing release key via `apksigner`, because `assembleRelease` does not apply the signing
  config under AGP 9.4.1.
- Parallax — Glass no longer offers System/Dark/Light chips: it inherits the last ordinary theme
  that was picked (`glass_base_mode`), so Dark → Glass gives dark glass and Light → Glass gives
  light glass. One row fewer in Appearance.
- Parallax — the mini player's pause button never answered reliably (the card's tap target swallowed
  it), so it is gone. The X is the one control that stops; the thumbnail, the title and the progress
  bar stay. Swipe sideways hides the card while the audio keeps playing. A downward swipe no longer
  animates the backflip: it closes the player at once, and a slow drag does nothing visible.
- Parallax — swiping to seek mapped travel to a share of the whole video, so a small move on a long
  video jumped minutes. Fixed 2026-11-06: seconds per tenth of the screen, rising to eight times
  with distance, with the base chosen in Settings (2 / 5 / 10 s).
- Parallax — the direct build is uploaded by this project (`deploy-apk.properties`), not carried in
  the portfolio. The portfolio ships no binaries; it only links to the URL.
- Parallax — the clip sheet is the whole share panel now: "This moment" or "A clip" on one bar with
  a live playhead line, go-to-start and go-to-end (two seconds early), and sync-to-now. A long press
  on the share pill shares the plain link without opening the panel.
- Parallax — bug reports: a note field and Send on the bug screen, posting one JSON bundle to
  `other_apps/parallax/upload.php`. Server-side reports sit in a sibling directory behind a 403.
- Parallax — decision: no shared secret on the upload endpoint. A token inside an APK is extractable,
  so it would guard nothing; per-IP rate limiting plus a schema check is what actually bounds abuse.
- Portfolio — a Parallax marketing page at `?app=parallax`, a `/parallax` share stub with its own
  Open Graph card, and an app row that links to both.

- Parallax — player: controls on the video, auto-hide, 56dp play/pause with a ring, double-tap
  seek chip, vertical swipes for volume and brightness, downward drag to minimise.
- Parallax — player: play/pause moved to the dead centre; the seek bar dropped from Material
  `Slider` to a 3dp track with a 12dp thumb; volume and brightness show a centred capsule with a
  fill; the minimise gesture follows the finger.
- Parallax — player: the play disc appears where the finger tapped, a fast downward flick
  minimises from any band, double taps accumulate, and the scrub readout is a wide pill at the far
  edge with a progress bar in it. Volume and brightness lost their text.
- Parallax — media notification: artwork from the video's thumbnail, so the shade and lock screen
  show what is playing.
- Parallax — player: the speed panel opens at the control that opened it, shows a vertical track,
  picks on tap and only commits a drag on release.
- Parallax — notes: text entry returned (the field is always there and starts a note at the
  playhead), per-note delete is an icon, and the list has copy-all and confirmed delete-all.
- Parallax — feed: the inner Feed/History tabs are replaced by one column and a history icon at the
  far right; the sort direction is pinned at the start of the row and each list only offers sorts
  it can honour.
- Parallax — player: collapsible headers (description, notes) open on a full-row tap and animate;
  the minimise gesture is a backflip into the mini card; the notes field carries the dictation
  river.
- Parallax — settings: a Picture-in-picture switch, because popping out on every app switch is not
  everyone's idea of polite.
- Parallax — player: seeking is one gesture (slide) with the distance as the pill's headline and
  the position underneath; the seek bar is replaced by a permanent 2dp bottom line; speed is a
  press-hold-slide with a left-edge readout; the share control is a live-time pill.
- Parallax — notes: per-note copy, tap-the-text editing, and a save tick that knows when the
  microphone is busy.
- Parallax — media notification: optional microphone in place of the restart button, opening the
  player with the notes card listening.
- Parallax — player: leaving the player in landscape no longer strands the whole app sideways.
- Parallax — player: the speed slider is a left-edge panel with the track pinned to that edge; the
  share pill distinguishes tap (plain link) from long tap (link with time); the collapsed
  description text expands on a tap.
- Parallax — settings: rebuilt as a root list of eight sections with a persistent search field,
  curated keywords and one-typo tolerance; each section is its own route.
- Parallax — player: speed in 0.1 steps with presets 0.8/1.0/1.2/1.5/2.0, remembered.
- Parallax — settings: grouped sections, live theme and accent, Data for import/export.
- Parallax — logs: feed pulls, per-channel failures, play/stop, extractor errors; 48h retention;
  bug report with a screenshot.
- Parallax — library: channel grid, avatars, per-channel sections, pull to refresh, Presets.
- Parallax — search: focused field, three readings (all/subscriptions/bored), local history.
- Parallax — navigation: swipe between destinations, inner tabs take priority.
- Parallax — history: grouped by video, watch counts, clear today/week/all.
- Parallax — sorting: one shared sort row (date, likes, watches, progress, duration, views, A-Z)
  on Feed and History.
- Oko — the grouped-settings concept is the reference Parallax copied; improvements should flow
  back both ways.

## Features

- Parallax — Collections (was Presets): gathering dust, almost done, they're back, never opened,
  read as a third mode of the Library rather than a screen of their own.
- Parallax — Pburger as the Settings entry on every screen with a top bar.
- Parallax — Bored Mode recommendations with a visible path.
- Parallax — selectable, link-aware description folded into the comments column. Built.
- Parallax — glassmorphic theme. Skill written; not shipped; not reviewed.
- Parallax — user-chosen speeds in a grid ("price is right" selector). Built in Settings → Playback.
- Oko — settings search with typo tolerance; ported to Parallax on 2026-11-06 as
  `SettingsRegistry.kt` plus `SettingsSearchTest`.

## Decisions

- Parallax — no Piped fallback. Revisit only if the extractor canary fails for real.
- Parallax — no core library desugaring; minSdk 33 instead, modern devices only.
- Parallax — personal release only for the foreseeable future; no F-Droid listing.
- Parallax — Resync tail is 15 seconds past a comment's timestamp.
- Parallax — Resync settings live in Settings; alignment is triggered from the comments.
- Parallax — posting is off until the user supplies a Google OAuth client; inline comment and reply
  only, no separate Post screen.
- Parallax — one video surface at a time (full player or mini), never two bound to one player.
- Parallax — no `material-icons-extended`; copy the vectors we need.
- Parallax — the layered P (pburger1) is the app mark; the Presaince wordmark on About stays on the
  brand ramp.
- Parallax — descriptions and notes are one column; no tabs.
- Parallax — a clip link carries only its start time; the end travels as text ("1:20–2:05"). No
  share target accepts both, and a link that silently drops the end would be a lie.
- Parallax — Collections are rules, not stored folders: nothing to sync, nothing to go stale.
