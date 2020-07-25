package com.cradle.neptune.manager.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.volley.Response
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.manager.network.VolleyRequests.*
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.view.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.ArrayList
import javax.inject.Inject

class VolleyRequestManager(private val context: Context) {

    private val volleyRequestQueue = VolleyRequestQueue.getInstance(context)
    private val volleyRequests:VolleyRequests
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var urlManager: UrlManager
    @Inject
    lateinit var readingManager:ReadingManager
    @Inject
    lateinit var patientManager:PatientManager

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)

        volleyRequests = VolleyRequests(sharedPreferences)
    }
    companion object {
        private val TAG = VolleyRequestManager::class.java.canonicalName
    }

    /**
     *Fetches all the patients from the server for the user.
     */
    fun fetchAllMyPatientsFromServer(){

        val request = volleyRequests.getJsonArrayRequest(urlManager.allPatientInfo,null,
        Response.Listener {response ->
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
            Log.i(TAG,"Failed to get all the patients from the server...")
                it.printStackTrace()
                //todo figure out how to let user know better? toast?
            })

        volleyRequestQueue?.addRequest(request)
    }

    /**
     * authonticate the user and save the TOKEN/ username
     */
    fun authenticateTheUser(userName:String, password:String, SuccessFullCallBack: SuccessFullCallBack) {
        val jsonObject = JSONObject()
        jsonObject.put("email", userName)
        jsonObject.put("password", password)
        val request = volleyRequests.putJsonObjectRequest(urlManager.authentication,jsonObject,
        Response.Listener {
            //save the user credentials
            val editor = sharedPreferences.edit()
            editor.putString(LoginActivity.TOKEN, it.getString(LoginActivity.TOKEN))
            editor.putString(LoginActivity.USER_ID, it.getString("userId"))
            editor.putString(LoginActivity.LOGIN_EMAIL, userName)
            editor.putInt(LoginActivity.LOGIN_PASSWORD, password.hashCode())
            editor.apply()
            //let the calling object know result
            SuccessFullCallBack.isSuccessFull(true)
        }, Response.ErrorListener {
                it.printStackTrace()
                SuccessFullCallBack.isSuccessFull(false)
            })
        volleyRequestQueue?.addRequest(request);

    }
}