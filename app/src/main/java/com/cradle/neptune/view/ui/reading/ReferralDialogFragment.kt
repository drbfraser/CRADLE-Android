package com.cradle.neptune.view.ui.reading

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.cradle.neptune.R
import com.cradle.neptune.view.ReadingActivity
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * src: https://medium.com/alexander-schaefer/
 *      implementing-the-new-material-design-full-screen-dialog-for-android-e9dcc712cb38
 */
@AndroidEntryPoint
class ReferralDialogFragment : DialogFragment() {
    private val viewModel: PatientReadingViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(context is ReadingActivity)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isCancelable = false
        return inflater.inflate(R.layout.referral_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Toolbar>(R.id.referral_toolbar).apply {
            setNavigationOnClickListener { dismiss() }
            title = getString(R.string.referral_dialog_title)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            // Make the dialog close to full screen.
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setStyle(STYLE_NORMAL, R.style.AppTheme_FullScreenDialog)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.setInputEnabledState(true)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel.setInputEnabledState(false)
        return super.onCreateDialog(savedInstanceState)
    }

    companion object {
        private const val TAG = "ReferralDialogFragment"
    }
}
