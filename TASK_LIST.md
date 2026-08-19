# TASK_LIST.md

HIGH PRIORITY
- [ ] Camera flow নিশ্চিত করা — ML Kit vs custom OpenCV, কোনটা বর্তমান কোডে আছে (CLAUDE.md → KNOWN OPEN QUESTION দেখো)
- [ ] Gallery import বাটন wire করা (registerForActivityResult(GetContent()))

MEDIUM PRIORITY
- [ ] "Manage folders" UI screen বানানো (backend/ViewModel আগে থেকেই আছে)
- [ ] Launcher icons বানানো (ic_launcher / ic_launcher_round PNG)
- [ ] WorkManager-based batch OCR (multi-page)

LOW PRIORITY
- [ ] On-device automatic OCR language detection
- [ ] Background removal filter GrabCut-ভিত্তিক করা
- [ ] Cloud-free multi-device sync (WebDAV/Syncthing)

COMPLETED
- [x] CameraX + DocumentDetector + OpenCVHelper camera scan flow
- [x] Crop corner detection বাগ ফিক্স (saved-file থেকে re-detect)
- [x] Coordinate-space mismatch ফিক্স (BitmapUtils.SCAN_MAX_DIMENSION unify)
- [x] OpenCVHelper.findDocumentCorners looser-tolerance + minAreaRect fallback
- [x] Room database integration (tool-generated docs, DbInvalidation)
- [x] Premium upgrade merge
- [x] FileProvider authority / FilterMode enum / missing drawable বাগ ফিক্স
- [x] Conditional PDF viewing routing (single vs multi-page)
- [x] UI reference-design matching
