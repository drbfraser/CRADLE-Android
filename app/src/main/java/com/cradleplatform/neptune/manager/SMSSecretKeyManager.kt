package com.cradleplatform.neptune.manager

import android.content.SharedPreferences
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SMSSecretKeyManager @Inject constructor(
    private val restApi: RestApi,
    private val sharedPreferences: SharedPreferences
){
    companion object{
        const val SMS_SECRET_KEY = "SMSSecretKey"
        const val NORMAL = "NORMAL"
        const val EXPIRED = "EXPIRED"
        const val WARN = "WARN"
        const val NOTFOUND = "NOTFOUND"
    }


    /**
     * fetching sms secret key based on provided userId.
     *
     * @param userID userid currently log in
     * @return the secret's status or null
     */
    suspend fun fetchSMSSecretKey(userID: Int): String? {
        val result = restApi.getSecretKey(userID)
        if (result is NetworkResult.Success) {
            return if (result.value.message == NORMAL) {
                val smsKey = result.value.smsKey
                sharedPreferences.edit().putString(SMS_SECRET_KEY, smsKey).apply()
                NORMAL
            } else {
                result.value.message
            }
        }
        return  null;
    }

    /**
     * updating sms secret key based on provided userId if stale_date or expiry_date passes.
     *
     * @param userID userid currently log in
     * @return the secret's status or null
     */
    private suspend fun updateSMSSecretKey(userID: Int): String? {
        val result = restApi.updateSecretKey(userID)
        if (result is NetworkResult.Success) {
            val smsKey = result.value.smsKey
            sharedPreferences.edit().putString(SMS_SECRET_KEY, smsKey).apply()
            return result.value.message;
        }
        return  null;
    }

    /**
     * posting sms secret key based on provided userId if the suer does not have a sms secret key.
     *
     * @param userID userid currently log in
     * @return the secret's status or null
     */
    private suspend fun postSMSSecretKey(userID: Int): String?{
        val result = restApi.postSecretKey(userID)
        if (result is NetworkResult.Success) {
            val smsKey = result.value.smsKey
            sharedPreferences.edit().putString(SMS_SECRET_KEY, smsKey).apply()
            return result.value.message;
        }
        return  null;
    }

    /**
     * evaluating the message, then based on that, execute a specific action
     *
     * @param userID userid currently log in
     * @param message the status received from server
     */
    suspend fun evaluateKey(userID: Int,message: String?): Boolean{
        if (message == WARN || message == EXPIRED){
            updateSMSSecretKey(userID)
            return  true;
        }
        if(message == NOTFOUND){
            postSMSSecretKey(userID)
            return true;
        }
        return false;
    }

}