package com.cradle.neptune.view.ui.reading;

import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;

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