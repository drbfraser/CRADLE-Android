package com.cradle.neptune.viewmodel

import android.app.Application
import android.util.Log
import androidx.annotation.MainThread
import androidx.databinding.ObservableArrayMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.cradle.neptune.R
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.GestationalAgeMonths
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import com.cradle.neptune.utilitiles.LiveDataDynamicModelBuilder
import com.cradle.neptune.view.ReadingActivity
import java.lang.IllegalStateException
import kotlin.reflect.KProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The ViewModel that is used in [ReadingActivity] and its [BaseFragment]s
 */
@SuppressWarnings("LargeClass")
class PatientReadingViewModel constructor(
    private val readingManager: ReadingManager,
    private val patientManager: PatientManager,
    private val app: Application
) : AndroidViewModel(app) {
    private val patientBuilder = LiveDataDynamicModelBuilder()
    private val readingBuilder = LiveDataDynamicModelBuilder()

    private lateinit var reasonForLaunch: ReadingActivity.LaunchReason

    /**
     * If not creating a new patient, the patient and reading requested will be asynchronously
     * initialized, and then [isInitialized] will emit true if no errors occur.
     */
    @MainThread
    fun initialize(
        launchReason: ReadingActivity.LaunchReason,
        readingId: String?
    ) {
        if (_isInitialized.value == true) {
            return
        }

        reasonForLaunch = launchReason
        if (reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
            _isInitialized.value = true
            return
        }

        check(!readingId.isNullOrEmpty()) {
            "was given no readingId despite not creating new reading"
        }

        viewModelScope.launch(Dispatchers.Default) {
            val reading = readingManager.getReadingById(readingId)
                ?: throw IllegalStateException("no reading associated with given id")
            val patient = patientManager.getPatientById(reading.patientId)
                ?: throw IllegalStateException("no patient associated with given reading")

            when (reasonForLaunch) {
                ReadingActivity.LaunchReason.LAUNCH_REASON_EDIT -> {
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
                    check(false) { "invalid launch reason" }
                }
            }

            withContext(Dispatchers.Main) {
                _isInitialized.value = true
            }
            Log.d("PatientReadingViewModel", "initialized, with id ${patientId.value}")
        }
    }

    private fun decompose(patient: Patient) {
        if (_isInitialized.value == true) {
            return
        }
        patientBuilder.decompose(patient)
    }

    private fun decompose(patient: Patient, reading: Reading) {
        if (_isInitialized.value == true) {
            return
        }
        patientBuilder.decompose(patient)
        // We have to do this so that the Activity and ViewModel don't share the same reference for
        // symptoms.
        val symptomsCopy = ArrayList<String>().apply {
            addAll(reading.symptoms as ArrayList<String>)
        }
        readingBuilder.decompose(reading.copy(symptoms = symptomsCopy))
    }

    private val _isInitialized = MutableLiveData<Boolean>(false)
    val isInitialized: LiveData<Boolean>
        get() = _isInitialized

    /* Patient Info */
    val patientId: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::id)

    val patientName: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::name)

    val patientDob: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::dob)

    val patientAge: MutableLiveData<Int?>
        get() = patientBuilder.get<Int?>(Patient::age)

    val patientGestationalAge: MutableLiveData<GestationalAge?>
        get() = patientBuilder.get<GestationalAge?>(Patient::gestationalAge)

    val patientGestationalAgeUnits: LiveData<String> = Transformations.map(patientGestationalAge) {
        if (it is GestationalAgeMonths) {
            app.resources.getStringArray(R.array.reading_ga_units)[1]
        } else {
            app.resources.getStringArray(R.array.reading_ga_units)[0]
        }
    }

    val patientSex: MutableLiveData<Sex?>
        get() = patientBuilder.get<Sex?>(Patient::sex)

    val patientIsPregnant: MutableLiveData<Boolean?>
        get() = patientBuilder.get<Boolean?>(Patient::isPregnant)

    val patientZone: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::zone)

    val patientVillageNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::villageNumber)

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

    val symptoms: MutableLiveData<List<String>?>
        get() = readingBuilder.get<List<String>?>(Reading::symptoms)

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
    val errorMap = ObservableArrayMap<String, String?>()

    /**
     * Describes the age input state. If the value inside is true, that means that age is derived
     * from date of birth, and the user has to clear the date of birth before adding new input.
     * If the value inside is false, they can add an approximate age, or overwrite that with a
     * date of birth via a date picker.
     */
    private val _isUsingDateOfBirth = MediatorLiveData<Boolean>().apply {
        // Catch the presence of the date of birth as soon as it decomposes.
        addSource(patientDob) { dob ->
            // Don't need to listen to changes; should use `setUsingDateOfBirth` to change this
            // in the future.
            removeSource(patientDob)
            // We use the date of birth iff there is a date of birth.
            value = dob != null
        }
    }
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
    fun getValidityErrorMessagePair(
        value: Any?,
        isForPatient: Boolean,
        property: KProperty<*>,
        putInErrorMap: Boolean = true
    ): Pair<Boolean, String> = if (isForPatient) {
            Patient.isValueValid(
                property, value, getApplication(),
                instance = null, currentValues = patientBuilder.publicMap
            )
        } else {
            Reading.isValueValid(
                property, value, getApplication(),
                instance = null, currentValues = readingBuilder.publicMap
            )
        }
        .also {
            if (!putInErrorMap) {
                return@also
            }

            if (!it.first) {
                errorMap[property.name] = it.second
            } else {
                errorMap[property.name] = null
            }
        }
}
