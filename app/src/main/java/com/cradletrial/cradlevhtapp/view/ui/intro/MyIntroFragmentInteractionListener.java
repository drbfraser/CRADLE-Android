package com.cradletrial.cradlevhtapp.view.ui.intro;

/**
 * For INTRO wizard
 * This interface must be implemented by activities that contain this
 * fragment to allow an interaction in this fragment to be communicated
 * to the activity and potentially other fragments contained in that
 * activity.
 */
public interface MyIntroFragmentInteractionListener {
    // Move on to next page
    void advanceToNextPage();

    // Set NEXT button being enabled/disabled
    void setNextButtonEnabled(boolean b);
}