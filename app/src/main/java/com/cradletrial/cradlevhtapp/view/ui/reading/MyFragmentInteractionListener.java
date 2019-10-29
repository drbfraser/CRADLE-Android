package com.cradletrial.cradlevhtapp.view.ui.reading;

import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.model.ReadingManager;

/**
 * This interface must be implemented by activities that contain this
 * fragment to allow an interaction in this fragment to be communicated
 * to the activity and potentially other fragments contained in that
 * activity.
 */
public interface MyFragmentInteractionListener {
    Reading getCurrentReading();

    ReadingManager getReadingManager();

    void advanceToNextPage();

    boolean saveCurrentReading();
}