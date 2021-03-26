package com.cradleVSA.neptune.model

import android.content.Context
import android.os.Parcelable
import androidx.core.text.isDigitsOnly
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.ext.Field
import com.cradleVSA.neptune.ext.jackson.get
import com.cradleVSA.neptune.ext.jackson.getOptObjectArray
import com.cradleVSA.neptune.ext.jackson.writeBooleanField
import com.cradleVSA.neptune.ext.jackson.writeObjectField
import com.cradleVSA.neptune.ext.jackson.writeOptLongField
import com.cradleVSA.neptune.ext.jackson.writeOptStringField
import com.cradleVSA.neptune.ext.jackson.writeStringField
import com.cradleVSA.neptune.ext.longField
import com.cradleVSA.neptune.ext.put
import com.cradleVSA.neptune.ext.stringField
import com.cradleVSA.neptune.utilitiles.Months
import com.cradleVSA.neptune.utilitiles.Seconds
import com.cradleVSA.neptune.utilitiles.UnixTimestamp
import com.cradleVSA.neptune.utilitiles.Weeks
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.round
import kotlin.reflect.KProperty

// TODO: Figure out which of these fields must be optional and which are never
//  used at all.

// TODO: Remove default constructor, we should never be able to instantiate
//  partial objects.

/**
 * Holds information about a patient.
 *
 * @property id The unique identifier for this patient.
 * @property name This patient's name or initials.
 * @property dob This patient's date of birth (if known).
 * @property isExactDob Whether [dob] is exact, or just a date of birth for approximate age.
 * @property gestationalAge The gestational age of this patient if applicable.
 * @property sex This patient's sex.
 * @property isPregnant Whether this patient is pregnant or not.
 * @property zone The zone in which this patient lives.
 * @property villageNumber The number of the village in which this patient lives.
 * @property drugHistory Drug history for the patient (paragraph form expected).
 * @property medicalHistory Medical history for the patient (paragraph form expected).
 * @property lastEdited Last time patient info was edited
 * @property base Last time the patient has been synced with the server.
 *
 * TODO: Make [isExactDob] and [dob] not optional. Requires backend work to enforce it.
 */
@Entity(
    indices = [
        Index(value = ["id"], unique = true)
    ]
)
@JsonSerialize(using = Patient.Serializer::class)
@JsonDeserialize(using = Patient.Deserializer::class)
data class Patient(
    @PrimaryKey @ColumnInfo
    var id: String = "",
    @ColumnInfo var name: String = "",
    @ColumnInfo var dob: String? = null,
    @ColumnInfo var isExactDob: Boolean? = null,
    @ColumnInfo var gestationalAge: GestationalAge? = null,
    @ColumnInfo var sex: Sex = Sex.OTHER,
    @ColumnInfo var isPregnant: Boolean = false,
    @ColumnInfo var zone: String? = null,
    @ColumnInfo var villageNumber: String? = null,
    @ColumnInfo var householdNumber: String? = null,
    @ColumnInfo var drugHistory: String = "",
    @ColumnInfo var medicalHistory: String = "",
    @ColumnInfo var lastEdited: Long? = null,
    @ColumnInfo var base: Long? = null
) : Serializable, Verifiable<Patient> {
    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context
    ): Pair<Boolean, String> = isValueValid(property, value, context, instance = this)

    companion object : Verifier<Patient> {
        const val ID_MAX_LENGTH = 14

        // This group of limits are derived from the backend database setup:
        // https://csil-git1.cs.surrey.sfu.ca/415-cradle/cradle-platform/-/blob/
        // 851d2dd02a1c7bd96e7aaf15737f801096774d4e/server/models.py#L170-197
        const val NAME_MAX_LENGTH = 50
        const val ZONE_MAX_LENGTH = 20
        const val VILLAGE_NUMBER_MAX_LENGTH = 50
        const val HOUSEHOLD_NUMBER_MAX_LENGTH = 50

        const val AGE_LOWER_BOUND = 15
        const val AGE_UPPER_BOUND = 65
        const val DOB_FORMAT_SIMPLEDATETIME = "yyyy-MM-dd"
        private const val GESTATIONAL_AGE_WEEKS_MAX = 43

        @Suppress("ObjectPropertyNaming")
        private val GESTATIONAL_AGE_MONTHS_MAX = round(
            GESTATIONAL_AGE_WEEKS_MAX * WeeksAndDays.DAYS_PER_WEEK /
                (WeeksAndDays.DAYS_PER_MONTH).toDouble()
        ).toInt()

        /**
         * This is currently how input validation is handled on frontend.
         *
         *     const isPatientInitialsValid = (myString: any) => {
         *         return /^[a-zA-Z'\- ]*$/.test(myString);
         *     };
         *
         */
        @Suppress("ObjectPropertyNaming")
        private val VALID_NAME_MATCHER = Regex("[A-Za-z'\\- ]+")

        /**
         * Validates the patient's info
         * ref:
         * - cradle-platform/client/src/pages/newReading/demographic/validation/index.tsx
         * - cradle-platform/server/validation/patients.py
         *
         * Determines if the [value] for property [property] is valid. If valid, the [Pair] returned
         * will have a Boolean value of true in the first component. Otherwise, false will be in the
         * first component and an error message will be present ([context] is required to get a
         * localized error message).
         *
         * @param instance An instance of the object to take current values from for properties that
         * check other properties for validity. Optional, but don't specify both a non-null
         * [instance] and a [currentValues] map.
         * @param currentValues A Map of KProperty.name to their values to describe current values for
         * properties that check other properties for validity. Optional only if not passing in an
         * instance. (The values in here take precedence if you do.)
         */
        @Suppress("ReturnCount", "SimpleDateFormat", "NestedBlockDepth")
        override fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context,
            instance: Patient?,
            currentValues: Map<String, Any?>?
        ): Pair<Boolean, String> = when (property) {
            // doesn't depend on other properties
            Patient::id -> with(value as String) {
                if (isBlank()) {
                    return Pair(false, context.getString(R.string.patient_error_id_missing))
                }
                if (length > ID_MAX_LENGTH) {
                    return Pair(
                        false,
                        context.getString(
                            R.string.patient_error_id_too_long_max_n_digits,
                            ID_MAX_LENGTH
                        )
                    )
                }
                if (!isDigitsOnly()) {
                    return Pair(false, context.getString(R.string.patient_error_id_must_be_number))
                }

                return Pair(true, "")
            }

            // doesn't depend on other properties
            Patient::name -> with(value as String) {
                if (isBlank()) {
                    return Pair(false, context.getString(R.string.patient_error_name_missing))
                }
                if (!VALID_NAME_MATCHER.matches(this)) {
                    return Pair(
                        false,
                        context.getString(R.string.patient_error_name_must_be_characters)
                    )
                }
                if (length > NAME_MAX_LENGTH) {
                    return false to context.getString(
                        R.string.error_too_long_over_n_chars,
                        NAME_MAX_LENGTH
                    )
                }
                return Pair(true, "")
            }

            // validity of dob depends on age
            Patient::dob -> with(value as String?) {
                if (this == null || isBlank()) {
                    return@with Pair(false, context.getString(R.string.patient_error_age_or_dob_missing))
                }

                val age = try {
                    calculateAgeFromDateString(this)
                } catch (e: ParseException) {
                    return@with Pair(
                        false,
                        context.getString(
                            R.string.patient_error_dob_format,
                            DOB_FORMAT_SIMPLEDATETIME
                        )
                    )
                }

                if (age > AGE_UPPER_BOUND || age < AGE_LOWER_BOUND) {
                    return Pair(
                        false,
                        context.getString(
                            R.string.patient_error_age_between_n_and_m,
                            AGE_LOWER_BOUND,
                            AGE_UPPER_BOUND
                        )
                    )
                }
                return Pair(true, "")
            }

            // Validity of gestational age depends on gender and pregnancy; we are requiring both to
            // be more robust about it. Note: If gender is not male or is not pregnant, this is
            // still validated.
            Patient::gestationalAge -> with(value as? GestationalAge?) {
                val dependentProperties = setupDependentPropertiesMap(
                    instance,
                    currentValues,
                    Patient::sex,
                    Patient::isPregnant
                )
                if (dependentProperties[Patient::sex.name] == Sex.MALE ||
                    dependentProperties[Patient::isPregnant.name] == false
                ) {
                    return if (this == null) {
                        Pair(true, "")
                    } else {
                        // Can't have gestational age if we're not pregnant.
                        Pair(false, context.getString(R.string.patient_error_gestation_for_no_preg))
                    }
                }
                if (this == null) {
                    return Pair(
                        false,
                        context.getString(R.string.patient_error_gestational_age_missing)
                    )
                }
                // to see where the logic is derived from, run
                // $ cat cradle-platform/server/validation/patients.py
                // $ cat cradle-platform/client/src/pages/newReading/demographic/index.tsx
                if (this.age.weeks < 1) {
                    return Pair(
                        false,
                        context.getString(R.string.patient_error_gestation_must_be_not_zero)
                    )
                }

                if (this.age.weeks > GESTATIONAL_AGE_WEEKS_MAX) {
                    return if (this is GestationalAgeWeeks) {
                        Pair(
                            false,
                            context.getString(
                                R.string.patient_error_gestation_greater_than_n_weeks,
                                GESTATIONAL_AGE_WEEKS_MAX
                            )
                        )
                    } else {
                        Pair(
                            false,
                            context.getString(
                                R.string.patient_error_gestation_greater_than_n_months,
                                GESTATIONAL_AGE_MONTHS_MAX
                            )
                        )
                    }
                }
                return Pair(true, "")
            }

            Patient::sex -> with(value as? Sex?) {
                if (this != null) {
                    Pair(true, "")
                } else {
                    Pair(false, context.getString(R.string.patient_error_sex_missing))
                }
            }

            Patient::zone -> with(value as? String?) {
                if (this == null) {
                    // Zone is optional
                    return true to ""
                }

                if (length > ZONE_MAX_LENGTH) {
                    return false to context.getString(
                        R.string.error_too_long_over_n_chars,
                        ZONE_MAX_LENGTH
                    )
                }

                return true to ""
            }

            Patient::villageNumber -> with(value as? String?) {
                if (isNullOrBlank()) {
                    // Village number is optional
                    return true to ""
                }

                if (!isDigitsOnly()) {
                    return Pair(
                        false,
                        context.getString(R.string.patient_error_must_be_number)
                    )
                }

                if (length > VILLAGE_NUMBER_MAX_LENGTH) {
                    return false to context.getString(
                        R.string.error_too_long_over_n_chars,
                        VILLAGE_NUMBER_MAX_LENGTH
                    )
                }

                return true to ""
            }

            Patient::householdNumber -> with(value as? String?) {
                if (this == null) {
                    // Household number is optional, so this is valid
                    return true to ""
                }

                if (!isDigitsOnly()) {
                    return false to context.getString(R.string.patient_error_must_be_number)
                }

                if (length > HOUSEHOLD_NUMBER_MAX_LENGTH) {
                    return false to context.getString(
                        R.string.error_too_long_over_n_chars,
                        HOUSEHOLD_NUMBER_MAX_LENGTH
                    )
                }

                return true to ""
            }

            // Default to true for all other fields / stuff that isn't implemented.
            else -> {
                true to ""
            }
        }

        /**
         * Given a date string of the from specified by [DOB_FORMAT_SIMPLEDATETIME], returns the
         * age. This logic is the same logic that is used in the frontend
         *
         * @throws ParseException if date is invalid, or not in the specified form.
         */
        @JvmStatic
        @Throws(ParseException::class)
        fun calculateAgeFromDateString(dateString: String): Int = with(dateString) {
            SimpleDateFormat(DOB_FORMAT_SIMPLEDATETIME, Locale.getDefault()).let {
                it.isLenient = false
                it.timeZone = TimeZone.getTimeZone("UTC")
                it.parse(this)
            }

            val year = substring(0, indexOf('-')).toIntOrNull()
                ?: throw ParseException("bad year", 0)
            val yearNow = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
            return@with yearNow - year
        }
    }

    object Serializer : StdSerializer<Patient>(Patient::class.java) {
        fun write(patient: Patient, gen: JsonGenerator) {
            patient.run {
                gen.writeStringField(PatientField.ID, id)
                gen.writeStringField(PatientField.NAME, name)
                gen.writeStringField(PatientField.DOB, dob!!)
                gen.writeBooleanField(PatientField.IS_EXACT_DOB, isExactDob!!)
                gen.writeStringField(PatientField.SEX, sex.name)
                gen.writeBooleanField(PatientField.IS_PREGNANT, isPregnant)
                if (isPregnant) {
                    patient.gestationalAge?.let { GestationalAge.serialize(gen, it) }
                }
                gen.writeOptStringField(PatientField.ZONE, zone)
                gen.writeOptStringField(PatientField.VILLAGE_NUMBER, villageNumber)
                gen.writeOptStringField(PatientField.HOUSEHOLD_NUMBER, householdNumber)
                gen.writeOptStringField(PatientField.DRUG_HISTORY, drugHistory)
                gen.writeOptStringField(PatientField.MEDICAL_HISTORY, medicalHistory)
                gen.writeOptLongField(PatientField.LAST_EDITED, lastEdited)
                gen.writeOptLongField(PatientField.BASE, base)
            }
        }

        override fun serialize(
            patient: Patient,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            gen.writeStartObject()
            write(patient, gen)
            gen.writeEndObject()
        }
    }

    object Deserializer : StdDeserializer<Patient>(Patient::class.java) {
        fun get(jsonNode: JsonNode): Patient = jsonNode.run {
            val id = get(PatientField.ID)!!.textValue()
            val name = get(PatientField.NAME)!!.textValue()
            // server seed data might have these as null
            val dob = get(PatientField.DOB)?.textValue()
            val isExactDob = get(PatientField.IS_EXACT_DOB)?.asBoolean(false)

            val gestationalAge = if (
                has(PatientField.GESTATIONAL_AGE_UNIT.text) &&
                has(PatientField.GESTATIONAL_AGE_VALUE.text)
            ) {
                GestationalAge.deserialize(this)
            } else {
                null
            }

            val sex = Sex.valueOf(get(PatientField.SEX)!!.textValue())
            val isPregnant = get(PatientField.IS_PREGNANT)!!.booleanValue()
            val zone = get(PatientField.ZONE)?.textValue()
            val villageNumber = get(PatientField.VILLAGE_NUMBER)?.textValue()
            val householdNumber = get(PatientField.HOUSEHOLD_NUMBER)?.textValue()
            val drugHistory = get(PatientField.DRUG_HISTORY)?.textValue() ?: ""
            val medicalHistory = get(PatientField.MEDICAL_HISTORY)?.textValue() ?: ""
            val lastEdited = get(PatientField.LAST_EDITED)?.asLong()
            val base = get(PatientField.BASE)?.asLong()

            return@run Patient(
                id = id,
                name = name,
                dob = dob,
                isExactDob = isExactDob,
                gestationalAge = gestationalAge,
                sex = sex,
                isPregnant = isPregnant,
                zone = zone,
                villageNumber = villageNumber,
                householdNumber = householdNumber,
                drugHistory = drugHistory,
                medicalHistory = medicalHistory,
                lastEdited = lastEdited,
                base = base
            )
        }

        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): Patient =
            parser.codec.readTree<JsonNode>(parser).run {
                return get(this)
            }
    }
}

/**
 * A database view containing a patient and all readings associated with it.
 *
 * Note that the default constructor for this class is required for
 * constructing from DAO objects but should never be used by user code.
 */
@JsonSerialize(using = PatientAndReadings.Serializer::class)
@JsonDeserialize(using = PatientAndReadings.Deserializer::class)
class PatientAndReadings() {
    @Embedded
    lateinit var patient: Patient

    @Relation(parentColumn = "id", entityColumn = "patientId")
    lateinit var readings: List<Reading>

    constructor(patient: Patient, readings: List<Reading>) : this() {
        // Note that this cannot be a primary constructor as the `patient` and
        // `reading` members cannot be constructor parameters as this is
        // forbidden by the `@Relation` annotation.
        this.patient = patient
        this.readings = readings
    }

    class Serializer : StdSerializer<PatientAndReadings>(PatientAndReadings::class.java) {
        override fun serialize(
            patientAndReadings: PatientAndReadings,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            patientAndReadings.run {
                gen.writeStartObject()
                Patient.Serializer.write(patientAndReadings.patient, gen)
                gen.writeObjectField(PatientField.READINGS, readings)
                gen.writeEndObject()
            }
        }
    }

    class Deserializer : StdDeserializer<PatientAndReadings>(PatientAndReadings::class.java) {
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext
        ): PatientAndReadings = p.codec.readTree<JsonNode>(p)!!.run {
            val patient = Patient.Deserializer.get(this)
            val readings = getOptObjectArray<Reading>(PatientField.READINGS, p.codec) ?: emptyList()
            return PatientAndReadings(patient, readings)
        }
    }

    /**
     * Generated by Android Studio
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PatientAndReadings

        if (patient != other.patient) return false
        if (readings != other.readings) return false

        return true
    }

    /**
     * Generated by Android Studio
     */
    override fun hashCode(): Int {
        var result = patient.hashCode()
        result = 31 * result + readings.hashCode()
        return result
    }
}

/**
 * The sex of a patient.
 */
enum class Sex {
    MALE, FEMALE, OTHER
}

/**
 * Holds data about the gestational age of a patient.
 *
 * Instead of storing both the unit type and value in the patient, we abstract
 * the concept of a gestational age into an abstract data type. Implementors
 * are responsible for marshalling to JSON and the base class is responsible
 * for unmarshalling.
 *
 * With regards to marshalling, care is taken to ensure that the new data
 * format conforms to the legacy API. This means that, when marshalling a
 * [Patient] object, one should union the gestational age JSON object with
 * the patient's one.
 *
 * @see GestationalAgeWeeks
 * @see GestationalAgeMonths
 * @property timestamp The UNIX timestamp (in seconds)
 */
sealed class GestationalAge(val timestamp: Long) : Marshal<JSONObject>, Serializable {
    /**
     * The age in weeks and days.
     *
     * Implementors must be able to convert whatever internal value they store
     * into this format.
     */
    abstract val age: WeeksAndDays

    companion object : Unmarshal<GestationalAge, JSONObject> {

        // These need to be marked static so we can share them with implementors.
        @JvmStatic
        protected val UNIT_VALUE_WEEKS = "WEEKS"
        @JvmStatic
        protected val UNIT_VALUE_MONTHS = "MONTHS"

        /**
         * Constructs a [GestationalAge] variant from a [JSONObject].
         *
         * @throws JSONException if any of the required fields are missing or
         * if the value for the gestational age unit field is invalid.
         */
        override fun unmarshal(data: JSONObject): GestationalAge {
            val units = data.stringField(PatientField.GESTATIONAL_AGE_UNIT)
            val value = data.longField(PatientField.GESTATIONAL_AGE_VALUE)
            return when (units) {
                UNIT_VALUE_WEEKS -> GestationalAgeWeeks(value)
                UNIT_VALUE_MONTHS -> GestationalAgeMonths(value)
                else -> throw JSONException("invalid value for ${PatientField.GESTATIONAL_AGE_UNIT.text}")
            }
        }

        /**
         * Nested deserialization from the given [jsonNode]
         */
        fun deserialize(jsonNode: JsonNode): GestationalAge? = jsonNode.run {
            val units = get(PatientField.GESTATIONAL_AGE_UNIT)!!.asText()
            val value = get(PatientField.GESTATIONAL_AGE_VALUE)!!.asLong()
            return when (units) {
                UNIT_VALUE_WEEKS -> GestationalAgeWeeks(value)
                UNIT_VALUE_MONTHS -> GestationalAgeMonths(value)
                else -> throw JSONException("invalid value for ${PatientField.GESTATIONAL_AGE_UNIT.text}")
            }
        }

        /**
         * Nested serialization into the given [gen]
         */
        fun serialize(gen: JsonGenerator, gestationalAge: GestationalAge) {
            val units = if (gestationalAge is GestationalAgeMonths) {
                UNIT_VALUE_MONTHS
            } else {
                UNIT_VALUE_WEEKS
            }
            gen.writeStringField(PatientField.GESTATIONAL_AGE_UNIT, units)
            gen.writeStringField(
                PatientField.GESTATIONAL_AGE_VALUE,
                gestationalAge.timestamp.toString()
            )
        }
    }

    /**
     * True if `this` and [other] are an instance of the same class and have the
     * same [timestamp].
     *
     * This means that a gestational age in weeks is never equal to a
     * gestational age in months even if they represent the same amount of time.
     */
    override fun equals(other: Any?): Boolean {
        if (this.hashCode() != other.hashCode()) {
            return false
        }

        return when (other) {
            is GestationalAgeWeeks ->
                if (this is GestationalAgeWeeks) {
                    this.timestamp == other.timestamp
                } else {
                    false
                }
            is GestationalAgeMonths ->
                if (this is GestationalAgeMonths) {
                    this.timestamp == other.timestamp
                } else {
                    false
                }
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + age.hashCode()
        return result
    }

    override fun toString(): String {
        return "GestationalAge($age, value=$timestamp)"
    }
}

/**
 * Variant of [GestationalAge] which stores age in number of weeks.
 */
class GestationalAgeWeeks(timestamp: Long) : GestationalAge(timestamp), Serializable {
    override val age: WeeksAndDays
        get() {
            val seconds = Seconds(UnixTimestamp.now - timestamp)
            return WeeksAndDays.weeks(Weeks(seconds).value)
        }

    constructor(duration: Weeks) : this(UnixTimestamp.ago(duration))

    override fun marshal(): JSONObject = with(JSONObject()) {
        // For legacy interop we store the value as a string instead of an int.
        put(PatientField.GESTATIONAL_AGE_VALUE, timestamp.toString())
        put(PatientField.GESTATIONAL_AGE_UNIT, UNIT_VALUE_WEEKS)
    }

    override fun toString(): String {
        return "GestationalAgeWeeks($age, value=$timestamp)"
    }
}

/**
 * Variant of [GestationalAge] which stores age in number of months.
 */
class GestationalAgeMonths(timestamp: Long) : GestationalAge(timestamp), Serializable {
    /**
     * Back up the months value as a String. This is not marshalled.
     *
     * The user can enter an arbitrary decimal into the gestational age input. However, Due to the
     * various round-off errors that can occur when using WeeksAndDays#asMonths, an input may change
     * suddenly and confuse the user (e.g., after putting in 6 months, going to Summary tab, and
     * then going back to the Patient info tab, the value that gets shown to the user is along the
     * lines of "6.0000000000000000001").
     *
     * TODO: Fix this when the fragments are rearchitected/redesigned
     */
    private var inputMonths: Months? = null

    constructor(duration: Months) : this(UnixTimestamp.ago(duration)) {
        inputMonths = duration
    }

    override val age: WeeksAndDays
        get() {
            val seconds = Seconds(UnixTimestamp.now - timestamp)
            return WeeksAndDays.months(Months(seconds).value)
        }

    override fun marshal(): JSONObject = with(JSONObject()) {
        // For legacy interop we store the value as a string instead of an int.
        put(PatientField.GESTATIONAL_AGE_VALUE, timestamp.toString())
        put(PatientField.GESTATIONAL_AGE_UNIT, UNIT_VALUE_MONTHS)
    }

    override fun toString(): String {
        return "GestationalAgeMonths($age, value=$timestamp)"
    }
}

/**
 * A temporal duration expressed in weeks and days.
 */
data class WeeksAndDays(val weeks: Long, val days: Long) : Serializable {

    /**
     * This value in number of weeks.
     */
    fun asWeeks(): Double = weeks.toDouble() + (days.toDouble() / DAYS_PER_WEEK)

    /**
     * This value in number of months.
     */
    fun asMonths(): Double =
        (weeks.toDouble() / WEEKS_PER_MONTH) + (days.toDouble() / DAYS_PER_MONTH)

    companion object {
        const val DAYS_PER_MONTH = 30
        const val DAYS_PER_WEEK = 7
        const val SECONDS_PER_DAY = 60 * 60 * 24
        const val SECONDS_PER_WEEK = SECONDS_PER_DAY * DAYS_PER_WEEK
        // Get as close as possible to the result of 30/7, a repeating decimal. However, due to the
        // finiteness of Doubles, anything past 4.2857142857142857 is ignored.
        private const val WEEKS_PER_MONTH = 4.2857142857142857

        fun weeks(weeks: Long) = WeeksAndDays(weeks, 0)

        fun months(months: Double): WeeksAndDays {
            val days = DAYS_PER_MONTH * months
            return WeeksAndDays((days / DAYS_PER_WEEK).toLong(), days.toLong() % DAYS_PER_WEEK)
        }
    }

    override fun toString(): String = "WeeksAndDays(weeks=$weeks, days=$days, " +
        "asWeeks()=${asWeeks()}, asMonths()=${asMonths()})"
}

/**
 * The collection of JSON fields which make up a [Patient] object.
 *
 * These fields are defined here to ensure that the marshal and unmarshal
 * methods use the same field names.
 */
private enum class PatientField(override val text: String) : Field {
    ID("patientId"),
    NAME("patientName"),
    DOB("dob"),
    IS_EXACT_DOB("isExactDob"),
    AGE("patientAge"),
    GESTATIONAL_AGE_UNIT("gestationalAgeUnit"),
    GESTATIONAL_AGE_VALUE("gestationalTimestamp"),
    SEX("patientSex"),
    IS_PREGNANT("isPregnant"),
    ZONE("zone"),
    VILLAGE_NUMBER("villageNumber"),
    HOUSEHOLD_NUMBER("householdNumber"),
    DRUG_HISTORY("drugHistory"),
    MEDICAL_HISTORY("medicalHistory"),
    LAST_EDITED("lastEdited"),
    BASE("base"),
    READINGS("readings")
}

/**
 * data class for patient list we get on a global search
 *
 * @property index Index in the global search RecyclerView
 */
@Parcelize
@JsonDeserialize(using = GlobalPatient.Deserializer::class)
data class GlobalPatient(
    val id: String,
    val name: String,
    val villageNum: String?,
    var isMyPatient: Boolean,
    var index: Int?
) : Parcelable {
    class Deserializer : StdDeserializer<GlobalPatient>(GlobalPatient::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): GlobalPatient {
            p.codec.readTree<JsonNode>(p)!!.run {
                return GlobalPatient(
                    id = get(PatientField.ID)!!.textValue(),
                    name = get(PatientField.NAME)!!.textValue(),
                    villageNum = get(PatientField.VILLAGE_NUMBER)?.textValue()?.let {
                        // server is ending back a null for this field for some patients
                        if (it == "null") null else it
                    },
                    isMyPatient = false,
                    index = null
                )
            }
        }
    }
}
