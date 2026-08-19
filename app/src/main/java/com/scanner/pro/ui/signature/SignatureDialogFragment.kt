package com.scanner.pro.ui.signature

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.scanner.pro.R

/**
 * Bottom-sheet signature pad launched from the page editor's "Sign" action.
 * Calls [onSigned] with the drawn strokes as a transparent-background bitmap
 * once the user taps Done; the caller is responsible for compositing it onto
 * the page image.
 */
class SignatureDialogFragment(
    private val onSigned: (Bitmap) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val sheet = BottomSheetDialog(requireContext())
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_signature, null)
        sheet.setContentView(view)

        val pad = view.findViewById<SignaturePadView>(R.id.signature_pad)
        val clearButton = view.findViewById<android.widget.TextView>(R.id.button_clear)
        val doneButton = view.findViewById<android.widget.Button>(R.id.button_done)

        clearButton.setOnClickListener { pad.clear() }
        doneButton.setOnClickListener {
            val bitmap = pad.exportBitmap()
            if (bitmap != null) {
                onSigned(bitmap)
                dismiss()
            } else {
                android.widget.Toast.makeText(requireContext(), "Draw a signature first", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        return sheet
    }
}
