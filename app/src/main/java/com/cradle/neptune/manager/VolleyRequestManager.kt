package com.cradle.neptune.manager

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.network.BooleanCallback
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.ListCallBack
import com.cradle.neptune.network.PatientReadingPairCallBack
import com.cradle.neptune.network.Success
import com.cradle.neptune.network.VolleyRequestQueue
import com.cradle.neptune.network.VolleyRequests
import com.cradle.neptune.network.unwrap
import com.cradle.neptune.view.LoginActivity
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A request manager for all the web requests. This should be the only interface for all requests.
 */
@Suppress("LargeClass")
class VolleyRequestManager(application: Application) {

    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var urlManager: UrlManager
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var patientManager: PatientManager
    @Inject
    lateinit var healthCentreManager: HealthCentreManager
    @Inject
    lateinit var volleyRequestQueue: VolleyRequestQueue

    private val volleyRequests: VolleyRequests

    init {
        (application as MyApp).appComponent.inject(this)

        volleyRequests =
            VolleyRequests(sharedPreferences)
    }

    companion object {
        private val TAG = VolleyRequestManager::class.java.canonicalName
    }

    /**
     *Fetches all the patients from the server for the user and saves them locally
     */
    fun fetchAllMyPatientsFromServer() {
        val request = volleyRequests.getJsonArrayRequest(urlManager.allPatientInfo, null) { result ->
            when (result) {
                is Success -> {
                    GlobalScope.launch(Dispatchers.IO) {
                        val response = result.unwrap()
                        val patientList = ArrayList<Patient>()
                        val readingList = ArrayList<Reading>()
                        for (i in 0 until response.length()) {
                            // get all the readings
                            patientList.add(Patient.unmarshal(response[i] as JsonObject))
                            val readingArray = (response[i] as JsonObject).getJSONArray("readings")
                            for (j in 0 until readingArray.length()) {
                                readingList.add(
                                    Reading.unmarshal(readingArray[j] as JsonObject)
                                        .apply { isUploadedToServer = true })
                            }
                        }
                        patientManager.addAll(patientList)
                        readingManager.addAllReadings(readingList)
                    }
                }
                is Failure -> {
                    // FIXME currently fails silently
                    Log.i(TAG, "Failed to get all the patients from the server...")
                    result.value.printStackTrace()
                }
            }
        }
        // give extra time in case too many patients.
        request.retryPolicy = volleyRequests.getRetryPolicy()
        volleyRequestQueue.addRequest(request)
    }

    /**
     * authenticate the user and save the TOKEN/ username
     * @param email email address for the user
     * @param password for the user
     * @param callBack a boolean callback to know whether request was successful or not
     */
    fun authenticateTheUser(email: String, password: String, callBack: BooleanCallback) {
        val jsonObject = JSONObject()
        jsonObject.put("email", email)
        jsonObject.put("password", password)
        val request = volleyRequests.postJsonObjectRequest(urlManager.authentication, jsonObject) { result ->
            when (result) {
                is Success -> {
                    // save the user credentials
                    val json = result.unwrap()
                    val editor = sharedPreferences.edit()
                    editor.putString(LoginActivity.TOKEN, json.getString(LoginActivity.TOKEN))
                    editor.putString(LoginActivity.USER_ID, json.getString("userId"))
                    editor.putString(LoginActivity.LOGIN_EMAIL, email)
                    editor.putInt(LoginActivity.LOGIN_PASSWORD, password.hashCode())
                    editor.apply()
                    // let the calling object know result
                    callBack(true)
                }
                is Failure -> {
                    result.value.printStackTrace()
                    callBack(false)
                }
            }
        }
        volleyRequestQueue.addRequest(request)
    }

    /**
     * Get all the healthFacilities for the user and save them locally.
     */
    fun getAllHealthFacilities() {
        val request = volleyRequests.getJsonArrayRequest(urlManager.healthFacilities, null) { result ->
            when (result) {
                is Success -> {
                    val facilities = ArrayList<HealthFacility>()
                    val response = result.unwrap()
                    for (i in 0 until response.length()) {
                        facilities.add(HealthFacility.unmarshal(response[i] as JsonObject))
                    }
                    healthCentreManager.addAll(facilities)
                }
                is Failure -> {
                    result.value.printStackTrace()
                }
            }
        }
        volleyRequestQueue.addRequest(request)
    }

    /**
     * get all the patients from the server that matches the given criteria
     * @param criteria could be id or initials
     */
    fun getAllPatientsFromServerByQuery(criteria: String, listCallBack: ListCallBack<GlobalPatient>) {
        val request = volleyRequests.getJsonArrayRequest(urlManager.getGlobalPatientSearch(criteria), null) { result ->
            when (result) {
                is Success -> {
                    val globalPatientList = ArrayList<GlobalPatient>()
                    val response = result.value
                    for (i in 0 until response.length()) {
                        val jsonObject: JSONObject = response[i] as JSONObject
                        val patientId = jsonObject.getString("patientId")
                        globalPatientList.add(
                            GlobalPatient(
                                patientId,
                                jsonObject.getString("patientName"),
                                jsonObject.getString("villageNumber"),
                                false
                            )
                        )
                    }
                    listCallBack(
                        Success(
                            globalPatientList
                        )
                    )
                }
                is Failure -> {
                    listCallBack(Failure(result.value))
                }
            }
        }
        volleyRequestQueue.addRequest(request)
    }

    /**
     * fetch a single patient by id from the server including readings
     * @param id id of the patient
     */
    fun getSinglePatientById(id: String, patientInfoCallBack: PatientReadingPairCallBack) {
        val request = volleyRequests.getJsonObjectRequest(urlManager.getPatientInfoById(id), null) { result ->
            when (result) {
                is Success -> {
                    val patientJsonObj = result.value
                    val readingArray = patientJsonObj.getJSONArray("readings")
                    val readingList = ArrayList<Reading>()
                    for (i in 0 until readingArray.length()) {
                        readingList.add(Reading.unmarshal(readingArray[i] as JsonObject))
                    }
                    patientInfoCallBack(
                        Success(
                            Pair(
                                Patient.unmarshal(patientJsonObj),
                                readingList
                            )
                        )
                    )
                }
                is Failure -> {
                    patientInfoCallBack(Failure(result.value))
                }
            }
        }
        volleyRequestQueue.addRequest(request)
    }
    /**
     * set user-patient assoication
     * @param id patient id.
     * @param callback a callback to let user know success/failure of the request.
     */
    fun setUserPatientAssociation(id: String, callback: BooleanCallback) {

        val jsonObject = JSONObject()
        jsonObject.put("patientId", id)
        val request = volleyRequests.postJsonObjectRequest(urlManager.userPatientAssociation, jsonObject) { result ->
            when (result) {
                is Success -> {
                    callback(true)
                }
                is Failure -> {
                    callback(false)
                    result.value.printStackTrace()
                }
            }
        }

        volleyRequestQueue.addRequest(request)
    }
}
