package com.cradleplatform.neptune.view.ui.intro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.view.ui.reading.PatientInfoFragment

/**
 * Confirm privacy policy with user
 */
class PermissionsFragment : IntroBaseFragment() {
    private var mView: View? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_intro_permissions, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Make sure next button is disabled, since this is the first fragment
        activityCallbackListener!!.setNextButtonEnabled(areAllPermissionsGranted(context))
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        mView = view
        updateDisplay()
        setupGrantPermissions()
        if (areAllPermissionsGranted(requireContext())) {
            activityCallbackListener!!.advanceToNextPage()
        }
    }

    override fun onMyBeingDisplayed() {
        // may not have created view yet.
        if (mView == null) {
            return
        }
        hideKeyboard()
        updateDisplay()

        // Disable NEXT button until all granted
        activityCallbackListener!!.setNextButtonEnabled(areAllPermissionsGranted(requireContext()))
    }

    override fun onMyBeingHidden(): Boolean {
        // may not have created view yet.
        return true
    }

    private fun updateDisplay() {
        // Show permissions message
        val linearLayout =
            requireView().findViewById<LinearLayout>(R.id.linearLayoutForWV)
        if (linearLayout.childCount == 0) {
            val wv =
                WebView(requireActivity().createConfigurationContext(Configuration()))
            val htmlContents = getString(R.string.intro_permission_description)
            wv.loadDataWithBaseURL(null, htmlContents, "text/html", "utf-8", null)
            linearLayout.addView(wv)
        }
        val allGranted =
            areAllPermissionsGranted(requireContext())
        // Configure screen if we need permissions
        val btn =
            requireView().findViewById<Button>(R.id.btnGrantPermissions)
        btn.visibility = if (allGranted) View.GONE else View.VISIBLE
        val tvAllGranted = requireView().findViewById<TextView>(R.id.txtAllGranted)
        tvAllGranted.visibility = if (allGranted) View.VISIBLE else View.INVISIBLE
        // Each time we load, hide this; shown if granting fails.
        val tvMore = requireView().findViewById<TextView>(R.id.txtMoreNeededWarning)
        tvMore.visibility = View.GONE

        // callback to activity done when we get focus
    }

    private fun setupGrantPermissions() {
        val btn =
            requireView().findViewById<Button>(R.id.btnGrantPermissions)
        btn.setOnClickListener { requestAllPermissions() }
    }

    /**
     * TODO: Stop using this deprecated way of requesting permissions (refer to issue #26)
     */
    private fun requestAllPermissions() {
        requestPermissions(
            requiredPermissions,
            MY_PERMISSIONS_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        // It seems that the requestCode is not intact from our call.
        // So... ignore it!
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }

        // If request is cancelled, the result arrays are empty.
        if (grantResults.isNotEmpty() && allGranted) {
            // permission was granted, yay!
            activityCallbackListener!!.advanceToNextPage()
            activityCallbackListener!!.setNextButtonEnabled(true)
        } else {
            // permission denied, boo!
            val tvMore = requireView().findViewById<TextView>(R.id.txtMoreNeededWarning)
            tvMore.visibility = View.VISIBLE
        }
    }

    companion object {
        /*
        Permissions
     */
        private val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        )
        private const val MY_PERMISSIONS_REQUEST = 1515
        fun newInstance(): PermissionsFragment {
            return PermissionsFragment()
        }

        @JvmStatic
        fun areAllPermissionsGranted(context: Context): Boolean {
            for (permission in requiredPermissions) {
                if (ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
            return true
        }
    }

    init {
        // Required empty public constructor
        TAG = PatientInfoFragment::class.java.name
    }
}
