# PROJECT_MASTER.md

PROJECT NAME:
DocScannerPro

PROJECT TYPE:
Mobile App (Android)

PROJECT PURPOSE:
CamScanner-style document scanner — camera দিয়ে scan, auto edge-detection, crop, filter, OCR (Bengali/Arabic/English সহ), PDF export

OWNER:
Tahmina

TECHNOLOGY:
Kotlin, Gradle Kotlin DSL, AGP 7.2.2, CameraX, OpenCV (local module), Tesseract OCR + ML Kit, Room + Gson/JSON hybrid storage, XML/ViewBinding/Navigation Component

CURRENT VERSION:
TBD — নিশ্চিত করা হয়নি

PROJECT STATUS:
Active — core scan/crop/OCR/PDF flow তৈরি, কিছু অংশ ইচ্ছাকৃতভাবে stub করা (নিচে দেখো)

PRIMARY REPOSITORY:
https://github.com/jahid2177/DocScannerPro

CURRENT OBJECTIVE:
TBD — পরবর্তী session-এ নির্দিষ্ট করতে হবে

IMPORTANT FEATURES:
- Camera scan flow: CameraX + DocumentDetector + OpenCVHelper (auto edge-detection, corner crop)
- Filter strip — ১৪টি ভিন্ন filter preview
- OCR: Tesseract (ben/ara) + ML Kit (en/fr/es/de/zh/ja/ko/hi)
- PDF export (searchable text option)
- Room DB দিয়ে tool-generated documents Docs section-এ দেখানো
- Premium paywall

COMPLETED FEATURES:
- Custom OpenCV camera scan flow (CameraX + DocumentDetector + OpenCVHelper)
- Crop corner detection বাগ ফিক্স (ভুল resolution/rotation সমস্যা, এখন saved file থেকে re-detect করে)
- Coordinate space mismatch ফিক্স (crop editor 2048px বনাম repository 3000px → unified BitmapUtils.SCAN_MAX_DIMENSION)
- OpenCVHelper.findDocumentCorners-এ looser-tolerance + minAreaRect fallback
- Room database integration (tool-generated docs, DbInvalidation singleton, schema version bump)
- Premium upgrade merge (manifest/gradle merge, missing drawables)
- FileProvider authority mismatch, FilterMode enum conflict, missing vector drawable বাগ ফিক্স
- Conditional PDF viewing routing (single-page vs multi-page)
- UI reference-design matching
- ML Kit Document Scanner integration (নোট: memory-তে conflicting তথ্য আছে, CLAUDE.md-এর "KNOWN OPEN QUESTION" দেখো)

PENDING FEATURES (INTEGRATION_GUIDE.md-এর "Future improvements" থেকে):
- WorkManager-based batch OCR (সব পেজের জন্য)
- On-device automatic language detection (এখন user/SettingsManager.ocrLanguage manually pick করে)
- Cloud-free multi-device sync (WebDAV/Syncthing, zero-analytics/zero-backend বজায় রেখে)

KNOWN BUGS:
বর্তমানে কোনো known/open bug নেই — উপরের bug গুলো ইতিমধ্যে fix হয়ে গেছে

KNOWN LIMITATIONS (ইচ্ছাকৃতভাবে scope-এ রাখা হয়নি):
- Gallery import বাটন stub করা (registerForActivityResult(GetContent()) wire করা বাকি)
- "Manage folders" আলাদা screen নেই (repository/ViewModel-এ support আছে, UI নেই)
- Background removal filter single global Otsu threshold ব্যবহার করে (GrabCut-ভিত্তিক ভার্সন busy background-এ ভালো হতো)
- Launcher icons বাইনারি PNG হিসেবে ship করা হয়নি (source-only delivery)

IMPORTANT DECISIONS:
DECISIONS.md দেখো (OpenCV local module, kapt এড়ানো, zero-analytics/backend design)

CURRENT DEVELOPMENT AREA:
TBD

NEXT PRIORITY:
TBD — পরবর্তী session-এ ঠিক করতে হবে

LAST UPDATED:
2026-08-19
