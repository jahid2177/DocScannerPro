# DocScanner Pro — Integration Guide

## 1. Project structure
This zip is a complete Gradle project (`settings.gradle.kts` at the root includes
`:app`). Open the `docscanner` folder as the project root in AndroidIDE.

## 2. Required manual step: OpenCV module
AndroidIDE cannot reliably resolve the official OpenCV AAR from Maven, so the
project references OpenCV as a **local module** (`implementation(project(":opencv"))`
in `app/build.gradle.kts`). To wire it up:

1. Download the **OpenCV Android SDK 4.x** (`opencv-4.x.x-android-sdk.zip`) from
   https://opencv.org/releases/
2. Unzip it; copy the `sdk/java` folder into your project root and rename it `opencv`.
3. Inside `opencv/build.gradle`, make sure `compileSdk`, `minSdk` match this
   project (compileSdk 33, minSdk 24) — edit if the bundled build.gradle differs.
4. Copy `opencv/native/libs/*` (the `.so` files) into
   `app/src/main/jniLibs/` (one subfolder per ABI: armeabi-v7a, arm64-v8a, x86, x86_64).
5. Add `include(":opencv")` to `settings.gradle.kts` (already present in this project).
6. Sync Gradle.

## 3. Tesseract trained-data files
`OCRHelper` copies `.traineddata` files from `assets/tessdata/` on first use.
Download the language files you need (fast/best variants both work) from:
https://github.com/tesseract-ocr/tessdata_fast
Required for this build: `ben.traineddata` (Bengali), `ara.traineddata` (Arabic).
Place them at `app/src/main/assets/tessdata/`.
ML Kit downloads its own models on first run for English/French/Spanish/German/
Chinese/Japanese/Korean/Hindi — no manual step needed for those.

## 4. Launcher icons
This project references `@mipmap/ic_launcher` / `ic_launcher_round` but does not
ship binary PNGs (this delivery is source-only). Generate them via Android
Studio/AndroidIDE's Image Asset tool, or drop your own into
`app/src/main/res/mipmap-*/`.

## 5. AndroidIDE-specific settings used in this build
- AGP 7.2.2, Gradle 7.5, Kotlin 1.7.20, Java 11 — matches known-working
  AndroidIDE constraints.
- No Compose, no Room/kapt — everything is XML + ViewBinding + Fragments +
  Navigation Component, and persistence is a hand-rolled Gson/JSON store
  (`FileManager`), avoiding kapt-related build crashes.
- `android.suppressUnsupportedCompileSdk=33` is set in `gradle.properties`
  since AGP 7.2.2 was validated up to API 32.

## 6. Performance notes (2GB RAM target)
- `DocumentDetector` downsamples every analyzed frame to 480px wide and skips
  every other frame.
- `BitmapUtils.decodeSampledBitmap` bounds every decode to a max dimension
  (3000px for processing, 2048px for on-screen preview, 300px for thumbnails).
- All OpenCV Mats are explicitly `.release()`d; all Bitmaps explicitly
  `.recycle()`d once no longer needed.

## 7. Testing guide
- **Unit tests**: `CornerHandle.isTouched`, `OpenCVHelper.orderPoints`, and
  `FileManager` CRUD are pure-logic/JVM-testable — add them under
  `app/src/test/java`.
- **Instrumented tests**: camera capture and OCR need a device/emulator with
  camera support; add under `app/src/androidTest/java`.
- **Manual QA checklist**: cold start → permission prompt → auto-capture
  triggers within ~1s of holding a document steady → crop screen loads with
  corners pre-filled → filter strip renders 14 distinct previews → OCR returns
  text in ≤5s on a 2019-era mid-range phone → PDF opens correctly in a
  third-party viewer with selectable text when "searchable" is on.

## 8. What's simplified in this delivery (by design, given scope)
- Gallery import button in the Scanner screen is stubbed (comment marks where
  to wire `registerForActivityResult(GetContent())`).
- Folder management has repository/ViewModel support but no dedicated
  "manage folders" screen yet — documents can be filtered by folder via
  `FileManager`/`ScannerViewModel` today.
- Background removal filter uses a single global Otsu threshold; a
  GrabCut-based version would handle busier backgrounds better.

## 9. Future improvements
- WorkManager-based batch OCR across all pages of a document.
- On-device language auto-detection before running OCR (currently the user
  or `SettingsManager.ocrLanguage` picks the language).
- Cloud-free multi-device sync via a user-provided WebDAV/Syncthing folder
  (still zero-analytics, zero-Anthropic/Google backend).
