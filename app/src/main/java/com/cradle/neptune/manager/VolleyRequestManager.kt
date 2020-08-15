package com.cradle.neptune.manager

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.model.Assessment
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.NetworkResult
import com.cradle.neptune.network.Success
import com.cradle.neptune.network.VolleyRequestQueue
import com.cradle.neptune.network.VolleyRequests
import com.cradle.neptune.network.unwrap
import com.cradle.neptune.sync.SyncStepperImplementation
import com.cradle.neptune.view.LoginActivity
import java.util.ArrayList
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.threeten.bp.ZonedDateTime

/**
 * typaliases for all the callbacks to make them more readable and shorter
 */
typealias BooleanCallback = (isSuccessful: Boolean) -> Unit

typealias ListCallBack<T> = (NetworkResult<List<T>>) -> Unit

typealias PatientReadingPairCallBack = (NetworkResult<Pair<Patient, List<Reading>>>) -> Unit

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
        val request =
            volleyRequests.getJsonArrayRequest(urlManager.allPatientInfo, null) { result ->
                when (result) {
                    is Success -> {
                        GlobalScope.launch(Dispatchers.IO) {
                            val response = result.unwrap()
                            val patientList = ArrayList<Patient>()
                            val readingList = ArrayList<Reading>()
                            for (i in 0 until response.length()) {
                                val patient = Patient.unmarshal(response[i] as JsonObject)
                                // we need to set base so we know it is a patient from the server
                                patient.base = patient.lastEdited
                                patientList.add(patient)
                                // get all the readings
                                val readingArray =
                                    (response[i] as JsonObject).getJSONArray("readings")
                                for (j in 0 until readingArray.length()) {
                                    readingList.add(
                                        Reading.unmarshal(readingArray[j] as JsonObject)
                                            .apply { isUploadedToServer = true })
                                }
                            }
                            patientManager.addAll(patientList)
                            readingManager.addAllReadings(readingList)
                            sharedPreferences.edit()
                                .putLong(
                                    SyncStepperImplementation.LAST_SYNC,
                                    ZonedDateTime.now().toEpochSecond()
                                ).apply()
                            Date().time
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
        val request =
            volleyRequests.postJsonObjectRequest(urlManager.authentication, jsonObject) { result ->
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
        val request =
            volleyRequests.getJsonArrayRequest(urlManager.healthFacilities, null) { result ->
                when (result) {
                    is Success -> {
                        val facilities = ArrayList<HealthFacility>()
                        val response = result.unwrap()
                        for (i in 0 until response.length()) {
                            facilities.add(HealthFacility.unmarshal(response[i] as JsonObject))
                        }
                        // one default HF
                        if (facilities.isNotEmpty()) {
                            facilities[0].isUserSelected = true
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
    fun getAllPatientsFromServerByQuery(
        criteria: String,
        listCallBack: ListCallBack<GlobalPatient>
    ) {
        val request = volleyRequests.getJsonArrayRequest(
            urlManager.getGlobalPatientSearch(criteria),
            null
        ) { result ->
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
    fun getFullPatientById(id: String, patientInfoCallBack: PatientReadingPairCallBack) {
        val request = volleyRequests.getJsonObjectRequest(
            urlManager.getPatientFullInfoById(id),
            null
        ) { result ->
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

    fun getPatientOnlyInfo(id: String, callback: (NetworkResult<JSONObject>) -> Unit) {
        val request = volleyRequests.getJsonObjectRequest(urlManager.getPatientInfoOnly(id), null) {
            when (it) {

                is Success -> {
                    patientManager.add(Patient.unmarshal(it.value))
                }
            }
            callback(it)
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
        val request = volleyRequests.postJsonObjectRequest(
            urlManager.userPatientAssociation,
            jsonObject
        ) { result ->
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

    /**
     * send a reading to the server and propogates its result down to the client
     * @param reading readings to upload
     * @param callback callback for the caller
     */
    fun uploadReadingToTheServer(reading: Reading, callback: (NetworkResult<JSONObject>) -> Unit) {

        val request =
            volleyRequests.postJsonObjectRequest(urlManager.readings, reading.marshal()) { result ->
                when (result) {
                    is Success -> {
                        reading.isUploadedToServer = true
                        readingManager.addReading(reading)
                        callback(Success(result.value))
                    }
                    is Failure -> {
                        callback(Failure(result.value))
                    }
                }
            }
        volleyRequestQueue.addRequest(request)
    }

    /**
     * send a reading to the server and propogates its result down to the client
     * @param patient patient to upload
     * @param readings optional list of readings to put inside the patient
     * @param callback callback for the caller
     */
    fun uploadPatientToTheServer(
        patientAndReadings: PatientAndReadings,
        callback: (NetworkResult<JSONObject>) -> Unit
    ) {

        val request =
            volleyRequests.postJsonObjectRequest(
                urlManager.patients,
                patientAndReadings.marshal()
            ) { result ->
                when (result) {
                    is Success -> {
                        // need to update the base to lastEdited since server compares patient's base
                        // with server's last edited field. If they dont match, server does not accepts
                        // the edited value.
                        patientAndReadings.patient.base = patientAndReadings.patient.lastEdited
                        patientManager.add(patientAndReadings.patient)
                        patientAndReadings.readings.forEach {
                            it.isUploadedToServer = true
                        }
                        readingManager.addAllReadings(patientAndReadings.readings)
                        callback(Success(result.value))
                    }
                    is Failure -> {
                        callback(Failure(result.value))
                    }
                }
            }
        volleyRequestQueue.addRequest(request)
    }

    fun getUpdates(currTime: Long, callback: (NetworkResult<JSONObject>) -> Unit) {
        val request =
            volleyRequests.getJsonObjectRequest(urlManager.getUpdates(currTime), null) { result ->
                when (result) {
                    is Success -> {
                        callback(Success(result.value))
                    }
                    is Failure -> {
                        callback(Failure(result.value))
                    }
                }
            }
        volleyRequestQueue.addRequest(request)
    }

    fun getReadingById(id: String, callback: (NetworkResult<JSONObject>) -> Unit) {
        val request =
            volleyRequests.getJsonObjectRequest(urlManager.getReadingById(id), null) { result ->
                when (result) {
                    is Success -> {
                        val reading =
                            Reading.unmarshal(result.value).apply { isUploadedToServer = true }
                        readingManager.addReading(reading)
                    }
                }
                callback(result)
            }
        volleyRequestQueue.addRequest(request)
    }

    /**
     * gets a  [FollowUp] from the server and update the reading
     */
    fun updateFollowUpById(id: String, callback: (NetworkResult<JSONObject>) -> Unit) {

        val request =
            volleyRequests.getJsonObjectRequest(urlManager.getAssessmentById(id), null) { result ->
                when (result) {
                    is Success -> {
                        GlobalScope.launch(Dispatchers.IO) {
                            val assessment = Assessment.unmarshal(result.value)
                            val reading =
                                readingManager.getReadingById(assessment.readingId)
                            reading?.followUp = assessment
                            reading?.let { readingManager.addReading(it) }
                        }
                    }
                }
                callback(result)
            }
        volleyRequestQueue.addRequest(request)
    }
}
