# Bug reports

One line per report the app filed, newest last. `[open]` means nobody has answered it yet;
anything else is what happened. Written by `node scripts/reports.mjs`, which never rewrites a
line that already has a status. Mark one with
`node scripts/reports.mjs --set "223004=fixed in PlayerScreen.kt, 2026-11-09"`.

This file is the machine's: it only ever holds the list. What was found and what is still pending
belongs in `ROADMAP.md`.

- [fixed: MembersOnly state, plain sentence, no retry, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-211837.json` — we need to show if s video is for members only and or other soon to be error states
- [same gate as 204259; the DNS drop itself is the phone's, 2026-11-10] `parallax_Google_Pixel_7_10_20261109-212007.json` — horrible buffering just happened and still is.
- [fixed: empty state lists each mode with its sentence, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-222124.json` — bored why these aren't self explanatory
- [open] `parallax_Google_Pixel_7_10_20261109-222224.json` — I'm underwhelmed by bored mode. good idea but let's enhance tf out of it!
- [open] `parallax_Google_Pixel_7_10_20261109-222407.json` — portfolio why needs remakinh
- [fixed: tap the pill for 1.0x, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-222511.json` — speed pill needs reset
- [fixed: seek undo names the seconds, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-222923.json` — seek undo should say secs seek too
- [fixed: a flick either way is the ladder, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-223004.json` — fast swipe up into fullscreen sometimes blasts volume then rotates.
- [fixed: sensor restored on exit, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-223137.json` — auto rotate from Fullscreen doesn't work
- [fixed: step buttons that seek the picture, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-223250.json` — share panel needs a low time seek system. or better allow video seek
- [fixed: a filed report writes 'bug: filed <name>' to the log, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-223316.json` — big reports should start saving in logs to user sees their history
- [fixed: same as 223316, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-223324.json` — *bug reports
- [fixed: up next refresh, modes named on the empty screen, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-223427.json` — up next should have refresh. and bored mode (BTN to run it or BTN to take you to screen?)

- [count beside the chevron, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-101405.json` — put reply count on same line as chevron
- [comment and reply bodies are linkified, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-101720.json` — links in comments should be a href
- [author on the right, per-reply YT mark gone, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-102432.json` — comments. commentor name on the right. Remove the individual yt logo on each reply.
- [channel page with tabs, subscribe, sort and watched, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-102750.json` — sibs need wub + unsib button on their main page. also ch details , and links to playlists nad other videos, sub count, sort by likes date, watches, and a tab f…
- [network failure reads as offline, no per-call repair storm, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-110003.json` — video failed. check log. repaired nice. could be lte issue
- [no empty media item, service starts with a titled item, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-110556.json` — now the os shade just says "parallax is running"
- [chapter tiles draw the storyboard frame at each start, PlayerScreen.kt, 2026-11-12] `parallax_Google_Pixel_7_10_20261110-111049.json` — why chapters have same thumbnail
- [timestamps off the frame, chapter caption switch, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-111135.json` — Sertiti9to toggle timestamp range in chapters view. maybe keep showing in DESC too
- [bug icon redrawn, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-112116.json` — our bug icon is wonky
- [My notes in the card switches, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-112225.json` — 10 player preview buttons: should say "My notes" not just notes.
- [open] `parallax_Google_Pixel_7_10_20261110-114022.json` — git preview player page
- [pushed screens eat taps that no child claims, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-114655.json` — bug screen has space near toggles that touch the video player.
- [Up next shown whenever it exists, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-114709.json` — up next should be avail always
- [cards padded inside the border, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-114820.json` — th4 card video borders are lacking padding w text
- [open] `parallax_Google_Pixel_7_10_20261110-115009.json` — an ai (or non ai if possible) feature that finds videos talking the other side of the argument or science or whatever
- [channel header carries avatar, subscribers and subscribe, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-131926.json` — too much dupe info on the channel name row
- [long press the comments header refreshes, 2026-11-10] `parallax_Google_Pixel_7_10_20261110-134240.json` — long press on comments header to refresh . even closing and reopening doesn't reload comments. should have stale cache timeout

- [age strings hold together with a no-break space, 2026-11-10] `parallax_Google_Pixel_7_12_20261110-160919.json` — wrap weird

- [card facts on their own line, right-aligned, 2026-11-10] `parallax_Google_Pixel_7_12_20261110-161450.json` — wrap

- [exit reason now recorded by the OS on the next start, 2026-11-10] `parallax_Google_Pixel_7_12_20261110-163203.json` — did we just force close crash?
- [same as 163203, 2026-11-10] `parallax_Google_Pixel_7_12_20261110-163241.json` — yes we did.
- [no note; same stall and dead-channel lines as 204259, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-202937.json` — (no note)
- [no retry spent while offline, network loss logged once, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-204259.json` — and yet playing. but did stutter
- [reply likes and age right-aligned, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-205539.json` — reply time etc should be on right too
- [channel Watched rows draw their thumbnail, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-205625.json` — missing thumbs
- [channel About linkifies URLs and @handles, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-205649.json` — href links
- [feed setting hides videos watched past 25/50/90/100 per cent, 2026-11-11] `glasstube_Google_Pixel_7_13_20261110-205752.json` — setting for how much played % feed should show watched videos. 1% to 100%
- [play on an idle player re-prepares, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-210828.json` — it won't give error toast or play or log. weird.
- [same fault as 210828, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-210851.json` — restart app fixed it
- [bar at the top, readouts 40dp above the finger, speed panel opens at the tap, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-213728.json` — speed pill doesn't come at finger pos. make pills for vol and bright higher above finger, and move progress bar to top.
- [equaliser, level and even-out volume, 2026-11-11] `glasstube_Google_Pixel_7_13_20261110-215202.json` — need audio stabilizer and maybe even eq
- [dictation commits each utterance and restarts while recording, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-220547.json` — my notes was recording the speaker for 1min and at the end it lost the first 95% and then exited.
- [a sideways drift no longer locks a seek; the top band cannot start a minimise, 2026-11-10] `glasstube_Google_Pixel_7_13_20261110-222730.json` — sometimes swiping up bringe up seek pill. needs more elegant solution.

- [the feed asks each channel's live tab when it has one, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-104716.json` — feed missing live subs
- [a tap on a comment opens its replies when its words fit, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-105921.json` — tapping comments should expand replies if main comment doesntt have ellipses. make sense? smart expand, collapse
- [two spent retries drop one quality stop, with a toast, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-110114.json` — if buffering a lot degarde the quality auto. give toast
- [fixed: the ladder steps down before the retries are spent and a stall counts, 2026-11-12] `glasstube_Google_Pixel_7_14_20261111-110306.json` — loading video buffers then stops then revers to previous video
- [reply text is the comment text size, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-110831.json` — replies should be same font size
- [the report draft survives leaving the screen, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-110857.json` — don't auto clear bug report if I close snf reopen. I might have to recheck something before submit.
- [the speed panel and dial stop where the presets stop, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-111118.json` — speed level pill isn't filters by user settings. it also needs confined space on screen so my finger isn't off the screen. also idk if it Shou be rap and slide…
- [bar back at the bottom of the picture with a time at each end, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-111214.json` — move progress bar back to bottom of video
- [the pill says Switching quality; a different file cannot reuse the old buffer, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-111445.json` — why hwne changing to lower quality the video buffers? shouldn't it play what's already buffered ?
- [the scrub pill follows the finger instead of owning the middle, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-111834.json` — seek shouldn't take over middle of screen
- [bar sits between the elapsed time and the length, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-112240.json` — rearrange location of elements. progress bar should be in middle of time and total time etc
- [captions, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-112435.json` - captions
- [open] `glasstube_Google_Pixel_7_14_20261111-113155.json` — sometimes swipe back does nothing
- [a filed report writes a log line and the list below shows it, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-113229.json` — bug reports should show in the log below and stay
- [the player sections are rounded cards, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-114914.json` — sections in player should be rounded cards w Chevron
- [Auto's first load is capped at 720p on every connection, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-115657.json` — sometimes opening screen lafe audio and video for 2s. weird.
- [the buffering pill carries the percentage, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-120655.json` — can buffering pill show %

- [not started is its own reason, fixed in SourceErrors.kt, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-192409.json` — wrong message. full screen shows black screen
- [open] `glasstube_Google_Pixel_7_14_20261111-192436.json` — do i need to include logs each time, or timestamp relates?
- [wrong sentence fixed in SourceErrors.kt; the retry loop is in the streaming repair plan, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-195831.json` — weird errors
- [open] `glasstube_Google_Pixel_7_14_20261111-201140.json` — I watched that Mises video once. I have 10% setting on. something is weird
- [hamburger toggles Settings, fixed in GlassTubeNav.kt, 2026-11-11] `glasstube_Google_Pixel_7_14_20261111-201231.json` — tapping hamburger should toggle, not just open.
- [open] `glasstube_Google_Pixel_7_14_20261111-202541.json` — why same video playa fine on YouTube Firefox, but our app constantly buffering

- [open] `glasstube_Google_Pixel_7_14_20261111-141329.json` — is theme changeable but video colors irt?
- [open] `glasstube_Google_Pixel_7_14_20261111-141347.json` — bug reports should show in users log
- [open] `glasstube_Google_Pixel_7_14_20261111-141402.json` — I mean thr text not filenam3.
- [open] `glasstube_Google_Pixel_7_14_20261111-143151.json` — mobile set to max 480. it shows 480 on civeor but {720} in brackets. itself been 1m wo it cwmt be cahce
- [open] `glasstube_Google_Pixel_7_14_20261111-143430.json` — about vertical
- [open] `glasstube_Google_Pixel_7_14_20261111-143439.json` — about vertic6
- [open] `glasstube_Google_Pixel_7_14_20261111-143534.json` — shorts should show date anywhere else videos are showing less info yhan chosen?
- [open] `glasstube_Google_Pixel_7_14_20261111-143736.json` — tapping cards hilites the full header in some. but not others.
- [open] `glasstube_Google_Pixel_7_14_20261111-144753.json` — cards Should save expanded state
- [open] `glasstube_Google_Pixel_7_14_20261111-144822.json` — I'm getting tonnes of buffering on lte on 480p

- [open] `glasstube_Google_Pixel_7_14_20261112-112201.json` — why this video buffered so much at start
- [open] `glasstube_Google_Pixel_7_14_20261112-112301.json` — speed pill closer to finger point. double finger as shortcut?
- [open] `glasstube_Google_Pixel_7_14_20261112-112406.json` — show captions should say font size title next to buttons on settings
- [fixed: the filed line carries the note text, capped at 120 chars, 2026-11-12] `glasstube_Google_Pixel_7_14_20261112-112509.json` — reports log should show the text I inputted into logs screen. I've asked 3x now.
- [open] `glasstube_Google_Pixel_7_14_20261112-115413.json` — says switching quality constantly. 480p but says 1080p playing
- [caption entities decoded in core/Entities.kt, 2026-11-12] `glasstube_Google_Pixel_7_14_20261112-115549.json` — apostrophes aren't showing in CC properly
- [open] `glasstube_Google_Pixel_7_14_20261112-120851.json` — full screen rotates but it's wrong for portrait Fullscreen vids
- [open] `glasstube_Google_Pixel_7_14_20261112-121140.json` — the current video came in bored mode short recipe as a 1m23s video. it's 1h long tho
- [open] `glasstube_Google_Pixel_7_14_20261112-121223.json` — channel row should show date in playing video
- [description image marks keep their alt text, Html.kt, 2026-11-12] `glasstube_Google_Pixel_7_14_20261112-121704.json` — description not showing Instagram emoji on current video
- [open] `glasstube_Google_Pixel_7_14_20261112-130135.json` — Hidden tab nerds Etter styling. thumbnail. date hidden. hidden by me is redundant.. who else?
- [open] `glasstube_Google_Pixel_7_14_20261112-130213.json` — 1 of 42 channels failed on feed header should be a toast not take up space
