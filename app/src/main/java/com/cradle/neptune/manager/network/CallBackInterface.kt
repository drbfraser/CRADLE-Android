package com.cradle.neptune.manager.network

import com.android.volley.VolleyError
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading

/**
 * callback interface for a list
 */
interface ListCallBack {
    fun <T> onSuccessFul(list: List<T>)
    fun onFail(error: VolleyError?)
}

/**
 * callback interface for a patient Information
 */
interface PatientInfoCallBack {
    fun onSuccessFul(patient: Patient, reading: List<Reading>)
    fun onFail(error: VolleyError?)
}