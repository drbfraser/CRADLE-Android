package com.cradle.neptune.viewmodel

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cradle.neptune.R
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.GestationalAgeMonths
import com.cradle.neptune.model.GestationalAgeWeeks
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.SymptomsState
import com.cradle.neptune.model.UrineTest
import com.cradle.neptune.utilitiles.LiveDataDynamicModelBuilder
import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Weeks
import com.cradle.neptune.view.ReadingActivity
import java.text.DecimalFormat
import java.util.Locale
import kotlin.reflect.KProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

// The index of the gestational age units inside of the string.xml array, R.array.reading_ga_units
private const val GEST_AGE_UNIT_WEEKS_INDEX = 0
private const val GEST_AGE_UNIT_MONTHS_INDEX = 1

/**
 * The ViewModel that is used in [ReadingActivity] and its [BaseFragment]s.
 * This should be initialized before the Fragments are created
 */
@SuppressWarnings("LargeClass")
class PatientReadingViewModel constructor(
    private val readingManager: ReadingManager,
    private val patientManager: PatientManager,
    private val app: Application
) : AndroidViewModel(app) {
    private val patientBuilder = LiveDataDynamicModelBuilder()
    private val readingBuilder = LiveDataDynamicModelBuilder()

    private val monthsUnitString = app.resources
        .getStringArray(R.array.reading_ga_units)[GEST_AGE_UNIT_MONTHS_INDEX]
    private val weeksUnitString = app.resources
        .getStringArray(R.array.reading_ga_units)[GEST_AGE_UNIT_WEEKS_INDEX]

    private lateinit var reasonForLaunch: ReadingActivity.LaunchReason

    private val isInitializedMutex = Mutex(locked = false)

    @GuardedBy("isInitializedMutex")
    private val _isInitialized = MutableLiveData<Boolean>(false)
    // It's not a problem that this variable isn't locked, since it's not meant to be Mutable, and
    // the only things that happen is that this is being observed.
    val isInitialized: LiveData<Boolean>
        get() = _isInitialized

    /**
     * If not creating a new patient, the patient and reading requested will be asynchronously
     * initialized, and then [isInitialized] will emit true if no errors occur.
     * This needs to be run before the ViewModel can be used.
     *
     * We **must** launch this from the main thread in order to guarantee that all the LiveData
     * will use values that are ready after decompose is finished.
     */
    @MainThread
    fun initialize(
        launchReason: ReadingActivity.LaunchReason,
        readingId: String?
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            isInitializedMutex.lock()
            try {
                if (_isInitialized.value == true) {
                    return@launch
                }

                reasonForLaunch = launchReason
                if (reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                    // _isInitialized will be set to true via the finally branch.
                    return@launch
                }

                check(!readingId.isNullOrEmpty()) {
                    "was given no readingId despite not creating new reading"
                }

                val reading = readingManager.getReadingById(readingId)
                    ?: error("no reading associated with given id")
                val patient = patientManager.getPatientById(reading.patientId)
                    ?: error("no patient associated with given reading")

                when (reasonForLaunch) {
                    ReadingActivity.LaunchReason.LAUNCH_REASON_EDIT -> {
                        check(!reading.isUploadedToServer) {
                            "trying to edit a reading that has already been uploaded"
                        }
                        decompose(patient, reading)
                    }
                    ReadingActivity.LaunchReason.LAUNCH_REASON_EXISTINGNEW -> {
                        decompose(patient)
                    }
                    ReadingActivity.LaunchReason.LAUNCH_REASON_RECHECK -> {
                        decompose(patient)

                        // Add the old reading to the previous list of the new reading.
                        val previousIds = previousReadingIds.value ?: ArrayList()
                        with(previousIds) {
                            add(reading.id)
                            previousReadingIds.postValue(this)
                        }
                    }
                    else -> {
                        error("invalid launch reason")
                    }
                }
            } finally {
                initializeLiveData()

                // Make sure we don't retrigger observers if this is run twice for some reason.
                if (_isInitialized.value != true) {
                    _isInitialized.value = true
                }
                isInitializedMutex.unlock()
            }
        }
    }

    @GuardedBy("isInitializedMutex")
    private fun decompose(patient: Patient) {
        if (_isInitialized.value == true) return

        patientBuilder.decompose(patient)
    }

    @GuardedBy("isInitializedMutex")
    private fun decompose(patient: Patient, reading: Reading) {
        if (_isInitialized.value == true) return

        patientBuilder.decompose(patient)
        // We have to do this so that the Activity and ViewModel don't share the same reference for
        // symptoms.
        val symptomsCopy = ArrayList<String>().apply {
            addAll(reading.symptoms as ArrayList<String>)
        }
        readingBuilder.decompose(reading.copy(symptoms = symptomsCopy))
    }

    /**
     * Initializes the various LiveData properties of this ViewModel. This **must** be run after
     * decomposing patients and readings, or else the values the functions will use will be
     * inconsistent.
     */
    @GuardedBy("isInitializedMutex")
    private fun initializeLiveData() {
        if (_isInitialized.value == true) return

        _isUsingDateOfBirth.value = patientDob.value != null

        val symptoms = symptoms.value ?: emptyList()
        SymptomsState(
            symptoms,
            getEnglishResources().getStringArray(R.array.reading_symptoms)
        ).let {
            _symptomsState.value = it
            otherSymptomsInput.value = it.otherSymptoms
        }

        patientGestationalAgeInput.apply {
            value = if (patientIsPregnant.value != true) {
                // If not pregnant, make the input blank. Note: If this is the empty string, then
                // an error will be triggered when the Pregnant CheckBox is ticked for the first
                // time.
                null
            } else {
                when (val gestationalAge = patientGestationalAge.value) {
                    is GestationalAgeMonths -> {
                        // We don't want to show an excessive amount of decimal places.
                        DecimalFormat("#.####").format(gestationalAge.age.asMonths())
                    }
                    is GestationalAgeWeeks -> {
                        // This also makes the default units in weeks.
                        gestationalAge.age.weeks.toString()
                    }
                    else -> {
                        // If it's missing, default to nothing.
                        ""
                    }
                }
            }
        }

        patientGestationalAgeUnits.apply {
            value = if (patientGestationalAge.value is GestationalAgeMonths) {
                monthsUnitString
            } else {
                // This also makes the default units in weeks.
                weeksUnitString
            }
        }

        addSourcesForSymptomsLiveData()
        addSourcesForGestationAgeMediatorLiveData()
    }

    @GuardedBy("isInitializedMutex")
    private fun addSourcesForSymptomsLiveData() {
        if (_isInitialized.value == true) return

        _symptomsState.apply {
            addSource(otherSymptomsInput) { otherSymptomsString ->
                val currentSymptomsState = value ?: return@addSource
                if (currentSymptomsState.otherSymptoms != otherSymptomsString) {
                    currentSymptomsState.setOtherSymptoms(otherSymptomsString)
                    value = currentSymptomsState
                }
            }
        }

        symptoms.apply {
            addSource(_symptomsState) { symptomsState ->
                // Store only English symptoms.
                val defaultEnglishSymptoms =
                    getEnglishResources().getStringArray(R.array.reading_symptoms)
                value = symptomsState.buildSymptomsList(defaultEnglishSymptoms)
            }
        }
    }

    @GuardedBy("isInitializedMutex")
    private fun addSourcesForGestationAgeMediatorLiveData() {
        if (_isInitialized.value == true) return

        patientGestationalAge.apply {
            addSource(patientGestationalAgeUnits) {
                // If we received an update to the units used, convert the GestationalAge object
                // into the right type.
                Log.d(TAG, "DEBUG: patientGestationalAge: units source: $it")
                it ?: return@addSource

                if (it == monthsUnitString && value is GestationalAgeWeeks) {
                    Log.d(TAG, "DEBUG: patientGestationalAge units source: converting to Months")

                    value = GestationalAgeMonths((value as GestationalAge).value)
                    // Zero out the input when changing units.
                    patientGestationalAgeInput.value = "0"
                } else if (it == weeksUnitString && value is GestationalAgeMonths) {
                    Log.d(TAG, "DEBUG: patientGestationalAge units source: converting to Weeks")

                    value = GestationalAgeWeeks((value as GestationalAge).value)
                    // Zero out the input when changing units.
                    patientGestationalAgeInput.value = "0"
                } else {
                    Log.d(TAG, "DEBUG: patientGestationalAge units source: didn't do anything")
                }
            }
            addSource(patientGestationalAgeInput) {
                // If the user typed something in, create a new GestationalAge object with the
                // specified values.
                Log.d(TAG, "DEBUG: patientGestationalAge input source: $it")
                it ?: return@addSource
                if (it.isBlank()) {
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
            addSource(patientIsPregnant) { isPregnant ->
                if (isPregnant == false) {
                    value = null
                }
            }
        }
    }

    private fun getEnglishResources(): Resources =
        Configuration(app.resources.configuration)
            .apply { setLocale(Locale.ENGLISH) }
            .run { app.createConfigurationContext(this).resources }

    /* Patient Info */
    val patientId: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::id)

    val patientName: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::name)

    val patientDob: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::dob)

    val patientAge: MutableLiveData<Int?>
        get() = patientBuilder.get<Int?>(Patient::age)

    val patientGestationalAge: MediatorLiveData<GestationalAge?> =
        patientBuilder.get<GestationalAge?>(Patient::gestationalAge)

    /**
     * Input that is taken directly from the form as a String.
     */
    val patientGestationalAgeInput: MutableLiveData<String> = MediatorLiveData<String>()

    val patientGestationalAgeUnits: MutableLiveData<String> = MediatorLiveData<String>()

    val patientSex: MutableLiveData<Sex?>
        get() = patientBuilder.get<Sex?>(Patient::sex)

    val patientIsPregnant: MutableLiveData<Boolean?>
        get() = patientBuilder.get<Boolean?>(Patient::isPregnant)

    val patientZone: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::zone)

    val patientVillageNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::villageNumber)

    val patientDrugHistory: MutableLiveData<List<String>> =
        patientBuilder.get<List<String>>(Patient::drugHistoryList, defaultValue = emptyList())

    val patientMedicalHistory: MutableLiveData<List<String>> =
        patientBuilder.get<List<String>>(Patient::medicalHistoryList, defaultValue = emptyList())

    val patientLastEdited: MutableLiveData<Long?>
        get() = patientBuilder.get<Long?>(Patient::lastEdited)

    /* Blood Pressure Info */
    val bloodPressure: MutableLiveData<BloodPressure?>
        get() = readingBuilder.get<BloodPressure?>(Reading::bloodPressure)

    /* Urine Test Info */
    val urineTest: MutableLiveData<UrineTest?>
        get() = readingBuilder.get<UrineTest?>(Reading::urineTest)

    /* Referral Info */
    val referral: MutableLiveData<Referral?>
        get() = readingBuilder.get<Referral?>(Reading::referral)

    /* Reading Info */
    val readingId: MutableLiveData<String>
        get() = readingBuilder.get(Reading::id, "")

    val dateTimeTaken: MutableLiveData<Long?>
        get() = readingBuilder.get<Long?>(Reading::dateTimeTaken)

    val symptoms: MediatorLiveData<List<String>> =
        readingBuilder.get<List<String>>(Reading::symptoms)

    /**
     * Keeps track of the symptoms that are checked. The checkboxes are as ordered in the string
     * array, R.array.reading_symptoms, except the last checkbox represents the other symptoms.
     * This will be changed directly by the CheckBoxes via [setSymptomsState]
     */
    private val _symptomsState = MediatorLiveData<SymptomsState>()
    val symptomsState: LiveData<SymptomsState> = _symptomsState

    /**
     * The user's input for the other symptoms is tracked here.
     */
    val otherSymptomsInput = MutableLiveData("")

    @MainThread
    fun setSymptomsState(index: Int, newValue: Boolean) {
        val currentSymptomsState = _symptomsState.value ?: return

        // Prevent infinite loops and unnecessary updates.
        if (currentSymptomsState.isSymptomIndexChecked(index) != newValue) {
            currentSymptomsState.setSymptomIndexState(index, newValue)
            _symptomsState.value = currentSymptomsState
        }
    }

    val dateRecheckVitalsNeeded: MutableLiveData<Long?>
        get() = readingBuilder.get<Long?>(Reading::dateRecheckVitalsNeeded)

    val isFlaggedForFollowUp: MutableLiveData<Boolean?>
        get() = readingBuilder.get<Boolean?>(Reading::isFlaggedForFollowUp)

    @Suppress("UNCHECKED_CAST")
    val previousReadingIds: MutableLiveData<MutableList<String>?>
        get() = readingBuilder.get<List<String>?>(Reading::previousReadingIds)
            as MutableLiveData<MutableList<String>?>

    /**
     * A map of KProperty.name to error messages. If an error message is null, that means the field
     * is valid. Used by Data Binding.
     */
    private val _errorMap = MediatorLiveData<ArrayMap<String, String?>>().apply {
        value = arrayMapOf()
        addSource(patientId) {
            setErrorMessageInErrorMap(
                value = it, propertyToCheck = Patient::id, isForPatient = true
            )
        }
        addSource(patientName) {
            setErrorMessageInErrorMap(
                value = it, propertyToCheck = Patient::name, isForPatient = true
            )
        }
        addSource(patientDob) {
            if (_isUsingDateOfBirth.value == false) {
                // The date of birth and age will use the same key for the error map.
                // Prefer errors that come from the age if using age.
                return@addSource
            }
            setErrorMessageInErrorMap(
                value = it, propertyToCheck = Patient::dob,
                isForPatient = true, propertyForMap = Patient::age
            )
        }
        addSource(patientAge) {
            if (_isUsingDateOfBirth.value == true) {
                // The date of birth and age will use the same key for the error map.
                // Prefer errors that come from the date of birth if using date of birth.
                return@addSource
            }
            setErrorMessageInErrorMap(
                value = it, propertyToCheck = Patient::age, isForPatient = true
            )
        }
        addSource(patientGestationalAge) {
            setErrorMessageInErrorMap(
                value = it, propertyToCheck = Patient::gestationalAge, isForPatient = true
            )
        }
        addSource(patientSex) {
            setErrorMessageInErrorMap(
                value = it, propertyToCheck = Patient::sex, isForPatient = true
            )
        }
    }
    val errorMap: LiveData<ArrayMap<String, String?>> = _errorMap

    /**
     * Describes the age input state. If the value inside is true, that means that age is derived
     * from date of birth, and the user has to clear the date of birth before adding new input.
     * If the value inside is false, they can add an approximate age, or overwrite that with a
     * date of birth via a date picker.
     */
    private val _isUsingDateOfBirth = MutableLiveData<Boolean>()
    val isUsingDateOfBirth: LiveData<Boolean> = _isUsingDateOfBirth

    /**
     * Sets the new age input state. If called with [useDateOfBirth] false, then the date of birth
     * will be cleared (nulled) out.
     */
    @MainThread
    fun setUsingDateOfBirth(useDateOfBirth: Boolean) {
        if (!useDateOfBirth) {
            patientDob.value = null
        }
        _isUsingDateOfBirth.value = useDateOfBirth
    }

    /**
     * @param isForPatient True if viewing patient property; false if viewing reading property
     */
    @MainThread
    private fun setErrorMessageInErrorMap(
        value: Any?,
        propertyToCheck: KProperty<*>,
        isForPatient: Boolean,
        propertyForMap: KProperty<*> = propertyToCheck
    ) {
        val (isValid, errorMessage) = if (isForPatient) {
            Patient.isValueValid(
                propertyToCheck, value, getApplication(),
                instance = null, currentValues = patientBuilder.publicMap
            )
        } else {
            Reading.isValueValid(
                propertyToCheck, value, getApplication(),
                instance = null, currentValues = readingBuilder.publicMap
            )
        }

        val errorMessageForMap = if (!isValid) errorMessage else null
        val currentMap = _errorMap.value ?: arrayMapOf()

        // Don't notify observers if the error message is the exact same message.
        if (currentMap[propertyForMap.name] != errorMessageForMap) {
            currentMap[propertyForMap.name] = errorMessageForMap
            _errorMap.value = currentMap
        }
    }

    companion object {
        private const val TAG = "PatientReadingViewModel"
    }
}
