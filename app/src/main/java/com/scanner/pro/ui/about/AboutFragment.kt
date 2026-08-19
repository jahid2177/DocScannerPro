package com.scanner.pro.ui.about

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.scanner.pro.BuildConfig
import com.scanner.pro.R

class AboutFragment : Fragment(R.layout.fragment_about) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { findNavController().popBackStack() }
        view.findViewById<android.widget.TextView>(R.id.text_version).text =
            "Version ${BuildConfig.VERSION_NAME}"
    }
}
