package com.cradle.neptune.dagger

import com.cradle.neptune.view.ui.reading.PatientIdConflictDialogFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

/**
 * Required to use AndroidSupportInjection.inject on these special Fragments.
 */
@Suppress("unused")
@Module
abstract class FragmentModule {
    @ContributesAndroidInjector
    abstract fun contributePatientIdInUseDialogFragment(): PatientIdConflictDialogFragment
}
