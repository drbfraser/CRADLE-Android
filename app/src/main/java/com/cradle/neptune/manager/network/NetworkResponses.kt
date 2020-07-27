package com.cradle.neptune.manager.network

import com.android.volley.VolleyError
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading



/**
 * callback interface for a patient Information
 */
interface PatientInfoCallBack {
    fun onSuccessFul(patient: Patient, reading: List<Reading>)
    fun onFail(error: VolleyError?)
}

/**
 * callback interface for a list
 */
interface ListCallBack {
    fun <T> onSuccessFul(list: List<T>)
    fun onFail(error: VolleyError?)
}

/**
 *
 */
sealed class NetworkResult<T>

data class Success<T>(val value: T) : NetworkResult<T>()

data class Failure<T>(val value: VolleyError) : NetworkResult<T>()

fun <T, U> NetworkResult<T>.map(f: (T) -> U): NetworkResult<U> = when(this) {
    is Success -> Success(f(this.value))
    is Failure -> Failure(this.value)
}

fun <T> NetworkResult<T>.unwrap(): T = when(this) {
    is Success -> this.value
    is Failure -> throw RuntimeException("unwrap of failure network result")
}

fun <T> NetworkResult<T>.unwrapFailure(): VolleyError = when(this) {
    is Success -> throw RuntimeException("unwrap failure of success network result")
    is Failure -> this.value
}
