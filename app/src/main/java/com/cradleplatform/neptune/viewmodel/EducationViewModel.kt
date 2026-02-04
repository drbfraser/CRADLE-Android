package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for EducationActivity to handle configuration changes like screen rotation.
 * Since EducationActivity is a simple navigation activity with no state to preserve,
 * this ViewModel serves primarily to mark the activity as rotation-safe.
 */
@HiltViewModel
class EducationViewModel @Inject constructor() : ViewModel()

