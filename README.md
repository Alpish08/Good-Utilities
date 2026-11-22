# Good Utilities

A collection of everyday tools for Android, designed to look and feel like Samsung's own One UI 8.5 apps:
expanding titles, floating toolbar buttons, frosted menus and popups, a blurred bottom tab bar, floating
search pill, compact One UI buttons, Samsung's own four-dot loading animation, One UI sliders and switches, predictive back, and full dark mode.

Home lists every tool by category, over a soft colour wash you can change from ⋮ → Background (Midnight, Amethyst,
Lagoon, Moss, Ember, Rosewood or Graphite). The bottom tab bar (Media, Files, Images, Device, Misc) works as filters:
tap a tab to show only its tools, tap it again to see everything. Search opens as a frosted bar above the keyboard.

Built for the Galaxy S24 Ultra (One UI 8.5, Android 16). It runs on Android 10 and newer (arm64).

## Tools

**Media**
- **Music downloader**: YouTube songs and playlists to MP3 with cover art and tags. Downloads in parallel and keeps running in the background. Paste a link (or share it from YouTube) and press Enter.
- **Video downloader**: YouTube, TikTok, Instagram, X, Reddit and more. Choose the quality, a format that plays everywhere (H.264 MP4), or audio only.
- **Audio merger**: join songs into one MP3 with a crossfade.
- **Audio trimmer**: cut a song without losing quality.

**Files**
- **Document reader (One Viewer)**: a fast PDF, Word, text and Markdown reader you can set as the default app. Search with highlights, text selection, pinch zoom, night mode, contents, password-protected PDFs, rotate to landscape, a frosted header and page indicator over the pages, and it remembers your page.
- **PDF editor**: merge, split, reorder, rotate, sign, annotate and password-protect PDFs, or create a PDF from images. Drawing with the S Pen: 20 colours, 5 brush sizes, marker, eraser (or hold the pen's button), undo and redo, pinch zoom.
- **File converter**: images, documents, audio and video (PNG, JPG, WebP, PDF, DOCX, TXT, MP3, WAV, FLAC, M4A, MP4, WebM, GIF…).
- **Print**: prints straight to network printers over IPP (AirPrint / Mopria / IPP Everywhere) with a live preview, page picker, two-sided printing and toner levels. No system print screen involved.
- **Compressor**: shrink videos with the phone's hardware encoder (H.264 / HEVC), audio files (MP3 / AAC / Opus), and photos (smaller dimensions, a quality level, or a size limit for websites that reject large uploads).
- **APK extractor**: save installed apps as APKs, merging split bundles into one installable file. Search apps from the bottom pill.

**Images**
- **Color picker**: sample any pixel from a photo and get HEX, RGB and a palette.
- **Image blur**: blur a whole photo or brush over faces and details.
- **Background remover**: cut out people, pets and objects as transparent PNGs, on the device.
- **Icon maker**: put any logo on an installed icon pack's empty tile (its folder icon) for apps the pack doesn't cover. Remove a flat background or cut out the subject, make it white, round it into a circle, set the size and nudge the position; saves a PNG at the pack's own resolution.

**Device**
- **App lock**: lock apps with fingerprint or PIN (Authy and other apps that hide their windows from accessibility included). The cover fades in the moment you tap a locked app's icon, with only the fingerprint icon showing over it; the App lock page itself asks for your fingerprint or phone PIN.
- **Private DNS**: switch the system DNS provider, with a Quick Settings tile.
- **Charge limit**: custom battery protection and bypass charging (run straight from the charger), with a Quick Settings tile and Bypass on / off / toggle actions for Modes and Routines. **Auto bypass when hot** turns bypass on while plugged in once the battery reaches a chosen temperature and off again when it cools or you unplug, optionally skipping night charging (12 AM – 7:30 AM).
- **Battery info**: Samsung's own battery health and lifetime cycle count (synced from a Mac with `sync-battery.sh`, since Android doesn't let apps read them), plus temperature and charging speed.

**Miscellaneous**
- **Card balance**: manage your expenses and track payments in exact cents. Choose − or +, type an amount and an optional note, tap Log; the payment history shows the balance after each entry. Several cards (one primary) with a quick switcher beside the balance, all kept on the phone.
- **Briefs**: your newsletters and articles from one Gmail label, as clean, readable pages, grouped into Today, Yesterday and Past. It opens in its own window (called One Mail in recent apps). Each email is reformatted in One UI's type with real dark mode (or "Show in light" for newsletters made for white), with tracking pixels, repeated titles and "view in browser / unsubscribe / forwarded" clutter removed. Images stay off until you allow a sender. Adjustable text size, reading time, a contents list for long digests, short data tables (like market prices) kept on one line, read aloud, and the next brief at the end. It keeps the 80 newest emails by date and syncs over IMAP with a Gmail app password (kept encrypted) only while it's open: no background activity, no notifications. Opening a brief marks it read in Gmail; nothing else changes there.
- **Japanese reader**: reads Japanese on your screen (Quick Settings tile), in photos or the camera: furigana, word meanings (JMdict, offline), sentence translation (on-device) and a saved-words list with flashcards.
- **QR code**: turn a link or any text into a QR code. It's also in the share sheet.
- **Random**: numbers in any range, or roll dice (d4 to d20) with One UI animations.

**Widgets**
- **Tabs** (4×1): the app's tab bar on the home screen; each tab opens Good Utilities on it.
- **Card balance** (2×4): the primary card's balance and newest entries; − and + open the app ready to log.
- **Spacer**: an invisible, untappable widget of any size (1×1 and up) for keeping space empty.

Everything the app creates is saved in **Internal Storage/Good Utilities/** (Music, Videos, Audio, Pictures, Documents, APKs, Icons).
If the app ever crashes, About → Crash log keeps the details so they can be shared.

## Future plans

- **More One UI widgets**: new utility widgets drawn to match Samsung's own (Clock, Weather, Battery), for quick
  glances and one-tap actions from the home screen.
- **New everyday tools**: more small, focused utilities for things that otherwise need an ad-filled app from the store.
- **Landscape mode**: fixing layouts that don't fit or scale properly in landscape.

## Changes

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

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

`sync-battery.sh` copies Samsung's battery health (`mSavedBatteryAsoc`) and cycle count (`mSavedBatteryUsage` / 100)
from `adb shell dumpsys battery` into the Battery page. Android blocks regular apps from that service, so it has to
come over adb. Run it every few weeks; between syncs the app's own estimate is corrected using the gap measured at sync time.

Android 16 marks sideloaded apps as restricted and switches their accessibility service off after each
install. `enable-applock.sh` turns App lock back on over adb after reinstalling.

## Tech

Kotlin and Jetpack Compose with a custom One UI component library (`ui/Components.kt`, `ui/theme/OneUi.kt`).
Uses yt-dlp and FFmpeg (via youtubedl-android), Media3 Transformer, PdfBox-Android, ML Kit subject
segmentation, ZXing, ARSCLib, apksig and Haze (the blur behind the tab bar).

## Credit

The app icon is **drawer alt 01** from [OneDark 3D](https://play.google.com/store/apps/details?id=com.eatos.onedark3d) made by
**Eatos**. Eatos makes the best icon packs for One UI: every icon is drawn to sit perfectly next to
Samsung's own, with alternates for almost everything. The full collection is worth every cent, so check them out:

- [OneDark 3D](https://play.google.com/store/apps/details?id=com.eatos.onedark3d)
- [OnePro Dark](https://play.google.com/store/apps/details?id=com.eatos.onedarkpro)
- [OneLiquid](https://play.google.com/store/apps/details?id=com.eatos.oneliquidpro)
- [OneGlass](https://play.google.com/store/apps/details?id=com.eatos.oneglass)
- [OneGlass Dark](https://play.google.com/store/apps/details?id=com.eatos.onedarkglass)
- [OneColor GD](https://play.google.com/store/apps/details?id=com.eatos.onecolor.glassdark)
- [HuxOne](https://play.google.com/store/apps/details?id=com.eatos.huxone.black)

The icon belongs to Eatos and is not covered by this project's license.

## License

Proprietary. All rights reserved. See [LICENSE](LICENSE). Third-party libraries included in the app remain
under their own licenses.

Made by [Alpish08](https://github.com/Alpish08), built with Claude.
