package com.cradle.neptune.view.ui.reading

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Base class for other ReadingFragments
 */
@AndroidEntryPoint
abstract class BaseFragment : Fragment() {
    // ViewModel is scoped to the Activity that this Fragment is attached to; therefore, this is
    // shared by all Fragments.
    protected val viewModel: PatientReadingViewModel by activityViewModels()
}
