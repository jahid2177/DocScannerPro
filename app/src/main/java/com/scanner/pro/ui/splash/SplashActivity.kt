package com.scanner.pro.ui.splash

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.scanner.pro.MainActivity
import com.scanner.pro.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val icon = findViewById<android.widget.ImageView>(R.id.icon)
        ObjectAnimator.ofFloat(icon, "scaleX", 0.6f, 1f).apply { duration = 400 }.start()
        ObjectAnimator.ofFloat(icon, "scaleY", 0.6f, 1f).apply { duration = 400 }.start()
        ObjectAnimator.ofFloat(icon, "alpha", 0f, 1f).apply { duration = 400 }.start()

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DURATION_MS)
    }

    companion object {
        private const val SPLASH_DURATION_MS = 900L
    }
}
