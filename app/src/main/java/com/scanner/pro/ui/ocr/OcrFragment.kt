package com.scanner.pro.ui.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.scanner.pro.R
import com.scanner.pro.di.ViewModelFactory
import com.scanner.pro.model.OcrLanguage
import com.scanner.pro.model.Resource
import com.scanner.pro.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch

class OcrFragment : Fragment(R.layout.fragment_ocr) {

    private val viewModel: ScannerViewModel by activityViewModels { ViewModelFactory.getInstance(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pageId = arguments?.getString("pageId")
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.language_chips)
        val progress = view.findViewById<android.widget.ProgressBar>(R.id.progress)
        val resultText = view.findViewById<android.widget.TextView>(R.id.text_result)
        val copyButton = view.findViewById<android.widget.Button>(R.id.button_copy)
        val shareButton = view.findViewById<android.widget.Button>(R.id.button_share_text)

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val page = viewModel.activeDocument.value?.pages?.firstOrNull { it.id == pageId }

        OcrLanguage.values().forEach { lang ->
            val chip = Chip(requireContext()).apply {
                text = lang.displayName
                isCheckable = true
                isChecked = lang == viewModel.settings.ocrLanguage
                setOnClickListener {
                    page?.let { viewModel.runOcr(it, lang) }
                }
            }
            chipGroup.addView(chip)
        }

        copyButton.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Scanned text", resultText.text))
        }

        shareButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, resultText.text.toString())
            }
            startActivity(Intent.createChooser(intent, "Share extracted text"))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ocrState.collect { state ->
                    when (state) {
                        is Resource.Loading -> {
                            progress.visibility = View.VISIBLE
                        }
                        is Resource.Success -> {
                            progress.visibility = View.GONE
                            resultText.text = state.data.fullText.ifBlank { "No text found on this page." }
                        }
                        is Resource.Error -> {
                            progress.visibility = View.GONE
                            resultText.text = getString(R.string.ocr_failed_message)
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}
