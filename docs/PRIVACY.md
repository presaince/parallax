# Parallax privacy policy

Parallax runs entirely on your device. There is no Parallax account and no Parallax server.

## What is stored, and where

All of it is in the app's private storage on your phone:

- subscriptions you import, videos seen in the feed, watch progress and history
- notes you write, and their timestamps
- settings, including the SponsorBlock switch
- the API key for Timestamp Resync, encrypted with a key held in the Android Keystore

Uninstalling the app deletes all of it. Nothing is backed up: `allowBackup` is off.

## What leaves the device

1. **YouTube.** Search, video metadata, streams, comments and captions are fetched directly from
   YouTube by NewPipeExtractor, on your connection. Stream URLs are never stored.
2. **SponsorBlock.** Segment lookups use the first four characters of the SHA-256 of the video id,
   which is the API's own privacy design.
3. **A model provider, only if you turn Timestamp Resync on.** Then the subtitle cues in the
   five-minute window before each timestamped comment, plus the comments themselves, are sent to
   the endpoint you configured, with your key. Nothing else is sent, and the result is cached
   locally. Off by default.
4. **YouTube's API, only if posting is enabled.** Not built yet.

There is no analytics SDK, no crash reporter, no advertising identifier and no telemetry of any
kind in this app.

## Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE` — the app is a client for a website.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` — background
  playback and its notification.
- `RECORD_AUDIO` — only while you hold Dictate on a note. Speech is transcribed by the phone's
  speech service to text; Parallax never writes an audio file.

## Contact

Open an issue on the project page.
