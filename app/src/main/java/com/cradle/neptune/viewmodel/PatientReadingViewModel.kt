package com.cradle.neptune.viewmodel

import android.app.Application
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import com.cradle.neptune.utilitiles.LiveDataDynamicModelBuilder
import com.cradle.neptune.view.ReadingActivity
import com.google.android.material.textfield.TextInputEditText
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
        get() = patientBuilder.get(Patient::id, "")

    val patientName: MutableLiveData<String>
        get() = patientBuilder.get(Patient::name, "")

    val patientDob: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::dob)

    val patientAge: MutableLiveData<Int?>
        get() = patientBuilder.get<Int?>(Patient::age)

    val patientGestationalAge: MutableLiveData<GestationalAge?>
        get() = patientBuilder.get<GestationalAge?>(Patient::gestationalAge)

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

    private val _errors = MutableLiveData<Map<KProperty<*>, String?>>(hashMapOf())
    val errors: LiveData<Map<KProperty<*>, String?>>
        get() = _errors

    fun onInputTextChanged(text: CharSequence, property: KProperty<*>?, isPatientField: Boolean) {
        Toast.makeText(getApplication() as Application, "Hello", Toast.LENGTH_SHORT).show()
        if (property == null) {
            // TODO: Other things

            return
        }

        if (isPatientField) {
            with(Patient.isValueValid(property, text, getApplication())) {
            }
        }
    }

    /**
     * @param isForPatient True if viewing patient property; false if viewing reading property
     */
    fun handleEditTextErrors(
        rootView: View,
        @IdRes resId: Int,
        value: Any?,
        isForPatient: Boolean,
        property: KProperty<*>
    ) {
        val textView = rootView.findViewById<TextInputEditText>(resId) ?: return
        val (isValid, errorMsg) = if (isForPatient) {
            Patient.isValueValid(
                property, value, getApplication(),
                instance = null, currentValues = patientBuilder.publicMap
            )
        } else {
            TODO("Implement validation for Reading")
        }
        textView.error = if (isValid) null else errorMsg
    }
}
