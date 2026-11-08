# Parallax

A native Android client for YouTube. Local-first: subscriptions, history, notes and preferences
live in a local SQLite database, and there is no Parallax server anywhere.

Licence: GPLv3. Package: `com.presaince.parallax`. minSdk 33, targetSdk 36.

## Status

| Milestone | State |
|---|---|
| M0 skeleton — search and playback | Built, gate verified on a Pixel 7 |
| M1 player — service, gestures, SponsorBlock, A-B loop, PiP | Built, gate (a week of daily use) not run |
| M2 library — Room, import, feed, comments, history | Built, gate not run |
| M3 deck — timestamped notes with dictation | Built, gate not run |
| M4 Timestamp Resync — BYOK, off by default | Built, gate not run |
| M5 Bored Mode — recipes and the walk | Built, gate not run |
| M6 optional posting | Scaffolded — needs a Google OAuth client (Desktop app type) |
| M7 release | CI, docs and headers in place; signing needs secrets |

Nothing above has been through the M1 daily-use gate. Treat it as a working shape, not a release.

## Build

Requirements: JDK 21, Android SDK with platform 36. No Android Studio needed.

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Versions are pinned inline in `app/build.gradle.kts`. There is no version catalogue; there is one
module, and one place to look.

### Release build

Signing is read from the environment so no key material lives in the repository:

```
PARALLAX_KEYSTORE=/path/to/parallax.jks \
PARALLAX_KEYSTORE_PASSWORD=... \
PARALLAX_KEY_ALIAS=... \
PARALLAX_KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

Without those variables the release variant still compiles, unsigned. CI reads the same names from
Actions secrets; `.github/workflows/release.yml` attaches the APK to a `v*` tag.

## Where things live

```
app/src/main/java/com/presaince/parallax/
  core/        AppResult, time formatting
  data/        model, source (the extractor boundary), db (Room), library (feed),
               resync, discover (recipes), secure (Keystore-backed prefs)
  playback/    PlaybackController and PlaybackService
  feature/     one package per screen
  ui/          theme and the route stack in one file
```

Two rules worth knowing before changing anything:

1. **The UI never touches the extractor.** Everything goes through `VideoSource`. That is what
   keeps the app testable on the JVM and a fallback source possible.
2. **Stream URLs are never persisted.** They are signed and expire. Persist the video id, resolve
   again on play.

## Tests

JVM tests cover the pure logic: timestamp formatting, subscription import parsing, gesture
arbitration, comment cache encoding, subtitle parsing, resync prompt building and reply parsing,
recipe validation and scoring, feed jitter.

The extractor canary is skipped by default because it needs the network:

```
./gradlew :app:testDebugUnitTest --tests '*ExtractorCanaryTest*' -Dparallax.canary=true
```

CI runs it nightly. When YouTube changes its markup, that job fails first.

## Privacy

See `docs/PRIVACY.md`. Short version: no analytics, no account, no server. The only outbound
traffic is to YouTube (through the extractor), SponsorBlock, and — only if you switch it on and
supply your own key — the model endpoint you choose.

## Licence

GPLv3. Every source file carries the header; see `LICENSE`. NewPipeExtractor is used as a
dependency and is never vendored.
