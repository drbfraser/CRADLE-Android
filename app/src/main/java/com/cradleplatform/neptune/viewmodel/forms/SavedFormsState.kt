package com.cradleplatform.neptune.viewmodel.forms

import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.Patient

/**
 * Represents the state of the saved forms screen.
 */
data class SavedFormsState(
    val patientId: String? = null,
    val patient: Patient? = null,
    val savedAsDraft: Boolean = false,
    val formMap: Map<Patient, List<FormResponse>> = emptyMap(),
    val isLoading: Boolean = false
)
