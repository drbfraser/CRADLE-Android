package com.cradle.neptune.view.ui.intro

/**
 * For INTRO wizard
 * This interface must be implemented by activities that contain this
 * fragment to allow an interaction in this fragment to be communicated
 * to the activity and potentially other fragments contained in that
 * activity.
 */
interface MyIntroFragmentInteractionListener {
    // Move on to next page
    fun advanceToNextPage()

    // Set NEXT button being enabled/disabled
    fun setNextButtonEnabled(isEnabled: Boolean)
}
