package com.cradle.neptune.view.ui.reading

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.cradle.neptune.viewmodel.PatientReadingViewModel

/**
 * Base class for other ReadingFragments
 * - Observer
 * - Shared access to Reading object
 *
 *
 * Activities that contain fragments derived from this class  must implement the
 * [MyFragmentInteractionListener] interface to handle interaction events.
 * Use the newInstance() factory method to create an instance of derived fragment.
 */
abstract class BaseFragment : Fragment() {
    protected val viewModel: PatientReadingViewModel by activityViewModels()
}
