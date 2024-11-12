package com.cradleplatform.neptune.viewmodel.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.views.LocalSearchPatient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val patientDao: PatientDao
) : ViewModel() {

    var currentQueryString: String? = null
        private set

    private var currentSearchResult: Flow<PagingData<LocalSearchPatient>>? = null

    fun isUsingSearch() = !currentQueryString.isNullOrBlank()

    /**
     * Searches the database for patients where [query] is a substring of the patient's name or ID.
     * If [query] is blank, then all the patients in the database will be used.
     */
    fun searchPatientsFlow(query: String): Flow<PagingData<LocalSearchPatient>> {
        // If the query hasn't changed, don't make a new result.
        // Adapted from https://developer.android.com/codelabs/android-paging
        val previousResult = currentSearchResult
        if (query == currentQueryString && previousResult != null) {
            return previousResult
        }

        return Pager(PagingConfig(pageSize = 60, enablePlaceholders = true, maxSize = 200)) {
            if (query.isBlank()) {
                patientDao.allLocalSearchPatientsByDate()
            } else {
                patientDao.localSearchPatientsByNameOrId(query)
            }
        }.flow.cachedIn(viewModelScope).also {
            currentQueryString = query
            currentSearchResult = it
        }
    }
}
