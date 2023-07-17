package com.cradleplatform.neptune.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context.TELEPHONY_SERVICE
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    application: Application
) : AndroidViewModel(application) {

    private lateinit var previousPhoneNumber: String

    fun setPreviousPhoneNumber(phoneNumber: String) {
        previousPhoneNumber = phoneNumber
    }

    companion object {
        const val userPhoneNumberKey = "user_phone_number"
    }

    fun updateUserPhoneNumber() {
        if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_PHONE_NUMBERS) ==
            PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val telManager = getApplication<Application>().getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val newPhoneNumber = telManager.line1Number

            if(hasUserPhoneNumberChanged(newPhoneNumber)) {
                // Store the new phone number in SharedPreferences
                sharedPreferences.edit().putString(userPhoneNumberKey, newPhoneNumber).apply()

                // TODO: update the user's phone number in the database
            }
        }
        // else: either the phone number doesn't exist
            // or permission is not granted - userPhoneNumber would remain equal to ""
    }

    private fun hasUserPhoneNumberChanged(newPhoneNumber: String): Boolean {
        return newPhoneNumber != previousPhoneNumber
    }
}