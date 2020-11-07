package com.cradle.neptune.viewmodel

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.database.views.LocalSearchPatient
import kotlinx.coroutines.flow.Flow

class PatientListViewModel @ViewModelInject constructor(
    private val patientDao: PatientDaoAccess
) : ViewModel() {

    private var currentQueryString: String? = null
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

        return Pager(PagingConfig(pageSize = 60, enablePlaceholders = true)) {
            if (query.isBlank()) {
                patientDao.allLocalSearchPatientsByName()
            } else {
                patientDao.localSearchPatientsByNameOrId(query)
            }
        }.flow.cachedIn(viewModelScope).also {
            currentQueryString = query
            currentSearchResult = it
        }
    }
}
