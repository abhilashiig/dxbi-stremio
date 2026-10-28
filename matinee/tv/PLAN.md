# Matinee TV: plan

A native Stremio client for Android TV and Fire TV. None of dx.bi's extras: browse, watch, sync, done
excellently. Playback quality first, then a smooth Netflix-style UI.

## Principles

Quality of experience is never traded away. When goals conflict, this order decides:

1. **Correct**: right colours (no DV green/purple), audio in sync, never silent, never a crash.
2. **Smooth**: no judder, stutter or dropped frames; menus respond instantly.
3. **Full fidelity**: the highest resolution, HDR and audio format the setup can play.
4. **Fast**: start-up and time to first frame.
5. **Light**: memory, battery, APK size.

- No silent downgrades: any fallback (DV → HDR10, Atmos → 7.1) shows in the player's info panel with why.
- Quality drops only when the setup physically can't do better, never to save effort.
- A stream that drops frames is a failure, not "high quality": the player watches dropped frames and offers
  the best version that plays smoothly.
- Same experience on every device. Low-end speed comes from cheaper techniques (pre-blurred images,
  images loaded at displayed size), never from removing screens, animations or features.
- Full-quality details: styled ASS subtitles (libass), lossless audio when the chain allows it, posters at
  the screen's real resolution.
- Real content over spinners: the last-known home shows instantly and refreshes behind it.
- Every phase ends with a pass/fail checklist on both test devices; benchmarks guard start-up and scroll
  jank. A failing check means the phase isn't done.

## Decisions

| Area | Decision |
|---|---|
| UI | Jetpack Compose for TV (Kotlin) |
| Player | Media3 / ExoPlayer, hardware decoders first |
| Stremio logic | Kotlin client for the addon protocol and Stremio API. Library sync and the watched-episodes bitfield are ported from stremio-core (MIT, notice kept) |
| Account | The user's Stremio account: addons, library, Continue Watching and watched episodes sync both ways with the official apps and dx.bi. Sign-in by QR/link code first (works for Google/Facebook accounts, no typing), email and password as a fallback. Only the auth key is stored, encrypted with the Android Keystore; never the password |
| Torrents | None. Streams come from the user's addons and debrid services; torrent-only results (`infoHash`, no `url`) are hidden |
| Licence | GPL-3.0-or-later, like the rest of the repo |

## Test devices

Emulators can't test HDR or passthrough, so these two decide what "works" means.

**TCL Android/Google TV (the high end).** Plays straight to its own panel, so HDR/Dolby Vision support is
whatever the panel and SoC report. Audio goes to the TV speakers or a soundbar over ARC/eARC. Many TVs
can't decode DTS, and plain ARC can't carry TrueHD or DTS-HD, so the app must pick a track the output can
take (or decode to PCM) rather than assume passthrough.

**Fire TV Stick on Fire OS 7 (Android 9, API 28; the low end).**
- `Surface.setFrameRate` needs API 30, so refresh-rate matching uses the window's `preferredDisplayModeId`.
- No Google services: no Watch Next row or Google TV home integration, and voice is Alexa rather than
  `SpeechRecognizer`, so search falls back to the keyboard.
- Likely 1080p and around 1 GB RAM, so buffers and image caches scale down.

The device report screen (phase 0) confirms the real capabilities of both.

## Playback

**Video:** H.264, HEVC (incl. 10-bit), VP9 (incl. Profile 2 HDR) and AV1 on hardware decoders, with a
software AV1 fallback up to 1080p. MKV, MP4, HLS, DASH.

**HDR:**

| Format | Handling |
|---|---|
| HDR10, HLG | Passed through |
| HDR10+ | Dynamic metadata passed through where the display supports it |
| Dolby Vision 8.1 | DV on DV devices, HDR10 base layer elsewhere |
| Dolby Vision 5 | DV devices only (green/purple picture elsewhere), so ranked down or hidden |
| Dolby Vision 7 | HDR10 base layer |

**Audio:** passthrough of AC-3, E-AC-3, E-AC-3 JOC (Atmos), TrueHD (Atmos), DTS, DTS-HD MA and DTS:X
where the output supports them; otherwise FFmpeg audio decoders (LGPL build) decode to PCM. AAC, Opus,
FLAC, Vorbis, MP3 natively. Audio delay, preferred language.

**Smoothness:** refresh-rate matching (23.976 → 24 Hz), optional resolution matching, tunnelled playback
per device, buffers sized for 80-100 Mbps remuxes and scaled to device RAM.

**Subtitles:** SRT, WebVTT, PGS, VobSub, ASS/SSA with full styling (libass), addon subtitles, size, style
and delay settings.

## UI/UX

- Home: backdrop of the focused title (cross-fades), Continue Watching first, addon catalog rows; each row
  remembers its position.
- Remote: smooth focus scaling, predictable Back (row → top → menu), no focus jumps.
- Details: seasons, episodes, resume.
- Stream picker: ranked by what this device plays well (resolution, HDR/DV support, audio the output can
  take, size), with 4K / DV / HDR10+ / Atmos badges.
- Search: keyboard, plus voice on Google TV.
- Google TV: Watch Next row, global search, `stremio://` links.
- TalkBack labels, large-text support.

**Targets**

| | TCL TV | Fire TV Stick |
|---|---|---|
| Cold start to usable home | < 1.5 s | < 3 s |
| Scrolling | no visible dropped frames | < 1% janky frames |
| Play to first frame (debrid) | < 2 s | < 3 s |

Baseline profiles, R8, last-known home shown instantly while it refreshes, posters loaded at displayed
size (Coil) with prefetch. Macrobenchmark tests guard start-up and scroll jank.

## Phases

| # | Phase | Deliverable |
|---|---|---|
| 0 | Foundations | Project structure, benchmark setup, device report screen |
| 1 | Player lab | Plays test files/URLs with everything under Playback, checked on both devices |
| 2 | Stremio client | Login, addons, catalogs, meta, streams, subtitles, library and Continue Watching sync |
| 3 | UI | Home, details, stream picker, search, library, settings |
| 4 | TV integration | Watch Next, voice and global search, deep links |
| 5 | Polish | Targets met on the Fire TV Stick |
| → | Release `matinee-tv-v0.1.0` (beta) | |
