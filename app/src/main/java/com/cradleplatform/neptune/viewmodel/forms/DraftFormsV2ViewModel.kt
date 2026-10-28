package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.model.FormV2Draft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DraftFormsV2State {
    object Loading : DraftFormsV2State()
    data class Success(val drafts: List<FormV2Draft>) : DraftFormsV2State()
    object Error : DraftFormsV2State()
}

@HiltViewModel
class DraftFormsV2ViewModel @Inject constructor(
    private val formV2DraftDao: FormV2DraftDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val patientId: String = requireNotNull(savedStateHandle[EXTRA_PATIENT_ID])
    private val _state = MutableStateFlow<DraftFormsV2State>(DraftFormsV2State.Loading)
    val state: StateFlow<DraftFormsV2State> = _state.asStateFlow()

    init {
        loadDrafts()
    }

    fun loadDrafts() {
        _state.value = DraftFormsV2State.Loading
        viewModelScope.launch {
            _state.value = try {
                DraftFormsV2State.Success(formV2DraftDao.getDraftsByPatientId(patientId))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                DraftFormsV2State.Error
            }
        }
    }

    companion object {
        const val EXTRA_PATIENT_ID = "form_v2_draft_patient_id"
    }
}
