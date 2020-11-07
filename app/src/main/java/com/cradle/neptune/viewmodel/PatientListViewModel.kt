package com.cradle.neptune.viewmodel

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.database.views.LocalSearchPatient
import kotlinx.coroutines.flow.Flow

class PatientListViewModel @ViewModelInject constructor(
    private val patientDao: PatientDaoAccess
) : ViewModel() {
    val allPatientsFlow: Flow<PagingData<LocalSearchPatient>> = Pager(
        PagingConfig(
            pageSize = 60,
            enablePlaceholders = true
        )
    ) {
        patientDao.allLocalSearchPatientsByName()
    }.flow
}
