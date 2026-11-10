# Bug reports

One line per report the app filed, newest last. `[open]` means nobody has answered it yet;
anything else is what happened. Written by `node scripts/reports.mjs`, which never rewrites a
line that already has a status. Mark one with
`node scripts/reports.mjs --set "223004=fixed in PlayerScreen.kt, 2026-11-09"`.

This file is the machine's: it only ever holds the list. What was found and what is still pending
belongs in `ROADMAP.md`.

- [fixed: MembersOnly state, plain sentence, no retry, 2026-11-09] `parallax_Google_Pixel_7_10_20261109-211837.json` — we need to show if s video is for members only and or other soon to be error states
- [open] `parallax_Google_Pixel_7_10_20261109-212007.json` — horrible buffering just happened and still is.
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
- [open] `parallax_Google_Pixel_7_10_20261110-111049.json` — why chapters have same thumbnail
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
