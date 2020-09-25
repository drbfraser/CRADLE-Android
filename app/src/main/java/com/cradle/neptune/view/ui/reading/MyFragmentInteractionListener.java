package com.cradle.neptune.view.ui.reading;

import com.cradle.neptune.manager.ReadingManager;
import com.cradle.neptune.viewmodel.PatientReadingViewModel;

/**
 * This interface must be implemented by activities that contain this
 * fragment to allow an interaction in this fragment to be communicated
 * to the activity and potentially other fragments contained in that
 * activity.
 */
public interface MyFragmentInteractionListener {
    PatientReadingViewModel getViewModel();

    // TODO: Fix the need for _ by removing this interface altogether. This should really be
    //  stored in the ViewModel, not in the Activity.
    ReadingManager getReadingManager_();

    void advanceToNextPage();

    boolean saveCurrentReading();

    void finishActivity();
}