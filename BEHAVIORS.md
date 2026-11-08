# Behaviours — Parallax

The rules the product obeys, by surface. Each one says what happens, where it breaks, and the file
that makes it true. Structure and tradeoffs are in `ARCHITECTURE.md`.

Format: **Rule** — the invariant. **Edge** — the boundary cases. **Enforced by** — the code.

---

## Playback

**Rule.** One `ExoPlayer`, one surface. A video opens playing; leaving the player keeps the audio
and shows the mini card.

**Edge.** The mini card and the full player are never bound at once. Tapping the mini reopens the
player for the same session, so nothing restarts. Back from the player is a backflip, not a stop.

**Enforced by** `playback/PlaybackController.kt`, `ui/nav/ParallaxNav.kt`, `ui/component/MiniPlayer.kt`.

**Rule.** Resume seeks only after the player reports `STATE_READY`; a seek before the duration is
known clamps to zero.

**Edge.** A resumed video with no stored progress starts at 0.

**Enforced by** `PlaybackController.kt`, `feature/player/PlayerViewModel.kt`.

**Rule.** Speed, quality ceiling and the audio track are playback settings, not per-video state.

**Edge.** Audio prefers the original track, then the device locale, then the highest bitrate;
multi-track videos are merged rather than taking the muxed default. Quality falls back to Auto when
the chosen height is missing.

**Enforced by** `data/source/NewPipeSource.kt`, `PlaybackController.kt`.

**Rule.** The media notification carries artwork and media controls; its restart button is optional.

**Edge.** The restart button is a custom-layout action; on this device custom notification actions do
nothing, so it is a toggle that drops seek-to-previous. PiP on leaving the app is a separate toggle.

**Enforced by** `playback/PlaybackService.kt`, `PlaybackController.kt`.

**Rule.** Fullscreen is landscape and nothing else is.

**Edge.** Entering fullscreen forces sensor-landscape; exiting restores portrait, not "unspecified",
because the manifest lock is portrait. Turning the phone in the player is treated as fullscreen
too, and the manifest's portrait lock exists so the rest of the app never turns.

**Enforced by** `feature/player/PlayerScreen.kt`, `AndroidManifest.xml`.

**Rule.** A stream that dies mid-video is retried twice at the position it reached before the
failure is shown.

**Edge.** The player publishes the error code; the screen reloads streams at the last position
after a short pause, at most twice per video. A stream URL that expired while paused is exactly
what this covers. Past two tries the failure stays on screen with Retry.

**Enforced by** `PlaybackController.kt` (`playerError`), `PlayerScreen.kt`.

---

## Player gestures

**Rule.** A tap toggles the controls, except in the corners that own a control.

**Edge.** A tap that hides the controls does not move the disc on the way out; only a tap that
reveals them chooses the position. Pressing play hides the overlay at once: the tap already said
"play". A tap on a control is consumed by that control and does not also
toggle the overlay. The back arrow and the bug button each register a named `TapZone`; a tap inside
one is not a picture tap, and the registry is the place new controls are added.

**Enforced by** `PlayerScreen.kt`, `feature/player/gesture/PlaybackGestures.kt`,
`feature/player/gesture/GestureState.kt` (`SurfaceTapZones`).

**Rule.** A sideways swipe seeks, in seconds per tenth of the screen, not in percent of the video.
**Edge.** The base is 2, 5 or 10 seconds per tenth (Settings → Playback, default 5) and the rate
rises to eight times as the finger travels. Seeking is applied during the drag, throttled to 90 ms,
plus one exact seek on release. The pill shows the signed delta and the target.

**Enforced by** `PlayerScreen.kt` (`onHorizontalDelta`), `SettingsSections.kt`.

**Rule.** Any downward drag minimises: left and right bands still change brightness and volume,
the centre is the way out.

**Edge.** Brightness only answers while the video is playing. On a paused or stopped picture the
same vertical drag belongs to the page, so it must not dim the screen. The detector sits on a box
that does not move, so the finger is read in screen space. A vertical gesture is not abandoned when
the finger leaves the picture, which is what froze the drag a few percent in. A fast flick counts
even when short; a slow drag has to pass about a fifth of the height. The surface follows the finger
and closes with no release animation, or snaps back.

**Enforced by** `PlaybackGestures.kt`, `PlayerScreen.kt`.

**Rule.** Speed is a press-and-hold dial plus a tap-to-open panel.

**Edge.** While the dial is held or the panel is open, the picture gesture is disabled, so a drag
cannot also be a brightness swipe. An interrupted gesture clears the hold in `finally`. The panel's
fill is measured against the panel, not the screen.

**Enforced by** `PlayerScreen.kt` (`SpeedPanel`, gesture gate).

---

## Player page

**Rule.** A collapsed description shows only its header. Tapping the header or the word opens the
whole text.

**Edge.** When the video has chapters, their timestamp lines are removed from the description: the
chapter strip above already shows them, and a second copy that does not seek is noise. Expansion
shows the remaining text whole. `DescriptionPreview.kt` and its test still describe the old
four-line fold and are no longer used by the screen.

**Enforced by** `feature/player/PlayerScreen.kt` (`DescriptionBlock`, `descriptionBody`),
`feature/player/Chapters.kt` (`timestampLine`, `parseChapters`).

**Rule.** Chapters render as a horizontal strip of cards by default; Settings can make it a plain
list.

**Edge.** The strip is collapsible like the description. Each card seeks on tap. The chapter the
playhead is inside is marked in the primary colour, and its picture carries a 2dp border; before the
first mark no chapter is marked. Chapter pictures are YouTube's own storyboard stills (mq1–mq3) and
the poster thumbnail, loaded only when the row is on screen. A
timestamp line may be wrapped in brackets or parentheses, follow a bullet or dash, and use
three-digit minutes ("100:30"); the title is what remains after the timestamp.

**Rule.** At the end of a video, related videos appear under "Up next" behind a Settings switch.

**Edge.** The list loads once via the source boundary when the playhead reaches the end, and is
hidden entirely when the switch is off.

**Rule.** The channel name under the title is a door: tapping it opens the channel page. The video's
facts (views, age) sit on the right of the same line.

**Enforced by** `PlayerScreen.kt` (`item(key = "titles")`), `ui/nav/ParallaxNav.kt`.

**Rule.** A seek that moved more than two seconds offers an undo on the picture for a few seconds.

**Edge.** The offer covers both a sideways swipe and a drag on the progress bar. Undo returns to the
position the gesture started from; the offer leaves on its own and never blocks the controls.

**Enforced by** `PlayerScreen.kt` (`SeekUndoChip`, `onHorizontalEnd`, `onBarEnd`).

---

## Sharing

**Rule.** One share control. A tap opens the panel; a long press shares the plain video link.

**Edge.** The long press is silent (no toast) and carries no timestamp.

**Enforced by** `PlayerScreen.kt` (`item(key = "actions")`).

**Rule.** The panel has two modes: **This moment** (the link carries the playhead) and **A clip**
(the link carries the start; the end travels in the text).

**Edge.** A YouTube link can only carry one time, so the text is `link&t=<start>s` plus a second
line `1:20–2:05`. The bar shows a live playhead line; the video keeps playing behind the sheet.

**Enforced by** `PlayerScreen.kt` (`ShareSheet`).

**Rule.** Both ends can be played to; the end lands two seconds early so the last moment can be
checked.

**Edge.** `Go to end` seeks to `end - 2 s`, clamped at 0. `Sync to now` pulls the end (clip mode) or
the whole moment (moment mode) to the playhead; if the playhead is before the start, both move.

**Enforced by** `PlayerScreen.kt` (`ShareSheet`).

---

## Navigation and shell

**Rule.** The tab pager is the floor. Pushed screens (player, search, settings, channel, about, logs,
bug) draw over it, so going back never refetches and never loses scroll.

**Edge.** The pager is not swipeable while anything is on top. A page change only rewrites the stack
when the stack is a single tab, and only once the pager has settled.

**Enforced by** `ui/nav/ParallaxNav.kt`.

**Rule.** There are three destinations — Feed, Library, Bored — and a pushed Search screen.

**Edge.** A channel page keeps the navigation bar (Library selected). The player hides the bar and
the top bar.

**Enforced by** `ParallaxNav.kt`, `ui/component/ParallaxScaffold.kt`.

**Rule.** Back pops one route; back on a tab leaves the app.

**Edge.** A bug screen, a settings section and a channel are all one press from where they were
opened.

**Enforced by** `ParallaxNav.kt`.

**Rule.** History is a mode inside Feed, reached by the clock icon, not a destination.

**Edge.** The icon in either tab raises a request counter that Feed observes and opens History.

**Enforced by** `feature/feed/FeedScreen.kt`, `ParallaxNav.kt`.

---

## Feed and library

**Rule.** The feed is the database, not the network. A refresh writes; the screen renders what is
stored.

**Edge.** Pull to refresh; automatic refresh about every four hours, jittered by up to thirty
minutes, as one-time work that reschedules itself. A refresh where every channel failed returns
`Result.retry()` with exponential backoff. Tapping the Feed tab always refreshes, even when the feed
is already the screen on view.

**Rule.** Changing the sort never moves the reader. A change made while the header is on screen
returns to the top; a change made further down keeps the video that was at the top at the top.

**Enforced by** `data/library/ContentRepository.kt`, `data/library/FeedWorker.kt`.

**Rule.** A live badge that has been seen stays for three hours even if a later listing says the
stream ended.

**Edge.** After the grace, a finished stream drops its badge. This costs a badge that can outlive a
stream by up to three hours.

**Enforced by** `ContentRepository.kt` (`LIVE_GRACE_MS`).

**Rule.** Library has three readings: a channel grid, sections by channel, and Collections.

**Edge.** Collections are rules over stored data (gathering dust, almost done, they're back, never
opened); nothing is stored per collection.

**Enforced by** `feature/library/*`, `feature/collections/*`.

**Rule.** A video card shows only the facts switched on in Appearance, in the layout chosen there:
Side (picture left, words right), Classic (full-width picture, words below) or Minimal (a short row
for scanning titles).

**Edge.** The meta line is assembled from the enabled parts and omitted entirely if none are on. A
style change repaints composed lists through a revision counter. History is a named button on the
feed header and a full-width row in the Library. Minimal with every fact switched off becomes a
two-column grid of pictures, because a thumbnail and a rectangle of empty space is not a card.
The feed header and its sort control are items of the list, so they scroll away with it; a
scroll-to-top button returns to them. The sort control is one word and a direction arrow that
opens a menu, not a row of chips.

**Rule.** Pulling to refresh on History means "show me new videos": the feed switches to Feed and
starts the refresh there.

**Edge.** History stores what was watched; refreshing it would only re-read its own record. The
pull therefore changes mode before it fetches.

**Enforced by** `feature/feed/FeedScreen.kt` (`HistoryList`).

**Enforced by** `ui/component/CardStyle.kt`, `ui/component/VideoRow.kt`,
`feature/feed/FeedScreen.kt`, `feature/library/LibraryScreen.kt`.

---

## Bored

**Rule.** Recipes, not a recommendation feed. A pick shows the path that produced it.

**Edge.** A recipe chip is an action, not a filter: it never stays highlighted after the walk. One
level of related videos from seeds in the library; a wildcard setting shuffles the
pool; tuning is session-only and resets on restart. The built-in recipes are Deep, Long, Short and
Fresh, named short so their chips stay on one line. "Why these?" is a card: the explanation only
appears when it is open, the sliders are at-least-length, at-most-age and surprise, and their
captions follow the thumb while the fetch waits for the finger to lift. The fold arrow sits at the
bottom under a divider, not beside the title. There is no "back to the recipe" button: restarting
the app is the reset.

**Rule.** A subscribed channel is the feed's business. Bored skips subscribed channels by default;
the gear beside the recipes holds the switch, and it applies to the next walk.

**Enforced by** `data/discover/DiscoverEngine.kt` (`subscribedChannels`), `feature/discover/DiscoverScreen.kt`.

**Enforced by** `feature/discover/*`, `data/discover/DiscoverEngine.kt`.

**Rule.** An empty result is an invitation, not an error: the river animates and says "Drag the
water".

**Edge.** The empty state is interactive (dragging bends the water).

**Enforced by** `feature/discover/DiscoverScreen.kt`.

---

## Search

**Rule.** One query, three readings: everything, only subscriptions, and Bored.

**Edge.** Local search history is stored in prefs; tapping a result opens the player and a long
press on a saved search removes just that pill. A horizontal swipe on the results moves between the
three tabs. Bored searches the typed words first and then walks out from the best match, so the
words on screen always describe the list. The same query pressed again keeps the current results
rather than re-fetching and shuffling them. Subscription matching reloads the subscription list on
every search and compares channel URLs without a trailing slash. A zero result in Subscriptions is
a large centred sentence, not a small "0 from your subscriptions" caption.

**Enforced by** `feature/search/*`.

---

## Comments

**Rule.** The section is collapsed until it is opened; the heading carries the count.

**Edge.** "Comments (n)" is the header, and it taps to expand or collapse, like the description.
The count does not appear in the video card toggles.

**Rule.** Comments page by continuation token and are cached for an hour.

**Edge.** A cached page that claims replies but carries no token is refetched. Replies expand from
their own token; a missing token says so instead of doing nothing.

**Enforced by** `data/library/ContentRepository.kt`, `data/cache/CommentCodec.kt`.

**Rule.** Aligned timestamps are tinted with the primary colour; comment text and replies are
selectable.

**Edge.** A comment shows the author and the words, nothing else. Tapping the row opens it: the
likes, the age, an aligned timestamp, the open-in-YouTube button and the reply control appear, and a
long comment unfolds. Author, likes and the button are not selectable. The total is stored on the
video row once comments load.

**Enforced by** `feature/comments/CommentsSection.kt`.

---

## Notes

**Rule.** Notes are transcript-only: speech becomes text, and no audio file is ever written.

**Edge.** One microphone, always visible. Dictation pauses playback; saving resumes it if dictation
had paused it. Typing starts a note at the playhead. Newest first; tapping the text edits it.

**Enforced by** `feature/notes/NotesTab.kt`, `feature/notes/VoiceInput.kt`,
`PlaybackController.kt` (resume-after-dictation).

**Rule.** A note keeps its timestamp and jumps back there when the timestamp is tapped.

**Edge.** Copy, delete and "All" sit in one capsule; the count is not shown as a number. Deleting one
note takes it away at once and offers Undo for a few seconds; deleting every note asks first. The
editor has a cancel button: a draft with words in it asks before it is thrown away.

**Enforced by** `feature/notes/NotesTab.kt`.

---

## SponsorBlock

**Rule.** Skipping is on or off, and the categories that are skipped are the chips that are on.

**Edge.** Categories are stored as a string set; the player skips a segment only if its category is
in that set.

**Enforced by** `data/sponsorblock/*`, `PlaybackController.kt`.

---

## AI chapters

**Rule.** A video with no chapters in its description gets AI chapters only when the Resync model
settings are on and a key is present.

**Edge.** AI chapters are cached in the `ai_chapters` table, so a revisit reads them back instead
of asking the model again. A video with no readable captions gets no chapters. The transcript is
sampled evenly across the whole video, so long ones are never cut to their opening.

**Enforced by** `data/resync/AiChaptersEngine.kt`, `data/resync/AiChaptersPrompt.kt`,
`feature/player/PlayerViewModel.kt`.

---

## Timestamp resync

**Rule.** Off by default. It needs an HTTPS endpoint and a key, and the key lives in the Android
Keystore.

**Edge.** One call per batch; cached by video + comment + cue hash. The window is the five minutes
before a comment's timestamp plus a 15-second tail. Settings live in Settings; alignment is triggered
from the comments.

**Enforced by** `data/resync/*`, `data/secure/SecurePrefs.kt`.

---

## Posting

**Rule.** Posting stays off until a Google OAuth client is supplied.

**Edge.** Inline comment and reply only, no separate post screen. Sign-in uses PKCE; the token is
kept in `SecurePrefs`.

**Enforced by** `data/auth/*`, `feature/posting/*`.

---

## Appearance

**Rule.** Four themes: System, Dark, Light, Glass. Changing any of them never recreates the Activity.

**Edge.** Glass takes light or dark from the last ordinary theme chosen; there is no separate
control. `Surfaces` is one block cut across: Glass on top, Solid underneath. Solid is Glass with
the aurora turned down and the cards nearly closed, not a second copy of Dark or Light. Glass has
to be visibly glass: its cards sit at 0.42 (dark) / 0.66 (light) alpha over an aurora at full
strength, and Solid is 0.88 / 0.94 over a quiet one, so the two settings look different on a real
page rather than only in a preview. Tapping the half in use flips to the other; tapping the other
half chooses it. Eighteen accents start at 20°, a deep
orange, so the wheel reads warm to cool and the first swatch is the colour a reader expects.
Prefs keep the 200° brand cyan slot. One line of chips — Shuffle, Daily, Hourly, Openly — where
the master sits beside its three rhythms and switching it off mutes the other three without moving
them. Choosing a rhythm never repaints the accent: the colour moves at the boundary the label
names, and nowhere else. Picking a colour by hand restarts the current period. The old
`accent_daily` switch is read as Daily.

**Enforced by** `ui/theme/Theme.kt`, `ui/theme/ThemeController.kt`,
`feature/settings/SettingsSections.kt`.

**Rule.** Preview is the second section, in a card of its own, and every control that changes it is
inside that card, directly under the card being previewed.

**Edge.** The layout buttons are Side, Classic and Minimal; the six facts are tap buttons in three
rows of two, not switches. The preview is full width, so the chosen shape is never judged from a
thumbnail-sized stand-in.

**Enforced by** `feature/settings/SettingsSections.kt` (`PreviewSection`, `PreviewCard`),
`ui/component/CardStyle.kt`.

**Rule.** Loading a video shows the video's own thumbnail as the wait: it breathes and a soft band
crosses it.

**Edge.** Setting `loading_style` chooses Kinetic, Still, Random or None; Random picks between
Kinetic and Still once per video. The `animations_enabled` master turns the movement off without
removing the picture. A missing thumbnail simply leaves the black frame.

**Enforced by** `feature/player/PlayerScreen.kt` (`LoadingPreview`).

**Rule.** Glass draws a static aurora behind translucent surfaces; it is not a blur of the video.

**Edge.** A pushed screen paints the same aurora or theme background as its floor, so the pager
underneath never shows through. A tile that wants a soft fill multiplies the scheme's alpha with
`faded(n)`; `copy(alpha = n)` would replace it and make the tile identical in Glass and Solid.

**Enforced by** `Theme.kt` (`ThemeBackdrop`).

**Rule.** Each content region row draws that region's real flag as its background, stretched to
the row.

**Edge.** The flags are vector drawables generated from the lipis/flag-icons SVGs, not bands drawn
by hand, so emblems and stars survive. FillBounds is deliberate: cropping a horizontal tricolour
to fit would leave only its middle band. The selected rows carry a primary border and the main one
is marked "Main"; there is no "Selected" text on the others.

**Rule.** Up to three regions can be selected. The first one tapped is the main region: the feed
and channel pages ask as it, and the other regions add their own search pass, one after another,
merged and deduplicated by video id.

**Edge.** A fourth tap is refused and the count line says to tap one off first. The last selected
region cannot be removed. Search with several regions is slower by roughly one fetch per extra
country, and its results can mix languages; deleting the list to one region restores today's
behaviour. A channel's uploads do not change by country, so the feed is never fetched more than
once.

**Enforced by** `ui/component/FlagBackground.kt`, `app/src/main/res/drawable/flag_*.xml`,
`feature/settings/SettingsSections.kt` (`RegionSection`), `data/source/ContentRegion.kt`,
`data/source/NewPipeSource.kt` (`search`).

---

## Bug reports and logs

**Rule.** Nothing leaves the phone until a button is pressed.

**Edge.** One explicit Send. After the server has the report the button is replaced by "Sent" and a
"Send another" reset, so the same note cannot be posted twice. A report carries the build and device
always; the note and the screenshot are what the person typed and chose. The "Include logs" switch is
on by default: on carries the last 300 log lines and a redacted copy of the settings; off sends the
note alone, or the note and screenshot, with `logsIncluded: false` in the bundle.

**Enforced by** `feature/settings/BugScreen.kt`, `core/BugReport.kt`.

**Rule.** Sending POSTs one JSON bundle with a `parallax.bug/1` schema marker to the drop box; a 2xx
that is not the endpoint's own `{"ok":true}` is a failure.

**Edge.** Keys and tokens are redacted before the body is built. A 429 disables the button until the
server's cooldown passes. Reports land in a directory that answers 403 over HTTP.

**Enforced by** `core/BugUpload.kt`, `server/upload.php`.

**Rule.** Logs are a 48-hour ring buffer on the device.

**Edge.** Feed, playback, player and extractor events are logged; failures are logged with the
channel or video id.

**Enforced by** `core/AppLog.kt`.

---

## Data

**Rule.** Importing adds; it never replaces or deletes.

**Edge.** Formats: NewPipe `subscriptions.json`, LibreTube JSON, Google Takeout `subscriptions.csv`,
and a NewPipe `.zip` backup is read for its JSON. Duplicates are ignored by URL.

**Enforced by** `data/importer/*`, `feature/library/LibraryViewModel.kt`.

**Rule.** Export writes a NewPipe-compatible JSON.

**Edge.** Settings can also be exported as JSON separately from subscriptions.

**Enforced by** `data/importer/SubscriptionExporter.kt`, `data/backup/SettingsExport.kt`.

---

## Updates

**Rule.** About checks the server's `version.json` and offers an update only when the published
build is newer than the installed one. A check alone never downloads anything.

**Edge.** Opening About checks once; the button checks again. "Up to date" and a failed check stay
as text, not dialogs. The manifest is always cache-busted, so a stale proxy copy cannot hide a
newer build.

**Enforced by** `data/update/UpdateManager.kt`, `feature/about/AboutScreen.kt`.

**Rule.** The downloaded APK is validated against the archive itself before the installer sees it:
same package, not older than installed, same signing key, and the published sha256 when one is
supplied.

**Edge.** A missing or unreachable `version.json` is a failed check, not an error screen. Only the
configured origin may serve the APK. Installing needs the unknown-sources permission; without it
the dialog routes to the system settings instead of attempting the install.

**Enforced by** `UpdateManager.kt` (`verifyArchive`, `buildInstallIntent`), `feature/about/UpdateViewModel.kt`.

---

## Privacy and failure rules

**Rule.** No account, no server, no analytics. Subscriptions, history, progress, notes and logs stay
in the app's database and files.

**Edge.** Uninstalling deletes all of it. The only network calls are YouTube (videos, comments,
search), SponsorBlock (segments), an optional model endpoint, and the bug drop box when pressed.

**Enforced by** the absence of any other client calls; `di/AppContainer.kt` builds the clients.

**Rule.** A failed request is a message, not a crash.

**Edge.** `AppResult.Err(message)` is rendered where the request was made; retries are explicit. The
one caught `Throwable`-adjacent case is a dependency `LinkageError`, which is reported as an
extractor failure.

**Enforced by** `core/AppResult.kt`, `data/source/NewPipeSource.kt`.
