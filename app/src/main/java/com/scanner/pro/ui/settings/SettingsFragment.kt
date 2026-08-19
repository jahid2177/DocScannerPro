package com.scanner.pro.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.scanner.pro.R
import com.scanner.pro.model.OcrLanguage
import com.scanner.pro.model.ScanFilterType
import com.scanner.pro.utils.SettingsManager
import com.scanner.pro.utils.ThemeMode

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var settings: SettingsManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsManager(requireContext())

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val autoSave = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_auto_save)
        val autoCapture = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_auto_capture)
        val pdfCompression = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_pdf_compression)
        val qualitySeek = view.findViewById<android.widget.SeekBar>(R.id.seek_quality)
        val filterSpinner = view.findViewById<android.widget.Spinner>(R.id.spinner_default_filter)
        val languageSpinner = view.findViewById<android.widget.Spinner>(R.id.spinner_ocr_language)
        val themeGroup = view.findViewById<android.widget.RadioGroup>(R.id.theme_group)

        autoSave.isChecked = settings.autoSave
        autoSave.setOnCheckedChangeListener { _, v -> settings.autoSave = v }

        autoCapture.isChecked = settings.autoCapture
        autoCapture.setOnCheckedChangeListener { _, v -> settings.autoCapture = v }

        pdfCompression.isChecked = settings.pdfCompressionEnabled
        pdfCompression.setOnCheckedChangeListener { _, v -> settings.pdfCompressionEnabled = v }

        qualitySeek.progress = (settings.imageQuality - 10).coerceIn(0, 90)
        qualitySeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) settings.imageQuality = progress + 10
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        val filterNames = ScanFilterType.values().map { it.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } }
        filterSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, filterNames)
        filterSpinner.setSelection(settings.defaultFilter.ordinal)
        filterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                settings.defaultFilter = ScanFilterType.values()[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        val languageNames = OcrLanguage.values().map { it.displayName }
        languageSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languageNames)
        languageSpinner.setSelection(settings.ocrLanguage.ordinal)
        languageSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                settings.ocrLanguage = OcrLanguage.values()[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        when (settings.themeMode) {
            ThemeMode.LIGHT -> themeGroup.check(R.id.theme_light)
            ThemeMode.DARK -> themeGroup.check(R.id.theme_dark)
            ThemeMode.SYSTEM -> themeGroup.check(R.id.theme_system)
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            settings.themeMode = when (checkedId) {
                R.id.theme_light -> ThemeMode.LIGHT
                R.id.theme_dark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            requireActivity().recreate()
        }
    }
}
