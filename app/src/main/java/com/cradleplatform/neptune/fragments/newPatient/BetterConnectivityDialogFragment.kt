package com.cradleplatform.neptune.fragments.newPatient

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * DialogFragment that asks the user if they want to send via DATA instead of SMS
 * when internet connectivity is available. This survives configuration changes like rotation.
 */
class BetterConnectivityDialogFragment : DialogFragment() {

    interface ConnectivityChoiceListener {
        fun onContinueWithSMS()
        fun onSendViaData()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val listener = parentFragment as? ConnectivityChoiceListener
            ?: throw IllegalStateException("Parent fragment must implement ConnectivityChoiceListener")

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Better Connectivity Available")
            .setMessage(
                "It seems that your current internet connection is stronger than the SMS network.\n\n" +
                    "Would you like to send the request via data instead to ensure faster and more reliable delivery?"
            )
            .setPositiveButton("Continue with SMS") { _, _ ->
                listener.onContinueWithSMS()
            }
            .setNegativeButton("Send via DATA") { _, _ ->
                listener.onSendViaData()
            }
            .create()
    }

    companion object {
        const val TAG = "BetterConnectivityDialog"

        fun newInstance() = BetterConnectivityDialogFragment()
    }
}
