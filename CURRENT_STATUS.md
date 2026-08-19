# CURRENT PROJECT STATUS

Date: 2026-08-19
Version: TBD

CURRENTLY WORKING ON:
TBD — এই ফাইল প্রথম setup, পরবর্তী session-এ আপডেট করো

LAST COMPLETED:
- Crop corner detection ও coordinate-space mismatch বাগ ফিক্স
- Room database integration (tool-generated documents)
- Premium paywall merge
- FileProvider/FilterMode/drawable বাগ ফিক্স
- Conditional PDF viewing routing

CURRENT PROGRESS:
Core scan → crop → filter → OCR → PDF flow কাজ করছে। Continuity system এইমাত্র সেটআপ হলো।

PENDING:
- Gallery import বাটন wire করা (stub আছে)
- "Manage folders" UI screen বানানো
- Camera flow ML Kit না custom OpenCV — কোনটা current তা নিশ্চিত করা (দেখো CLAUDE.md → KNOWN OPEN QUESTION)
- বাকি TASK_LIST.md-এ

BLOCKED BY:
কিছু নেই বর্তমানে

KNOWN ISSUES:
কোনো open bug নেই (দেখুন PROJECT_MASTER.md → KNOWN BUGS)

NEXT ACTION:
TBD — পরবর্তী session শুরুতে ঠিক করো

FILES RECENTLY MODIFIED (memory অনুযায়ী):
- DocumentDetector, OpenCVHelper (corner detection)
- BitmapUtils (SCAN_MAX_DIMENSION unify)
- DbInvalidation, Room entities/DAO
- FileProvider config, FilterMode enum
- PDF viewer routing logic

TEST STATUS:
INTEGRATION_GUIDE.md-এর manual QA checklist অনুযায়ী regression test verify করা হয়নি — পরবর্তী session-এ verify করো

IMPORTANT NOTES:
- OpenCV local module হিসেবে wire করতে হয় (Maven-এ resolve হয় না) — নতুন environment-এ setup করলে INTEGRATION_GUIDE.md ধাপ ২ অনুসরণ করো
- Launcher icon PNG ship করা হয়নি (source-only delivery)
- এই ফাইল প্রথমবার তৈরি হয়েছে ২০২৬-০৮-১৯ তারিখে Continuity System সেটআপের অংশ হিসেবে
