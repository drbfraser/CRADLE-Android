package com.cradle.neptune.view.ui.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cradle.neptune.R
import com.cradle.neptune.view.ui.reading.PatientInfoFragment

/**
 * Confirm privacy policy with user
 */
class WelcomeFragment : IntroBaseFragment() {
    private var mView: View? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_intro_welcome, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        mView = view
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
        return true
    }

    companion object {
        fun newInstance(): WelcomeFragment {
            return WelcomeFragment()
        }
    }

    init {
        // Required empty public constructor
        TAG = PatientInfoFragment::class.java.name
    }
}
