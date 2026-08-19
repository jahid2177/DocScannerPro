package com.scanner.pro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.scanner.pro.utils.SettingsManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsManager(this).applyTheme() // apply saved theme before inflating any view
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
