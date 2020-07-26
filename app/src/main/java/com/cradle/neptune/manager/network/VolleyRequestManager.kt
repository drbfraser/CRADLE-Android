package com.cradle.neptune.manager.network

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.volley.Response
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.manager.network.VolleyRequests.ListCallBack
import com.cradle.neptune.manager.network.VolleyRequests.PatientInfoCallBack
import com.cradle.neptune.manager.network.VolleyRequests.SuccessFullCallBack
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
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

        volleyRequests = VolleyRequests(sharedPreferences)
    }

    companion object {
        private val TAG = VolleyRequestManager::class.java.canonicalName
    }

    /**
     *Fetches all the patients from the server for the user and saves them locally
     */
    fun fetchAllMyPatientsFromServer() {

        val request = volleyRequests.getJsonArrayRequest(urlManager.allPatientInfo, null,
        Response.Listener { response ->
            GlobalScope.launch(Dispatchers.IO) {
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
        }, Response.ErrorListener {
            Log.i(TAG, "Failed to get all the patients from the server...")
                it.printStackTrace()
                // todo figure out how to let user know better? toast?
            })
        // give extra time in case too many patients.
        request.retryPolicy = volleyRequests.getRetryPolicy()

        volleyRequestQueue.addRequest(request)
    }

    /**
     * authenticate the user and save the TOKEN/ username
     * @param email email address for the user
     * @param password for the user
     * @param successFullCallBack a boolean callback to know whether request was successful or not
     */
    fun authenticateTheUser(email: String, password: String, successFullCallBack: SuccessFullCallBack) {
        val jsonObject = JSONObject()
        jsonObject.put("email", email)
        jsonObject.put("password", password)
        val request = volleyRequests.postJsonObjectRequest(urlManager.authentication, jsonObject,
        Response.Listener {
            // save the user credentials
            val editor = sharedPreferences.edit()
            editor.putString(LoginActivity.TOKEN, it.getString(LoginActivity.TOKEN))
            editor.putString(LoginActivity.USER_ID, it.getString("userId"))
            editor.putString(LoginActivity.LOGIN_EMAIL, email)
            editor.putInt(LoginActivity.LOGIN_PASSWORD, password.hashCode())
            editor.apply()
            // let the calling object know result
            successFullCallBack.isSuccessFull(true)
        }, Response.ErrorListener {
                it.printStackTrace()
                successFullCallBack.isSuccessFull(false)
            })
        volleyRequestQueue.addRequest(request)
    }

    /**
     * Get all the healthFacilities for the user and save them locally.
     */
    fun getAllHealthFacilities() {
        val requests = volleyRequests.getJsonArrayRequest(urlManager.healthFacilities,
        null, Response.Listener {
                val facilities = ArrayList<HealthFacility>()
                for (i in 0 until it.length()) {
                    facilities.add(HealthFacility.unmarshal(it[i] as JsonObject))
                }
                healthCentreManager.addAll(facilities)
            }, Response.ErrorListener {
                it.printStackTrace()
            })

        volleyRequestQueue.addRequest(requests)
    }

    /**
     * get all the patients from the server that matches the given criteria
     * @param criteria could be id or initials
     */
    fun getAllPatientsFromServerByQuery(criteria: String, listCallBack: ListCallBack) {
            val request = volleyRequests.getJsonArrayRequest(urlManager.getGlobalPatientSearch(criteria),
            null, Response.Listener { response ->
                    val globalPatientList = ArrayList<GlobalPatient>()

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
                    listCallBack.onSuccessFul(globalPatientList)
                }, Response.ErrorListener {
                    listCallBack.onFail(it)
                })
        volleyRequestQueue.addRequest(request)
    }

    /**
     * fetch a single patient by id from the server
     * @param id id of the patient
     */
    fun getSinglePatientById(id: String, patientInfoCallBack: PatientInfoCallBack) {
        val request = volleyRequests.getJsonObjectRequest(urlManager.getPatientInfoById(id),
        null, Response.Listener {
                val readingArray = it.getJSONArray("readings")
                val readingList = ArrayList<Reading>()
                for (i in 0 until readingArray.length()) {
                    readingList.add(Reading.unmarshal(readingArray[i] as JsonObject))
                }
                patientInfoCallBack.onSuccessFul(Patient.unmarshal(it), readingList)
            }, Response.ErrorListener {
                patientInfoCallBack.onFail(it)
            })
        volleyRequestQueue.addRequest(request)
    }
    /**
     * set user-patient assoication
     * @param id patient id.
     */
    fun setUserPatientAssociation(id: String, successFullCallBack: SuccessFullCallBack) {

        val jsonObject = JSONObject()
        jsonObject.put("patientId", id)

        val request = volleyRequests.postJsonObjectRequest(urlManager.userPatientAssociation, jsonObject,
        Response.Listener {
            successFullCallBack.isSuccessFull(true)
        }, Response.ErrorListener {
                successFullCallBack.isSuccessFull(false)
                it.printStackTrace()
            })

        volleyRequestQueue.addRequest(request)
    }
}
