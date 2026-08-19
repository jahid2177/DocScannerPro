package com.scanner.pro.ui.pdf

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.scanner.pro.R
import com.scanner.pro.di.ViewModelFactory
import com.scanner.pro.model.PageSize
import com.scanner.pro.model.Resource
import com.scanner.pro.pdf.PdfOptions
import com.scanner.pro.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

class PdfPreviewFragment : Fragment(R.layout.fragment_pdf_preview) {

    private val viewModel: ScannerViewModel by activityViewModels { ViewModelFactory.getInstance(requireContext()) }
    private var lastGeneratedFile: File? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val pageSizeGroup = view.findViewById<android.widget.RadioGroup>(R.id.page_size_group)
        val compressSwitch = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_compress)
        val searchableSwitch = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_searchable)
        val passwordInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.input_password)
        val progress = view.findViewById<android.widget.ProgressBar>(R.id.progress)
        val statusText = view.findViewById<android.widget.TextView>(R.id.text_status)
        val generateButton = view.findViewById<android.widget.Button>(R.id.button_regenerate)
        val shareButton = view.findViewById<android.widget.Button>(R.id.button_share_pdf)

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        fun buildOptions(): PdfOptions {
            val pageSize = when (pageSizeGroup.checkedRadioButtonId) {
                R.id.size_letter -> PageSize.LETTER
                R.id.size_legal -> PageSize.LEGAL
                else -> PageSize.A4
            }
            val password = passwordInput.text?.toString()?.takeIf { it.isNotBlank() }
            return PdfOptions(
                pageSize = pageSize,
                quality = viewModel.settings.imageQuality,
                losslessMode = !compressSwitch.isChecked,
                password = password,
                title = viewModel.activeDocument.value?.name ?: "Scanned Document",
                makeSearchable = searchableSwitch.isChecked
            )
        }

        generateButton.setOnClickListener {
            val doc = viewModel.activeDocument.value ?: return@setOnClickListener
            val outputFile = File(requireContext().cacheDir, "${doc.name}.pdf")
            viewModel.exportPdf(outputFile, buildOptions())
        }

        shareButton.setOnClickListener {
            val file = lastGeneratedFile ?: return@setOnClickListener
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
        }

        // Auto-generate once with default options when the screen first opens.
        generateButton.performClick()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportState.collect { state ->
                    when (state) {
                        is Resource.Loading -> {
                            progress.visibility = View.VISIBLE
                            statusText.text = "Generating PDF..."
                        }
                        is Resource.Success -> {
                            progress.visibility = View.GONE
                            lastGeneratedFile = state.data
                            statusText.text = "Ready: ${state.data.name} (${state.data.length() / 1024} KB)"
                        }
                        is Resource.Error -> {
                            progress.visibility = View.GONE
                            statusText.text = getString(R.string.pdf_failed_message)
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}
