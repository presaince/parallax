# Parallax

A YouTube client for Android that keeps your subscriptions, history, notes and settings on your phone. No account, no tracking, no server in the middle.

Free and open source under GPLv3. Android 13 or newer.

## Screenshots

<table>
<tr>
<td><img src="docs/screenshots/01-feed.png" width="260"><br><b>Feed.</b> Side cards, one column, sort and History on the header.</td>
<td><img src="docs/screenshots/02-appearance-glass.png" width="260"><br><b>Appearance · Glass.</b> Glass inherits Dark or Light, accent picker with Shuffle.</td>
</tr>
<tr>
<td><img src="docs/screenshots/03-preview.png" width="260"><br><b>Preview.</b> The card you change is on the same screen as the controls.</td>
<td><img src="docs/screenshots/04-bored.png" width="260"><br><b>Bored.</b> Four modes (Deep, Fresh, Long, Short) with a visible path.</td>
</tr>
<tr>
<td><img src="docs/screenshots/05-chapters.png" width="260"><br><b>Chapters.</b> Horizontal thumbnail row, tap to seek, active chapter highlighted.</td>
<td><img src="docs/screenshots/06-notes.png" width="260"><br><b>My notes.</b> Dictation mic plus text field, stamped at the playhead.</td>
</tr>
</table>

Captured on a Pixel 7.

## What you get

- **Feed and Library.** Subscriptions feed, Library with Grid / By channel / Collections, History grouped by video.
- **Player.** Controls on the picture, vertical swipe for volume and brightness, horizontal swipe to seek, drag down to minimise. A small player stays under the bar while audio keeps playing.
- **Chapters.** Timestamps from the description, or generated when there are none. Tap to jump.
- **Notes.** Write or dictate a note stamped at the moment you are watching. Edit, copy, delete.
- **Clip sharing.** Drag the start and end, share the link with the range.
- **Bored Mode.** Pick Deep Cut, Fresh, Longform or Short Break and follow the walk.

## Improved over NewPipe and LibreTube

| | NewPipe | LibreTube | Parallax |
|---|---|---|---|
| When YouTube changes something | Stops working until an update ships. Empty feeds and error popups. | Depends on a Piped server. When it goes down or gets blocked, nothing loads until you switch servers. | Tries to repair itself and retry on its own. No server in the path, nothing for you to switch. |
| Starting a video | Blank spinner, or a hang, especially on long videos. | Waits on the server first, so a slow server feels like a slow app. | Thumbnail shows straight away and is the progress. Video starts first, chapters and comments fill in after. |
| Look | Busy, heavy red, plain lists. | Clean, but generic Material You. | Glass over a soft gradient, your accent, side-by-side cards. Change a setting and see the card change on the same screen. |
| Playback | Standard controls. | Standard controls with a good quality picker. | Volume and brightness by swipe, seek in seconds, drag down to a mini player that keeps playing, swipe up to reopen. |
| Extras | No notes, no clip range, no discovery mode. | Good sponsor skip, but still a library. | Timestamped notes you can dictate, chapters when there are none, clip sharing, and Bored Mode. |

## Private by design

No analytics, no account, no Parallax server. Your data stays in a local database on your phone. See `docs/PRIVACY.md`.

## Get it

Download the latest signed APK from Releases, then open it and allow install from your browser. Android 13 or newer.

## Licence

GPLv3. See `LICENSE`.
