package com.cradleplatform.neptune.database.firstversiondata.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.ext.jackson.get
import com.cradleplatform.neptune.ext.jackson.getOptObject
import com.cradleplatform.neptune.ext.jackson.getOptObjectArray
import com.cradleplatform.neptune.ext.jackson.writeBooleanField
import com.cradleplatform.neptune.ext.jackson.writeIntField
import com.cradleplatform.neptune.ext.jackson.writeLongField
import com.cradleplatform.neptune.ext.jackson.writeObjectField
import com.cradleplatform.neptune.ext.jackson.writeOptIntField
import com.cradleplatform.neptune.ext.jackson.writeOptLongField
import com.cradleplatform.neptune.ext.jackson.writeOptObjectField
import com.cradleplatform.neptune.ext.jackson.writeStringField
import com.cradleplatform.neptune.utilities.nullIfEmpty
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.threeten.bp.ZonedDateTime
import java.io.Serializable
import java.util.UUID
import kotlin.reflect.KProperty

const val RED_SYSTOLIC = 160
const val RED_DIASTOLIC = 110
const val YELLOW_SYSTOLIC = 140
const val YELLOW_DIASTOLIC = 90
const val SHOCK_HIGH = 1.7
const val SHOCK_MEDIUM = 0.9

const val MAX_SYSTOLIC = 300
const val MIN_SYSTOLIC = 50
const val MAX_DIASTOLIC = 200
const val MIN_DIASTOLIC = 30
const val MAX_HEART_RATE = 250
const val MIN_HEART_RATE = 30

private const val SECONDS_IN_MIN = 60

/**
 * Holds information about a reading.
 * DO NOT EDIT
 *
 * @property id The identifier for this reading. If not supplied, a random UUID
 * will be generated for this field.
 * @property patientId The identifier for the patient this reading is
 * associated with.
 * @property dateTaken Unix time at which this reading was taken.
 * @property bloodPressure The result of a blood pressure test.
 * @property urineTest The result of a urine test.
 * @property symptoms A list of symptoms that the patient has at the time this
 * reading was taken.
 * @property referral An optional referral associated with this reading.
 * @property dateRetestNeeded Unix time at which this patient's vitals
 * should be rechecked (if applicable).
 * @property isFlaggedForFollowUp Whether this patient requires a followup.
 * @property previousReadingIds A list of previous readings associated with
 * this one. By default this is empty.
 */
@Entity(
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["patientId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("patientId"),
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@JsonSerialize(using = Reading.Serializer::class)
@JsonDeserialize(using = Reading.Deserializer::class)
internal data class Reading(
    @PrimaryKey
    @ColumnInfo(name = "id")
    var id: String = UUID.randomUUID().toString(),
    @ColumnInfo var patientId: String,
    @ColumnInfo var dateTaken: Long,
    @ColumnInfo var bloodPressure: BloodPressure,
    @ColumnInfo var urineTest: UrineTest?,
    @ColumnInfo var symptoms: List<String>,
    @ColumnInfo var referral: Referral?,
    @ColumnInfo var followUp: Assessment?,
    @ColumnInfo var dateRetestNeeded: Long?,
    @ColumnInfo var isFlaggedForFollowUp: Boolean,
    @ColumnInfo var previousReadingIds: List<String> = emptyList(),
    @ColumnInfo var isUploadedToServer: Boolean = false,
    @ColumnInfo var lastEdited: Long,
    @ColumnInfo var userId: Int?
) : Serializable, Verifiable<Reading> {

    /**
     * True if this reading has a referral attached to it.
     */
    val isReferredToHealthFacility: Boolean get() = referral != null

    /**
     * True if this reading notes that a vital recheck is required.
     */
    val isVitalRecheckRequired get() = dateRetestNeeded != null

    /**
     * True if a vital recheck is required right now.
     */
    val isVitalRecheckRequiredNow
        get() = minutesUntilVitalRecheck != null && minutesUntilVitalRecheck!! <= 0

    /**
     * The number of minutes until a vital recheck is required.
     *
     * `null` if no recheck is required.
     */
    val minutesUntilVitalRecheck: Long?
        get() {
            val recheckTime = dateRetestNeeded ?: return null
            val timeLeft = recheckTime - ZonedDateTime.now().toEpochSecond()
            return if (timeLeft <= 0) {
                0
            } else {
                timeLeft / SECONDS_IN_MIN
            }
        }

    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context?
    ): Verifiable.Result = isValueValid(property, value, context)

    class Serializer : StdSerializer<Reading>(Reading::class.java) {
        override fun serialize(
            reading: Reading,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            reading.run {
                gen.writeStartObject()

                gen.writeStringField(ReadingField.ID, id)
                gen.writeStringField(ReadingField.PATIENT_ID, patientId)
                gen.writeLongField(ReadingField.DATE_TAKEN, dateTaken)
                bloodPressure.serialize(gen)
                gen.writeOptLongField(
                    ReadingField.DATE_RETEST_NEEDED,
                    dateRetestNeeded
                )
                gen.writeBooleanField(ReadingField.IS_FLAGGED_FOR_FOLLOW_UP, isFlaggedForFollowUp)
                gen.writeObjectField(ReadingField.SYMPTOMS, symptoms)
                gen.writeOptObjectField(ReadingField.REFERRAL, referral)
                gen.writeOptObjectField(ReadingField.FOLLOW_UP, followUp)
                gen.writeOptObjectField(ReadingField.URINE_TESTS, urineTest)
                gen.writeStringField(
                    ReadingField.PREVIOUS_READING_IDS,
                    previousReadingIds.joinToString(",")
                )
                gen.writeLongField(ReadingField.LAST_EDITED, lastEdited)
                gen.writeOptIntField(ReadingField.USER_ID, userId)

                gen.writeEndObject()
            }
        }
    }

    class Deserializer : StdDeserializer<Reading>(Reading::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Reading =
            p.codec.readTree<JsonNode>(p)!!.run {
                val readingId = get(ReadingField.ID)!!.textValue()
                val patientId = get(ReadingField.PATIENT_ID)!!.textValue()
                val dateTaken = get(ReadingField.DATE_TAKEN)!!.longValue()
                val bloodPressure = BloodPressure.deserialize(this)
                val urineTests = getOptObject<UrineTest>(ReadingField.URINE_TESTS, p.codec)
                val symptoms = getOptObjectArray<String>(ReadingField.SYMPTOMS, p.codec)
                    ?: emptyList()
                val referral = getOptObject<Referral>(ReadingField.REFERRAL, p.codec)
                val followUp = getOptObject<Assessment>(ReadingField.FOLLOW_UP, p.codec)
                val dateRetestNeeded = get(ReadingField.DATE_RETEST_NEEDED)
                    ?.longValue()
                val isFlaggedForFollowUp = get(ReadingField.IS_FLAGGED_FOR_FOLLOW_UP)
                    ?.booleanValue() ?: false
                val previousReadingIds = get(ReadingField.PREVIOUS_READING_IDS)
                    ?.textValue()
                    ?.let { it.nullIfEmpty()?.split(",") }
                    ?: emptyList()
                val lastEdited = get(ReadingField.LAST_EDITED)!!.longValue()
                val userId = get(ReadingField.USER_ID)?.intValue()

                return@run Reading(
                    id = readingId,
                    patientId = patientId,
                    dateTaken = dateTaken,
                    lastEdited = lastEdited,
                    bloodPressure = bloodPressure,
                    urineTest = urineTests,
                    symptoms = symptoms,
                    referral = referral,
                    followUp = followUp,
                    dateRetestNeeded = dateRetestNeeded,
                    isFlaggedForFollowUp = isFlaggedForFollowUp,
                    previousReadingIds = previousReadingIds,
                    userId = userId
                )
            }
    }

    companion object : Verifiable.Verifier<Reading> {

        @Suppress("NestedBlockDepth")
        override fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context?,
            instance: Reading?,
            currentValues: Map<String, Any?>?
        ): Verifiable.Result = when (property) {
            Reading::patientId -> {
                // Safe to call without instance or currentValues, as id doesn't depend on anything
                // else.
                Patient.isValueValid(Patient::id, value, context, null, null)
            }
            Reading::bloodPressure -> with(value as? BloodPressure) {
                return if (this == null) {
                    Verifiable.Valid
                } else {
                    // Derive errors from BloodPressure from its implementation.
                    return getAllMembersWithInvalidValues(context).let { invalids ->
                        val isValid = invalids.isEmpty()
                        if (isValid) {
                            Verifiable.Valid
                        } else {
                            Verifiable.Invalid(
                                property,
                                invalids.joinToString(separator = ",") { it.second }
                            )
                        }
                    }
                }
            }
            Reading::urineTest -> Verifiable.Valid
            else -> Verifiable.Valid
        }
    }

    object AscendingDataComparator : Comparator<Reading> {
        override fun compare(o1: Reading?, o2: Reading?): Int {
            val hasO1 = o1?.dateTaken != null
            val hasO2 = o2?.dateTaken != null
            return when {
                hasO1 && hasO2 -> o1!!.dateTaken.compareTo(o2!!.dateTaken)
                hasO1 && !hasO2 -> -1
                !hasO1 && hasO2 -> 1
                else -> 0
            }
        }
    }

    object DescendingDateComparator : Comparator<Reading> {
        override fun compare(o1: Reading?, o2: Reading?): Int =
            -AscendingDataComparator.compare(o1, o2)
    }
}

/**
 * Holds information about a blood pressure reading.
 *
 * @property systolic The systolic value (i.e., the first/top value).
 * @property diastolic The diastolic value (i.e., the second/bottom value).
 * @property heartRate The heart rate in beats per minute (BPM).
 */
data class BloodPressure constructor(
    @JsonProperty("systolicBloodPressure")
    val systolic: Int,
    @JsonProperty("diastolicBloodPressure")
    val diastolic: Int,
    @JsonProperty("heartRate")
    val heartRate: Int
) : Serializable, Verifiable<BloodPressure> {
    /**
     * The shock index for this blood pressure result.
     */
    @get:JsonIgnore
    private val shockIndex
        get() = if (systolic == 0) {
            0.0
        } else {
            heartRate.toDouble() / systolic.toDouble()
        }

    /**
     * The analysis for this blood pressure result.
     */
    @get:JsonIgnore
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

    fun serialize(gen: JsonGenerator) {
        gen.apply {
            writeIntField(BloodPressureField.SYSTOLIC, systolic)
            writeIntField(BloodPressureField.DIASTOLIC, diastolic)
            writeIntField(BloodPressureField.HEART_RATE, heartRate)
        }
    }

    companion object : Verifiable.Verifier<BloodPressure> {
        override fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context?,
            instance: BloodPressure?,
            currentValues: Map<String, Any?>?
        ): Verifiable.Result = with(value as? Int) {
            // Normally, we would use a `when (property)` statement here.
            // We do a `with(value as? Int)` here because we assume that all the properties that we
            // want to check in this class are of type Int, and they're all verified in the exact
            // same way up to the boundaries used. So we assume that the [value] passed here is an
            // Int for one of systolic, diastolic, or heartRate.
            // If that's not the case, the cast will fail, and `this` will be null.
            if (this == null) {
                when (property) {
                    BloodPressure::systolic -> {
                        return@with Verifiable.Invalid(
                            property,
                            context?.getString(R.string.blood_pressure_error_missing_systolic)
                        )
                    }
                    BloodPressure::diastolic -> {
                        return@with Verifiable.Invalid(
                            property,
                            context?.getString(R.string.blood_pressure_error_missing_diastolic)
                        )
                    }
                    BloodPressure::heartRate -> {
                        return@with Verifiable.Invalid(
                            property,
                            context?.getString(R.string.blood_pressure_error_missing_heart_rate)
                        )
                    }
                    else -> return Verifiable.Valid
                }
            }
            val (lowerBound, upperBound, @StringRes resId) = when (property) {
                BloodPressure::systolic -> Triple(
                    MIN_SYSTOLIC,
                    MAX_SYSTOLIC,
                    R.string.blood_pressure_error_systolic_out_of_bounds
                )
                BloodPressure::diastolic -> Triple(
                    MIN_DIASTOLIC,
                    MAX_DIASTOLIC,
                    R.string.blood_pressure_error_diastolic_out_of_bounds
                )
                BloodPressure::heartRate -> Triple(
                    MIN_HEART_RATE,
                    MAX_HEART_RATE,
                    R.string.blood_pressure_error_heart_rate_out_of_bounds
                )
                // not a verifiable property; true by default.
                else -> {
                    return@with Verifiable.Valid
                }
            }
            return if (lowerBound <= this && this <= upperBound) {
                Verifiable.Valid
            } else {
                Verifiable.Invalid(property, context?.getString(resId, lowerBound, upperBound))
            }
        }

        fun deserialize(jsonNode: JsonNode) = jsonNode.run {
            BloodPressure(
                systolic = get(BloodPressureField.SYSTOLIC)!!.intValue(),
                diastolic = get(BloodPressureField.DIASTOLIC)!!.intValue(),
                heartRate = get(BloodPressureField.HEART_RATE)!!.intValue()
            )
        }
    }

    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context?
    ): Verifiable.Result = isValueValid(property, value, context)
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
 * A list of related readings with each successive reading being a retest of
 * the last. The group as a whole can be analysed to determine if the user
 * should perform an additional retest or not.
 */
internal class RetestGroup(val readings: List<Reading>) : Iterable<Reading> {

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

/**
 * JSON keys for [Reading] fields.
 */
private enum class ReadingField(override val text: String) : Field {
    ID("id"),
    PATIENT_ID("patientId"),
    DATE_TAKEN("dateTaken"),
    URINE_TESTS("urineTests"),
    SYMPTOMS("symptoms"),
    DATE_RETEST_NEEDED("dateRetestNeeded"),
    IS_FLAGGED_FOR_FOLLOW_UP("isFlaggedForFollowUp"),
    PREVIOUS_READING_IDS("retestOfPreviousReadingIds"),
    LAST_EDITED("lastEdited"),
    USER_ID("userId"),
    REFERRAL("referral"),
    FOLLOW_UP("followUp"),
}

/**
 * JSON keys for [BloodPressure] fields.
 */
private enum class BloodPressureField(override val text: String) : Field {
    SYSTOLIC("systolicBloodPressure"),
    DIASTOLIC("diastolicBloodPressure"),
    HEART_RATE("heartRate"),
}
