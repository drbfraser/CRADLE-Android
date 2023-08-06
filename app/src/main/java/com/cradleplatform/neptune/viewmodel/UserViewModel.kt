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

/**
 * ViewModel for managing user-related operations and data.
 * As of Aug 6, 2023: handles the detection of changes in users' phone numbers
 * and updates platform's database.
 * @param sharedPreferences The SharedPreferences instance for storing user data.
 * @param application The application context.
 * @param restApi The RestApi instance for making network requests.
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    application: Application,
    private val restApi: RestApi
) : AndroidViewModel(application) {

    // TODO: check whether is should be the source of SMS and handle the edge cases
    //  - e.g.: User selects no when asked to update their phone number.
    private lateinit var currentPhoneNumber: String

    /**
     * Checks if the current user's phone number, fetched from the Android device, matches the
     * number stored in the platform's database.
     * If the phone number differs from the database, returns the new phone number.
     * If the phone number has not changed, returns an empty string.
     *
     * @return The new phone number if different from the database, otherwise an empty string.
     */
    fun getNewNumber(): String {
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
        // Fetched phone number is already stored in the databse
        return ""
    }

    /**
     * Updates the user's phone numbers in the platform's database and updates the
     * current user's phone number.
     *
     * @param phoneNumber The new phone number to be associated with the user.
     */
    fun updateUserPhoneNumbers(phoneNumber: String) {
        // Add the user's phone number to the database.
        // TODO: Handle database update failure
        viewModelScope.launch {
            val userId = sharedPreferences.getInt(LoginManager.USER_ID_KEY, -1)
            if (userId != -1) {
                val result = restApi.postUserPhoneNumber(userId, phoneNumber)
                println("Debug-number-view-model: updating the database -> result = $result")
            }
        }

        val allPhoneNumbers = parsePhoneNumberString(sharedPreferences.getString(PHONE_NUMBERS, ""))
        //TODO: CHECK of having CURRENT_PHONE_NUMBER is necessary
        // TODO:Instead of sharedPreferences, use userViewModel's getCurrentUserPhoneNumber method
        // Set the current phone number - This number is the source of SMS and will be used to validate user
        // sharedPreferences.edit().putString(CURRENT_PHONE_NUMBER, fetchedPhoneNumber).apply()
        println("Debug-number-view-model: A new phone number has been detected")
        println("Debug-number-view-model: fetchedPhoneNumber = $phoneNumber")
        println("Debug-number-view-model: allPhoneNumbers = $allPhoneNumbers")
    }

    /**
     * Retrieves the current phone number associated with the user.
     *
     * @return The current user's phone number.
     */
    fun getCurrentUserPhoneNumber(): String {
        return currentPhoneNumber
    }

    /**
     * Checks whether the user's phone number has changed from the previously fetched number.
     *
     * @param fetchedPhoneNumber The phone number fetched from the Android device.
     * @return `true` if the phone number has changed, `false` otherwise.
     */
    private fun hasUserPhoneNumberChanged(fetchedPhoneNumber: String): Boolean {
        return fetchedPhoneNumber != currentPhoneNumber
    }

    /**
     * Parses the comma-separated string of phone numbers (which is the encoding format in which
     * all of the user's phone numbers are stored in the sharedPreferences) into a kotlin list of
     * phone numbers (string).
     *
     * @param allPhoneNumbersString The string containing comma-separated phone numbers.
     * @return A kotlin list of parsed phone numbers (string).
     */
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
