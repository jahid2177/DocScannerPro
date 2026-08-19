# DECISIONS.md

DATE: TBD
DECISION: OpenCV Maven dependency না ব্যবহার করে local module (`:opencv`) হিসেবে যোগ করা
WHY: AndroidIDE-তে official OpenCV AAR Maven থেকে reliably resolve হয় না
ALTERNATIVES CONSIDERED: Maven-based OpenCV dependency (কাজ করেনি)
RESULT: `app/build.gradle.kts`-এ `implementation(project(":opencv"))`, manual SDK download + jniLibs setup (INTEGRATION_GUIDE.md-তে ধাপ আছে)

---

DATE: TBD
DECISION: Room + kapt এড়িয়ে হাতে-লেখা Gson/JSON store (FileManager) ব্যবহার + Compose ব্যবহার না করা
WHY: kapt-related build crash AndroidIDE-তে এড়ানোর জন্য
ALTERNATIVES CONSIDERED: Compose UI, kapt-based Room পুরোপুরি
RESULT: XML + ViewBinding + Fragments + Navigation Component; Room শুধু tool-generated docs-এর জন্য সীমিত ব্যবহার, বাকি সব FileManager (Gson/JSON)

---

DATE: TBD
DECISION: Zero-analytics, zero-backend design (কোনো Anthropic/Google backend নির্ভরতা নেই)
WHY: Privacy-first approach
ALTERNATIVES CONSIDERED: TBD
RESULT: ভবিষ্যতে multi-device sync হলেও WebDAV/Syncthing-এর মতো cloud-free অপশন বিবেচনা করা হচ্ছে

---
নতুন architectural decision নেওয়া হলে এই format-এ এখানে যোগ করো:

DATE:
DECISION:
WHY:
ALTERNATIVES CONSIDERED:
RESULT:
