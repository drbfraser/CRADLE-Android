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
import androidx.test.espresso.idling.CountingIdlingResource
import com.cradleplatform.neptune.BuildConfig
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.setValueOnMainThread
import com.cradleplatform.neptune.ext.use
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.GestationalAge
import com.cradleplatform.neptune.model.GestationalAgeMonths
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.model.Verifiable
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.utilities.LiveDataDynamicModelBuilder
import com.cradleplatform.neptune.utilities.Months
import com.cradleplatform.neptune.utilities.Weeks
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import com.cradleplatform.neptune.viewmodel.EditPatientViewModel.LiveDataInitializationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.threeten.bp.ZonedDateTime
import java.lang.reflect.InvocationTargetException
import java.text.DecimalFormat
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
 * @see EditPatientViewModel.LiveDataInitializationManager
 * @see EditPatientViewModel.ErrorMessageManager
 * @see EditPatientViewModel.SaveManager
 */

@HiltViewModel
class EditPatientViewModel @Inject constructor(
    private val patientManager: PatientManager,
    @ApplicationContext @SuppressLint("StaticFieldLeak")
    private val app: Context
) : ViewModel() {
    private val patientBuilder = LiveDataDynamicModelBuilder()

    companion object {
        private const val TAG = "EditPatientViewModel"
    }

    /**
     * This is null if and only if this [ReadingActivity] is not being tested under Espresso.
     */
    var idlingResource: CountingIdlingResource? = null

    private val monthsUnitString = app.resources
        .getStringArray(R.array.reading_ga_units)[GEST_AGE_UNIT_MONTHS_INDEX]
    private val weeksUnitString = app.resources
        .getStringArray(R.array.reading_ga_units)[GEST_AGE_UNIT_WEEKS_INDEX]

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
        patientId: String? = null
    ) {
        viewModelScope.launch(Dispatchers.Main) {
            val startTime = System.currentTimeMillis()
            isInitializedMutex.lock()
            try {
                Log.d(TAG, "initialize start")
                if (_isInitialized.value == true) {
                    return@launch
                }

                if (!patientId.isNullOrBlank()) {
                    val patient = patientManager.getPatientById(patientId)
                        ?: error("no patient with given id")
                    originalPatient = patient
                    decompose(patient)
                    return@launch
                }


            } finally {
                // originalPatient is null implies that we should save the patient.
                check(shouldSavePatient())

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

    fun getPatient() : Patient? {
        return originalPatient
    }

    /**
     * Decompose the patient. Needs to be on the main thread, because we need the LiveData values
     * to be instantly set via [MutableLiveData.setValue] and not [MutableLiveData.postValue].
     */
    @GuardedBy("isInitializedMutex")
    private suspend fun decompose(patient: Patient) = withContext(Dispatchers.Main) {
        patientBuilder.decompose(patient)
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
    }

    /* Patient Info */
    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientId: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::id)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientName: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::name)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientDob: MediatorLiveData<String?> = patientBuilder.get<String?>(Patient::dob)

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
    val patientAllergies: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::allergy)

    val patientLastEdited: MutableLiveData<Long?>
        get() = patientBuilder.get<Long?>(Patient::lastEdited)

    /**
     * Describes the age input state. If the value inside is true, that means that age is derived
     * from date of birth, and the user has to clear the date of birth before adding new input.
     * If the value inside is false, they can add an approximate age, or overwrite that with a
     * date of birth via a date picker.
     */
    private val _patientIsExactDob: MediatorLiveData<Boolean?> =
        patientBuilder.get(Patient::isExactDob, defaultValue = false)
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

    val isNetworkAvailable = NetworkAvailableLiveData(app)

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

    private fun shouldSavePatient() = true

    /** Save manager fields */

    private val saveManager = SaveManager()

    val isSaving: LiveData<Boolean>
        get() = saveManager.isSaving

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
        private suspend fun constructValidPatientFromBuilders() : Patient? {
            return withContext(Dispatchers.Default) {
                val patientAsync = async {
                    if (shouldSavePatient()) {
                        patientLastEdited.setValueOnMainThread(ZonedDateTime.now().toEpochSecond())
                        attemptToBuildValidPatient()
                    } else {
                        null
                    }
                }

                val patient = patientAsync.await()
                if (shouldSavePatient() && patient == null) {
                    null
                } else {
                    patient
                }
            }
        }

        private val isSavingMutex = Mutex()
        val isSaving = MutableLiveData<Boolean>(false)

        /**
         * When the save button in the AdviceFragment is clicked, this is run/
         * Saves the patient (if creating a new patient) and saves the reading to the database.
         * If sending a referral, then we need to handle that elsewhere.
         *
         * FOR ME: if no network connection, save here. Otherwise you'll handle this when sending through web
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

                // Otherwise, we're in the main saving path.
                isSaving.setValueOnMainThread(true)
                yield()

                val patient= constructValidPatientFromBuilders()
                    ?: run {
                        isSaving.setValueOnMainThread(false)
                        return@withContext ReadingFlowSaveResult.ErrorConstructing
                    }
                yield()

                handleStoringPatientFromBuilders(patient)

                // Don't set isSaving to false to ensure this can't be run again.
                return@withContext ReadingFlowSaveResult.SaveSuccessful.NoSmsNeeded
            }
        }

        /**
         * Will always save the reading to the database.
         * The patient is saved to the database only if [shouldSavePatient] is true and the
         * patient from the builder is non-null.
         */
        private suspend fun handleStoringPatientFromBuilders(
            patientFromBuilder: Patient
        ) {
            if (shouldSavePatient() && patientFromBuilder != null) {
                // Insertion needs to be atomic.
                //TODO: add patient no readings! to patient manager
                //patientManager.addPatientWithReading(
                ///    patientFromBuilder
                //)
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
         * @see com.cradleplatform.neptune.view.EditPatientInfoActivity.setError
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
}