# CLAUDE.md — DocScannerPro

এই ফাইলটি প্রতিটি নতুন Claude session শুরুতে সবার আগে পড়তে হবে।
কোনো code modification করার আগে এই ফাইল পড়া বাধ্যতামূলক।

## PROJECT PURPOSE
DocScannerPro — CamScanner-style Android document scanner app। Camera দিয়ে document scan, auto edge-detection, crop, filter, OCR, এবং PDF export।

## TECHNOLOGY STACK
- Language: Kotlin
- Build: Gradle Kotlin DSL (`build.gradle.kts`), AGP 7.2.2, Gradle 7.5, Kotlin 1.7.20, Java 11
- compileSdk 33, minSdk 24
- UI: XML + ViewBinding + Fragments + Navigation Component (Compose ব্যবহার হয়নি)
- Camera/Detection: CameraX + custom `DocumentDetector` + OpenCV (local module, Maven থেকে নয়)
- OCR: Tesseract (`OCRHelper`, ben.traineddata + ara.traineddata) + ML Kit (ইংরেজি/ফরাসি/স্প্যানিশ/জার্মান/চাইনিজ/জাপানিজ/কোরিয়ান/হিন্দির জন্য, auto-download)
- Persistence: Room database (tool-generated docs-এর জন্য, `DbInvalidation` singleton) + হাতে-লেখা Gson/JSON store (`FileManager`) — kapt এড়ানোর জন্য
- Dev environment: AndroidIDE (তাই কিছু non-standard constraint আছে, নিচে দেখো)

## ARCHITECTURE NOTES
- `settings.gradle.kts`-এ `:app` ও `:opencv` module include করা আছে; OpenCV local module হিসেবে যোগ করতে হয় (Maven resolve নির্ভরযোগ্য নয় AndroidIDE-তে) — বিস্তারিত `INTEGRATION_GUIDE.md`-তে
- `DocumentDetector` প্রতিটি frame 480px-এ downsample করে + every-other-frame skip করে (perf, 2GB RAM target)
- `BitmapUtils.decodeSampledBitmap` — max dimension bound করা: processing 3000px, preview 2048px, thumbnail 300px (`BitmapUtils.SCAN_MAX_DIMENSION`-এর অধীনে unify করা হয়েছে)
- সব OpenCV `Mat` explicitly `.release()`, সব `Bitmap` explicitly `.recycle()` করতে হয়
- `OpenCVHelper.findDocumentCorners` — looser-tolerance second pass + minAreaRect fallback আছে
- `FileProvider` authority আগে mismatch হয়েছিল — এই জায়গায় change করলে সতর্ক থাকা

## KNOWN OPEN QUESTION
মেমরিতে দুই রকম তথ্য আছে — কোনটা current build তা পরবর্তী session-এ নিশ্চিত করে নাও:
- এক জায়গায় বলা হয়েছে ML Kit Document Scanner integrate করে custom camera flow replace করা হয়েছিল
- আরেক জায়গায় বলা হয়েছে reviewed build-এ custom OpenCV-based camera flow (CameraX + DocumentDetector + OpenCVHelper) আছে, ML Kit না
এই দুটো সম্ভবত ভিন্ন সময়ের iteration — session শুরুতে repo-র actual code check করে নিশ্চিত হও, ধরে নিও না।

## CODING RULES
1. `DocumentDetector`/`OpenCVHelper`-এর perf-sensitive কোড (downsampling, frame-skip, release/recycle) অকারণে বদলানো যাবে না।
2. Compose বা Room+kapt যোগ করা যাবে না — এই প্রজেক্ট ইচ্ছাকৃতভাবে kapt এড়িয়ে চলে (AndroidIDE build crash এড়াতে)।
3. OpenCV Maven dependency হিসেবে যোগ করার চেষ্টা করা যাবে না — local module পদ্ধতি বজায় রাখতে হবে।
4. `gradle.properties`-এ `android.suppressUnsupportedCompileSdk=33` আছে — AGP আপগ্রেড ছাড়া এটা সরানো যাবে না।

## FILE MODIFICATION RULES
- প্রথমে existing code inspect করো, related files identify করো।
- Blind full-file replace করা যাবে না — targeted, minimum necessary change।
- Breaking change দরকার হলে আগে জানাতে হবে + DECISIONS.md ও CHANGELOG.md আপডেট।

## TESTING RULES (INTEGRATION_GUIDE.md অনুযায়ী)
- Unit test (JVM): `CornerHandle.isTouched`, `OpenCVHelper.orderPoints`, `FileManager` CRUD
- Instrumented test: camera capture, OCR (device/emulator লাগবে)
- Manual QA: cold start → permission prompt → auto-capture (~1s) → crop screen (corners pre-filled) → filter strip (14 preview) → OCR (≤5s, 2019-era মিড-রেঞ্জ ফোনে) → PDF export (searchable text সহ)

## DOCUMENTATION RULES
প্রতিটি major task শেষে আপডেট করতে হবে:
CURRENT_STATUS.md → TASK_LIST.md → CHANGELOG.md → PROJECT_MASTER.md
(architectural decision থাকলে DECISIONS.md-ও)

## SECURITY RULES
- কোনো API key/secret commit করা যাবে না। এই প্রজেক্ট zero-analytics, zero-backend design — নতুন কোনো analytics/backend যোগ করার আগে জানাতে হবে।

## MEMORY UPDATE RULE
নতুন requirement/bug পেলে আগে TASK_LIST.md আপডেট করে তারপর কাজ শুরু করবে।
