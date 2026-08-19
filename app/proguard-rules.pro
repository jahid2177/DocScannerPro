# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Tesseract4Android (JNI bindings)
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**

# iText7
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Gson models used for JSON persistence — keep field names for deserialization
-keep class com.scanner.pro.model.** { *; }
-keep class com.scanner.pro.repository.ScanFolder { *; }
-keepattributes Signature
-keepattributes *Annotation*
