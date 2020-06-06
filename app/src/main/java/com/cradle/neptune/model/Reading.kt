package com.cradle.neptune.model

import android.content.Context
import com.cradle.neptune.R
import com.cradle.neptune.database.ReadingEntity
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.temporal.ChronoUnit
import java.util.UUID

const val RED_SYSTOLIC = 160
const val RED_DIASTOLIC = 110
const val YELLOW_SYSTOLIC = 140
const val YELLOW_DIASTOLIC = 90
const val SHOCK_HIGH = 1.7
const val SHOCK_MEDIUM = 0.9

const val MAX_SYSTOLIC = 300
const val MIN_SYSTOLIC = 10
const val MAX_DIASTOLIC = 300
const val MIN_DIASTOLIC = 10
const val MAX_HEART_RATE = 200
const val MIN_HEART_RATE = 40

/**
 * Holds information about a reading.
 *
 * @property id The identifier for this reading. If not supplied, a random UUID
 * will be generated for this field.
 * @property patientId The identifier for the patient this reading is
 * associated with.
 * @property dateTimeTaken The time at which this reading was taken.
 * @property bloodPressure The result of a blood pressure test.
 * @property urineTest The result of a urine test.
 * @property symptoms A list of symptoms that the patient has at the time this
 * reading was taken.
 * @property referral An optional referral associated with this reading.
 * @property dateRecheckVitalsNeeded The date at which this patient's vitals
 * should be rechecked (if applicable).
 * @property isFlaggedForFollowUp Whether this patient requires a followup.
 * @property previousReadingIds A list of previous readings associated with
 * this one. By default this is empty.
 * @property metadata Some internal metadata associated with this reading. By
 * default an empty metadata object (with all `null` values) will be used.
 */
data class Reading(
    var id: String = UUID.randomUUID().toString(),
    var patientId: String,
    var dateTimeTaken: ZonedDateTime,

    var bloodPressure: BloodPressure,
    var urineTest: UrineTest?,
    var symptoms: List<String>,

    var referral: Referral?,
    var followUp: FollowUp?,

    var dateRecheckVitalsNeeded: ZonedDateTime?,
    var isFlaggedForFollowUp: Boolean,

    var previousReadingIds: List<String> = emptyList(),

    var metadata: ReadingMetadata = ReadingMetadata()
) : Marshal<JsonObject> {

    /**
     * True if this reading has a referral attached to it.
     */
    val isReferredToHealthCentre: Boolean get() = referral != null

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
     * True if this reading has an associated referral attached to it.
     */
    val hasReferral get() = referral != null

    /**
     * Converts this [Reading] object into a [JsonObject].
     */
    override fun marshal(): JsonObject = with(JsonObject()) {
        put(ReadingField.ID, id)
        put(ReadingField.PATIENT_ID, patientId)
        put(ReadingField.DATE_TIME_TAKEN, dateTimeTaken)

        union(bloodPressure)
        union(referral)
        put(ReadingField.URINE_TEST, urineTest)
        put(ReadingField.SYMPTOMS, symptoms)

        put(ReadingField.DATE_RECHECK_VITALS_NEEDED, dateRecheckVitalsNeeded)
        put(ReadingField.IS_FLAGGED_FOR_FOLLOWUP, isFlaggedForFollowUp)

        put(ReadingField.PREVIOUS_READING_IDS, previousReadingIds)

        union(metadata)
    }

    /**
     * Converts this reading into a [ReadingEntity] object.
     */
    fun toEntity(): ReadingEntity {
        return ReadingEntity(id, patientId, marshal().toString(), metadata.isUploaded)
    }

    companion object : Unmarshal<Reading, JsonObject> {
        /**
         * Constructs a [Reading] object form a [JsonObject].
         *
         * @throws JsonException if any required fields are missing.
         */
        override fun unmarshal(data: JsonObject): Reading {
            val id = data.stringField(ReadingField.ID)

            val patientId =
                data.optStringField(ReadingField.PATIENT_ID) ?: data.getJSONObject("patient").getString("patientId")

            val dateTimeTaken = data.dateField(ReadingField.DATE_TIME_TAKEN)

            val bloodPressure = BloodPressure.unmarshal(data)
            val referral = maybeUnmarshal(Referral, data)
            val urineTest = data.optObjectField(ReadingField.URINE_TEST)?.let {
                maybeUnmarshal(UrineTest, it)
            }

            val symptomsJsonArray = data.arrayField(ReadingField.SYMPTOMS)
            val symptoms = mutableListOf<String>()

            // For some reason, legacy symptoms were encoded in JSON as an
            // array of a single, comma separated string.
            //
            // Something like:
            //   ["ABDO PAIN, FEVERISH, HEADACHE, BLEEDING"]
            //
            // In order to handle this, and not incorrectly split up a user-
            // supplied symptom, we'll check to see if the JSON array only
            // contains a single element and if we can split it using a ','
            // separator. Since there is no enumeration which defines the
            // set of hard-coded symptoms, this is the best we can to for
            // legacy support.
            if (symptomsJsonArray.length() == 1) {
                val symptomString = symptomsJsonArray.getString(0)
                val split = symptomString.split(',')
                    .map { it.trim() }
                    .filterNot { it.isEmpty() }
                if (split.size != 1) {
                    symptoms.addAll(split)
                } else {
                    symptoms.add(symptomString)
                }
            } else {
                for (i in 0 until symptomsJsonArray.length()) {
                    symptoms.add(symptomsJsonArray.getString(i))
                }
            }

            val dateRecheckVitalsNeeded = data.optDateField(ReadingField.DATE_RECHECK_VITALS_NEEDED)
            val isFlaggedForFollowUp = data.optBooleanField(ReadingField.IS_FLAGGED_FOR_FOLLOWUP) ?: false

            val previousReadingIds = data.optArrayField(ReadingField.PREVIOUS_READING_IDS)?.let {
                val list = mutableListOf<String>()
                for (i in 0 until it.length()) {
                    list.add(it.getString(i))
                }
                list
            } ?: emptyList<String>()

            val metadata = unmarshal(ReadingMetadata, data)

            return Reading(
                id = id,
                patientId = patientId,
                dateTimeTaken = dateTimeTaken,

                bloodPressure = bloodPressure,
                urineTest = urineTest,
                symptoms = symptoms,

                referral = referral,
                followUp = null,

                dateRecheckVitalsNeeded = dateRecheckVitalsNeeded,
                isFlaggedForFollowUp = isFlaggedForFollowUp,

                previousReadingIds = previousReadingIds,

                metadata = metadata
            )
        }
    }

    object AscendingDataComparator : Comparator<Reading> {
        override fun compare(o1: Reading?, o2: Reading?): Int {
            val hasO1 = o1?.dateTimeTaken != null
            val hasO2 = o2?.dateTimeTaken != null
            return when {
                hasO1 && hasO2 -> o1!!.dateTimeTaken.compareTo(o2!!.dateTimeTaken)
                hasO1 && !hasO2 -> -1
                !hasO1 && hasO2 -> 1
                else -> 0
            }
        }
    }

    object DescendingDateComparator : Comparator<Reading> {
        override fun compare(o1: Reading?, o2: Reading?): Int = -AscendingDataComparator.compare(o1, o2)
    }
}

/**
 * Holds information about a blood pressure reading.
 *
 * @property systolic The systolic value (i.e., the first/top value).
 * @property diastolic The diastolic value (i.e., the second/bottom value).
 * @property heartRate The heart rate in beats per minute (BPM).
 */
data class BloodPressure(
    val systolic: Int,
    val diastolic: Int,
    val heartRate: Int
) : Marshal<JsonObject> {
    /**
     * The shock index for this blood pressure result.
     */
    private val shockIndex
        get() = if (systolic == 0) {
            0.0
        } else {
            heartRate.toDouble() / systolic.toDouble()
        }

    /**
     * The analysis for this blood pressure result.
     */
    val analysis: ReadingAnalysis
        get() = when {
            // In severe shock
            shockIndex >= SHOCK_HIGH -> ReadingAnalysis.RED_DOWN

            // Blood pressure is very high
            systolic >= RED_SYSTOLIC || diastolic >= RED_DIASTOLIC -> ReadingAnalysis.RED_UP

            // In shock
            shockIndex >= SHOCK_MEDIUM -> ReadingAnalysis.YELLOW_DOWN

            // Blood pressure is high
            systolic >= YELLOW_SYSTOLIC || diastolic >= YELLOW_DIASTOLIC -> ReadingAnalysis.YELLOW_UP

            // All good
            else -> ReadingAnalysis.GREEN
        }

    /**
     * True if this blood pressure reading is valid (i.e., all fields are
     * within bounds).
     */
    val isValid: Boolean
        get() = systolic in MIN_SYSTOLIC..MAX_SYSTOLIC
            && diastolic in MIN_DIASTOLIC..MAX_DIASTOLIC
            && heartRate in MIN_HEART_RATE..MAX_HEART_RATE

    /**
     * Marshals this object to JSON.
     */
    override fun marshal(): JsonObject = with(JsonObject()) {
        put(BloodPressureField.SYSTOLIC, systolic)
        put(BloodPressureField.DIASTOLIC, diastolic)
        put(BloodPressureField.HEART_RATE, heartRate)
    }

    companion object : Unmarshal<BloodPressure, JsonObject> {
        /**
         * Constructs a [BloodPressure] object from a [JsonObject].
         *
         * @throws JsonException if any of the required fields are missing
         */
        override fun unmarshal(data: JsonObject): BloodPressure {
            val systolic = data.intField(BloodPressureField.SYSTOLIC)
            val diastolic = data.intField(BloodPressureField.DIASTOLIC)
            val heartRate = data.intField(BloodPressureField.HEART_RATE)
            return BloodPressure(systolic, diastolic, heartRate)
        }
    }
}

/**
 * Holds information about a referral.
 *
 * @property messageSendTime The time at which this referral message was sent.
 * @property healthCentre The health center this referral should be sent to.
 * @property comment A comment to be included along with the referral.
 */
data class Referral(
    val messageSendTime: ZonedDateTime,
    val healthCentre: String,
    val comment: String
) : Marshal<JsonObject> {

    override fun marshal(): JsonObject = with(JsonObject()) {
        put(ReferralField.MESSAGE_SEND_TIME, messageSendTime.toEpochSecond())
        put(ReferralField.HEALTH_CENTRE, healthCentre)
        put(ReferralField.COMMENT, comment)
    }

    companion object : Unmarshal<Referral, JsonObject> {
        /**
         * Constructs a [Referral] object from a [JsonObject].
         *
         * @throws JsonException if any of the required fields are missing
         */
        override fun unmarshal(data: JsonObject): Referral {
            val messageSendTime = data.dateField(ReferralField.MESSAGE_SEND_TIME)
            val healthCentre = data.stringField(ReferralField.HEALTH_CENTRE)
            val comment = data.stringField(ReferralField.COMMENT)
            return Referral(messageSendTime, healthCentre, comment)
        }
    }
}

/**
 * An analysis of a blood pressure reading.
 */
enum class ReadingAnalysis(private val analysisTextId: Int, private val adviceTextId: Int) {
    NONE(R.string.analysis_none, R.string.brief_advice_none),
    GREEN(R.string.analysis_green, R.string.brief_advice_green),
    YELLOW_UP(R.string.analysis_yellow_up, R.string.brief_advice_yellow_up),
    YELLOW_DOWN(R.string.analysis_yellow_down, R.string.brief_advice_yellow_down),
    RED_UP(R.string.analysis_red_up, R.string.brief_advice_red_up),
    RED_DOWN(R.string.analysis_red_down, R.string.brief_advice_red_down);

    /**
     * Resolves a description of the analysis in a given context.
     */
    fun getAnalysisText(context: Context) = context.getString(analysisTextId)

    /**
     * Resolves a brief advisement to the user in a given context.
     */
    fun getBriefAdviceText(context: Context) = context.getString(adviceTextId)

    /**
     * True if this analysis is an "UP" variant.
     */
    val isUp get() = this == YELLOW_UP || this == RED_UP

    /**
     * True if this analysis is a "DOWN" variant.
     */
    val isDown get() = this == YELLOW_DOWN || this == RED_DOWN

    /**
     * True if this analysis is "GREEN".
     */
    val isGreen get() = this == GREEN

    /**
     * True if this analysis is a "YELLOW" variant.
     */
    val isYellow get() = this == YELLOW_UP || this == YELLOW_DOWN

    /**
     * True if this analysis is a "RED" variant.
     */
    val isRed get() = this == RED_UP || this == RED_DOWN

    /**
     * True if it is recommended that the user refer this patient to a health
     * center.
     */
    val isReferralRecommended
        get() = when (this) {
            YELLOW_UP -> true
            RED_UP -> true
            RED_DOWN -> true
            else -> false
        }
}

/**
 * A collection of metadata associated with a reading.
 *
 * The data in this class is mainly for developer use and is not intended to be
 * visible to the user.
 */
data class ReadingMetadata(
    /* Application Data */
    var appVersion: String? = null,
    var deviceInfo: String? = null,
    var dateLastSaved: ZonedDateTime? = null,
    var dateUploadedToServer: ZonedDateTime? = null,

    /* Image Data */
    var photoPath: String? = null,
    var isImageUploaded: Boolean = false,
    var totalOcrSeconds: Float? = null,

    /* GPS Data */
    var gpsLocation: String? = null
) : Marshal<JsonObject> {
    /**
     * True if this reading has been uploaded to the server.
     */
    val isUploaded get() = dateUploadedToServer != null

    override fun marshal() = with(JsonObject()) {
        put(MetadataField.APP_VERSION, appVersion)
        put(MetadataField.DEVICE_INFO, deviceInfo)
        put(MetadataField.DATE_LAST_SAVED, dateLastSaved)
        put(MetadataField.DATE_UPLOADED_TO_SERVER, dateUploadedToServer)
        put(MetadataField.PHOTO_PATH, photoPath)
        put(MetadataField.IS_IMAGE_UPLOADED, isImageUploaded)
        put(MetadataField.TOTAL_OCR_SECONDS, totalOcrSeconds?.toDouble())
        put(MetadataField.GPS_LOCATION, gpsLocation)
    }

    companion object : Unmarshal<ReadingMetadata, JsonObject> {
        override fun unmarshal(data: JsonObject): ReadingMetadata {
            val appVersion = data.optStringField(MetadataField.APP_VERSION)
            val deviceInfo = data.optStringField(MetadataField.DEVICE_INFO)
            val dateLastSaved = data.optDateField(MetadataField.DATE_LAST_SAVED)
            val dateUploadedToServer = data.optDateField(MetadataField.DATE_UPLOADED_TO_SERVER)
            val photoPath = data.optStringField(MetadataField.PHOTO_PATH)
            val isImageUploaded = data.booleanField(MetadataField.IS_IMAGE_UPLOADED)

            var totalOcrSeconds = data.optDoubleField(MetadataField.TOTAL_OCR_SECONDS)?.toFloat()
            // The legacy implementation used -1 to represent `null` for this
            // field so we handle that case here.
            if (totalOcrSeconds?.let { it < 0 } == true) {
                totalOcrSeconds = null
            }

            val gpsLocation = data.optStringField(MetadataField.GPS_LOCATION)

            return ReadingMetadata(
                appVersion = appVersion,
                deviceInfo = deviceInfo,
                dateLastSaved = dateLastSaved,
                dateUploadedToServer = dateUploadedToServer,
                photoPath = photoPath,
                isImageUploaded = isImageUploaded,
                totalOcrSeconds = totalOcrSeconds,
                gpsLocation = gpsLocation
            )
        }
    }
}

/**
 * A list of related readings with each successive reading being a retest of
 * the last. The group as a whole can be analysed to determine if the user
 * should perform an additional retest or not.
 */
class RetestGroup(val readings: List<Reading>) : Iterable<Reading> {

    /**
     * The number of readings in this group.
     */
    val size get() = readings.size

    /**
     * The blood pressure analyses for each reading in the group.
     */
    val analyses get() = readings.map { it.bloodPressure.analysis }

    val isRetestRecommendedNow get() = getRetestAdvice() == RetestAdvice.RIGHT_NOW

    val isRetestRecommendedIn15Min get() = getRetestAdvice() == RetestAdvice.IN_15_MIN

    val isRetestRecommended get() = getRetestAdvice() != RetestAdvice.NOT_NEEDED

    val mostRecentReadingAnalysis get() = readings.last().bloodPressure.analysis

    /**
     * An iterator over the readings in this group with older readings coming
     * before newer ones.
     */
    override operator fun iterator(): Iterator<Reading> = readings.iterator()

    /**
     * Computes and returns advice for retesting based on this group's data.
     */
    fun getRetestAdvice(): RetestAdvice {
        val (green, yellow, red) = this.analyses
            .fold(Triple(0, 0, 0)) { (green, yellow, red), x ->
                when {
                    x.isGreen -> Triple(green + 1, yellow, red)
                    x.isYellow -> Triple(green, yellow + 1, red)
                    x.isRed -> Triple(green, yellow, red + 1)
                    else -> throw RuntimeException("unreachable")
                }
            }

        // With 1 reading, recommend a retest if the reading is not green. How
        // soon the retest should be done is dependent on whether the reading
        // is yellow or red.
        if (size == 1) {
            return when {
                green == 1 -> RetestAdvice.NOT_NEEDED
                yellow == 1 -> RetestAdvice.IN_15_MIN
                red == 1 -> RetestAdvice.RIGHT_NOW
                else -> throw RuntimeException("unreachable")
            }
        }

        // With 2 readings, don't recommend a retest if the two readings agree
        // with each other. If they don't recommend a retest right away.
        if (size == 2) {
            return if (green == 2 || yellow == 2 || red == 2) {
                RetestAdvice.NOT_NEEDED
            } else {
                RetestAdvice.RIGHT_NOW
            }
        }

        // With 3+ readings there is no need to retest as the most recent
        // reading is considered sufficient.
        return RetestAdvice.NOT_NEEDED
    }
}

/**
 * Advice for when to retest a patient.
 *
 * @see RetestGroup.getRetestAdvice
 */
enum class RetestAdvice {
    NOT_NEEDED,
    RIGHT_NOW,
    IN_15_MIN
}

// TODO: Figure out which of these are nullable and which are not. Also, it
//  seems that the data stored on mobile is different from web so we'll need
//  to get that resolved as well.

/**
 * Data about a follow up which may be attached to a reading.
 */
data class FollowUp(
    var readingServerId: String?,
    var followUpAction: String?,
    var treatment: String?,
    var diagnosis: String?,
    var healthcare: String?,
    var date: String?,
    var assessedBy: String?,
    var referredBy: String?,
    var patientMedInfoUpdate: String?,
    var patientDrugInfoUpdate: String?,
    var patientId: String?,
    var followUpNeeded: Boolean,
    var followUpNeededTill: String?,
    var medicationPrescribed: String?,
    var followUpFrequencyUnit: String?,
    var followUpFrequencyValue: Int,
    var specialInvestigation: String?
) : Marshal<JsonObject> {
    constructor(
        readingServerId: String?,
        followUpAction: String?,
        treatment: String?,
        diagnosis: String?,
        healthcare: String?,
        date: String?,
        assessedBy: String?,
        referredBy: String?
    ) : this(
        readingServerId = readingServerId,
        followUpAction = followUpAction,
        treatment = treatment,
        diagnosis = diagnosis,
        healthcare = healthcare,
        date = date,
        assessedBy = assessedBy,
        referredBy = referredBy,
        patientMedInfoUpdate = null,
        patientDrugInfoUpdate = null,
        patientId = null,
        followUpNeeded = false,
        followUpNeededTill = null,
        medicationPrescribed = null,
        followUpFrequencyUnit = null,
        followUpFrequencyValue = 0,
        specialInvestigation = null
    )

    /**
     * Converts this object into a [JsonObject].
     */
    override fun marshal() = with(JsonObject()) {
        put(FollowUpField.READING_SERVER_ID, readingServerId)
        put(FollowUpField.FOLLOW_UP_ACTION, followUpAction)
        put(FollowUpField.TREATMENT, treatment)
        put(FollowUpField.DIAGNOSIS, diagnosis)
        put(FollowUpField.HEALTHCARE, healthcare)
        put(FollowUpField.DATE, date)
        put(FollowUpField.ASSESSED_BY, assessedBy)
        put(FollowUpField.REFERRED_BY, referredBy)
        put(FollowUpField.PATIENT_MED_INFO_UPDATE, patientMedInfoUpdate)
        put(FollowUpField.PATIENT_DRUG_INFO_UPDATE, patientDrugInfoUpdate)
        put(FollowUpField.PATIENT_ID, patientId)
        put(FollowUpField.FOLLOW_UP_NEEDED, followUpNeeded)
        put(FollowUpField.FOLLOW_UP_NEEDED_TILL, followUpNeededTill)
        put(FollowUpField.MEDICATION_PRESCRIBED, medicationPrescribed)
        put(FollowUpField.FOLLOW_UP_FREQUENCY_UNIT, followUpFrequencyUnit)
        put(FollowUpField.FOLLOW_UP_FREQUENCY_VALUE, followUpFrequencyValue)
        put(FollowUpField.SPECIAL_INVESTIGATION, specialInvestigation)
    }

    companion object : Unmarshal<FollowUp, JsonObject> {
        /**
         * Constructs a new instance of this class from a [JsonObject].
         */
        override fun unmarshal(data: JsonObject) = FollowUp(
            readingServerId = data.optStringField(FollowUpField.READING_SERVER_ID),
            followUpAction = data.optStringField(FollowUpField.FOLLOW_UP_ACTION),
            treatment = data.optStringField(FollowUpField.TREATMENT),
            diagnosis = data.optStringField(FollowUpField.DIAGNOSIS),
            healthcare = data.optStringField(FollowUpField.HEALTHCARE),
            date = data.optStringField(FollowUpField.DATE),
            assessedBy = data.optStringField(FollowUpField.ASSESSED_BY),
            referredBy = data.optStringField(FollowUpField.REFERRED_BY),
            patientMedInfoUpdate = data.optStringField(FollowUpField.PATIENT_MED_INFO_UPDATE),
            patientDrugInfoUpdate = data.optStringField(FollowUpField.PATIENT_DRUG_INFO_UPDATE),
            patientId = data.optStringField(FollowUpField.PATIENT_ID),
            followUpNeeded = data.optBooleanField(FollowUpField.FOLLOW_UP_NEEDED) ?: false,
            followUpNeededTill = data.optStringField(FollowUpField.FOLLOW_UP_NEEDED_TILL),
            medicationPrescribed = data.optStringField(FollowUpField.MEDICATION_PRESCRIBED),
            followUpFrequencyUnit = data.optStringField(FollowUpField.FOLLOW_UP_FREQUENCY_UNIT),
            followUpFrequencyValue = data.optIntField(FollowUpField.FOLLOW_UP_FREQUENCY_VALUE) ?: 0,
            specialInvestigation = data.optStringField(FollowUpField.SPECIAL_INVESTIGATION)
        )
    }
}

/**
 * JSON keys for [Reading] fields.
 */
private enum class ReadingField(override val text: String) : Field {
    ID("readingId"),
    PATIENT_ID("patientId"),
    DATE_TIME_TAKEN("dateTimeTaken"),
    URINE_TEST("urineTests"),
    SYMPTOMS("symptoms"),
    DATE_RECHECK_VITALS_NEEDED("dateRecheckVitalsNeeded"),
    IS_FLAGGED_FOR_FOLLOWUP("isFlaggedForFollowup"),
    PREVIOUS_READING_IDS("retestOfPreviousReadingIds"),
}

/**
 * JSON keys for [BloodPressure] fields.
 */
private enum class BloodPressureField(override val text: String) : Field {
    SYSTOLIC("bpSystolic"),
    DIASTOLIC("bpDiastolic"),
    HEART_RATE("heartRateBPM"),
}

/**
 * JSON keys for [Referral] fields.
 */
private enum class ReferralField(override val text: String) : Field {
    MESSAGE_SEND_TIME("referralMessageSendTime"),
    HEALTH_CENTRE("referralHealthCentre"),
    COMMENT("referral"),
}

/**
 * JSON keys for [Metadata] fields.
 */
private enum class MetadataField(override val text: String) : Field {
    APP_VERSION("appVersion"),
    DEVICE_INFO("deviceInfo"),
    DATE_LAST_SAVED("dateLastSaved"),
    DATE_UPLOADED_TO_SERVER("dateUploadedToServer"),
    PHOTO_PATH("photoPath"),
    IS_IMAGE_UPLOADED("isImageUploaded"),
    TOTAL_OCR_SECONDS("totalOcrSeconds"),
    GPS_LOCATION("gpsLocation"),
}

private enum class FollowUpField(override val text: String) : Field {
    READING_SERVER_ID("readingServerId"),
    FOLLOW_UP_ACTION("followUpAction"),
    TREATMENT("treatment"),
    DIAGNOSIS("diagnosis"),
    HEALTHCARE("healthcare"),
    DATE("date"),
    ASSESSED_BY("assessedBy"),
    REFERRED_BY("referredBy"),
    PATIENT_MED_INFO_UPDATE("patientMedInfoUpdate"),
    PATIENT_DRUG_INFO_UPDATE("patientDrugInfoUpdate"),
    PATIENT_ID("patientId"),
    FOLLOW_UP_NEEDED("followUpNeeded"),
    FOLLOW_UP_NEEDED_TILL("followupNeededTill"),
    MEDICATION_PRESCRIBED("medicationPrescribed"),
    FOLLOW_UP_FREQUENCY_UNIT("followupFrequencyUnit"),
    FOLLOW_UP_FREQUENCY_VALUE("followupFrequencyValue"),
    SPECIAL_INVESTIGATION("specialInvestigation"),
}
