package com.cradleplatform.neptune.viewmodel
import android.Manifest
import android.app.Application
import android.content.Context.TELEPHONY_SERVICE
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.manager.LoginManager.Companion.CURRENT_PHONE_NUMBER
import com.cradleplatform.neptune.manager.LoginManager.Companion.PHONE_NUMBERS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    application: Application,
    private val restApi: RestApi
) : AndroidViewModel(application) {

    private lateinit var currentPhoneNumber: String

    fun isNewNumber(): String {
        if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_PHONE_NUMBERS) ==
            PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val telManager = getApplication<Application>().getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val fetchedPhoneNumber = telManager.line1Number
            currentPhoneNumber = sharedPreferences.getString(CURRENT_PHONE_NUMBER, "") ?: ""

            if (hasUserPhoneNumberChanged(fetchedPhoneNumber)) {
                // Get the list of the user phone numbers that were fetched at login
                val allPhoneNumbers = parsePhoneNumberString(sharedPreferences.getString(PHONE_NUMBERS, ""))
                // Check if fetchedPhoneNumber is in allPhoneNumbers
                if (!allPhoneNumbers.contains(fetchedPhoneNumber)) {
                    return fetchedPhoneNumber
                }
            }
        }
        // Is not a new number
        return ""
    }

    fun updateUserPhoneNumbers(phoneNumber: String) {
        // Add the user's phone number to the database.
        viewModelScope.launch {
            val userId = sharedPreferences.getInt(LoginManager.USER_ID_KEY, -1)
            if (userId != -1) {
                val result = restApi.postUserPhoneNumber(userId, phoneNumber)
                println("Debug-number-view-model: updating the database -> result = $result")
            }
        }

        val allPhoneNumbers = parsePhoneNumberString(sharedPreferences.getString(PHONE_NUMBERS, ""))
        //TODO:
        // Set the current phone number - This number is the source of SMS and will be used to validate user
        // sharedPreferences.edit().putString(CURRENT_PHONE_NUMBER, fetchedPhoneNumber).apply()
        println("Debug-number-view-model: A new phone number has been detected")
        println("Debug-number-view-model: fetchedPhoneNumber = $phoneNumber")
        println("Debug-number-view-model: allPhoneNumbers = $allPhoneNumbers")
    }

    fun getCurrentUserPhoneNumber(): String {
        return currentPhoneNumber
    }

    private fun hasUserPhoneNumberChanged(fetchedPhoneNumber: String): Boolean {
        return fetchedPhoneNumber != currentPhoneNumber
    }

    private fun parsePhoneNumberString(allPhoneNumbersString: String?): List<String> {
        var allPhoneNumbers = listOf<String>()
        if (!allPhoneNumbersString.isNullOrEmpty()) {
            allPhoneNumbersString.split(",").also { allPhoneNumbers = it }
        }
        val allPhoneNumbersParsed = allPhoneNumbers.map { phoneNumber ->
            phoneNumber.replace("-", "")
        }
        return allPhoneNumbersParsed
    }
}
