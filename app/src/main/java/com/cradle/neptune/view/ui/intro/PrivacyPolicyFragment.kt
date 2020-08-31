package com.cradle.neptune.view.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.cradle.neptune.R
import com.cradle.neptune.view.ui.reading.PatientInfoFragment
import java.io.IOException

/**
 * Confirm privacy policy with user
 */
class PrivacyPolicyFragment : IntroBaseFragment() {
    private var mView: View? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_intro_privacy_policy, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        mView = view
        displayTextOnScreen()
    }

    private fun displayTextOnScreen() {
        // source: http://www.java2s.com/Code/Android/UI/Programmaticallyloadtextfromanassetandplaceitintothetextview.htm
        // Programmatically load text from an asset and place it into the
        // text view.  Note that the text we are loading is ASCII, so we
        // need to convert it to UTF-16.
        try {
            val inputStream =
                requireActivity().assets.open("CradleVSASupportAppPrivacyPolicy.txt")

            // We guarantee that the available method returns the total
            // size of the asset...  of course, this does mean that a single
            // asset can't be more than 2 gigs.
            val size = inputStream.available()

            // Read the entire asset into a local byte buffer.
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()

            // Convert the buffer into a string.
            val text = String(buffer)

            // Finally stick the string into the text view.
            val tv = requireView().findViewById<TextView>(R.id.tvPrivacyPolicy)
            tv.text = text
        } catch (e: IOException) {
            // Should never happen!
            throw RuntimeException(e)
        }
    }

    override fun onMyBeingDisplayed() {
        // may not have created view yet.
        if (mView == null) {
            return
        }
        hideKeyboard()
    }

    override fun onMyBeingHidden(): Boolean {
        // may not have created view yet.
        return if (mView == null) {
            true
        } else true
    }

    companion object {
        fun newInstance(): PrivacyPolicyFragment {
            return PrivacyPolicyFragment()
        }
    }

    init {
        // Required empty public constructor
        TAG = PatientInfoFragment::class.java.name
    }
}
