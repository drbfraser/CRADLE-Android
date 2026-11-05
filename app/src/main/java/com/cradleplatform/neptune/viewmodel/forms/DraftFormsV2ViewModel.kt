package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.FormV2Draft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DraftFormsV2State {
    object Loading : DraftFormsV2State()
    data class Success(
        val drafts: List<FormV2Draft>,
        val patientNames: Map<String, String>? = null
    ) : DraftFormsV2State()
    object Error : DraftFormsV2State()
}

@HiltViewModel
class DraftFormsV2ViewModel @Inject constructor(
    private val formV2DraftDao: FormV2DraftDao,
    private val patientManager: PatientManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val patientId: String? = savedStateHandle.get<String>(EXTRA_PATIENT_ID)?.takeIf { it.isNotBlank() }
    val isAllPatients: Boolean = patientId == null
    private val _state = MutableStateFlow<DraftFormsV2State>(DraftFormsV2State.Loading)
    val state: StateFlow<DraftFormsV2State> = _state.asStateFlow()
    private var observeDraftsJob: Job? = null

    init {
        loadDrafts()
    }

    fun loadDrafts() {
        observeDraftsJob?.cancel()
        _state.value = DraftFormsV2State.Loading
        observeDraftsJob = viewModelScope.launch {
            try {
                val draftsFlow = patientId?.let { formV2DraftDao.observeDraftsByPatientId(it) }
                    ?: formV2DraftDao.observeAllDrafts()
                draftsFlow.collect { drafts ->
                    // Dashboard drafts need patient labels; patient-specific lists already have context.
                    val patientNames = if (isAllPatients) {
                        drafts.map { it.patientId }.distinct().associateWith { id ->
                            patientManager.getPatientById(id)?.name.orEmpty()
                        }
                    } else null
                    _state.value = DraftFormsV2State.Success(drafts, patientNames)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.value = DraftFormsV2State.Error
            }
        }
    }

    companion object {
        const val EXTRA_PATIENT_ID = "form_v2_draft_patient_id"
    }
}
