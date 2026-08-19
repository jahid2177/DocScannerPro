package com.scanner.pro.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.scanner.pro.R
import com.scanner.pro.databinding.ActivityCrashBinding
import kotlin.system.exitProcess

/**
 * Shown instead of an instant, silent crash. Displays the full stack trace
 * from [CrashHandler] so it can be read or copied straight from the device.
 */
class CrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val trace = intent.getStringExtra(EXTRA_TRACE).orEmpty()
        binding.crashTraceText.text = trace

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Crash report", trace))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnClose.setOnClickListener {
            finishAffinity()
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }

    companion object {
        const val EXTRA_TRACE = "extra_trace"
    }
}
