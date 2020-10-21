package com.cradle.neptune.viewmodel

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.cradle.neptune.R
import com.cradle.neptune.ext.setValueOnMainThread
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.GestationalAgeMonths
import com.cradle.neptune.model.GestationalAgeWeeks
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.ReadingMetadata
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.RetestAdvice
import com.cradle.neptune.model.RetestGroup
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.SymptomsState
import com.cradle.neptune.model.UrineTest
import com.cradle.neptune.model.Verifier
import com.cradle.neptune.net.Success
import com.cradle.neptune.utilitiles.LiveDataDynamicModelBuilder
import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Weeks
import com.cradle.neptune.view.ReadingActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationTargetException
import java.text.DecimalFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.threeten.bp.ZonedDateTime

// The index of the gestational age units inside of the string.xml array, R.array.reading_ga_units
private const val GEST_AGE_UNIT_WEEKS_INDEX = 0
private const val GEST_AGE_UNIT_MONTHS_INDEX = 1

/**
 * The ViewModel that is used in [ReadingActivity] and its [BaseFragment]s.
 * This should be initialized before the Fragments are created
 */
@SuppressWarnings("LargeClass")
class PatientReadingViewModel @ViewModelInject constructor(
    private val readingManager: ReadingManager,
    private val patientManager: PatientManager,
    @ApplicationContext private val app: Context
) : ViewModel() {
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
     *
     * @param patientId The patient ID to use when launching for reason LAUNCH_REASON_EXISTINGNEW
     */
    fun initialize(
        launchReason: ReadingActivity.LaunchReason,
        readingId: String?,
        patientId: String? = null
    ) {
        reasonForLaunch = launchReason
        viewModelScope.launch(Dispatchers.Main) {
            val startTime = System.currentTimeMillis()
            isInitializedMutex.lock()
            try {
                Log.d(TAG, "initialize start")
                if (_isInitialized.value == true) {
                    return@launch
                }

                if (reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                    // _isInitialized will be set to true via the finally branch.
                    return@launch
                }

                // TODO: clean up this logic. This logic was taken from the old code, and it's
                //  unclear why the patient has to be derived from the reading.
                if (!patientId.isNullOrBlank() &&
                        reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_EXISTINGNEW) {
                    val patient = patientManager.getPatientById(patientId)
                        ?: error("no patient with given id")
                    updateActionBarSubtitle(patient.name, patient.id)
                    decompose(patient)
                    return@launch
                }

                // At this point, we expect to be doing something with a previous reading.
                check(!readingId.isNullOrBlank()) {
                    "was given no readingId despite not creating new reading"
                }

                val reading = readingManager.getReadingById(readingId)
                    ?: error("no reading associated with given id")
                val patient = patientManager.getPatientById(reading.patientId)
                    ?: error("no patient associated with given reading")
                updateActionBarSubtitle(patient.name, patient.id)

                when (reasonForLaunch) {
                    ReadingActivity.LaunchReason.LAUNCH_REASON_EDIT_READING -> {
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

                        // If we are rechecking vitals, then readingId is the ID of the previous
                        // reading. We derive the previous reading IDs from the previous reading,
                        // and add the previous reading onto it. Note: The most recent reading
                        // will be at the end.
                        val previousIds = reading.previousReadingIds.toMutableList()
                        with(previousIds) {
                            add(reading.id)
                            previousReadingIds.value = this
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
                    logTime("setupBloodPressureLiveData") { setupBloodPressureLiveData() }
                },
                viewModelScope.launch {
                    logTime("setupUrineTestAndSourcesLiveData") { setupUrineTestAndSourcesLiveData() }
                },
                viewModelScope.launch {
                    logTime("setupGestationAgeLiveData") {
                        if (reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                            setupGestationAgeLiveData()
                        }
                    }
                },
                viewModelScope.launch {
                    logTime("setupIsPatientValidLiveData") {
                        // Only check if the patient is valid if we are creating a new patient.
                        // TODO: Handle the case where we are editing patient info only.
                        if (reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                            setupIsPatientValidLiveData()
                        } else {
                            // Force this to be true so that we can build.
                            isPatientValid.value = true
                        }
                    }
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
                            yield()
                            // Post the value immediately: requires main thread to do so.
                            setValueOnMainThread(patient != null)
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
            if (reasonForLaunch != ReadingActivity.LaunchReason.LAUNCH_REASON_EDIT_READING) {
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

    val bloodPressureSystolicInput = MutableLiveData<Int?>()
    val bloodPressureDiastolicInput = MutableLiveData<Int?>()
    val bloodPressureHeartRateInput = MutableLiveData<Int?>()

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
        get() = readingBuilder.get(Reading::id, UUID.randomUUID().toString())

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

    private val previousReadingIds: MutableLiveData<List<String>>
        get() = readingBuilder.get<List<String>>(Reading::previousReadingIds, emptyList())

    val metadata: MutableLiveData<ReadingMetadata> =
        readingBuilder.get(Reading::metadata, ReadingMetadata())

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
     * @see onDestinationChange
     */
    private val _isNextButtonEnabled = MediatorLiveData<Boolean>()
    val isNextButtonEnabled: LiveData<Boolean> = _isNextButtonEnabled

    /**
     * Whether the form input fields are enabled or disabled.
     */
    private val _isInputEnabled = MutableLiveData(true)
    val isInputEnabled: LiveData<Boolean>
        get() = _isInputEnabled

    /**
     * What message to show for the message in the nav bar. If the message is nonempty, a
     * ProgressBar is also shown.
     */
    private val _bottomNavBarMessage = MutableLiveData("")
    val bottomNavBarMessage: LiveData<String>
        get() = _bottomNavBarMessage

    private val _actionBarSubtitle = MutableLiveData<String?>(null)
    val actionBarSubtitle: LiveData<String?>
        get() = _actionBarSubtitle

    /**
     * Handles next button clicking by validating the current Fragment inputs.
     * Will be run on the main thread to ensure the values are consistent.
     */
    @MainThread
    suspend fun validateCurrentDestinationForNextButton(
        @IdRes currentDestinationId: Int
    ): Pair<ReadingFlowError, Patient?> = withContext(Dispatchers.Main) {
        when (currentDestinationId) {
            R.id.loadingFragment -> return@withContext ReadingFlowError.NO_ERROR to null
            R.id.patientInfoFragment -> {
                // Disable input while patient building validation is happening.
                setInputEnabledState(false)
                setBottomNavBarMessage(app.getString(R.string.reading_bottom_nav_bar_checking_id))

                val patient = attemptToBuildValidPatient()
                    ?: return@withContext ReadingFlowError.ERROR_INVALID_FIELDS to null

                // We need to make sure the user isn't making a patient with an ID already in use.
                // Check if a patient with the same ID already exists in their local patients list
                // in the phone's database.
                val existingLocalPatient = patientManager.getPatientById(patient.id)
                if (existingLocalPatient != null) {
                    // Send bac
                    return@withContext ReadingFlowError.ERROR_PATIENT_ID_IN_USE_LOCAL to
                        existingLocalPatient
                }

                // Opportunistically check if a patient with the same ID exists on the server. This
                // only serves to lower the probability that a duplicate ID will be used, as it
                // obviously doesn't work when the user has no internet.
                val existingOnServer = patientManager.downloadPatientInfoFromServer(patient.id)
                if (existingOnServer is Success) {
                    // Send back the patient to give the user an option to
                    // download the patient with all of their readings.
                    return@withContext ReadingFlowError.ERROR_PATIENT_ID_IN_USE_ON_SERVER to
                        existingOnServer.value
                }

                return@withContext ReadingFlowError.NO_ERROR to null
            }
            R.id.symptomsFragment -> return@withContext ReadingFlowError.NO_ERROR to null
            R.id.vitalSignsFragment -> return@withContext if (validateVitalSignsAndBuildReading()) {
                ReadingFlowError.NO_ERROR to null
            } else {
                ReadingFlowError.ERROR_INVALID_FIELDS to null
            }
            else -> return@withContext ReadingFlowError.NO_ERROR to null
        }
    }

    @MainThread
    private suspend fun validateVitalSignsAndBuildReading(): Boolean {
        if (!verifyVitalSignsFragment()) {
            return false
        }
        yield()

        // Add data for now in order to get it to build. This needs to be set with the
        // proper values after pressing SAVE READING.
        readingBuilder.apply {
            set(Reading::patientId, patientId.value)
            // These will only populate the fields with null if there's nothing in there already.
            // dateTimeTaken will be updated when the save button is pressed.
            // Update date time taken if null. This may be nonnull if we're editing a reading.
            get(Reading::dateTimeTaken, defaultValue = ZonedDateTime.now().toEpochSecond())
            get(Reading::referral, defaultValue = null)
            get(Reading::followUp, defaultValue = null)
        }

        val validReading = withContext(Dispatchers.Default) { attemptToBuildValidReading() }
            ?: return false

        _currentValidReading.value = validReading

        // This will populate all advice fields. It will return false if it fails.
        if (!adviceManager.populateAllAdviceFields(validReading)) {
            return false
        }

        return true
    }

    private fun attemptToBuildValidReading(): Reading? {
        if (!readingBuilder.isConstructable<Reading>()) {
            Log.d(TAG, "attemptToBuildValidReading: reading not constructable, " +
                "missing ${readingBuilder.missingParameters(Reading::class)}")
            return null
        }

        val reading = try {
            readingBuilder.build<Reading>()
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "attemptToBuildValidReading: " +
                "reading failed to build, exception ${e.cause?.message ?: e.message}")
            return null
        } catch (e: InvocationTargetException) {
            Log.d(TAG, "attemptToBuildValidReading: " +
                "reading failed to build, exception ${e.cause?.message ?: e.message}")
            return null
        }

        if (!reading.isValidInstance(app)) {
            Log.d(TAG, "attemptToBuildValidReading: " +
                "reading is invalid, " +
                "errors: ${reading.getAllMembersWithInvalidValues(app)}")
            return null
        }

        Log.d(TAG, "attemptToBuildValidReading: built a valid reading")
        return reading
    }

    @MainThread
    fun setInputEnabledState(isEnabled: Boolean) {
        _isInputEnabled.apply { if (value != isEnabled) value = isEnabled }
    }

    @MainThread
    private fun setBottomNavBarMessage(message: String) {
        _bottomNavBarMessage.apply { if (value != message) value = message }
    }

    @MainThread
    fun clearBottomNavBarMessage() {
        _bottomNavBarMessage.apply { if (value?.isEmpty() != true) value = "" }
    }

    private val isNextButtonEnabledMutex = Mutex()

    /**
     * Updates the criteria that's needed to be satisfied in order for the next button to be
     * enabled.
     */
    @MainThread
    fun onDestinationChange(@IdRes currentDestinationId: Int) {
        Log.d(TAG, "onDestinationChange")
        updateActionBarSubtitle(patientName.value, patientId.value)
        clearBottomNavBarMessage()

        viewModelScope.launch(Dispatchers.Main) {
            isInitializedMutex.withLock {
                if (_isInitialized.value == true) {
                    setInputEnabledState(true)
                }
            }

            isNextButtonEnabledMutex.withLock {
                vitalSignsFragmentVerifyJob?.cancel()

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
                            addSource(isPatientValid) {
                                viewModelScope.launch {
                                    isNextButtonEnabledMutex.withLock { value = it }
                                }
                            }
                        }
                        R.id.symptomsFragment -> {
                            value = true
                        }
                        R.id.vitalSignsFragment -> {
                            Log.d(
                                TAG,
                                "next button uses vitalSignsFragment criteria: valid BloodPressure, " +
                                    "and valid urine test if using urine test"
                            )
                            launchVitalSignsFragmentVerifyJob()
                            addSource(bloodPressure) { launchVitalSignsFragmentVerifyJob() }
                            addSource(isUsingUrineTest) { launchVitalSignsFragmentVerifyJob() }
                            addSource(urineTest) { launchVitalSignsFragmentVerifyJob() }
                        }
                        R.id.loadingFragment -> return@launch
                        else -> return@launch
                    }
                }
            }
        }
    }

    /**
     * Triggers a download from the server. TODO: Refactor
     */
    fun downloadPatientFromServer(patientId: String): LiveData<Result<Patient>> = liveData {
        withContext(Dispatchers.IO) {
            when (val result = patientManager.downloadPatientAndReading(patientId)) {
                is Success -> {
                    // Safe to cancel here since we only downloaded patients + readings
                    // After this point, we shouldn't have cancellation points because we want the
                    // resulting operations to be atomic.
                    yield()

                    val downloadedPatient = result.value.patient
                    val associateResult =
                        patientManager.associatePatientWithUser(downloadedPatient.id)
                    if (associateResult !is Success) {
                        emit(Result.failure(Throwable()))
                        return@withContext
                    }

                    patientManager.add(downloadedPatient)
                    result.value.readings.let {
                        it.forEach { reading -> reading.isUploadedToServer = true }
                        readingManager.addAllReadings(it)
                    }
                    emit(Result.success(downloadedPatient))
                }
                else -> emit(Result.failure(Throwable()))
            }
        }
    }

    private fun updateActionBarSubtitle(patientName: String?, patientId: String?) {
        val subtitle = if (patientName == null || patientId == null) {
            null
        } else {
            app.getString(
                    R.string.reading_activity_subtitle_name_and_id,
                    patientName, patientId
                )
        }
        val currentSubtitle: String? = _actionBarSubtitle.value
        if (currentSubtitle == subtitle) {
            return
        }
        _actionBarSubtitle.value = subtitle
    }

    private var vitalSignsFragmentVerifyJob: Job? = null

    private fun launchVitalSignsFragmentVerifyJob() {
        vitalSignsFragmentVerifyJob?.cancel()
        vitalSignsFragmentVerifyJob = viewModelScope.launch {
            isNextButtonEnabledMutex.withLock {
                val newValue = verifyVitalSignsFragment()
                yield()
                _isNextButtonEnabled.value = newValue.also {
                    Log.d(TAG, "vital signs fragment job: next button Enabled? $it")
                }
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
    private suspend fun verifyVitalSignsFragment(): Boolean = withContext(Dispatchers.Default) {
        val bloodPressureValid = bloodPressure.value?.isValidInstance(app) ?: false
        if (!bloodPressureValid) return@withContext false

        yield()

        return@withContext if (isUsingUrineTest.value != false) {
            urineTest.value?.isValidInstance(app) ?: false
        } else {
            true
        }
    }

    private val adviceManager = AdviceManager()

    private val _currentValidReading = MutableLiveData<Reading?>(null)

    val currentValidPatientAndRetestGroup: LiveData<Pair<Reading, RetestGroup>?>
        get() = adviceManager.currentValidReadingAndRetestGroup

    val adviceRecheckButtonId = MutableLiveData<Int>()
    val adviceFollowUpButtonId = MutableLiveData<Int>()
    val adviceReferralButtonId = MutableLiveData<Int>()

    val adviceText: LiveData<String> = adviceManager.adviceText
    val recommendedAdvice: LiveData<RecommendedAdvice>
        get() = adviceManager.currentRecommendedAdvice

    val dateRecheckVitalsNeeded: MediatorLiveData<Long?> =
        readingBuilder.get(Reading::dateRecheckVitalsNeeded, null).apply {
            addSource(adviceRecheckButtonId) {
                value = when (it) {
                    R.id.recheck_vitals_after_15_min_radio_button -> {
                        ZonedDateTime.now().toEpochSecond() +
                            TimeUnit.MINUTES.toSeconds(DEFAULT_RECHECK_INTERVAL_IN_MIN)
                    }
                    R.id.recheck_vitals_after_radio_button -> {
                        ZonedDateTime.now().toEpochSecond()
                    }
                    R.id.dont_recheck_vitals_radio_button -> {
                        // Remove date if none selected.
                        null
                    }
                    else -> {
                        value
                    }
                }
            }
        }

    val isFlaggedForFollowUp: MediatorLiveData<Boolean?> =
        readingBuilder.get<Boolean?>(Reading::isFlaggedForFollowUp, defaultValue = false).apply {
            addSource(adviceFollowUpButtonId) {
                value = when (it) {
                    R.id.follow_up_needed_radio_button -> true
                    R.id.no_follow_up_needed_radio_button -> false
                    else -> value
                }
            }
        }

    /**
     * Saves the patient (if creating a new patient) and saves the reading.
     * This writes them to the database.
     * If sending a referral, then we need to handle that elsewhere.
     *
     * @return a [ReadingFlowSaveResult]
     */
    @MainThread
    suspend fun save(isSendingReferral: Boolean = false): ReadingFlowSaveResult =
        withContext(Dispatchers.Default) {
            // Don't save if we are uninitialized for some reason.
            isInitializedMutex.withLock {
                if (_isInitialized.value == false) return@withContext ReadingFlowSaveResult.ERROR
            }

            if (adviceReferralButtonId.value == R.id.send_referral_radio_button) {
                // Don't save the reading / patient yet; we need the AdviceFragment to launch a
                // referral dialog. TODO: Handle isSendingReferral
                return@withContext ReadingFlowSaveResult.REFERRAL_REQUIRED
            }
            yield()

            // Only add patient if we're creating a new patient.
            // If we're just creating a reading, users will not be able to edit patient info.
            val shouldSavePatient =
                reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW

            val patientAsync = async {
                return@async if (shouldSavePatient) {
                    patientLastEdited.setValueOnMainThread(ZonedDateTime.now().toEpochSecond())
                    attemptToBuildValidPatient()
                } else {
                    null
                }
            }

            val readingAsync = async {
                if (reasonForLaunch != ReadingActivity.LaunchReason.LAUNCH_REASON_EDIT_READING) {
                    // Update the reading's time taken if we're not editing a previous reading.
                    dateTimeTaken.setValueOnMainThread(ZonedDateTime.now().toEpochSecond())
                }

                // Try to construct a reading for saving. It should be valid, as these validated
                // properties should have been enforced in the previous fragments
                // (VitalSignsFragment). We get null if it's not valid.
                return@async attemptToBuildValidReading()
            }

            val (patient, reading) = patientAsync.await() to readingAsync.await()
            if (reading == null ||
                    (shouldSavePatient && patient == null)) {
                return@withContext ReadingFlowSaveResult.ERROR
            }
            yield()

            if (shouldSavePatient && patient != null) {
                // Insertion needs to be atomic.
                patientManager.addPatientWithReading(patient, reading)
            } else {
                readingManager.addReading(reading)
            }
            return@withContext ReadingFlowSaveResult.SAVE_SUCCESSFUL
        }

    /**
     * Describes the current recommended advice. The RadioButtons will indicate which option is
     * recommended.
     */
    data class RecommendedAdvice(
        val retestAdvice: RetestAdvice,
        val isFollowupNeeded: Boolean,
        val isReferralRecommended: Boolean
    )

    /**
     * Manages the advice shown in the AdviceFragment.
     */
    private inner class AdviceManager {
        val currentValidReadingAndRetestGroup = MutableLiveData<Pair<Reading, RetestGroup>?>(null)

        val adviceText = MutableLiveData<String>("")

        val currentRecommendedAdvice = MutableLiveData<RecommendedAdvice>()

        init {
            // Put some placeholder in there so we don't deal with nulls.
            currentRecommendedAdvice.value = RecommendedAdvice(
                RetestAdvice.NOT_NEEDED,
                isFollowupNeeded = false,
                isReferralRecommended = false
            )
        }

        /**
         * Submits a reading for advice. Expected to be run when the next button is pressed to go
         * to the AdviceFragment. This will set the default radio button selections.
         *
         * @return Whether advice calculation was successful
         */
        suspend fun populateAllAdviceFields(reading: Reading): Boolean =
            withContext(Dispatchers.Default) {
                // Only build retest groups for valid readings.
                if (!reading.isValidInstance(app)) {
                    currentValidReadingAndRetestGroup.setValueOnMainThread(null)
                    return@withContext false
                }

                val retestGroup = readingManager.getRetestGroup(reading)

                // Set the radio buttons.
                val needDefaultForRecheckVitals = metadata.value?.dateLastSaved == null
                val retestAdvice: RetestAdvice = if (needDefaultForRecheckVitals) {
                    retestGroup.getRetestAdvice()
                } else {
                    val recheckVitalsDate = dateRecheckVitalsNeeded.value
                    val isVitalRecheckRequired = recheckVitalsDate != null
                    val isVitalRecheckRequiredNow = isVitalRecheckRequired &&
                        (recheckVitalsDate!! - ZonedDateTime.now().toEpochSecond() <= 0)

                    when {
                        isVitalRecheckRequiredNow -> RetestAdvice.RIGHT_NOW
                        isVitalRecheckRequired -> RetestAdvice.IN_15_MIN
                        else -> RetestAdvice.NOT_NEEDED
                    }
                }

                val needDefaultForFollowup = dateTimeTaken.value == null
                val isFollowupRecommended = if (needDefaultForFollowup) {
                    retestGroup.mostRecentReadingAnalysis.isRed
                } else {
                    isFlaggedForFollowUp.value ?: false
                }

                val isReferralRecommended =
                    retestGroup.mostRecentReadingAnalysis.isReferralRecommended

                val currentRecommendedAdvice = RecommendedAdvice(
                    retestAdvice = retestAdvice,
                    isFollowupNeeded = isFollowupRecommended,
                    isReferralRecommended = isReferralRecommended
                )

                updateLiveData(reading, retestGroup, currentRecommendedAdvice)

                return@withContext true
            }

        /**
         * Updates both the LiveData in here and the MutableLiveData in the ViewModel.
         */
        private suspend fun updateLiveData(
            reading: Reading,
            retestGroup: RetestGroup,
            recommendedAdvice: RecommendedAdvice
        ) = withContext(Dispatchers.Main) {
            // Update so that the AdviceFragment can inflate using Data Binding.
            currentValidReadingAndRetestGroup.value = reading to retestGroup

            currentRecommendedAdvice.value = recommendedAdvice

            val newAdvice = when {
                retestGroup.isRetestRecommendedNow ->
                    app.getString(R.string.brief_advice_retest_now)
                retestGroup.isRetestRecommendedIn15Min ->
                    app.getString(R.string.brief_advice_retest_after15)
                else ->
                    retestGroup.mostRecentReadingAnalysis.getBriefAdviceText(app)
            }
            adviceText.value = newAdvice

            recommendedAdvice.run {
                adviceRecheckButtonId.value = when (retestAdvice) {
                    RetestAdvice.NOT_NEEDED -> R.id.dont_recheck_vitals_radio_button
                    RetestAdvice.RIGHT_NOW -> R.id.recheck_vitals_after_radio_button
                    RetestAdvice.IN_15_MIN -> R.id.recheck_vitals_after_15_min_radio_button
                }

                adviceFollowUpButtonId.value = if (isFollowupNeeded) {
                    R.id.follow_up_needed_radio_button
                } else {
                    R.id.no_follow_up_needed_radio_button
                }

                adviceReferralButtonId.value = if (isReferralRecommended) {
                    R.id.send_referral_radio_button
                } else {
                    R.id.no_referral_radio_button
                }
            }
        }
    }

    /**
     * Sets the errorMessageManager. Must be initialized here below all the LiveData in order for it
     * to add the LiveData above as its sources to listen to for errors.
     */
    private val errorMessageManager = ErrorMessageManager()

    /**
     * Immutable LiveData variant of [ErrorMessageManager.errorMap] so that Fragments can only
     * observe this and not modify (and they just have to type less words)
     *
     * Observed by Data Binding.
     *
     * @see ErrorMessageManager
     * @see com.cradle.neptune.binding.ReadingBindingAdapters.setError
     */
    val errorMap: LiveData<ArrayMap<String, String?>> = errorMessageManager.errorMap

    /**
     * An inner class that manages the [errorMap] which contain error messages that are instantly
     * shown to the user as they type invalid input.
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
                        value = it,
                        propertyToCheck = Patient::villageNumber,
                        verifier = Patient.Companion
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
                        value = it,
                        propertyToCheck = Patient::gestationalAge,
                        verifier = Patient.Companion
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

                val urineTestLiveDataMap = arrayMapOf(
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
                        errorMapMutex.withLock {
                            val currentErrorMap = value ?: return@launch
                            for ((property, _) in urineTestLiveDataMap) {
                                currentErrorMap.remove(property.name)
                            }
                            setValueOnMainThread(currentErrorMap)
                        }
                    }
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

                errorMapMutex.withLock {
                    val currentMap = errorMap.value ?: arrayMapOf()

                    // Don't notify observers if the error message is the exact same message.
                    if (currentMap[propertyForErrorMapKey.name] != errorMessageForMap) {
                        if (isValid) {
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

    init {
        viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                Log.d(TAG, "DEBUG: errorMap is ${errorMap.value}")
                Log.d(TAG, "DEBUG: isPatientValid is ${attemptToBuildValidPatient() != null}")
                Log.d(TAG, "DEBUG: isNextButtonEnabled is ${_isNextButtonEnabled.value}")
                Log.d(TAG, "DEBUG: _isInputEnabled is ${_isInputEnabled.value}")
                Log.d(TAG, "DEBUG: dateRecheckVitals is ${dateRecheckVitalsNeeded.value}")
                Log.d(TAG, "DEBUG: isFlaggedForFollowup is ${isFlaggedForFollowUp.value}")
                @Suppress("MagicNumber")
                delay(5000L)
            }
        }
    }

    companion object {
        private const val TAG = "PatientReadingViewModel"
        private const val DEFAULT_RECHECK_INTERVAL_IN_MIN = 15L
    }
}

/**
 * Describes errors that occur when the next button is pressed. The ReadingActivity must validate
 * against these errors before moving on to the next destination.
 */
enum class ReadingFlowError {
    /**
     * When there are no errors with the current Fragment, this is returned to allow navigation to
     * the next step of the Reading flow.
     */
    NO_ERROR,

    /**
     * This error occurs when there is a patient from the app's local database whose ID is the same
     * as the one being created.
     */
    ERROR_PATIENT_ID_IN_USE_LOCAL,

    /**
     * This error occurs when there is a patient not in the user's local patients list but from the
     * server whose ID is the same as the one being created. This error can only come up when the
     * user has internet.
     *
     * It does not solve the conflict issue fully; it just lowers the probability of it happening.
     * Even if we have internet, someone could have created a new patient with the same ID after
     * this error is checked.
     */
    ERROR_PATIENT_ID_IN_USE_ON_SERVER,

    /**
     * This error occurs when there is am error in one of the fields for the current Fragment.
     * This should rarely happen, since the Next button tries to make it so that it is disabled if
     * if there are errors in any of the fields. However, we need to check against it to prevent
     * the user from inserting invalid Patients / Readings into their database and trying to upload
     * that to the server.
     */
    ERROR_INVALID_FIELDS
}

enum class ReadingFlowSaveResult {
    SAVE_SUCCESSFUL,
    REFERRAL_REQUIRED,
    ERROR
}
