package com.cradleplatform.neptune.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.SmsKey
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val sharedPreferences: SharedPreferences,
    private val networkStateManager: NetworkStateManager,
    private val smsKeyManager: SmsKeyManager,
    private val restApi: RestApi
) : AndroidViewModel(application) {

    private val _smsKeyUpdateResult = MutableLiveData<SmsKeyUpdateState>()
    val smsKeyUpdateResult: LiveData<SmsKeyUpdateState> = _smsKeyUpdateResult

    private val _newPhoneNumber = MutableLiveData<String?>()
    val newPhoneNumber: LiveData<String?> = _newPhoneNumber

    private val _pinWarningShown = MutableLiveData<Boolean>(false)
    val pinWarningShown: LiveData<Boolean> = _pinWarningShown

    val isNetworkAvailable: LiveData<Boolean> = networkStateManager.getInternetConnectivityStatus()

    init {
        checkForNewPhoneNumber()
    }

    private fun checkForNewPhoneNumber() {
        val newNumber = getNewNumber()
        if (newNumber.isNotEmpty()) {
            _newPhoneNumber.value = newNumber
        }
    }

    /**
     * Checks if the current user's phone number, fetched from the Android device, matches the
     * number stored in the platform's database.
     * Returns the new phone number if different, otherwise returns empty string.
     */
    @Suppress("DEPRECATION", "HardwareIds")
    private fun getNewNumber(): String {
        if (ActivityCompat.checkSelfPermission(
                getApplication(), Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                getApplication(), Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                getApplication(), Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val telManager =
                getApplication<Application>().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val fetchedPhoneNumber = telManager.line1Number
            val currentPhoneNumber = sharedPreferences.getString(UserViewModel.USER_PHONE_NUMBER, "") ?: ""

            if (fetchedPhoneNumber != currentPhoneNumber) {
                // Get the list of user phone numbers that were fetched at login
                val allPhoneNumbers = parsePhoneNumberString(
                    sharedPreferences.getString(UserViewModel.PHONE_NUMBERS, "")
                )
                // Check if fetchedPhoneNumber is NOT in allPhoneNumbers
                if (!allPhoneNumbers.contains(fetchedPhoneNumber)) {
                    return fetchedPhoneNumber
                }
                // Update current phone number if it's in the list
                sharedPreferences.edit {
                    putString(UserViewModel.USER_PHONE_NUMBER, fetchedPhoneNumber)
                }
            }
        }
        return ""
    }

    /**
     * Parses comma-separated phone numbers string into a list.
     */
    private fun parsePhoneNumberString(allPhoneNumbersString: String?): List<String> {
        if (allPhoneNumbersString.isNullOrEmpty()) {
            return emptyList()
        }
        return allPhoneNumbersString.split(",").map { phoneNumber ->
            phoneNumber.replace("-", "")
        }
    }

    fun updateUserPhoneNumber(newPhoneNumber: String) {
        viewModelScope.launch {
            val userId = sharedPreferences.getInt(UserViewModel.USER_ID_KEY, -1)
            if (userId != -1) {
                try {
                    val result = restApi.postUserPhoneNumber(userId, newPhoneNumber, Protocol.HTTP)
                    if (!result.failed) {
                        // Update sharedPreferences with new phone number
                        val allPhoneNumbersString = sharedPreferences.getString(UserViewModel.PHONE_NUMBERS, "")
                        val allPhoneNumbersList = parsePhoneNumberString(allPhoneNumbersString)
                        val updatedPhoneNumbers = allPhoneNumbersList.toMutableList().apply {
                            add(newPhoneNumber)
                        }
                        val updatedPhoneNumbersSerialized = updatedPhoneNumbers.joinToString(",")

                        sharedPreferences.edit {
                            putString(UserViewModel.PHONE_NUMBERS, updatedPhoneNumbersSerialized)
                            putString(UserViewModel.USER_PHONE_NUMBER, newPhoneNumber)
                        }
                    }
                } catch (@Suppress("SwallowedException") e: Exception) {
                    // Handle exception silently or log
                }
            }
            _newPhoneNumber.value = null
        }
    }

    fun dismissPhoneNumberDialog() {
        _newPhoneNumber.value = null
    }

    fun checkPinIfPinSet(pinCodePrefKey: String, defaultPinCode: String): Boolean {
        val pin = sharedPreferences.getString(pinCodePrefKey, defaultPinCode)
        return if (pin == defaultPinCode && !_pinWarningShown.value!!) {
            _pinWarningShown.value = true
            true
        } else {
            false
        }
    }

    fun validateSmsKeyAndPerformActions() {
        // Check SMS key validity only when there is internet connection
        val isNetworkAvailable = networkStateManager.getInternetConnectivityStatus().value
        if (isNetworkAvailable != true) {
            return
        }

        val smsKeyStatus = smsKeyManager.validateSmsKey()
        val userId = sharedPreferences.getInt(UserViewModel.USER_ID_KEY, -1)

        when (smsKeyStatus) {
            SmsKeyManager.KeyState.NOTFOUND -> {
                // User doesn't have a valid SMS key
                if (userId != -1) {
                    viewModelScope.launch {
                        when (val response = restApi.getNewSmsKey(userId)) {
                            is NetworkResult.Success -> {
                                val smsKey = response.value
                                if (smsKey != null) {
                                    smsKeyManager.storeSmsKey(smsKey)
                                    _smsKeyUpdateResult.value = SmsKeyUpdateState.Success("Key update was successful")
                                }
                            }
                            else -> _smsKeyUpdateResult.value = SmsKeyUpdateState.Error("Network Error: Key update unsuccessful")
                        }
                    }
                }
            }
            SmsKeyManager.KeyState.EXPIRED -> {
                // User's SMS key is expired
                if (userId != -1) {
                    viewModelScope.launch {
                        val response = restApi.refreshSmsKey(userId)
                        handleSmsKeyUpdateResult(response)
                    }
                }
            }
            SmsKeyManager.KeyState.WARN -> {
                // User's SMS key is stale - Warn the user to refresh their SmsKey
                val daysUntilExpiry = smsKeyManager.getDaysUntilExpiry()
                _smsKeyUpdateResult.value = SmsKeyUpdateState.Warning(daysUntilExpiry)
            }
            else -> {
                // Key is valid, no action needed
            }
        }
    }

    private fun handleSmsKeyUpdateResult(result: NetworkResult<SmsKey>) {
        when (result) {
            is NetworkResult.Success -> {
                smsKeyManager.storeSmsKey(result.value)
                _smsKeyUpdateResult.value = SmsKeyUpdateState.Success("Key update was successful")
            }
            else -> _smsKeyUpdateResult.value = SmsKeyUpdateState.Error("Network Error: Key update unsuccessful")
        }
    }

    fun resetSmsKeyUpdateState() {
        _smsKeyUpdateResult.value = SmsKeyUpdateState.Idle
    }
}

sealed class SmsKeyUpdateState {
    object Idle : SmsKeyUpdateState()
    data class Success(val message: String) : SmsKeyUpdateState()
    data class Error(val message: String) : SmsKeyUpdateState()
    data class Warning(val daysUntilExpiry: Int) : SmsKeyUpdateState()
}

