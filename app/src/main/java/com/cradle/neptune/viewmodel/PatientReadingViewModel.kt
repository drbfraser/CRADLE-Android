package com.cradle.neptune.viewmodel

import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.ReadingManager
import com.cradle.neptune.model.ReadingMetadata
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.RetestGroup
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import java.lang.IllegalStateException
import java.util.UUID
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.temporal.ChronoUnit

/**
 * A bridge between the legacy model API used by the new model structures.
 *
 * TODO: This class is only temporary and should be replaced with a proper
 *   design pattern.
 */
class PatientReadingViewModel() {
    /* Patient Info */
    var patientId: String? = null
    var patientName: String? = null
    var patientDob: String? = null
    var patientAge: Int? = null
    var patientGestationalAge: GestationalAge? = null
    var patientSex: Sex? = null
    var patientIsPregnant: Boolean = false
    var patientZone: String? = null
    var patientVillageNumber: String? = null
    var hasNoSymptoms: Boolean = false

    /* Blood Pressure Info */
    var bloodPressure: BloodPressure? = null

    /* Urine Test Info */
    var urineTest: UrineTest? = null

    /* Referral Info */
    var referral: Referral? = null

    /* Reading Info */
    var readingId: String? = null
    var dateTimeTaken: ZonedDateTime? = null
    var symptoms: List<String>? = null
    var dateRecheckVitalsNeeded: ZonedDateTime? = null
    var isFlaggedForFollowUp: Boolean = false
    var previousReadingIds: List<String> = emptyList()
    var metadata: ReadingMetadata = ReadingMetadata()

    /**
     * True if any of the required fields are missing.
     */
    val isMissingAnything: Boolean
        get() = missingPatientInfoDescription() == null
            && missingVitalInformationDescription() == null

    val symptomsString get() = symptoms?.joinToString(", ")

    /**
     * True if this reading notes that a vital recheck is required.
     */
    val isVitalRecheckRequired get() = dateRecheckVitalsNeeded != null

    /**
     * True if a vital recheck is required right now.
     */
    val isVitalRecheckRequiredNow
        get() = isVitalRecheckRequired &&
            (dateRecheckVitalsNeeded?.isBefore(ZonedDateTime.now()) ?: false)

    /**
     * The number of minutes until a vital recheck is required.
     *
     * `null` if no recheck is required.
     */
    val minutesUtilVitalRecheck: Long?
        get() {
            val recheckTime = dateRecheckVitalsNeeded ?: return null
            return ChronoUnit.SECONDS.between(ZonedDateTime.now(), recheckTime)
        }

    /**
     * Returns a description of the missing patient information or `null` if
     * everything is supplied.
     */
    fun missingPatientInfoDescription(): String? {
        var description = ""
        patientId?.let { description += "- ID\n" }
        patientSex?.let { description += "- sex\n" }
        patientName?.let { description += "- initials\n" }
        if (patientDob == null && patientAge == null) {
            description += "- date-of-birth or age\n"
        }

        return if (description.isNotEmpty()) {
            "Missing required patient information: $description\n"
        } else {
            null
        }
    }

    /**
     * Returns a description of the missing vital information or `null` if
     * everything is supplied.
     */
    fun missingVitalInformationDescription(): String? {
        var description = ""
        bloodPressure?.let { description += "- blood pressure (systolic, diastolic, heart rate)\n" }
        symptoms?.let { description += "- symptoms" }

        return if (description.isNotEmpty()) {
            "Missing required patient vitalsS: $description\n"
        } else {
            null
        }
    }

    /**
     * Constructs [Patient] and [Reading] models based on the information
     * contained in this class.
     *
     * @throws IllegalStateException if any of the required fields needed to
     * construct the models are missing. [isMissingAnything] should be used
     * to ensure that this view model is in a valid state before calling this
     * method.
     */
    fun constructModels(): Pair<Patient, Reading> {
        if (isMissingAnything) {
            throw IllegalStateException("missing data")
        }

        val patient = Patient(
            id = patientId!!,
            name = patientName!!,
            dob = patientDob,
            age = patientAge,
            gestationalAge = patientGestationalAge,
            sex = patientSex!!,
            isPregnant = patientIsPregnant,
            zone = patientZone,
            villageNumber = patientVillageNumber
        )

        val reading = Reading(
            id = readingId ?: UUID.randomUUID().toString(),
            patientId = patientId!!,
            dateTimeTaken = dateTimeTaken!!,
            bloodPressure = bloodPressure!!,
            urineTest = urineTest,
            symptoms = symptoms!!,
            referral = referral,
            dateRecheckVitalsNeeded = dateRecheckVitalsNeeded,
            isFlaggedForFollowUp = isFlaggedForFollowUp,
            previousReadingIds = previousReadingIds,
            metadata = metadata
        )

        return Pair(patient, reading)
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
            dateTimeTaken = dateTimeTaken ?: ZonedDateTime.now(),
            bloodPressure = bloodPressure!!,
            urineTest = urineTest,
            symptoms = symptoms ?: emptyList(),
            referral = referral,
            dateRecheckVitalsNeeded = dateRecheckVitalsNeeded,
            isFlaggedForFollowUp = isFlaggedForFollowUp,
            previousReadingIds = previousReadingIds,
            metadata = metadata
        )
        return readingManager.getRetestGroup(reading)
    }
}
