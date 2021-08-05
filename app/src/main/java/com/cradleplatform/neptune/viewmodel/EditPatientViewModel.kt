package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.BuildConfig
import com.cradleplatform.neptune.ext.setValueOnMainThread
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.GestationalAge
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.utilities.LiveDataDynamicModelBuilder
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.threeten.bp.ZonedDateTime
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject

private val DEBUG = BuildConfig.DEBUG

/**
 * ViewModel for [EditPatientInfoActivity]
 */
@HiltViewModel
class EditPatientViewModel @Inject constructor(
    private val patientManager: PatientManager,
    private val sharedPreferences: SharedPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "EditPatientViewModel"
    }

    private val patientBuilder = LiveDataDynamicModelBuilder()

    val isConnectedToInternet = NetworkAvailableLiveData(context)

    fun initialize(patientId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val patient = patientManager.getPatientById(patientId)
                ?: error("no patient with given id")
            patientBuilder.decompose(patient)
            Log.d("Weird-debug", patient.toString())
        }
    }

    /** Patient Info */
    /** Used in two-way Data Binding with EditPatientInfoActivity */
    val patientId: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::id)

    val patientName: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::name)

    // TODO: DO AGE THINGSSSS
    val patientDob: MediatorLiveData<String?> = patientBuilder.get<String?>(Patient::dob)
    val patientAge = MutableLiveData<Int?>(null)

    private val _patientIsExactDob: MediatorLiveData<Boolean?> =
        patientBuilder.get(Patient::isExactDob, defaultValue = false)
    val patientIsExactDob: LiveData<Boolean?> = _patientIsExactDob

    val patientSex: MutableLiveData<Sex?>
        get() = patientBuilder.get<Sex?>(Patient::sex)

    val patientZone: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::zone)

    val patientHouseholdNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::householdNumber)

    val patientVillageNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::villageNumber)

    val patientAllergies: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::allergy)

    val patientLastEdited: MutableLiveData<Long?>
        get() = patientBuilder.get<Long?>(Patient::lastEdited)

    /**
     * Implicitly used in two-way Data Binding with .
     * This listens to [patientGestationalAgeInput] and [patientGestationalAgeUnits].
     *
     * @see LiveDataInitializationManager.setupGestationAgeLiveData
     */
    val patientGestationalAge: MediatorLiveData<GestationalAge?> =
        patientBuilder.get<GestationalAge?>(Patient::gestationalAge)

    val patientGestationalAgeInput: MutableLiveData<String> = MediatorLiveData<String>()

    val patientGestationalAgeUnits: MutableLiveData<String> = MediatorLiveData<String>()

    val patientIsPregnant: MutableLiveData<Boolean?>
        get() = patientBuilder.get<Boolean?>(Patient::isPregnant)


    fun save(): Boolean {
        viewModelScope.launch(Dispatchers.Main) {
            val patient = constructValidPatientFromBuilders() ?: run {
                return@run false
            }
            yield()
            patientManager.add(patient as Patient)
            Log.d("Weird-debug", patient.toString())
        }
        return true
    }

    private suspend fun constructValidPatientFromBuilders(): Patient? {
        return withContext(Dispatchers.Default) {
            val patientAsync = async {
                patientLastEdited.setValueOnMainThread(ZonedDateTime.now().toEpochSecond())
                attemptToBuildValidPatient()
            }
            val patient = patientAsync.await()
            patient
        }
    }

    private fun attemptToBuildValidPatient(): Patient? {
        if (!patientBuilder.isConstructable<Patient>()) {
            if (DEBUG) Log.d(
                TAG,
                "attemptToBuildValidPatient: patient not constructable, " +
                    "missing ${patientBuilder.missingParameters(Patient::class)}"
            )
            return null
        }

        val patient = try {
            patientBuilder.build<Patient>()
        } catch (e: IllegalArgumentException) {
            if (DEBUG) Log.d(
                TAG,
                "attemptToBuildValidPatient: " +
                    "patient failed to build, exception ${e.cause?.message ?: e.message}"
            )
            return null
        } catch (e: InvocationTargetException) {
            if (DEBUG) Log.d(
                TAG,
                "attemptToBuildValidPatient: " +
                    "patient failed to build, exception ${e.cause?.message ?: e.message}"
            )
            return null
        }

        if (!patient.isValidInstance()) {
            if (DEBUG) Log.d(
                TAG,
                "attemptToBuildValidPatient: " +
                    "patient is invalid, " +
                    "errors: ${patient.getAllMembersWithInvalidValues(context)}"
            )
            return null
        }

        if (DEBUG) Log.d(TAG, "attemptToBuildValidPatient: built a valid patient")
        return patient
    }

}
