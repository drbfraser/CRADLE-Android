package com.cradleplatform.neptune.viewmodel.forms

import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question

/**
 * Represents the complete UI state of the form rendering screen.
 */
data class FormState(
    val formTemplate: FormTemplate? = null,
    val patient: Patient? = null,
    val patientId: String? = null,
    val language: String? = null,
    val formResponseId: Long? = null,
    val isFromSavedResponse: Boolean = false,
    val currentCategory: Int = 1,
    val bottomSheetExpanded: Boolean = false,
    val categoryList: List<Pair<String, List<Question>?>>? = null,
    val currentAnswers: Map<String, Answer> = emptyMap(),
    val hasUnsavedChanges: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * Represents UI events that can be triggered by the user in the form rendering screen.
 */
sealed class FormUiEvent {
    data class Submit(val protocol: String) : FormUiEvent()
    data object SaveDraft : FormUiEvent()
    data object Delete : FormUiEvent()
    data class CategoryChange(val category: Int) : FormUiEvent()
    data object NextCategory : FormUiEvent()
    data object PrevCategory : FormUiEvent()
    data object ToggleBottomSheet : FormUiEvent()
    data object HideBottomSheet : FormUiEvent()
}

/**
 * Represents one-time events that trigger side effects in the UI.
 */
sealed class FormSideEffect {
    data class ShowToast(val message: String) : FormSideEffect()
    data class ShowValidationError(val message: String) : FormSideEffect()
    data object NavigateBack : FormSideEffect()
    data object FormSubmittedSuccessfully : FormSideEffect()
}
