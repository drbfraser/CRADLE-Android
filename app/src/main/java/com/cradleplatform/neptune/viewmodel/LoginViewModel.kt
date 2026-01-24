package com.cradleplatform.neptune.viewmodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginManager: LoginManager,
    private val workManager: WorkManager,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    private val _email = MutableLiveData<String>("")
    val email: LiveData<String> = _email

    private val _password = MutableLiveData<String>("")
    val password: LiveData<String> = _password

    fun setEmail(email: String) {
        _email.value = email
    }

    fun setPassword(password: String) {
        _password.value = password
    }

    fun isLoggedIn(): Boolean {
        return loginManager.isLoggedIn()
    }

    fun login(email: String, password: String) {
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = loginManager.login(email, password)

            when (result) {
                is NetworkResult.Success -> {
                    // Enqueue sync work
                    val workRequest = OneTimeWorkRequestBuilder<SyncAllWorker>()
                        .addTag(WORK_TAG)
                        .build()
                    sharedPreferences.edit {
                        putString(LAST_SYNC_JOB_UUID, workRequest.id.toString())
                    }
                    workManager.enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        workRequest
                    )
                    _loginState.value = LoginState.Success
                }
                is NetworkResult.Failure -> {
                    _loginState.value = LoginState.Error(
                        statusCode = result.statusCode,
                        message = null
                    )
                }
                is NetworkResult.NetworkException -> {
                    _loginState.value = LoginState.NetworkError(result.cause)
                }
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }

    companion object {
        private const val WORK_TAG =
            "SyncLogin-DownloadPatientsReadingsAssessmentsReferralsFacilitiesForms"
        private const val WORK_NAME = "SyncWorkerUniqueSync"
        private const val LAST_SYNC_JOB_UUID = "lastSyncJobUuid"
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val statusCode: Int, val message: String?) : LoginState()
    data class NetworkError(val exception: Throwable) : LoginState()
}

