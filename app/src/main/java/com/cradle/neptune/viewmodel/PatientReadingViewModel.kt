package com.cradle.neptune.viewmodel

import android.content.Context
import com.cradle.neptune.R
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.ReadingMetadata
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.RetestGroup
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import com.cradle.neptune.utilitiles.DynamicModelBuilder
import com.cradle.neptune.utilitiles.UnixTimestamp
import com.cradle.neptune.utilitiles.discard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.threeten.bp.ZonedDateTime

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
class PatientReadingViewModel() {
    private val patientBuilder = DynamicModelBuilder()
    private val readingBuilder = DynamicModelBuilder()

    /**
     * Constructs a view model for an existing patient.
     */
    constructor(patient: Patient) : this() {
        patientBuilder.decompose(patient)
    }

    /**
     * Constructs a view model from an existing patient and reading.
     */
    constructor(patient: Patient, reading: Reading) : this() {
        patientBuilder.decompose(patient)
        readingBuilder.decompose(reading)
    }

    /* Patient Info */
    var patientId: String?
        get() = patientBuilder.get(Patient::id) as String?
        set(value) = patientBuilder.set(Patient::id, value).discard()

    var patientName: String?
        get() = patientBuilder.get(Patient::name) as String?
        set(value) = patientBuilder.set(Patient::name, value).discard()

    var patientDob: String?
        get() = patientBuilder.get(Patient::dob) as String?
        set(value) = patientBuilder.set(Patient::dob, value).discard()

    var patientAge: Int?
        get() = patientBuilder.get(Patient::age) as Int?
        set(value) = patientBuilder.set(Patient::age, value).discard()

    var patientGestationalAge: GestationalAge?
        get() = patientBuilder.get(Patient::gestationalAge) as GestationalAge?
        set(value) = patientBuilder.set(Patient::gestationalAge, value).discard()

    var patientSex: Sex?
        get() = patientBuilder.get(Patient::sex) as Sex?
        set(value) = patientBuilder.set(Patient::sex, value).discard()

    var patientIsPregnant: Boolean?
        get() = patientBuilder.get(Patient::isPregnant) as Boolean?
        set(value) = patientBuilder.set(Patient::isPregnant, value).discard()

    var patientZone: String?
        get() = patientBuilder.get(Patient::zone) as String?
        set(value) = patientBuilder.set(Patient::zone, value).discard()

    var patientVillageNumber: String?
        get() = patientBuilder.get(Patient::villageNumber) as String?
        set(value) = patientBuilder.set(Patient::villageNumber, value).discard()

    var patientLastEdited: Long?
        get() = patientBuilder.get(Patient::lastEdited) as Long?
        set(value) = patientBuilder.set(Patient::lastEdited, value).discard()

    /* Blood Pressure Info */
    var bloodPressure: BloodPressure?
        get() = readingBuilder.get(Reading::bloodPressure) as BloodPressure?
        set(value) = readingBuilder.set(Reading::bloodPressure, value).discard()

    /* Urine Test Info */
    var urineTest: UrineTest?
        get() = readingBuilder.get(Reading::urineTest) as UrineTest?
        set(value) = readingBuilder.set(Reading::urineTest, value).discard()

    /* Referral Info */
    var referral: Referral?
        get() = readingBuilder.get(Reading::referral) as Referral?
        set(value) = readingBuilder.set(Reading::referral, value).discard()

    /* Reading Info */
    var readingId: String?
        get() = readingBuilder.get(Reading::id) as String?
        set(value) = readingBuilder.set(Reading::id, value).discard()

    var dateTimeTaken: Long?
        get() = readingBuilder.get(Reading::dateTimeTaken) as Long?
        set(value) = readingBuilder.set(Reading::dateTimeTaken, value).discard()

    @Suppress("UNCHECKED_CAST")
    var symptoms: ArrayList<String>?
        get() = readingBuilder.get(Reading::symptoms) as ArrayList<String>?
        set(value) = readingBuilder.set(Reading::symptoms, value).discard()

    var dateRecheckVitalsNeeded: Long?
        get() = readingBuilder.get(Reading::dateRecheckVitalsNeeded) as Long?
        set(value) = readingBuilder.set(Reading::dateRecheckVitalsNeeded, value).discard()

    var isFlaggedForFollowUp: Boolean?
        get() = readingBuilder.get(Reading::isFlaggedForFollowUp) as Boolean?
        set(value) = readingBuilder.set(Reading::isFlaggedForFollowUp, value).discard()

    @Suppress("UNCHECKED_CAST")
    var previousReadingIds: MutableList<String>?
        get() = readingBuilder.get(Reading::previousReadingIds) as MutableList<String>?
        set(value) = readingBuilder.set(Reading::previousReadingIds, value).discard()

    var metadata: ReadingMetadata = ReadingMetadata()

    /**
     * True if any of the required fields are missing.
     */
    fun isMissingAnything(context: Context): Boolean =
        missingPatientInfoDescription(context) == null &&
            missingVitalInformationDescription(context) == null

    /**
     * True if the data in this view model is invalid.
     *
     * For this to be false, the blood pressure reading must exist and be valid
     * and the implication "patient is pregnant" -> "gestational age not null"
     * must be true.
     */
    val isDataInvalid: Boolean
        get() = !(bloodPressure?.isValid ?: false) ||
            (patientIsPregnant ?: false && patientGestationalAge == null)

    val symptomsString get() = symptoms?.joinToString(", ")

    /**
     * True if this reading notes that a vital recheck is required.
     */
    val isVitalRecheckRequired get() = dateRecheckVitalsNeeded != null

    /**
     * True if a vital recheck is required right now.
     */
    val isVitalRecheckRequiredNow: Boolean
        get() = isVitalRecheckRequired &&
            (dateRecheckVitalsNeeded?.minus(ZonedDateTime.now().toEpochSecond())!! <= 0)

    /**
     * The number of minutes until a vital recheck is required.
     *
     * `null` if no recheck is required.
     */
    val minutesUtilVitalRecheck: Long?
        get() {
            val recheckTime = dateRecheckVitalsNeeded ?: return null
            return ZonedDateTime.now().toEpochSecond() - recheckTime
        }

    /**
     * Returns a description of the missing patient information or `null` if
     * everything is supplied.
     */
    fun missingPatientInfoDescription(context: Context): String? {
        var description = ""
        patientId?.let { description += "- ${context.getString(R.string.id)}\n" }
        patientSex?.let { description += "- ${context.getString(R.string.sex)}\n" }
        patientName?.let { description += "- ${context.getString(R.string.initials)}\n" }
        if (patientDob == null && patientAge == null) {
            description += "- ${context.getString(R.string.date_of_birth_or_age)}\n"
        }

        return if (description.isNotEmpty()) {
            "${context.getString(R.string.missing_required_patient_info)}: $description\n"
        } else {
            null
        }
    }

    /**
     * Returns a description of the missing vital information or `null` if
     * everything is supplied.
     */
    fun missingVitalInformationDescription(context: Context): String? {
        var description = ""
        bloodPressure?.let { description += "- ${context.getString(R.string.missing_vitals_blood_pressure)}\n" }
        symptoms?.let { description += "- ${context.getString(R.string.missing_vitals_symptoms)}" }

        return if (description.isNotEmpty()) {
            "${context.getString(R.string.missing_required_patient_vitals)}: $description\n"
        } else {
            null
        }
    }

    /**
     * Constructs [Patient] and [Reading] models based on the information
     * contained in this object.
     *
     * @throws IllegalArgumentException if any of the required parameters are
     * missing
     */
    fun constructModels(): Pair<Patient, Reading> {
        // The reading requires the patient's id so we'll copy it over before
        // building the models.
        val patientId = patientBuilder.get(Patient::id)
        readingBuilder.set(Reading::patientId, patientId)

        // If the reading doesn't have a date time taken field, we'll populate
        // that now.
        if (dateTimeTaken == null) {
            dateTimeTaken = UnixTimestamp.now
        }

        val patient = patientBuilder.build<Patient>()
        val reading = readingBuilder.build<Reading>()
        return Pair(patient, reading)
    }

    /**
     * Constructs [Patient] and [Reading] models based on the information
     * contained in this object. If unable to construct these readings due to
     * missing mandatory fields for example, then `null` is returned.
     */
    fun maybeConstructModels(): Pair<Patient, Reading>? = try {
        constructModels()
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: kotlin.IllegalArgumentException) { // Exception may also be the Kotlin version
        null
    }

    /**
     * Attempts to construct the retest group for the partially completed
     * reading stored in this view model.
     *
     * If the blood pressure data has not been supplied to us by the user then
     * construction is impossible and we return `null`. Otherwise we go with
     * what we have, giving some defaults if needed; the UI will just have to
     * deal with it.
     */
    fun buildRetestGroup(readingManager: ReadingManager): RetestGroup? {
        if (bloodPressure == null) {
            return null
        }

        val reading = Reading(
            id = readingId ?: "in-progress",
            patientId = patientId ?: "",
            dateTimeTaken = dateTimeTaken ?: UnixTimestamp.now,
            bloodPressure = bloodPressure!!,
            urineTest = urineTest,
            symptoms = symptoms ?: emptyList(),
            referral = referral,
            followUp = null,
            dateRecheckVitalsNeeded = dateRecheckVitalsNeeded,
            isFlaggedForFollowUp = isFlaggedForFollowUp ?: false,
            previousReadingIds = previousReadingIds ?: emptyList(),
            metadata = metadata
        )
        return runBlocking { withContext(Dispatchers.IO) { readingManager.getRetestGroup(reading) } }
    }
}
