# Good Utilities

A collection of everyday tools for Android, designed to look and feel like Samsung's own One UI 8.5 apps:
expanding titles, frosted menus, One UI sliders and switches, predictive back, and full dark mode.

Built for the Galaxy S24 Ultra (One UI 8.5, Android 16). It runs on Android 10 and newer (arm64).

## Tools

**Media**
- **Music Downloader**: YouTube songs and playlists to MP3 with cover art and tags. Downloads in parallel and keeps running in the background.
- **Video downloader**: YouTube, TikTok, Instagram, X, Reddit and more. Choose the quality, a format that plays everywhere (H.264 MP4), or audio only.
- **Audio merger**: join songs into one MP3 with a crossfade.
- **Audio trimmer**: cut a song without losing quality.

**Files**
- **Document reader (Good Reader)**: a fast PDF, Word, text and Markdown reader you can set as the default app. Search with highlights, text selection, pinch zoom, night mode, contents, password-protected PDFs, and it remembers your page.
- **PDF editor**: merge, split, reorder, rotate, sign, annotate and password-protect PDFs.
- **File converter**: images, documents, audio and video (PNG, JPG, WebP, PDF, DOCX, TXT, MP3, WAV, FLAC, M4A, MP4, WebM, GIF…).
- **Print**: prints straight to network printers over IPP (AirPrint / Mopria / IPP Everywhere) with a live preview, page picker, two-sided printing and toner levels. No system print screen involved.
- **Compressor**: shrink videos with the phone's hardware encoder (H.264 / HEVC) and audio files (MP3 / AAC / Opus).

**Images**
- **Color picker**: sample any pixel from a photo and get HEX, RGB and a palette.
- **Image blur**: blur a whole photo or brush over faces and details.
- **Background remover**: cut out people, pets and objects as transparent PNGs, on the device.

**Device**
- **App lock**: lock apps with fingerprint, face or PIN.
- **Private DNS**: switch the system DNS provider, with a Quick Settings tile.
- **Charge limit**: custom battery protection and bypass charging.

**Miscellaneous**
- **Battery**: health estimate, cycle count, temperature and charging speed.
- **APK extractor**: save installed apps as APKs, merging split bundles into one installable file.
- **QR code**: turn a link or any text into a QR code. It's also in the share sheet.
- **Random**: numbers in any range, or roll dice (d4 to d20) with One UI animations.

Everything the app creates is saved in **Internal Storage/Good Utilities/** (Music, Videos, Audio, Pictures, Documents, APKs).

## Building

Requirements: Android Studio (its bundled JDK), Android SDK 37.

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with the key in `~/.good-utilities/release.p12`. Its password is read from the
macOS Keychain item "Good Utilities release key", or from the `GU_KEYSTORE_PASSWORD` environment variable.
Neither the key nor the password is in this repository. Without them, release builds fall back to the debug key,
and those can't be installed over the published app.

Android 16 marks sideloaded apps as restricted and switches their accessibility service off after each
install. `enable-applock.sh` turns App lock back on over adb after reinstalling.

## Tech

Kotlin and Jetpack Compose with a custom One UI component library (`ui/Components.kt`, `ui/theme/OneUi.kt`).
Uses yt-dlp and FFmpeg (via youtubedl-android), Media3 Transformer, PdfBox-Android, ML Kit subject
segmentation, ZXing, ARSCLib and apksig.

## License

Proprietary. All rights reserved. See [LICENSE](LICENSE). Third-party libraries included in the app remain
under their own licenses.

Made by [Alpish08](https://github.com/Alpish08), built with Claude.
