package com.scanner.pro

import android.app.Application
import com.scanner.pro.crash.CrashHandler
import org.opencv.android.OpenCVLoader

class ScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install first, before anything else gets a chance to crash.
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        // Static OpenCV init (OpenCV 4.x Android SDK bundles native libs, no async loader needed).
        // Wrapped in try/catch: a native-library load failure can throw (not just return false),
        // which would otherwise crash the app before any screen is shown.
        try {
            if (!OpenCVLoader.initLocal()) {
                android.util.Log.e("ScannerApplication", "OpenCV native init failed")
            }
        } catch (t: Throwable) {
            android.util.Log.e("ScannerApplication", "OpenCV native init crashed", t)
        }
    }
}
