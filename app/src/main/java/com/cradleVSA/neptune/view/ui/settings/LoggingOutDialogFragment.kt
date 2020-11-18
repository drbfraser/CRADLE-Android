package com.cradleVSA.neptune.view.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.cradleVSA.neptune.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LoggingOutDialogFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isCancelable = false
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.logout_in_progress_dialog_title)
            .setView(R.layout.dialog_loading)
            .show()
    }
}
