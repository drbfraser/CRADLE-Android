package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.BuildConfig
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.setValueOnMainThread
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.GestationalAge
import com.cradleplatform.neptune.model.GestationalAgeMonths
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Verifiable
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.utilities.LiveDataDynamicModelBuilder
import com.cradleplatform.neptune.utilities.Months
import com.cradleplatform.neptune.utilities.Seconds
import com.cradleplatform.neptune.utilities.Weeks
import com.cradleplatform.neptune.utilities.WeeksAndDays
import com.cradleplatform.neptune.utilities.connectivity.legacy.NetworkAvailableLiveData
import com.cradleplatform.neptune.viewmodel.EditPatientViewModel.SaveResult
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import java.math.BigInteger
import javax.inject.Inject
import kotlin.reflect.KProperty

private val DEBUG = BuildConfig.DEBUG

// The index of the gestational age units inside of the string.xml array, R.array.reading_ga_units
private const val GEST_AGE_UNIT_WEEKS_INDEX = 0
private const val GEST_AGE_UNIT_MONTHS_INDEX = 1

/**
 * ViewModel for [EditPregnancyActivity]
 */
@HiltViewModel
class EditPregnancyViewModel @Inject constructor(
    private val patientManager: PatientManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "EditPregnancyViewModel"
        private const val GESTATIONAL_AGE_WEEKS_MAX = 43
        private const val MILLIS_IN_SEC = 1000
    }

    private val patientBuilder = LiveDataDynamicModelBuilder()
    val isNetworkAvailable = NetworkAvailableLiveData(context)

    private var isAddPregnancy = true

    val isInputEnabled = MediatorLiveData<Boolean>()
    val patientName = MutableLiveData<String>()

    private val monthsUnitString = context.resources
        .getStringArray(R.array.reading_ga_units)[GEST_AGE_UNIT_MONTHS_INDEX]
    private val weeksUnitString = context.resources
        .getStringArray(R.array.reading_ga_units)[GEST_AGE_UNIT_WEEKS_INDEX]

    /** Values for Add pregnancy */
    val patientGestationalAge: MediatorLiveData<GestationalAge?> =
        patientBuilder.get<GestationalAge?>(Patient::gestationalAge)
    val patientGestationalAgeInput: MutableLiveData<String> = MediatorLiveData<String>()
    val patientGestationalAgeUnits: MutableLiveData<String> = MediatorLiveData<String>()

    /** Values for End pregnancy */
    var pregnancyEndTimestamp: Long? = null
    // startTimestamp is taken from db saved patient, should not change throughout current lifecycle
    val pregnancyStartTimestamp: MutableLiveData<BigInteger> = MediatorLiveData()
    val calculatedGestationalAge: MutableLiveData<String> = MediatorLiveData<String>()
    val pregnancyOutcome: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::prevPregnancyOutcome)
    val endDateError: MutableLiveData<String> = MutableLiveData<String>()

    fun initialize(patientId: String, isPregnant: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            allowEdit(false)

            val patient = patientManager.getPatientById(patientId)
                ?: error("no patient with given id")

            isAddPregnancy = !isPregnant

            if (!isPregnant) {
                patient.isPregnant = true
                setupGestationAgeLiveData()
            } else if (patient.gestationalAge != null) {
                pregnancyStartTimestamp.value = (patient.gestationalAge?.timestamp ?: return@launch)
                gestationalAgeFromEndDate((MaterialDatePicker.todayInUtcMilliseconds() / MILLIS_IN_SEC).toBigInteger())
            }

            patientBuilder.decompose(patient)

            patientName.value = patient.name

            allowEdit(true)
            Log.d(TAG, patient.toString())
        }
    }

    private fun allowEdit(isEnabled: Boolean) {
        isInputEnabled.value = isEnabled
    }

    fun gestationalAgeFromEndDate(endDate: BigInteger) {
        val startDate = pregnancyStartTimestamp.value
        if (startDate == null) {
            calculatedGestationalAge.value = "Error"
            return
        }
        calculatedGestationalAge.value =
            "%.2f".format(WeeksAndDays.fromSeconds(Seconds(endDate - startDate)).asWeeks())
    }

    private suspend fun setupGestationAgeLiveData() = withContext(Dispatchers.Main) {

        // Populate the gestational age units input field.
        patientGestationalAgeUnits.apply {
            value = if (patientGestationalAge.value is GestationalAgeMonths) {
                monthsUnitString
            } else {
                // This also makes the default units in weeks.
                weeksUnitString
            }
        }

        // Listen for changes from the input fields for gestational age
        patientGestationalAge.apply {
            addSource(patientGestationalAgeUnits) {
                // If we received an update to the units used, convert the GestationalAge object
                // into the right type.
                it ?: return@addSource
                val currentValue = value ?: return@addSource

                value = if (it == monthsUnitString && currentValue !is GestationalAgeMonths) {
                    GestationalAgeMonths((value as GestationalAge).timestamp)
                } else if (it == weeksUnitString && currentValue !is GestationalAgeWeeks) {
                    GestationalAgeWeeks((value as GestationalAge).timestamp)
                } else {
                    Log.d(TAG, "DEBUG: patientGestationalAge units source: didn't do anything")
                    return@addSource
                }

                Log.d(TAG, "DEBUG: patientGestationalAge units source: clearing gest age input")
                // Zero out the input when changing units.
                patientGestationalAgeInput.value = "0"
            }
            addSource(patientGestationalAgeInput) {
                // If the user typed something in, create a new GestationalAge object with the
                // specified values.
                if (it.isNullOrBlank()) {
                    value = null
                    return@addSource
                }

                val currentUnitsString = patientGestationalAgeUnits.value
                value = if (currentUnitsString == monthsUnitString) {
                    val userInput: Double = it.toDoubleOrNull() ?: 0.0
                    GestationalAgeMonths(Months(userInput))
                } else {
                    val userInput: Long = it.toLongOrNull() ?: 0L
                    GestationalAgeWeeks(Weeks(userInput))
                }
            }
        }
    }

    suspend fun saveAndUploadPregnancy(): SaveResult {

        allowEdit(false)
        return withContext(Dispatchers.Main) {

            Log.d(TAG, pregnancyOutcome.value.toString())

            if (! isAddPregnancy) {
                patientBuilder.set(Patient::prevPregnancyEndDate, pregnancyEndTimestamp)
                // pregnancyOutcome is already in the patientBuilder
                patientBuilder.set(Patient::isPregnant, false)
                patientBuilder.set(Patient::gestationalAge, null)
            }

            val patientAsync = async {
                attemptToBuildValidPatient()
            }
            val patient = patientAsync.await()

            when {
                patient == null -> {
                    allowEdit(true)
                    SaveResult.Error
                }
                isAddPregnancy -> {
                    addPregnancy(patient)
                }
                pregnancyEndTimestamp != null && isEndPregnancyValid(patient) -> {
                    closePregnancy(patient)
                }
                else -> {
                    allowEdit(true)
                    SaveResult.Error
                }
            }
        }
    }

    private suspend fun addPregnancy(patient: Patient): SaveResult {
        if (isNetworkAvailable.value == true) {
            // if there is a pregnancy end date (and there is internet connection) then you have to
            // make sure that the previous pregnancy information is sent to the server before adding
            // this one. If any errors occur, make the user sync before continuing
            if (patient.prevPregnancyEndDate != null && patient.pregnancyId != null) {
                if (closePregnancy(patient) != SaveResult.SavedAndUploaded) {
                    // something is not good - make patient sync and don't save changes
                    allowEdit(true)
                    return SaveResult.ServerReject
                }
            }

            return when (val result = patientManager.addPregnancyOnServerSaveOnSuccess(patient)) {
                is NetworkResult.Success -> {
                    SaveResult.SavedAndUploaded
                }
                is NetworkResult.Failure -> {
                    Log.d(TAG, String(result.body))
                    allowEdit(true)
                    SaveResult.ServerReject
                }
                else -> {
                    allowEdit(true)
                    SaveResult.ServerReject
                }
            }
        } else {
            patientManager.add(patient)
            return SaveResult.SavedOffline
        }
    }

    private suspend fun closePregnancy(patient: Patient): SaveResult {

        // If there's no pregnancyId we won't be able to update the server pregnancy with end date
        // Also if this doesn't exist, there's a bigger issue than the user can fix - direct them to sync
        if (patient.pregnancyId == null) {
            allowEdit(true)
            return SaveResult.ServerReject
        }

        return if (isNetworkAvailable.value == true) {

            when (patientManager.pushAndSaveEndPregnancy(patient)) {
                is NetworkResult.Success -> {
                    // everything good, continue
                    Log.d(TAG, "everything good")
                    SaveResult.SavedAndUploaded
                }
                else -> {
                    // something is not good - make patient sync and don't save changes
                    allowEdit(true)
                    SaveResult.ServerReject
                }
            }
        } else {
            patientManager.add(patient)
            SaveResult.SavedOffline
        }
    }

    // called every time the date is set in ClosePregnancyFragment
    fun checkEndDateErrors(endDateVal: Long) {

        val startDate = pregnancyStartTimestamp.value
        if (startDate == null) {
            calculatedGestationalAge.value = "Error"
            return
        }

        // if end date is before the start date of pregnancy
        if (endDateVal.toBigInteger() < startDate) {
            if (DEBUG) Log.d(
                TAG, "Input error: cannot have an end date before the pregnancy's start date"
            )
            endDateError.value = context.getString(R.string.invalid_pregnancy_end_date_before_start)
            return
        }

        // if end date is after current time
        if (MaterialDatePicker.todayInUtcMilliseconds() / MILLIS_IN_SEC <= endDateVal) {
            if (DEBUG) Log.d(
                TAG, "Input error: cannot have an end date greater than current time"
            )
            endDateError.value = context.getString(R.string.invalid_pregnancy_end_date_future)
            return
        }

        try {
            val gestationLen = calculatedGestationalAge.value?.toFloatOrNull() ?: return

            // if total gestation length is > 10 months/43 weeks
            if (gestationLen >= GESTATIONAL_AGE_WEEKS_MAX) {
                if (DEBUG) Log.d(
                    TAG, "Input error: pregnancy gestation length is too long"
                )
                endDateError.value = context.getString(
                    R.string.patient_error_gestation_greater_than_n_weeks,
                    GESTATIONAL_AGE_WEEKS_MAX
                )
                return
            }
        } catch (error: NumberFormatException) {
            if (DEBUG) Log.d(
                TAG, "Error with calculatedGestationalAge conversion"
            )
        }

        endDateError.value = ""
    }

    private fun isEndPregnancyValid(patient: Patient): Boolean {

        // No previous error exist
        if (endDateError.value != "") {
            return false
        }

        if (patient.prevPregnancyEndDate == null) {
            if (DEBUG) Log.d(
                TAG, "Input error: cannot end pregnancy with no end date"
            )
            endDateError.value = context.getString(R.string.missing)
            return false
        }

        return true
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
                addSource(patientGestationalAge) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = Patient::gestationalAge,
                        verifier = Patient.Companion
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
}
