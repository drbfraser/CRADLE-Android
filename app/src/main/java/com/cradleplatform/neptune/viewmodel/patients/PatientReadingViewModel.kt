package com.cradleplatform.neptune.viewmodel.patients

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.espresso.idling.CountingIdlingResource
import com.cradleplatform.neptune.BuildConfig
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.getEnglishResources
import com.cradleplatform.neptune.ext.getIntOrNull
import com.cradleplatform.neptune.ext.setValueOnMainThread
import com.cradleplatform.neptune.ext.use
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.manager.ReadingUploadManager
import com.cradleplatform.neptune.model.BloodPressure
import com.cradleplatform.neptune.model.GestationalAge
import com.cradleplatform.neptune.model.GestationalAgeMonths
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.RetestAdvice
import com.cradleplatform.neptune.model.RetestGroup
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.model.SymptomsState
import com.cradleplatform.neptune.model.UrineTest
import com.cradleplatform.neptune.model.Verifiable
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.utilities.LiveDataDynamicModelBuilder
import com.cradleplatform.neptune.utilities.Months
import com.cradleplatform.neptune.utilities.Weeks
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.activities.newPatient.ReadingActivity
import com.cradleplatform.neptune.viewmodel.UserViewModel
import com.cradleplatform.neptune.viewmodel.patients.PatientReadingViewModel.LiveDataInitializationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.threeten.bp.ZonedDateTime
import java.lang.reflect.InvocationTargetException
import java.text.DecimalFormat
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.reflect.KProperty

// The index of the gestational age units inside of the string.xml array, R.array.reading_ga_units
private const val GEST_AGE_UNIT_WEEKS_INDEX = 0
private const val GEST_AGE_UNIT_MONTHS_INDEX = 1

private val DEBUG = BuildConfig.DEBUG

/**
 * The ViewModel that is used in [ReadingActivity] and its Fragments to hold intermediate data
 * entered into the forms (helps persist it across configuration changes). It also handles the logic
 * involved in saving [Patient]s and [Reading]s, some navigation-related logic (such as determining
 * when the next button is enabled), error message generation, calculating the advice to show in the
 * AdviceFragment, etc. All the Fragments are able to access this ViewModel, as this ViewModel is
 * scoped to ReadingActivity.
 *
 * LiveData and Data Binding (with two-way Data Binding for the various text fields, checkboxes,
 * radio buttons, spinners) are used to reduce the amount of manual code that deals with loading
 * data from the model into the UI and vice versa, though the ViewModel contains some setup code
 * that deals with the fact that LiveData doesn't work as well when we want to do two-way Data
 * Binding on non-primitive types like UrineTest and BloodPressure. This setup code is located in
 * the inner class [LiveDataInitializationManager].
 *
 * The ViewModel has been split into managers to make it clear what functions are used for which
 * purpose (e.g., the functions and variables in the SaveManager deal with saving the reading to the
 * local database and also deals with sending referrals).
 *
 * The ViewModel should be initialized before the Fragments are created.
 *
 * @see PatientReadingViewModel.LiveDataInitializationManager
 * @see PatientReadingViewModel.NavigationManager
 * @see PatientReadingViewModel.ErrorMessageManager
 * @see PatientReadingViewModel.SaveManager
 * @see PatientReadingViewModel.AdviceManager
 */

@HiltViewModel
class PatientReadingViewModel @Inject constructor(
    private val readingManager: ReadingManager,
    private val referralManager: ReferralManager,
    private val patientManager: PatientManager,
    private val readingUploadManager: ReadingUploadManager,
    private val networkStateManager: NetworkStateManager,
    private val sharedPreferences: SharedPreferences,
    @ApplicationContext @SuppressLint("StaticFieldLeak")
    private val app: Context
) : ViewModel() {
    private val patientBuilder = LiveDataDynamicModelBuilder()
    private val readingBuilder = LiveDataDynamicModelBuilder()

    /**
     * This is null if and only if this [ReadingActivity] is not being tested under Espresso.
     */
    var idlingResource: CountingIdlingResource? = null

    private val monthsUnitString = app.resources
        .getStringArray(R.array.reading_ga_units)[GEST_AGE_UNIT_MONTHS_INDEX]
    private val weeksUnitString = app.resources
        .getStringArray(R.array.reading_ga_units)[GEST_AGE_UNIT_WEEKS_INDEX]

    lateinit var reasonForLaunch: ReadingActivity.LaunchReason
        private set

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

    private var originalPatient: Patient? = null

    private var originalReadingId: String? = null

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

                if (!patientId.isNullOrBlank() &&
                    reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_EXISTINGNEW
                ) {
                    val patient = patientManager.getPatientById(patientId)
                        ?: error("no patient with given id")
                    originalPatient = patient
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
                originalPatient = patient
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
                        originalReadingId = readingId
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
                // originalPatient is null implies that we should save the patient.
                check(originalPatient != null || shouldSavePatient())

                // Make sure we don't retrigger observers if this is run twice for some reason.
                if (_isInitialized.value != true) {
                    liveDataInitializationManager.initializeLiveData()
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

    private inline fun logTime(functionName: String, block: () -> Unit) {
        val startTime = System.currentTimeMillis()
        block()
        Log.d(TAG, "$functionName took ${System.currentTimeMillis() - startTime} ms")
    }

    private val liveDataInitializationManager = LiveDataInitializationManager()

    /**
     * Handles the initialization of all LiveData.
     */
    private inner class LiveDataInitializationManager {
        /**
         * Initializes the various LiveData properties of this ViewModel. This **must** be run after
         * decomposing patients and readings, or else the values the functions will use will be
         * inconsistent.
         */
        @GuardedBy("isInitializedMutex")
        suspend fun initializeLiveData() {
            if (_isInitialized.value == true) return

            if (patientIsPregnant.value == null) {
                patientIsPregnant.value = false
            }

            logTime("initializeLiveData") {
                joinAll(
                    viewModelScope.launch {
                        setUpAgeLiveData()
                    },
                    viewModelScope.launch {
                        logTime("setUpSymptomsLiveData") { setUpSymptomsLiveData() }
                    },
                    viewModelScope.launch {
                        logTime("setupBloodPressureLiveData") {
                            setupBloodPressureLiveData()
                        }
                    },
                    viewModelScope.launch {
                        logTime("setupUrineTestAndSourcesLiveData") {
                            setupUrineTestAndSourcesLiveData()
                        }
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
                            // TODO: Handle the case where we are editing patient info only. (refer to issue 36)
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

        private fun setUpAgeLiveData() {
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

        /**
         * There's a really ugly edge case where tapping the Pregnant checkbox and inputting a
         * village number at the same time can result in the next button being active. Likely caused
         * by the coroutine for the valid patient before winning over the coroutine for the invalid
         * patient. Normally this doesn't happen since the user can only type in one field at a
         * time, but the CheckBox is a special case, since users can interact with it as they are
         * typing.
         *
         * So, we cancel the previous job to ensure the coroutine in [setupIsPatientValidLiveData]
         * can launch with the most recent [patientBuilder] values.
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
                    patientName,
                    patientId,
                    patientDob,
                    patientAge,
                    patientGestationalAge,
                    patientSex,
                    patientIsPregnant,
                    patientVillageNumber
                ).forEach { liveData ->
                    addSource(liveData) {
                        if (_isInitialized.value != true) return@addSource

                        isPatientValidJob?.cancel()
                            ?: Log.d(TAG, "isPatientValidJob: no job to cancel")
                        isPatientValidJob = viewModelScope.launch(Dispatchers.Default) {
                            // Let Espresso know we're busy
                            idlingResource.use {
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
            SymptomsState(currentSymptoms, app).let {
                _symptomsState.value = it
                otherSymptomsInput.value = it.otherSymptoms
            }

            otherSymptomsInput.apply {
                addSource(_symptomsState) {
                    if (value != it.otherSymptoms) {
                        value = it.otherSymptoms
                    }
                }
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

            val defaultEnglishSymptoms = app.getEnglishResources()
                .getStringArray(R.array.reading_symptoms)
            symptoms.apply {
                addSource(_symptomsState) { symptomsState ->
                    // Store only English symptoms.
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
                    // If not pregnant, make the input blank. Note: If this is the empty string,
                    // then an error will be triggered when the Pregnant CheckBox is ticked for the
                    // first time.
                    null
                } else {
                    when (val gestationalAge = patientGestationalAge.value) {
                        is GestationalAgeMonths -> {
                            // We don't want to show an excessive amount of decimal places.
                            DecimalFormat("#.####").format(gestationalAge.ageFromNow.asMonths())
                        }
                        is GestationalAgeWeeks -> {
                            gestationalAge.ageFromNow.weeks.toString()
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
                    // Setting urine test to not needed by default
                    false
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
                            leukocytes = leukocytes!!,
                            nitrites = nitrites!!,
                            glucose = glucose!!,
                            protein = protein!!,
                            blood = blood!!
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
    }

    /* Patient Info */
    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientId: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::id)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientName: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::name)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientDob: MediatorLiveData<String?> = patientBuilder.get<String?>(Patient::dateOfBirth)

    /**
     * Used in two-way Data Binding with PatientInfoFragment
     */
    val patientAge = MutableLiveData<Int?>(null)

    /**
     * Implicitly used in two-way Data Binding with PatientInfoFragment.
     * This listens to [patientGestationalAgeInput] and [patientGestationalAgeUnits].
     *
     * @see LiveDataInitializationManager.setupGestationAgeLiveData
     */
    val patientGestationalAge: MediatorLiveData<GestationalAge?> =
        patientBuilder.get<GestationalAge?>(Patient::gestationalAge)

    /**
     * Used in two-way Data Binding with PatientInfoFragment
     * Input that is taken directly from the form as a String.
     * Can hold either an integer or a Double (for months input).
     */
    val patientGestationalAgeInput: MutableLiveData<String> = MediatorLiveData<String>()

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientGestationalAgeUnits: MutableLiveData<String> = MediatorLiveData<String>()

    /**
     * Used in two-way Data Binding with PatientInfoFragment
     * @see com.cradleplatform.neptune.binding.Converter.sexToString
     */
    val patientSex: MutableLiveData<Sex?>
        get() = patientBuilder.get<Sex?>(Patient::sex)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientIsPregnant: MutableLiveData<Boolean?>
        get() = patientBuilder.get<Boolean?>(Patient::isPregnant)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientZone: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::zone)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientHouseholdNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::householdNumber)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientVillageNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::villageNumber)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientDrugHistory: MutableLiveData<String> =
        patientBuilder.get<String>(Patient::drugHistory, defaultValue = "")

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientMedicalHistory: MutableLiveData<String> =
        patientBuilder.get<String>(Patient::medicalHistory, defaultValue = "")

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientAllergies: MutableLiveData<String> =
        patientBuilder.get<String>(Patient::allergy, defaultValue = "")

    val patientLastEdited: MutableLiveData<Long?>
        get() = patientBuilder.get<Long?>(Patient::lastEdited)

    /** Used in SymptomsFragment */
    val symptoms: MediatorLiveData<List<String>> =
        readingBuilder.get<List<String>>(Reading::symptoms)

    /**
     * Keeps track of the symptoms that are checked. The checkboxes are as ordered in the string
     * array, R.array.reading_symptoms, except the last checkbox represents the other symptoms.
     * This will be changed directly by the CheckBoxes via [setSymptomsState]
     *
     * @see LiveDataInitializationManager.setUpSymptomsLiveData
     */
    private val _symptomsState = MediatorLiveData<SymptomsState>()
    val symptomsState: LiveData<SymptomsState> = _symptomsState

    /**
     * Used in two-way Data Binding with SymptomsFragment
     * The user's arbitrary text input for the "Other symptoms" field is tracked here.
     */
    val otherSymptomsInput = MediatorLiveData<String>()

    /**
     * Sets the symptom state for a particular symptom with index [index] inside of the symptoms
     * string array to [newValue].
     */
    @MainThread
    fun setSymptomsState(index: Int, newValue: Boolean) {
        val currentSymptomsState = _symptomsState.value ?: return

        // Prevent infinite loops and unnecessary updates.
        if (currentSymptomsState[index] != newValue) {
            currentSymptomsState[index] = newValue
            _symptomsState.value = currentSymptomsState
        }
    }

    /**
     * Implicitly in two-way Data Binding with VitalSignsFragment
     * Has [bloodPressureSystolicInput], [bloodPressureDiastolicInput], and
     * [bloodPressureHeartRateInput] as sources.
     *
     * @see LiveDataInitializationManager.setupBloodPressureLiveData
     */
    val bloodPressure: MediatorLiveData<BloodPressure?> =
        readingBuilder.get<BloodPressure?>(Reading::bloodPressure)

    /** Used in two-way Data Binding with VitalSignsFragment */
    val bloodPressureSystolicInput = MutableLiveData<Int?>()
    /** Used in two-way Data Binding with VitalSignsFragment */
    val bloodPressureDiastolicInput = MutableLiveData<Int?>()
    /** Used in two-way Data Binding with VitalSignsFragment */
    val bloodPressureHeartRateInput = MutableLiveData<Int?>()

    /**
     * Implicitly in two-way Data Binding with VitalSignsFragment
     * Has all of the below as sources.
     *
     * @see LiveDataInitializationManager.setupUrineTestAndSourcesLiveData
     */
    val urineTest: MediatorLiveData<UrineTest?> = readingBuilder.get<UrineTest?>(Reading::urineTest)
    /** Used in two-way Data Binding with VitalSignsFragment */
    val isUsingUrineTest = MutableLiveData<Boolean>()
    /** Used in two-way Data Binding with VitalSignsFragment */
    val urineTestLeukocytesInput = MutableLiveData<String>()
    /** Used in two-way Data Binding with VitalSignsFragment */
    val urineTestNitritesInput = MutableLiveData<String>()
    /** Used in two-way Data Binding with VitalSignsFragment */
    val urineTestGlucoseInput = MutableLiveData<String>()
    /** Used in two-way Data Binding with VitalSignsFragment */
    val urineTestProteinInput = MutableLiveData<String>()
    /** Used in two-way Data Binding with VitalSignsFragment */
    val urineTestBloodInput = MutableLiveData<String>()

    /* Referral Info */
    init {
        readingBuilder.get(Reading::referral, defaultValue = null)
    }

    /* Reading Info */
    val readingId: MutableLiveData<String> =
        readingBuilder.get(Reading::id, UUID.randomUUID().toString())

    val dateTimeTaken: MutableLiveData<Long?> = readingBuilder.get<Long?>(Reading::dateTaken)

    val lastEdited: MutableLiveData<Long?> = readingBuilder.get<Long?>(Reading::lastEdited)

    private val previousReadingIds: MutableLiveData<List<String>>
        get() = readingBuilder.get<List<String>>(Reading::previousReadingIds, emptyList())

    init {
        // Populate the builder with the current user's userId if there is none present.
        readingBuilder.get(
            Reading::userId,
            defaultValue = sharedPreferences.getIntOrNull(UserViewModel.USER_ID_KEY)
        )
    }

    /**
     * Describes the age input state. If the value inside is true, that means that age is derived
     * from date of birth, and the user has to clear the date of birth before adding new input.
     * If the value inside is false, they can add an approximate age, or overwrite that with a
     * date of birth via a date picker.
     */
    private val _patientIsExactDob: MediatorLiveData<Boolean?> =
        patientBuilder.get(Patient::isExactDateOfBirth, defaultValue = false)
    val patientIsExactDob: LiveData<Boolean?> = _patientIsExactDob

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

    private val isPatientValid = MediatorLiveData<Boolean>()

    private val navigationManager = NavigationManager()

    val isNextButtonEnabled: LiveData<Boolean> = navigationManager.isNextButtonEnabled

    val isInputEnabled: LiveData<Boolean>
        get() = navigationManager.isInputEnabled

    /**
     * What message to show for the message in the nav bar. If the message is nonempty, a
     * ProgressBar is also shown.
     */
    val bottomNavBarMessage: LiveData<String>
        get() = navigationManager.bottomNavBarMessage

    private val _actionBarSubtitle = MutableLiveData<String?>(null)
    val actionBarSubtitle: LiveData<String?>
        get() = _actionBarSubtitle

    fun clearBottomNavBarMessage() {
        navigationManager.clearBottomNavBarMessage()
    }

    fun setInputEnabledState(isEnabled: Boolean) {
        navigationManager.setInputEnabledState(isEnabled)
    }

    val isNetworkAvailable: LiveData<Boolean> =
        networkStateManager.getInternetConnectivityStatus()

    private suspend fun populateDateFields() {
        withContext(Dispatchers.Main) {
            // Update date time taken if null. This may be nonnull if we're editing a reading.
            val now = ZonedDateTime.now().toEpochSecond()
            if (dateTimeTaken.value == null) {
                dateTimeTaken.value = now
            }
            // Always update this.
            lastEdited.value = now
        }
    }

    /**
     * The [NavigationManager] handles the navigation in the Activity / Fragments.
     *
     * It manages the following:
     *
     * - The bottom navigation bar messages
     * - The Next button state, including when it is enabled and updating the
     *   criteria for when it is active based on the current destination
     * - Changes to the destination
     * - Validation when trying to press the Next button. For example, when
     *   trying to move on from the PatientInfoFragment, it will try to build
     *   a patient. When trying to move on from the VitalSignsFragment, it will
     *   try to build a reading.
     * - Getting the [adviceManager] to build advice for the AdviceFragment when
     *   trying to move to the AdviceFragment.
     *
     */
    private inner class NavigationManager {

        /**
         * Whether the next button is enabled.
         *
         * @see onDestinationChange
         */
        val isNextButtonEnabled = MediatorLiveData<Boolean>()

        val bottomNavBarMessage = MutableLiveData("")

        /**
         * Whether the form input fields are enabled or disabled.
         */
        val isInputEnabled = MutableLiveData(true)

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

                    // Opportunistically check if a patient with the same ID exists on the server.
                    // This only serves to lower the probability that a duplicate ID will be used,
                    // as it obviously doesn't work when the user has no internet.
                    if (isNetworkAvailable.value == true) {
                        Log.d(TAG, "checking if patient ID is in use on the server")
                        val existingOnServer =
                            patientManager.downloadPatientInfoFromServer(patient.id)
                        if (existingOnServer is NetworkResult.Success) {
                            // Send back the patient to give the user an option to
                            // download the patient with all of their readings.
                            Log.d(TAG, "patient ID already in use on the server")
                            return@withContext ReadingFlowError.ERROR_PATIENT_ID_IN_USE_ON_SERVER to
                                existingOnServer.value
                        }
                    } else {
                        Log.d(TAG, "skipping patient ID on server check due to no internet")
                    }

                    return@withContext ReadingFlowError.NO_ERROR to null
                }
                R.id.symptomsFragment -> {
                    // The symptoms fragment has no blockers.
                    return@withContext ReadingFlowError.NO_ERROR to null
                }
                R.id.vitalSignsFragment -> {
                    return@withContext if (validateVitalSignsAndBuildReading()) {
                        ReadingFlowError.NO_ERROR to null
                    } else {
                        ReadingFlowError.ERROR_INVALID_FIELDS to null
                    }
                }
                else -> {
                    return@withContext ReadingFlowError.NO_ERROR to null
                }
            }
        }

        /**
         * Validates the input on the VitalSignsFragment (basically tries to build a valid Reading),
         * and if valid, will get [adviceManager] to calculate the advice for the reading.
         *
         * @return true if a reading was build and advice was calculated, false if a reading could
         * not be built.
         */
        private suspend fun validateVitalSignsAndBuildReading(): Boolean {
            if (!verifyVitalSignsFragment()) {
                return false
            }
            yield()

            // Add data for now in order to get it to build. This needs to be set with the
            // proper values after pressing SAVE READING.
            readingBuilder.apply {
                set(Reading::patientId, originalPatient?.id ?: patientId.value)
                get(Reading::referral, defaultValue = null)
                get(Reading::followUp, defaultValue = null)
            }
            populateDateFields()

            val validReading = withContext(Dispatchers.Default) { attemptToBuildValidReading() }
                ?: return false

            // This will populate all advice fields. It will return false if it fails.
            if (!adviceManager.populateAllAdviceFields(validReading)) {
                return false
            }

            return true
        }

        fun setInputEnabledState(isEnabled: Boolean) {
            isInputEnabled.apply { if (value != isEnabled) value = isEnabled }
        }

        @MainThread
        fun setBottomNavBarMessage(message: String) {
            bottomNavBarMessage.apply { if (value != message) value = message }
        }

        @MainThread
        fun clearBottomNavBarMessage() {
            bottomNavBarMessage.apply { if (value?.isEmpty() != true) value = "" }
        }

        private val isNextButtonEnabledMutex = Mutex()

        /**
         * Updates the criteria that's needed to be satisfied in order for the next button to be
         * enabled.
         */
        @MainThread
        fun onDestinationChange(@IdRes currentDestinationId: Int) {
            updateActionBarSubtitle(patientName.value, patientId.value)
            navigationManager.clearBottomNavBarMessage()

            viewModelScope.launch(Dispatchers.Main) {
                isInitializedMutex.withLock {
                    if (_isInitialized.value == true) {
                        navigationManager.setInputEnabledState(true)
                    }
                }

                isNextButtonEnabledMutex.withLock {
                    vitalSignsFragmentVerifyJob?.cancel()

                    // Clear out any sources to be safe.
                    arrayOf(
                        isPatientValid,
                        bloodPressure,
                        isUsingUrineTest,
                        urineTest
                    ).forEach { isNextButtonEnabled.removeSource(it) }

                    isNextButtonEnabled.apply {
                        when (currentDestinationId) {
                            R.id.patientInfoFragment -> {
                                Log.d(
                                    TAG,
                                    "next button uses patientInfoFragment criteria: " +
                                        "patient must be valid"
                                )
                                addSource(isPatientValid) {
                                    viewModelScope.launch {
                                        isNextButtonEnabledMutex.withLock { value = it }
                                    }
                                }
                            }
                            R.id.symptomsFragment -> {
                                Log.d(
                                    TAG,
                                    "next button uses symptomsFragment criteria: no criteria"
                                )
                                value = true
                            }
                            R.id.vitalSignsFragment -> {
                                Log.d(
                                    TAG,
                                    "next button uses vitalSignsFragment criteria: " +
                                        "valid BloodPressure, " +
                                        "valid respiratoryRate, oxygenSaturation, temperature, " +
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
         * Ensures only one VitalSignsFragment Job is running so that old inputs don't override
         * new inputs.
         */
        private var vitalSignsFragmentVerifyJob: Job? = null

        private fun launchVitalSignsFragmentVerifyJob() {
            vitalSignsFragmentVerifyJob?.cancel()
            vitalSignsFragmentVerifyJob = viewModelScope.launch {
                idlingResource.use {
                    isNextButtonEnabledMutex.withLock {
                        val newValue = verifyVitalSignsFragment()
                        yield()
                        isNextButtonEnabled.value = newValue
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
            val bloodPressureValid = bloodPressure.value?.isValidInstance() ?: false
            if (!bloodPressureValid) return@withContext false
            yield()

            return@withContext if (isUsingUrineTest.value != false) {
                urineTest.value?.isValidInstance() ?: false
            } else {
                true
            }
        }
    }

    /**
     * Handles next button clicking by validating the current Fragment inputs.
     * Will be run on the main thread to ensure the values are consistent.
     *
     * @see NavigationManager.validateCurrentDestinationForNextButton
     */
    suspend fun validateCurrentDestinationForNextButton(
        @IdRes currentDestinationId: Int
    ): Pair<ReadingFlowError, Patient?> =
        navigationManager.validateCurrentDestinationForNextButton(currentDestinationId)

    /**
     * @return a non-null valid Patient if and only if the Patient that can be constructed with the
     * values in [patientBuilder] is valid.
     *
     * Restrict the logging to debug builds only, as this code is run every time a required patient
     * info field changes.
     */
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
                    "errors: ${patient.getAllMembersWithInvalidValues(app)}"
            )
            return null
        }

        if (DEBUG) Log.d(TAG, "attemptToBuildValidPatient: built a valid patient")
        return patient
    }

    /**
     * @return a non-null, valid [Reading] if and only if the Patient that can be constructed with
     * the values in [readingBuilder] is valid.
     */
    private fun attemptToBuildValidReading(): Reading? {
        if (!readingBuilder.isConstructable<Reading>()) {
            Log.d(
                TAG,
                "attemptToBuildValidReading: reading not constructable, " +
                    "missing ${readingBuilder.missingParameters(Reading::class)}"
            )
            return null
        }

        val reading = try {
            readingBuilder.build<Reading>()
        } catch (e: IllegalArgumentException) {
            Log.d(
                TAG,
                "attemptToBuildValidReading: " +
                    "reading failed to build, exception ${e.cause?.message ?: e.message}"
            )
            return null
        } catch (e: InvocationTargetException) {
            Log.d(
                TAG,
                "attemptToBuildValidReading: " +
                    "reading failed to build, exception ${e.cause?.message ?: e.message}"
            )
            return null
        }

        if (!reading.isValidInstance()) {
            Log.d(
                TAG,
                "attemptToBuildValidReading: " +
                    "reading is invalid, " +
                    "errors: ${reading.getAllMembersWithInvalidValues(app)}"
            )
            return null
        }

        Log.d(TAG, "attemptToBuildValidReading: built a valid reading")
        return reading
    }

    /**
     * Updates the criteria that's needed to be satisfied in order for the next button to be
     * enabled. This is here so that the Fragments don't have to access navigationManager.
     *
     * @see NavigationManager.onDestinationChange
     */
    @MainThread
    fun onDestinationChange(@IdRes currentDestinationId: Int) {
        navigationManager.onDestinationChange(currentDestinationId)
    }

    /**
     * Triggers a download from the server when there is a patient ID conflict.
     */
    suspend fun downloadAssociateAndSavePatient(
        patientId: String
    ): NetworkResult<PatientAndReadings> = patientManager.downloadAssociateAndSavePatient(patientId)

    private fun updateActionBarSubtitle(patientName: String?, patientId: String?) {
        val subtitle = if (patientName == null || patientId == null) {
            null
        } else {
            app.getString(
                R.string.reading_activity_subtitle_name_and_id,
                patientName,
                patientId
            )
        }
        val currentSubtitle: String? = _actionBarSubtitle.value
        if (currentSubtitle == subtitle) {
            return
        }
        // Lint is acting like _actionBarSubtitle holds a non-null type, but _actionBarSubtitle
        // holds a nullable String (because a null action bar title means there will be no subtitle)
        @SuppressLint("NullSafeMutableLiveData")
        _actionBarSubtitle.value = subtitle
    }

    private fun shouldSavePatient() =
        reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW

    /**
     * Checks if a patient has been synced to the server.
     */
    fun isPatientSynced(): Boolean {
        val lastServerUpdate = runBlocking { patientManager.getPatientById(
            patientId.value.toString())?.lastServerUpdate }
        return lastServerUpdate != null
    }

    /**
     * Updates a patient information (specifically used for updating lastServerUpdate)
     * after it has been sent to the server via SMS.
     */
    suspend fun patientSentViaSMS(patient: Patient) {
        patientManager.add(patient)
    }

    /** Save manager fields */

    private val saveManager = SaveManager()

    val isSaving: LiveData<Boolean>
        get() = saveManager.isSaving

    /**
     * This is here so that the Fragments don't have to access [saveManager].
     *
     * @see [SaveManager.saveWithReferral]
     */
    suspend fun saveWithReferral(
        referralOption: ReferralOption,
        referralComment: String,
        healthFacilityName: String
    ): ReadingFlowSaveResult {
        val saveResult = saveManager.saveWithReferral(referralOption, referralComment, healthFacilityName)
        if (saveResult is ReadingFlowSaveResult.SaveSuccessful) {
            originalReadingId?.let { readingManager.clearDateRecheckVitalsAndMarkForUpload(it) }
        }
        return saveResult
    }

    /**
     * This is here so that the Fragments don't have to access [saveManager].
     *
     * @see [SaveManager.save]
     */
    suspend fun save(): ReadingFlowSaveResult {
        val saveResult = saveManager.save()
        if (saveResult is ReadingFlowSaveResult.SaveSuccessful) {
            originalReadingId?.let {
                readingManager.clearDateRecheckVitalsAndMarkForUpload(
                    readingId = it,
                    lastEdited = ZonedDateTime.now().toEpochSecond()
                )
            }
        }
        return saveResult
    }

    /**
     * Manages the saving of the patient / reading, including sending referrals to the server or
     * giving information for the Fragment to construct an SMS message.
     */
    private inner class SaveManager {

        /**
         * Tries to construct a valid patient and a valid reading from the values contains in
         * [patientBuilder] and [readingBuilder]. This is meant to be used in order to construct a
         * patient and reading for saving.
         *
         * Will return a null pair if reading is invalid, or we are expected to save a new patient but
         * the built patient is invalid.
         *
         * Otherwise, it will return a Pair of a nullable Patient and a non-null Reading. The Patient
         * is null if it wasn't built.
         */
        private suspend fun constructValidPatientAndReadingFromBuilders(): Pair<Patient?, Reading>? {
            return withContext(Dispatchers.Default) {
                val patientAsync = async {
                    if (shouldSavePatient()) {
                        patientLastEdited.setValueOnMainThread(ZonedDateTime.now().toEpochSecond())
                        attemptToBuildValidPatient()
                    } else {
                        null
                    }
                }

                val readingAsync = async {
                    // Try to construct a reading for saving. It should be valid, as these validated
                    // properties should have been enforced in the previous fragments
                    // (VitalSignsFragment). We get null if it's not valid.
                    attemptToBuildValidReading()
                }

                val reading = readingAsync.await() ?: return@withContext null
                val patient = patientAsync.await()
                if (shouldSavePatient() && patient == null) {
                    null
                } else {
                    patient to reading
                }
            }
        }

        private val isSavingMutex = Mutex()
        val isSaving = MutableLiveData<Boolean>(false)

        /**
         * Saves the current patient and reading with a referral, with the type of referral as
         * given by [referralOption].
         *
         * If [referralOption] is [ReferralOption.SMS], the patient and reading will be saved to the
         * local database first with the referral in the reading, and then a success with a non-null
         * PatientAndReadings is returned. The referral dialog can then use the PatientAndReadings
         * object to construct an SMS body.
         *
         * If [referralOption] is [ReferralOption.WEB], the patient and reading will be sent to the
         * server first with the referral in the reading, then the patient and reading are saved in
         * the local database, and then a success with a null PatientAndReadings is returned. The
         * referral dialog should then finish.
         *
         * If an error occurs, like [ReadingFlowSaveResult.ErrorUploadingReferral] is returned,
         * then the PatientAndReadings will be null.
         *
         * @return A Pair of the save result and a PatientAndReadings object. The PairAndReadings
         * object is non-null iff referralOption is for SMS and it was successful.
         */
        suspend fun saveWithReferral(
            referralOption: ReferralOption,
            referralComment: String,
            healthFacilityName: String
        ): ReadingFlowSaveResult = withContext(Dispatchers.Default) {
            // Don't save if we are uninitialized for some reason.
            isInitializedMutex.withLock {
                if (_isInitialized.value == false) {
                    return@withContext ReadingFlowSaveResult.ErrorConstructing
                }
            }

            isSavingMutex.withLock {
                // Prevent saving from being run while it's already running. We have to do this
                // since the save button launches a coroutine.
                if (isSaving.value == true) {
                    return@withContext ReadingFlowSaveResult.ErrorConstructing
                }
                isSaving.setValueOnMainThread(true)

                if (referralOption == ReferralOption.NONE) {
                    isSaving.setValueOnMainThread(false)
                    return@withContext ReadingFlowSaveResult.ReferralRequired
                }

                // If this returns null, then something is invalid.
                val (patientFromBuilder, readingFromBuilder) =
                    constructValidPatientAndReadingFromBuilders()
                        ?: run {
                            isSaving.setValueOnMainThread(false)
                            return@withContext ReadingFlowSaveResult.ErrorConstructing
                        }

                readingFromBuilder.referral =
                    Referral(
                        id = UUID.randomUUID().toString(),
                        comment = referralComment,
                        healthFacilityName = healthFacilityName,
                        dateReferred = readingFromBuilder.dateTaken,
                        userId = sharedPreferences.getIntOrNull(UserViewModel.USER_ID_KEY),
                        patientId = readingFromBuilder.patientId,
                        actionTaken = null,
                        cancelReason = null,
                        notAttendReason = null,
                        isCancelled = false,
                        notAttended = false,
                        isAssessed = false,
                        lastEdited = readingFromBuilder.dateTaken
                    )

                yield()

                // If original patient is null, then that implies that we should save the patient.
                check(originalPatient != null || shouldSavePatient())

                // Choose a patient to use.
                // We want to use the originalPatient if available.
                // If it's not available, then shouldSavePatient() is true, so we must be
                // creating a new patient, and so patientFromBuilder should not be null.
                val patient = originalPatient ?: patientFromBuilder ?: error("unreachable state")
                yield()

                // Handle the referral based on the type.
                when (referralOption) {
                    ReferralOption.HTTP -> {
                        // Upload patient and reading to the server, with the referral embedded in
                        // the reading.
                        val result =
                            readingUploadManager.uploadReferralViaWebCoupled(patient, readingFromBuilder)
                        if (result is NetworkResult.Success) {
                            // Save the patient and reading in local database
                            // Note: If patient already exists on server, then
                            val patientFromServer = result.value.patient
                            val readingFromServer = result.value.readings[0]
                            check(readingFromBuilder.id == readingFromServer.id)

                            patientManager.addPatientWithReading(
                                patientFromServer,
                                readingFromServer,
                                isReadingFromServer = true
                            )

                            // Dialog should finish the Activity.
                            // Don't set isSaving to false to ensure this can't be run again.
                            return@withContext ReadingFlowSaveResult.SaveSuccessful.NoSmsNeeded
                        } else {
                            isSaving.setValueOnMainThread(false)
                            return@withContext ReadingFlowSaveResult.ErrorUploadingReferral
                        }
                    }
                    ReferralOption.SMS -> {
                        // If we're just sending by SMS, we first store it locally in the database
                        // and then have the DialogFragment that calls this to launch an SMS intent.
                        // We don't mark the reading as uploaded to the server, as we can't know
                        // for sure that the reading has been uploaded. When the VHT goes to
                        // sync this will be resolved, either by overwriting this reading with
                        // the one from the server (if it made it successfully) or by uploading
                        // it through the sync process (if it failed to make it to the server).
                        handleStoringPatientReadingFromBuilders(
                            patientFromBuilder,
                            readingFromBuilder
                        )

                        // Pass a PatientAndReadings object for the SMS message.
                        // Don't set isSaving to false to ensure this can't be run again.
                        return@withContext ReadingFlowSaveResult.SaveSuccessful.ReferralSmsNeeded(
                            PatientAndReadings(patient, listOf(readingFromBuilder))
                        )
                    }
                    else -> error("unreachable")
                }
            }
        }

        /**
         * When the save button in the AdviceFragment is clicked, this is run/
         * Saves the patient (if creating a new patient) and saves the reading to the database.
         * If sending a referral, then we need to handle that elsewhere.
         *
         * @return a [ReadingFlowSaveResult]
         */
        suspend fun save(): ReadingFlowSaveResult = withContext(Dispatchers.Default) {
            // Don't save if we are uninitialized for some reason.
            isInitializedMutex.withLock {
                if (_isInitialized.value == false) {
                    return@withContext ReadingFlowSaveResult.ErrorConstructing
                }
            }

            isSavingMutex.withLock {
                // Prevent saving from being run while it's already running. We have to do
                // this since the save button launches a coroutine.
                if (isSaving.value == true) {
                    return@withContext ReadingFlowSaveResult.ErrorConstructing
                }

                // If user selected to send a referral, handle that. When the AdviceFragment sees
                // REFERRAL_REQUIRED, it launches a referral dialog.
                if (adviceReferralButtonId.value == R.id.send_referral_radio_button) {
                    // Don't save the reading / patient yet; we need the AdviceFragment to launch a
                    // referral dialog.
                    return@withContext ReadingFlowSaveResult.ReferralRequired
                }

                // Otherwise, we're in the main saving path.
                isSaving.setValueOnMainThread(true)
                yield()

                val (patient, reading) = constructValidPatientAndReadingFromBuilders()
                    ?: run {
                        isSaving.setValueOnMainThread(false)
                        return@withContext ReadingFlowSaveResult.ErrorConstructing
                    }
                yield()

                handleStoringPatientReadingFromBuilders(patient, reading)

                if (reading.isVitalRecheckRequiredNow)
                    return@withContext ReadingFlowSaveResult.SaveSuccessful.ReCheckNeededNow
                else if (reading.isVitalRecheckRequired)
                    return@withContext ReadingFlowSaveResult.SaveSuccessful.ReCheckNeededInFuture

                // Don't set isSavingReferral to false to ensure this can't be run again.
                return@withContext ReadingFlowSaveResult.SaveSuccessful.NoSmsNeeded
            }
        }

        /**
         * Will always save the reading to the database.
         * The patient is saved to the database only if [shouldSavePatient] is true and the
         * patient from the builder is non-null.
         */
        private suspend fun handleStoringPatientReadingFromBuilders(
            patientFromBuilder: Patient?,
            readingFromBuilder: Reading
        ) {
            if (shouldSavePatient() && patientFromBuilder != null) {
                // Insertion needs to be atomic.
                patientManager.addPatientWithReading(
                    patientFromBuilder,
                    readingFromBuilder,
                    isReadingFromServer = false
                )
            } else {
                readingManager.addReading(readingFromBuilder, isReadingFromServer = false)
            }
        }

        private val isSavingReferralMutex = Mutex()
        val isSavingReferral = MutableLiveData<Boolean>(false)
    }

    /** Advice fields */

    private val adviceManager = AdviceManager()

    val currentValidPatientAndRetestGroup: LiveData<Pair<Reading, RetestGroup>?>
        get() = adviceManager.currentValidReadingAndRetestGroup

    val adviceRecheckButtonId = MutableLiveData<Int?>()
    val adviceFollowUpButtonId = MutableLiveData<Int?>()
    val adviceReferralButtonId = MutableLiveData<Int?>()

    fun isSendingReferral() = adviceReferralButtonId.value == R.id.send_referral_radio_button

    val adviceText: LiveData<String> = adviceManager.adviceText
    val recommendedAdvice: LiveData<RecommendedAdvice>
        get() = adviceManager.currentRecommendedAdvice

    val dateRecheckVitalsNeeded: MediatorLiveData<Long?> =
        readingBuilder.get(Reading::dateRetestNeeded, null).apply {
            addSource(adviceRecheckButtonId) {
                value = when (it) {
                    R.id.recheck_vitals_after_15_min_radio_button -> {
                        ZonedDateTime.now().toEpochSecond() +
                            TimeUnit.MINUTES.toSeconds(DEFAULT_VITALS_RECHECK_INTERVAL_IN_MIN)
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
     * Describes the current recommended advice. The RadioButtons will indicate which option is
     * recommended.
     *
     * @see com.cradleplatform.neptune.binding.ReadingBindingAdapters.addRecommendedToEndWhen
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
                if (!reading.isValidInstance()) {
                    currentValidReadingAndRetestGroup.setValueOnMainThread(null)
                    return@withContext false
                }

                val retestGroup = readingManager.createRetestGroup(reading)

                // Set the radio buttons.
                val retestAdvice: RetestAdvice = retestGroup.getRetestAdvice()

                val isFollowupRecommended = retestGroup.mostRecentReadingAnalysis.isRed

                val isReferralRecommended =
                    retestGroup.mostRecentReadingAnalysis.isReferralRecommended

                val currentRecommendedAdvice = RecommendedAdvice(
                    retestAdvice = retestAdvice,
                    isFollowupNeeded = isFollowupRecommended,
                    isReferralRecommended = isReferralRecommended
                )

                updateAdviceLiveData(reading, retestGroup, currentRecommendedAdvice)

                return@withContext true
            }

        /**
         * Updates LiveData related to advice.
         *
         * Updates both the LiveData in here and the MutableLiveData in the ViewModel.
         */
        private suspend fun updateAdviceLiveData(
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
     * @see ErrorMessageManager.errorMap
     * @see com.cradleplatform.neptune.binding.ReadingBindingAdapters.setError
     */
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
                addSource(patientId) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = Patient::id,
                        verifier = Patient.Companion
                    )
                }
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
                        propertyToCheck = Patient::dateOfBirth,
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
                        verifier = Patient.Companion
                    )
                }

                // Errors from Readings
                addSource(bloodPressureSystolicInput) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = BloodPressure::systolic,
                        verifier = BloodPressure.Companion
                    )
                }
                addSource(bloodPressureDiastolicInput) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = BloodPressure::diastolic,
                        verifier = BloodPressure.Companion
                    )
                }
                addSource(bloodPressureHeartRateInput) {
                    testValueForValidityAndSetErrorMapAsync(
                        value = it,
                        propertyToCheck = BloodPressure::heartRate,
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
                            value = it,
                            propertyToCheck = property,
                            verifier = UrineTest.Companion
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
         * @see com.cradleplatform.neptune.binding.ReadingBindingAdapters.setError
         */
        private fun <T> testValueForValidityAndSetErrorMapAsync(
            value: T?,
            propertyToCheck: KProperty<T>,
            verifier: Verifiable.Verifier<*>,
            propertyForErrorMapKey: KProperty<*> = propertyToCheck,
            currentValuesMap: Map<String, Any?>? = null
        ) {
            idlingResource?.increment()
            viewModelScope.launch(Dispatchers.Default) {
                // Selects the map to use for all the other fields on the form. The validity of a
                // property might depend on other properties being there, e.g. a null date of birth
                // is acceptable if there is an estimated age.
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

                // Get the actual validity status and any error message.
                val verificationResult = verifier.isValueValid(
                    property = propertyToCheck,
                    value = value,
                    context = app,
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
                idlingResource?.decrement()
            }
        }
    }

    companion object {
        private const val TAG = "PatientReadingViewModel"
        private const val DEFAULT_VITALS_RECHECK_INTERVAL_IN_MIN = 15L
    }
}

enum class ReferralOption {
    NONE, SMS, HTTP
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

sealed interface ReadingFlowSaveResult {
    /**
     * Indicates when saving the referral to the local database was successful.
     */
    sealed interface SaveSuccessful : ReadingFlowSaveResult {
        object NoSmsNeeded : SaveSuccessful

        object ReCheckNeededNow : SaveSuccessful

        object ReCheckNeededInFuture : SaveSuccessful

        /**
         * Indicates when saving the referral to the local database was successful but referral SMS
         * still needs to be sent. The Intent to send an SMS should be launched,
         * because the patient and reading are valid and stored in the local database.
         */
        @JvmInline
        value class ReferralSmsNeeded(val patientInfoForReferral: PatientAndReadings) :
            SaveSuccessful
    }

    /**
     * Indicates when a referral dialog is required.
     */
    object ReferralRequired : ReadingFlowSaveResult

    /**
     * Indicates an error when trying to upload a referral via web
     */
    object ErrorUploadingReferral : ReadingFlowSaveResult

    /**
     * Indicates an error with constructing a valid patient / reading.
     */
    object ErrorConstructing : ReadingFlowSaveResult
}

sealed interface ReferralFlowSaveResult {
    /**
     * Indicates when saving the referral to the local database was successful.
     */
    sealed interface SaveSuccessful : ReferralFlowSaveResult {
        object NoSmsNeeded : SaveSuccessful

        object ReCheckNeededNow : SaveSuccessful

        object ReCheckNeededInFuture : SaveSuccessful

        /**
         * Indicates when saving the referral to the local database was successful but referral SMS
         * still needs to be sent. The Intent to send an SMS should be launched,
         * because the patient and reading are valid and stored in the local database.
         */
        @JvmInline
        value class ReferralSmsNeeded(val patientInfoForReferral: PatientAndReferrals) :
            SaveSuccessful
    }

    /**
     * Indicates when a referral successfully saved locally, but a network error occurred when
     * attempting to communicate with the server
     */
    object NetworkError: ReferralFlowSaveResult

    /**
     * Indicates when a referral dialog is required.
     */
    object ReferralRequired : ReferralFlowSaveResult

    /**
     * Indicates an error when trying to upload a referral via web
     */
    object ErrorUploadingReferral : ReferralFlowSaveResult

    /**
     * Indicates an error with constructing a valid patient / reading.
     */
    object ErrorConstructing : ReferralFlowSaveResult
}
