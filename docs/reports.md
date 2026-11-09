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
