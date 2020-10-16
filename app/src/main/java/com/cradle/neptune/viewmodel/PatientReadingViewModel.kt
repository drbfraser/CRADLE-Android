package com.cradle.neptune.viewmodel

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.IdRes
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
import com.cradle.neptune.model.Verifier
import com.cradle.neptune.utilitiles.LiveDataDynamicModelBuilder
import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Weeks
import com.cradle.neptune.view.ReadingActivity
import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationTargetException
import java.text.DecimalFormat
import java.util.Locale
import kotlin.reflect.KProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

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

    /**
     * The initialization state as a MutableLiveData. This must be set to true by the [initialize]
     * function before any other funtions and LiveData can be used in this ViewModel.
     *
     * Any things marked with the [GuardedBy] annotation are intended to only be used in the
     * [initialize] function, which holds the [isInitializedMutex]. This means those functions are
     * intended to only run once.
     *
     * While [_isInitialized] is false, when decompose is done, the LiveData won't be observed by
     * the UI. The UI only observes the LiveData when [_isInitialized] is true.
     */
    @GuardedBy("isInitializedMutex")
    private val _isInitialized = MutableLiveData<Boolean>(false)

    /**
     * The observable initialization state. This is immutable since Fragments / Activities are not
     * meant to modify this in any circumstance.
     */
    val isInitialized: LiveData<Boolean> = _isInitialized

    /**
     * If not creating a new patient, the patient and reading requested will be asynchronously
     * initialized, and then [isInitialized] will emit true if no errors occur.
     * This needs to be run before the ViewModel can be used.
     *
     * We **must** launch this from the main thread in order to guarantee that all the LiveData
     * will use values that are ready after decompose is finished.
     */
    fun initialize(
        launchReason: ReadingActivity.LaunchReason,
        readingId: String?
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            isInitializedMutex.lock()
            val startTime = System.currentTimeMillis()
            try {
                Log.d(TAG, "initialize start")
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
                // Make sure we don't retrigger observers if this is run twice for some reason.
                if (_isInitialized.value != true) {
                    initializeLiveData()
                    _isInitialized.value = true
                } else {
                    Log.w(TAG, "attempted to run initialize twice!")
                }
                isInitializedMutex.unlock()
                Log.d(TAG, "initialize took ${System.currentTimeMillis() - startTime} ms")
            }
        }
    }

    /**
     * Decompose the patient. Needs to be on the main thread, because we need the LiveData values
     * to be instantly set via [MutableLiveData.setValue] and not [MutableLiveData.postValue].
     */
    @GuardedBy("isInitializedMutex")
    private suspend fun decompose(patient: Patient) = withContext(Dispatchers.Main) {
        patientBuilder.decompose(patient)
    }

    /**
     * Decompose the patient and reading. Needs to be on the main thread, because we need the
     * LiveData values to be instantly set via [MutableLiveData.setValue] and not
     * [MutableLiveData.postValue].
     */
    @GuardedBy("isInitializedMutex")
    private suspend fun decompose(
        patient: Patient,
        reading: Reading
    ) = withContext(Dispatchers.Main) {
        if (_isInitialized.value == true) return@withContext

        // We have to do this so that the Activity and ViewModel don't share the same reference for
        // symptoms.
        val symptomsCopy = ArrayList<String>().apply {
            addAll(reading.symptoms as ArrayList<String>)
        }

        logTime("double decompose") {
            joinAll(
                viewModelScope.launch {
                    logTime("patient decompose") { patientBuilder.decompose(patient) }
                },
                viewModelScope.launch {
                    logTime("reading decompose") {
                        readingBuilder.decompose(reading.copy(symptoms = symptomsCopy))
                    }
                }
            )
        }
    }

    /**
     * Initializes the various LiveData properties of this ViewModel. This **must** be run after
     * decomposing patients and readings, or else the values the functions will use will be
     * inconsistent.
     */
    @GuardedBy("isInitializedMutex")
    private suspend fun initializeLiveData() {
        if (_isInitialized.value == true) return

        if (patientIsPregnant.value == null) {
            patientIsPregnant.value = false
        }

        _isUsingDateOfBirth.value = patientDob.value != null

        logTime("initializeLiveData") {
            joinAll(
                viewModelScope.launch {
                    logTime("setUpSymptomsLiveData") { setUpSymptomsLiveData() }
                },
                viewModelScope.launch {
                    logTime("setupGestationAgeLiveData") { setupGestationAgeLiveData() }
                },
                viewModelScope.launch {
                    logTime("setupBloodPressureLiveData") { setupBloodPressureLiveData() }
                },
                viewModelScope.launch {
                    logTime("setupUrineTestAndSourcesLiveData") { setupUrineTestAndSourcesLiveData() }
                },
                viewModelScope.launch {
                    logTime("setupIsPatientValidLiveData") { setupIsPatientValidLiveData() }
                }
            )
        }
    }

    /**
     * There's a really ugly edge case where tapping the Pregnant checkbox and inputting a village
     * number at the same time can result in the next button being active. Likely caused by the
     * coroutine for the valid patient before winning over the coroutine for the invalid patient.
     * Normally this doesn't happen since the user can only type in one field at a time, but the
     * CheckBox is a special case, since users can interact with it as they are typing.
     *
     * So, we cancel the previous job to ensure the coroutine in [setupIsPatientValidLiveData] can
     * launch with the most recent [patientBuilder] values.
     */
    private var isPatientValidJob: Job? = null
    /**
     * Setup the [isPatientValid] LiveData that will attempt to construct a valid patient
     * using the current patient data.
     */
    @GuardedBy("isInitializedMutex")
    private fun setupIsPatientValidLiveData() {
        isPatientValid.apply {
            // When any of these change, attempt to construct a valid patient and post whether
            // or not it succeeded. This is used to determine whether the Next button is enabled.
            arrayOf(
                patientName, patientId, patientDob, patientAge, patientGestationalAge, patientSex,
                patientIsPregnant, patientVillageNumber
            ).forEach { liveData ->
                addSource(liveData) {
                    if (_isInitialized.value != true) return@addSource

                    isPatientValidJob?.cancel() ?: Log.d(TAG, "no job to cancel")
                    isPatientValidJob = viewModelScope.launch(Dispatchers.Default) {
                        Log.d(TAG, "attempting to construct patient for validation")
                        attemptToBuildValidPatient().let { patient ->
                            // Post the value immediately: requires main thread to do so.
                            withContext(Dispatchers.Main) { value = patient != null }
                        }
                    }
                }
            }
        }
    }

    private inline fun logTime(functionName: String, block: () -> Unit) {
        val startTime = System.currentTimeMillis()
        block.invoke()
        Log.d(TAG, "$functionName took ${System.currentTimeMillis() - startTime} ms")
    }

    @GuardedBy("isInitializedMutex")
    private suspend fun setupBloodPressureLiveData() = withContext(Dispatchers.Main) {
        // Populate the text fields using the current blood pressure data that was decomposed.
        bloodPressure.value?.let {
            bloodPressureSystolicInput.value = it.systolic
            bloodPressureDiastolicInput.value = it.diastolic
            bloodPressureHeartRateInput.value = it.heartRate
        }

        bloodPressure.apply {
            addSource(bloodPressureSystolicInput) { systolic ->
                maybeConstructBloodPressure(
                    systolic,
                    bloodPressureDiastolicInput.value,
                    bloodPressureHeartRateInput.value
                )
            }
            addSource(bloodPressureDiastolicInput) { diastolic ->
                maybeConstructBloodPressure(
                    bloodPressureSystolicInput.value,
                    diastolic,
                    bloodPressureHeartRateInput.value
                )
            }
            addSource(bloodPressureHeartRateInput) { heartRate ->
                maybeConstructBloodPressure(
                    bloodPressureSystolicInput.value,
                    bloodPressureDiastolicInput.value,
                    heartRate
                )
            }
        }
    }

    /**
     * Constructs and sets the [bloodPressure] LiveData value only when all values are present.
     * Note: This isn't going to necessarily be held by [isInitializedMutex], since the Observer set
     * by [MediatorLiveData.addSource] code runs at any time.
     */
    private fun maybeConstructBloodPressure(systolic: Int?, diastolic: Int?, heartRate: Int?) {
        if (systolic == null || diastolic == null || heartRate == null) return
        bloodPressure.value = BloodPressure(systolic, diastolic, heartRate)
    }

    @GuardedBy("isInitializedMutex")
    private suspend fun setUpSymptomsLiveData() = withContext(Dispatchers.Main) {
        if (_isInitialized.value == true) return@withContext

        // Put an initial SymptomsState in _symptomsState based on the decomposed data.
        val currentSymptoms = symptoms.value ?: emptyList()
        SymptomsState(
            currentSymptoms,
            getEnglishResources().getStringArray(R.array.reading_symptoms)
        ).let {
            _symptomsState.value = it
            otherSymptomsInput.value = it.otherSymptoms
        }

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
    private suspend fun setupGestationAgeLiveData() = withContext(Dispatchers.Main) {
        if (_isInitialized.value == true) return@withContext

        // Populate the gestational age input field / EditText.
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
                Log.d(TAG, "DEBUG: patientGestationalAge: units source: $it")
                it ?: return@addSource

                if (it == monthsUnitString && value is GestationalAgeWeeks) {
                    Log.d(TAG, "DEBUG: patientGestationalAge units source: converting to Months")

                    value = GestationalAgeMonths((value as GestationalAge).timestamp)
                    // Zero out the input when changing units.
                    patientGestationalAgeInput.value = "0"
                } else if (it == weeksUnitString && value is GestationalAgeMonths) {
                    Log.d(TAG, "DEBUG: patientGestationalAge units source: converting to Weeks")

                    value = GestationalAgeWeeks((value as GestationalAge).timestamp)
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
            addSource(patientIsPregnant) { isPregnant ->
                if (isPregnant == false) {
                    value = null
                }
            }
        }
    }

    @GuardedBy("isInitializedMutex")
    private suspend fun setupUrineTestAndSourcesLiveData() = withContext(Dispatchers.Main) {
        if (_isInitialized.value == true) return@withContext

        val currentUrineTest = urineTest.value

        // Set the initial urine test checkbox state
        isUsingUrineTest.value =
            if (reasonForLaunch != ReadingActivity.LaunchReason.LAUNCH_REASON_EDIT) {
                // Ensure urine test enabled by default for new readings like the frontend.
                true
            } else {
                // Otherwise, derive urine test usage state from the present urine test.
                currentUrineTest != null
            }

        val liveDataMap = mapOf(
            UrineTest::leukocytes to urineTestLeukocytesInput,
            UrineTest::nitrites to urineTestNitritesInput,
            UrineTest::glucose to urineTestGlucoseInput,
            UrineTest::protein to urineTestProteinInput,
            UrineTest::blood to urineTestBloodInput
        )
        for ((property, inputLiveData) in liveDataMap) {
            if (currentUrineTest != null) {
                inputLiveData.value = property.getter.call(currentUrineTest)
            }

            urineTest.apply {
                addSource(inputLiveData) {
                    if (isUsingUrineTest.value == false && urineTest.value != null) {
                        value = null
                        return@addSource
                    }
                    val leukocytes = urineTestLeukocytesInput.value.apply {
                        if (isNullOrBlank()) return@addSource
                    }
                    val nitrites = urineTestNitritesInput.value.apply {
                        if (isNullOrBlank()) return@addSource
                    }
                    val glucose = urineTestGlucoseInput.value.apply {
                        if (isNullOrBlank()) return@addSource
                    }
                    val protein = urineTestProteinInput.value.apply {
                        if (isNullOrBlank()) return@addSource
                    }
                    val blood = urineTestBloodInput.value.apply {
                        if (isNullOrBlank()) return@addSource
                    }
                    val newUrineTest = UrineTest(
                        leukocytes = leukocytes!!, nitrites = nitrites!!, glucose = glucose!!,
                        protein = protein!!, blood = blood!!
                    )

                    if (value != newUrineTest) {
                        value = newUrineTest
                    }
                }
            }
        }
        urineTest.addSource(isUsingUrineTest) {
            if (it == false && urineTest.value != null) {
                urineTest.value = null
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
    val bloodPressure: MediatorLiveData<BloodPressure?> =
        readingBuilder.get<BloodPressure?>(Reading::bloodPressure)

    val bloodPressureSystolicInput = MutableLiveData<Int>()
    val bloodPressureDiastolicInput = MutableLiveData<Int>()
    val bloodPressureHeartRateInput = MutableLiveData<Int>()

    /* Urine Test Info */
    val urineTest: MediatorLiveData<UrineTest?> = readingBuilder.get<UrineTest?>(Reading::urineTest)
    val isUsingUrineTest = MutableLiveData<Boolean>()
    val urineTestLeukocytesInput = MutableLiveData<String>()
    val urineTestNitritesInput = MutableLiveData<String>()
    val urineTestGlucoseInput = MutableLiveData<String>()
    val urineTestProteinInput = MutableLiveData<String>()
    val urineTestBloodInput = MutableLiveData<String>()

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
     *
     * @see setUpSymptomsLiveData
     */
    private val _symptomsState = MediatorLiveData<SymptomsState>()
    val symptomsState: LiveData<SymptomsState> = _symptomsState

    /**
     * The user's arbitrary text input for the "Other symptoms" field is tracked here.
     */
    val otherSymptomsInput = MutableLiveData("")

    /**
     * Sets the symptom state for a particular symptom with index [index] inside of the symptoms
     * string array to [newValue].
     */
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
     * Required to guard against modifications to [_errorMap], since changing the [_errorMap] is not
     * done on **the** (singular) main thread.
     */
    private val errorMapMutex = Mutex()
    /**
     * A map of KProperty.name to error messages. If an error message is null, that means the field
     * is valid. The values are set from the function [testValueForValidityAndSetErrorMapAsync]]
     *
     * Modifications to the _errorMap value must be done when [errorMapMutex] is held.
     *
     * @see errorMap
     * @see [testValueForValidityAndSetErrorMapAsync]
     */
    @GuardedBy("errorMapMutex")
    private val _errorMap = MediatorLiveData<ArrayMap<String, String?>>().apply {
        value = arrayMapOf()
        addSource(patientId) {
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = Patient::id, verifier = Patient.Companion
            )
        }
        addSource(patientName) {
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = Patient::name, verifier = Patient.Companion
            )
        }
        addSource(patientVillageNumber) {
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = Patient::villageNumber, verifier = Patient.Companion
            )
        }
        addSource(patientDob) {
            if (_isUsingDateOfBirth.value == false) {
                // The date of birth and age will use the same key for the error map.
                // Prefer errors that come from the age if using age.
                return@addSource
            }
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = Patient::dob,
                verifier = Patient.Companion, propertyForErrorMapKey = Patient::age
            )
        }
        addSource(patientAge) {
            if (_isUsingDateOfBirth.value == true) {
                // The date of birth and age will use the same key for the error map.
                // Prefer errors that come from the date of birth if using date of birth.
                return@addSource
            }
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = Patient::age, verifier = Patient.Companion
            )
        }
        addSource(patientGestationalAge) {
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = Patient::gestationalAge, verifier = Patient.Companion
            )
        }
        addSource(patientSex) {
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = Patient::sex, verifier = Patient.Companion
            )
        }

        // Errors from Readings
        addSource(bloodPressureSystolicInput) {
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = BloodPressure::systolic,
                verifier = BloodPressure.Companion
            )
        }
        addSource(bloodPressureDiastolicInput) {
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = BloodPressure::diastolic,
                verifier = BloodPressure.Companion
            )
        }
        addSource(bloodPressureHeartRateInput) {
            testValueForValidityAndSetErrorMapAsync(
                value = it, propertyToCheck = BloodPressure::heartRate,
                verifier = BloodPressure.Companion
            )
        }

        val urineTestLiveDataMap = mapOf(
            UrineTest::leukocytes to urineTestLeukocytesInput,
            UrineTest::nitrites to urineTestNitritesInput,
            UrineTest::glucose to urineTestGlucoseInput,
            UrineTest::protein to urineTestProteinInput,
            UrineTest::blood to urineTestBloodInput
        )
        for ((property, liveData) in urineTestLiveDataMap) {
            addSource(liveData) {
                if (isUsingUrineTest.value == false) return@addSource

                testValueForValidityAndSetErrorMapAsync(
                    value = it, propertyToCheck = property, verifier = UrineTest.FromJson
                )
            }
        }
        addSource(isUsingUrineTest) {
            // Clear all urine test errors if it is disabled.
            if (it != false) return@addSource
            viewModelScope.launch(Dispatchers.Default) {
                errorMapMutex.lock()
                val currentErrorMap = value ?: return@launch
                for ((property, _) in urineTestLiveDataMap) {
                    currentErrorMap.remove(property.name)
                }
                withContext(Dispatchers.Main) { value = currentErrorMap }
                errorMapMutex.unlock()
            }
        }
    }
    /**
     * Immutable LiveData variant of [_errorMap] so that Fragments can only observe this and not
     * modify. The values are set from the function [testValueForValidityAndSetErrorMapAsync]
     *
     * Observed by Data Binding.
     *
     * @see _errorMap
     * @see testValueForValidityAndSetErrorMapAsync
     * @see com.cradle.neptune.binding.ReadingBindingAdapters.setError
     */
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

    private val isPatientValid = MediatorLiveData<Boolean>()

    /**
     * @return a non-null Patient if and only if the Patient that can be constructed with the
     * values in [patientBuilder] is valid.
     */
    private fun attemptToBuildValidPatient(): Patient? {
        if (!patientBuilder.isConstructable<Patient>()) {
            Log.d(TAG, "patient not constructable")
            return null
        }

        val patient = try {
            patientBuilder.build<Patient>()
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "attemptToBuildValidPatient: " +
                "patient failed to build, exception ${e.cause?.message ?: e.message}")
            return null
        } catch (e: InvocationTargetException) {
            Log.d(TAG, "attemptToBuildValidPatient: " +
                "patient failed to build, exception ${e.cause?.message ?: e.message}")
            return null
        }

        if (!patient.isValidInstance(app)) {
            Log.d(TAG, "attemptToBuildValidPatient: " +
                "patient is invalid, " +
                "errors: ${patient.getAllMembersWithInvalidValues(app)}")
            return null
        }

        Log.d(TAG, "attemptToBuildValidPatient: built a valid patient")
        return patient
    }

    /**
     * Whether the next button is enabled.
     *
     * @see updateNextButtonCriteriaBasedOnDestination
     */
    private val _isNextButtonEnabled = MediatorLiveData<Boolean>()
    val isNextButtonEnabled: LiveData<Boolean> = _isNextButtonEnabled

    @MainThread
    suspend fun onNextButtonClicked(
        @IdRes currentDestinationId: Int
    ): ReadingFlowError = withContext(Dispatchers.Main) {
        when (currentDestinationId) {
            R.id.loadingFragment -> return@withContext ReadingFlowError.NO_ERROR
            R.id.patientInfoFragment -> {
                val patient = attemptToBuildValidPatient()
                    ?: return@withContext ReadingFlowError.ERROR_INVALID_FIELDS

                val existingPatient = patientManager.getPatientById(patient.id)
                if (existingPatient != null) {
                    return@withContext ReadingFlowError.ERROR_PATIENT_ID_IN_USE
                }

                return@withContext ReadingFlowError.NO_ERROR
            }
            R.id.symptomsFragment -> return@withContext ReadingFlowError.NO_ERROR
            R.id.vitalSignsFragment -> {
                return@withContext if (isVitalSignsFragmentInputValid()) {
                    ReadingFlowError.NO_ERROR
                } else {
                    ReadingFlowError.ERROR_INVALID_FIELDS
                }
            }
            else -> return@withContext ReadingFlowError.NO_ERROR
        }
    }

    /**
     * Updates the criteria that's needed to be satisfied in order for the next button to be
     * enabled.
     */
    @MainThread
    fun updateNextButtonCriteriaBasedOnDestination(@IdRes currentDestinationId: Int) {
        // Clear out any sources to be safe.
        arrayOf(isPatientValid, bloodPressure, isUsingUrineTest, urineTest)
            .forEach { _isNextButtonEnabled.removeSource(it) }

        _isNextButtonEnabled.apply {
            when (currentDestinationId) {
                R.id.patientInfoFragment -> {
                    Log.d(
                        TAG,
                        "next button uses patientInfoFragment criteria: patient must be valid"
                    )
                    addSource(isPatientValid) { _isNextButtonEnabled.value = it }
                }
                R.id.symptomsFragment -> {
                    Log.d(TAG, "next button uses symptomsFragment criteria: no criteria")
                    value = true
                }
                R.id.vitalSignsFragment -> {
                    Log.d(
                        TAG, "next button uses symptomsFragment criteria: valid BloodPressure, " +
                            "and valid urine test if using urine test"
                    )
                    value = isVitalSignsFragmentInputValid()
                    addSource(bloodPressure) { value = isVitalSignsFragmentInputValid() }
                    addSource(isUsingUrineTest) { value = isVitalSignsFragmentInputValid() }
                    addSource(urineTest) { value = isVitalSignsFragmentInputValid() }
                }
                R.id.loadingFragment -> return
                else -> return
            }
        }
    }

    /**
     * Determines if the blood pressure and urine tests right now are valid.
     * Blood pressure is mandatory, so if it is null, it's not valid.
     *
     * Urine tests are optional, but if the reading is going to be using a urine test,
     * then it must be a valid urine test.
     */
    private fun isVitalSignsFragmentInputValid(): Boolean {
        val bloodPressureValid = bloodPressure.value?.isValidInstance(app) ?: false
        if (!bloodPressureValid) return false

        return if (isUsingUrineTest.value != false) {
            urineTest.value?.isValidInstance(app) ?: false
        } else {
            true
        }
    }

    /**
     * Asynchronously tests the validity of the [value] for the [propertyToCheck] that belongs to
     * the class that [verifier] verifies. If not valid, an error message will be added to the
     * [_errorMap], which is map of error messages for a property that uses the name of
     * [propertyForErrorMapKey] for the key. If valid, the error message for the property will be
     * removed (so getting the error returns null).
     *
     * By default, [propertyForErrorMapKey] is just [propertyToCheck], but this can be overridden
     * for cases where we might want to share a single error key-value pair between two properties,
     * like date of birth and age since they use the same EditText to display the errors.
     *
     * The error messages are observed via the immutable LiveData [errorMap] in the XML layout files
     * under the errorMessage attribute, and they update in real time.
     *
     * @see _errorMap
     * @see errorMap
     * @see com.cradle.neptune.binding.ReadingBindingAdapters.setError
     */
    private fun testValueForValidityAndSetErrorMapAsync(
        value: Any?,
        propertyToCheck: KProperty<*>,
        verifier: Verifier<*>,
        propertyForErrorMapKey: KProperty<*> = propertyToCheck,
        currentValuesMap: Map<String, Any?>? = null
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            // Selects the map to use for all the other fields on the form. The validity of a
            // property might depend on other properties being there, e.g. a null date of birth is
            // acceptable if there is an estimated age.
            val currentValuesMapToUse = currentValuesMap
                ?: when (verifier) {
                    is Patient.Companion -> {
                        patientBuilder.publicMap
                    }
                    is Reading.Companion -> {
                        readingBuilder.publicMap
                    }
                    else -> {
                        null
                    }
                }

            val (isValid, errorMessage) = verifier.isValueValid(
                property = propertyToCheck, value = value, context = app,
                instance = null, currentValues = currentValuesMapToUse
            )

            val errorMessageForMap = if (!isValid) errorMessage else null

            // Don't notify observers if the error message is the exact same message.
            errorMapMutex.lock()
            try {
                val currentMap = _errorMap.value ?: arrayMapOf()
                if (currentMap[propertyForErrorMapKey.name] != errorMessageForMap) {
                    if (isValid) {
                        currentMap.remove(propertyForErrorMapKey.name)
                    } else {
                        currentMap[propertyForErrorMapKey.name] = errorMessageForMap
                    }

                    withContext(Dispatchers.Main) { _errorMap.value = currentMap }
                }
            } finally {
                errorMapMutex.unlock()
            }
        }
    }

    companion object {
        private const val TAG = "PatientReadingViewModel"
    }
}

enum class ReadingFlowError {
    NO_ERROR, ERROR_PATIENT_ID_IN_USE, ERROR_INVALID_FIELDS
}
