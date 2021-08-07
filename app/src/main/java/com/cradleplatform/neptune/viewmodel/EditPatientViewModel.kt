package com.cradleplatform.neptune.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
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
import com.cradleplatform.neptune.model.Verifiable
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.utilities.LiveDataDynamicModelBuilder
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.threeten.bp.ZonedDateTime
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject
import kotlin.reflect.KProperty

private val DEBUG = BuildConfig.DEBUG

/**
 * ViewModel for [EditPatientInfoActivity]
 */
@HiltViewModel
class EditPatientViewModel @Inject constructor(
    private val patientManager: PatientManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "EditPatientViewModel"
    }

    private val patientBuilder = LiveDataDynamicModelBuilder()
    val isNetworkAvailable = NetworkAvailableLiveData(context)

    fun initialize(patientId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            allowEdit(false)

            val patient = patientManager.getPatientById(patientId)
                ?: error("no patient with given id")

            patientBuilder.decompose(patient)
            setUpAgeLiveData(patient.dob)

            // Disable editing gender if patient is pregnant
            isPatientSexEditable.value = !patient.isPregnant

            allowEdit(true)
            Log.d(TAG, patient.toString())
        }
    }

    val isInputEnabled = MediatorLiveData<Boolean>()
    val isPatientSexEditable = MediatorLiveData<Boolean>()
    var loadingStatus = MutableLiveData<String>().apply { value = "Loading" }

    /** Patient Info */
    /** Used in two-way Data Binding with EditPatientInfoActivity */
    val patientId: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::id)

    val patientName: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::name)

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

    private fun allowEdit(isEnabled: Boolean) {
        isInputEnabled.value = isEnabled
    }

    /**
     * Sets the new age input state. If called with [useDateOfBirth] false, then the date of birth
     * will be cleared (nulled) out.
     */
    @MainThread
    fun setUsingDateOfBirth(useDateOfBirth: Boolean) {
        if (!useDateOfBirth) {
            // Lint is acting like patientDob's data type is is non-null, but it is nullable.
            @SuppressLint("NullSafeMutableLiveData")
            patientDob.value = null
            patientAge.value = null
        }
        _patientIsExactDob.value = useDateOfBirth
    }

    private fun setUpAgeLiveData(dob: String?) {

        if (dob != null) {
            patientAge.value = Patient.calculateAgeFromDateString(dateString = dob)
        }

        patientDob.apply {
            addSource(patientAge) {
                // If we're using exact age, do not attempt to construct from approximate age
                // input
                if (it == null || _patientIsExactDob.value == true) return@addSource

                // Otherwise, make a date of birth that corresponds to the age entered.
                val newDateString = DateUtil.getDateStringFromAge(it.toLong())
                if (value != newDateString) {
                    value = newDateString
                }
            }
        }
    }

    suspend fun save(): SaveResult {
        loadingStatus = MutableLiveData<String>().apply { value = "Saving" }

        allowEdit(false)

        return withContext(Dispatchers.Main) {

            val patientAsync = async {
                constructValidPatientFromBuilders()
            }
            val patient = patientAsync.await()

            if (patient == null) {
                allowEdit(true)
                SaveResult.Error
            } else {
                saveAndUploadPatient(patient)
            }
        }
    }

    private suspend fun saveAndUploadPatient(patient: Patient): SaveResult {
        return if (isNetworkAvailable.value == true) {
            when (patientManager.updatePatientOnServer(patient)) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "SAVE FN: Sent to server and saved")
                    SaveResult.SavedAndUploaded
                }
                else -> {
                    Log.d(TAG, "SAVE FN: Tried to send to server, but didn't work")
                    SaveResult.SavedOffline
                }
            }
        } else {
            Log.d(TAG, "SAVE FN: No network connection, saved patient")
            patientManager.add(patient)
            SaveResult.SavedOffline
        }
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

    private val errorMessageManager = ErrorMessageManager()
    val errorMap: LiveData<ArrayMap<String, String?>> = errorMessageManager.errorMap

    /**
     * An inner class that manages the [errorMap] which contain error messages that are instantly
     * shown to the user as they type invalid input. It calculates the error messages on a
     * background thread ([Dispatchers.Default]), and then it modifies the [errorMap] accordingly.
     */
    private inner class ErrorMessageManager {
        /**
         * Required to guard against modifications to [errorMap], since changing the [errorMap] is
         * not done on **the** (singular) main thread.
         */
        private val errorMapMutex = Mutex()

        /**
         * A map of KProperty.name to error messages. If an error message is null, that means the
         * field is valid. The values are set from the function
         * [testValueForValidityAndSetErrorMapAsync]
         *
         * Modifications to the [errorMap] value must be done when [errorMapMutex] is held.
         *
         * @see [testValueForValidityAndSetErrorMapAsync]
         */
        @GuardedBy("errorMapMutex")
        val errorMap = MediatorLiveData<ArrayMap<String, String?>>()

        init {
            // The reason we can't do this in Data Binding itself with some update callback function
            // is that Data Binding doesn't support KProperty references like Patient::id.
            errorMap.apply {
                value = arrayMapOf()
                addSource(patientName) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = Patient::name,
                        verifier = Patient.Companion
                    )
                }
                addSource(patientVillageNumber) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = Patient::villageNumber,
                        verifier = Patient.Companion
                    )
                }
                addSource(patientDob) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = Patient::dob,
                        verifier = Patient.Companion
                    )
                }
                addSource(patientGestationalAge) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = Patient::gestationalAge,
                        verifier = Patient.Companion
                    )
                }
                addSource(patientSex) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = Patient::sex,
                        verifier = Patient.Companion,
                        currentValuesMap = patientBuilder.publicMap
                    )
                }
            }
        }

        /**
         * Asynchronously tests the validity of the [value] for the [propertyToCheck] that belongs
         * to the class that [verifier] verifies. If not valid, an error message will be added to
         * the [errorMap], which is map of error messages for a property that uses the name of
         * [propertyForErrorMapKey] for the key. If valid, the error message for the property will
         * be removed (so getting the error returns null).
         *
         * By default, [propertyForErrorMapKey] is just [propertyToCheck], but this can be
         * overridden for cases where we might want to share a single error key-value pair between
         * two properties, like date of birth and age since they use the same EditText to display
         * the errors.
         *
         * The error messages are observed via the immutable LiveData [errorMap] in the XML layout
         * files under the errorMessage attribute, and they update in real time.
         *
         * @see errorMap
         * @see com.cradleplatform.neptune.binding.ReadingBindingAdapters.setError
         */
        private fun <T> testValueForValidityAndSetErrorMapAsync(
            value: T?,
            propertyToCheck: KProperty<T>,
            verifier: Verifiable.Verifier<*>,
            propertyForErrorMapKey: KProperty<*> = propertyToCheck,
            currentValuesMap: Map<String, Any?>? = null
        ) {
            viewModelScope.launch(Dispatchers.Default) {
                // Selects the map to use for all the other fields on the form. The validity of a
                // property might depend on other properties being there, e.g. a null date of birth
                // is acceptable if there is an estimated age.
                val currentValuesMapToUse = currentValuesMap
                    ?: when (verifier) {
                        is Patient.Companion -> {
                            patientBuilder.publicMap
                        }
                        else -> {
                            null
                        }
                    }

                // Get the actual validity status and any error message.
                val verificationResult = verifier.isValueValid(
                    property = propertyToCheck,
                    value = value,
                    context = context,
                    instance = null,
                    currentValues = currentValuesMapToUse
                )

                val errorMessageForMap = if (verificationResult is Verifiable.Invalid) {
                    verificationResult.errorMessage
                } else {
                    null
                }

                errorMapMutex.withLock {
                    val currentMap = errorMap.value ?: arrayMapOf()

                    // Don't notify observers if the error message is the exact same message.
                    if (currentMap[propertyForErrorMapKey.name] != errorMessageForMap) {
                        if (verificationResult is Verifiable.Valid) {
                            currentMap.remove(propertyForErrorMapKey.name)
                        } else {
                            currentMap[propertyForErrorMapKey.name] = errorMessageForMap
                        }
                        errorMap.setValueOnMainThread(currentMap)
                    }
                }
            }
        }
    }

    interface SaveResult {
        object SavedAndUploaded : SaveResult
        object SavedOffline : SaveResult
        object Error : SaveResult
    }
}
