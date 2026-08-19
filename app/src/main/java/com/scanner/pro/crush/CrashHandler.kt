package com.scanner.pro.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Catches any uncaught exception anywhere in the app (main thread or background
 * threads) and, instead of letting the OS silently kill the process, launches
 * [CrashActivity] with the full stack trace so it can be read/copied on-device
 * without needing logcat access.
 *
 * Install once, as early as possible, from Application.onCreate():
 *   Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
 */
class CrashHandler(private val appContext: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val trace = buildReport(thread, throwable)

            val intent = Intent(appContext, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_TRACE, trace)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            appContext.startActivity(intent)
        } catch (t: Throwable) {
            // If we can't even show the crash screen, fall back to the system's
            // default handler so the crash still gets reported somewhere.
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        return buildString {
            appendLine("Crashed thread: ${thread.name}")
            appendLine("App version: com.scanner.pro")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine()
            append(sw.toString())
        }
    }
}
