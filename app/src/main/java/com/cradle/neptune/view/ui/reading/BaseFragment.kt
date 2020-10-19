package com.cradle.neptune.view.ui.reading

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import com.cradle.neptune.viewmodel.PatientReadingViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
@AndroidEntryPoint
abstract class BaseFragment : Fragment() {
    // Injection is handled by ReadingActivity.
    @Inject
    lateinit var viewModelFactory: PatientReadingViewModelFactory

    // ViewModel is scoped to the Activity that this Fragment is attached to; therefore, this is
    // shared by all Fragments.
    protected val viewModel: PatientReadingViewModel by activityViewModels {
        viewModelFactory
    }
}
