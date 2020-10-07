package com.cradle.neptune.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import com.cradle.neptune.utilitiles.DynamicModelBuilder
import com.cradle.neptune.utilitiles.LiveDataDynamicModelBuilder
import com.cradle.neptune.utilitiles.discard
import com.cradle.neptune.view.ReadingActivity
import java.lang.IllegalStateException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A bridge between the legacy model API used by the new model structures.
 *
 * Once we migrate the UI to Kotlin, the UI will most likely interface
 * directly with [DynamicModelBuilder] instances instead of going through
 * this object.
 *
 * TODO: This class is only temporary and should be replaced with a proper
 *   design pattern.
 */
@SuppressWarnings("LargeClass")
class PatientReadingViewModel constructor(
    private val readingManager: ReadingManager,
    private val patientManager: PatientManager
) : ViewModel() {
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
        reasonForLaunch = launchReason
        if (reasonForLaunch == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
            _isInitialized.value = true
            return
        }

        check(readingId != null) { "was given no readingId despite not creating new reading" }

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
        // TODO: Completely revisit this ViewModel setup. We have to do this so that the Activity
        //  and ViewModel don't share the same reference for symptoms.
        val symptomsCopy = ArrayList<String>().apply {
            addAll(reading.symptoms as ArrayList<String>)
        }
        readingBuilder.decompose(reading.copy(symptoms = symptomsCopy))
    }

    private val _isInitialized = MutableLiveData<Boolean>(false)
    val isInitialized: LiveData<Boolean>
        get() = _isInitialized

    /* Patient Info */
    var patientId: MutableLiveData<String>
        get() = patientBuilder.get(Patient::id, "")
        set(value) = patientBuilder.set(Patient::id, value).discard()

    var patientName: MutableLiveData<String>
        get() = patientBuilder.get(Patient::name, "")
        set(value) = patientBuilder.set(Patient::name, value).discard()

    var patientDob: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::dob)
        set(value) = patientBuilder.set(Patient::dob, value).discard()

    var patientAge: MutableLiveData<Int?>
        get() = patientBuilder.get<Int?>(Patient::age)
        set(value) = patientBuilder.set(Patient::age, value).discard()

    var patientGestationalAge: MutableLiveData<GestationalAge?>
        get() = patientBuilder.get<GestationalAge?>(Patient::gestationalAge)
        set(value) = patientBuilder.set(Patient::gestationalAge, value).discard()

    var patientSex: MutableLiveData<Sex?>
        get() = patientBuilder.get<Sex?>(Patient::sex)
        set(value) = patientBuilder.set(Patient::sex, value).discard()

    var patientIsPregnant: MutableLiveData<Boolean?>
        get() = patientBuilder.get<Boolean?>(Patient::isPregnant)
        set(value) = patientBuilder.set(Patient::isPregnant, value).discard()

    var patientZone: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::zone)
        set(value) = patientBuilder.set(Patient::zone, value).discard()

    var patientVillageNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::villageNumber)
        set(value) = patientBuilder.set(Patient::villageNumber, value).discard()

    var patientLastEdited: MutableLiveData<Long?>
        get() = patientBuilder.get<Long?>(Patient::lastEdited)
        set(value) = patientBuilder.set(Patient::lastEdited, value).discard()

    /* Blood Pressure Info */
    var bloodPressure: MutableLiveData<BloodPressure?>
        get() = readingBuilder.get<BloodPressure?>(Reading::bloodPressure)
        set(value) = readingBuilder.set(Reading::bloodPressure, value).discard()

    /* Urine Test Info */
    var urineTest: MutableLiveData<UrineTest?>
        get() = readingBuilder.get<UrineTest?>(Reading::urineTest)
        set(value) = readingBuilder.set(Reading::urineTest, value).discard()

    /* Referral Info */
    var referral: MutableLiveData<Referral?>
        get() = readingBuilder.get<Referral?>(Reading::referral)
        set(value) = readingBuilder.set(Reading::referral, value).discard()

    /* Reading Info */
    var readingId: MutableLiveData<String>
        get() = readingBuilder.get(Reading::id, "")
        set(value) = readingBuilder.set(Reading::id, value).discard()

    var dateTimeTaken: MutableLiveData<Long?>
        get() = readingBuilder.get<Long?>(Reading::dateTimeTaken)
        set(value) = readingBuilder.set(Reading::dateTimeTaken, value).discard()

    var symptoms: MutableLiveData<List<String>?>
        get() = readingBuilder.get<List<String>?>(Reading::symptoms)
        set(value) = readingBuilder.set(Reading::symptoms, value).discard()

    var dateRecheckVitalsNeeded: MutableLiveData<Long?>
        get() = readingBuilder.get<Long?>(Reading::dateRecheckVitalsNeeded)
        set(value) = readingBuilder.set(Reading::dateRecheckVitalsNeeded, value).discard()

    var isFlaggedForFollowUp: MutableLiveData<Boolean?>
        get() = readingBuilder.get<Boolean?>(Reading::isFlaggedForFollowUp)
        set(value) = readingBuilder.set(Reading::isFlaggedForFollowUp, value).discard()

    @Suppress("UNCHECKED_CAST")
    var previousReadingIds: MutableLiveData<MutableList<String>?>
        get() = readingBuilder.get<List<String>?>(Reading::previousReadingIds)
            as MutableLiveData<MutableList<String>?>
        set(value) = readingBuilder.set(Reading::previousReadingIds, value).discard()
}
