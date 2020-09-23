package com.cradle.neptune.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.cradle.neptune.ext.Field
import com.cradle.neptune.ext.map
import com.cradle.neptune.ext.mapField
import com.cradle.neptune.ext.optArrayField
import com.cradle.neptune.ext.optBooleanField
import com.cradle.neptune.ext.optIntField
import com.cradle.neptune.ext.optLongField
import com.cradle.neptune.ext.optStringField
import com.cradle.neptune.ext.put
import com.cradle.neptune.ext.stringField
import com.cradle.neptune.ext.toList
import com.cradle.neptune.ext.union
import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Seconds
import com.cradle.neptune.utilitiles.UnixTimestamp
import com.cradle.neptune.utilitiles.Weeks
import java.io.Serializable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

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
 * @property age This patient's age; used if [dob] is not known.
 * @property gestationalAge The gestational age of this patient if applicable.
 * @property sex This patient's sex.
 * @property isPregnant Whether this patient is pregnant or not.
 * @property zone The zone in which this patient lives.
 * @property villageNumber The number of the village in which this patient lives.
 * @property drugHistoryList A list of drug history for the patient.
 * @property medicalHistoryList A list of medical history for the patient.
 * @property lastEdited Last time patient info was edited
 */
@Entity
data class Patient(
    @PrimaryKey
    @ColumnInfo var id: String = "",
    @ColumnInfo var name: String = "",
    @ColumnInfo var dob: String? = null,
    @ColumnInfo var age: Int? = null,
    @ColumnInfo var gestationalAge: GestationalAge? = null,
    @ColumnInfo var sex: Sex = Sex.OTHER,
    @ColumnInfo var isPregnant: Boolean = false,
    @ColumnInfo var zone: String? = null,
    @ColumnInfo var villageNumber: String? = null,
    @ColumnInfo var drugHistoryList: List<String> = emptyList(),
    @ColumnInfo var medicalHistoryList: List<String> = emptyList(),
    @ColumnInfo var lastEdited: Long? = null,
    @ColumnInfo var base: Long? = null
) : Marshal<JSONObject>, Serializable {

    /**
     * Constructs a [JSONObject] from this object.
     */
    override fun marshal(): JSONObject = with(JSONObject()) {
        if (gestationalAge != null) {
            union(gestationalAge)
        }

        put(PatientField.ID, id)
        put(PatientField.NAME, name)
        put(PatientField.DOB, dob)
        put(PatientField.AGE, age)
        put(PatientField.SEX, sex.name)
        put(PatientField.IS_PREGNANT, isPregnant)
        put(PatientField.ZONE, zone)
        put(PatientField.VILLAGE_NUMBER, villageNumber)
        // server only takes string
        put(PatientField.DRUG_HISTORY, drugHistoryList.joinToString())
        put(PatientField.MEDICAL_HISTORY, medicalHistoryList.joinToString())
        put(PatientField.LAST_EDITED, lastEdited)
        put(PatientField.BASE, base)
    }

    companion object : Unmarshal<Patient, JSONObject> {
        /**
         * Constructs a [Patient] object from a [JSONObject].
         *
         * @param data The JSON data to unmarshal.
         * @return A new patient.
         *
         * @throws JSONException If any of the required patient fields are
         * missing from [data].
         *
         * @throws IllegalArgumentException If the value for an enum field
         * cannot be converted into said enum.
         */
        override fun unmarshal(data: JSONObject): Patient = Patient().apply {
            id = data.optStringField(PatientField.ID) ?: ""
            name = data.optStringField(PatientField.NAME) ?: ""
            dob = data.optStringField(PatientField.DOB)
            age = data.optIntField(PatientField.AGE)
            gestationalAge = maybeUnmarshal(GestationalAge, data)
            sex = data.mapField(PatientField.SEX, Sex::valueOf)
            isPregnant = data.optBooleanField(PatientField.IS_PREGNANT)
            zone = data.optStringField(PatientField.ZONE)
            villageNumber = data.optStringField(PatientField.VILLAGE_NUMBER)

            drugHistoryList = data.optArrayField(PatientField.DRUG_HISTORY)
                ?.toList(JSONArray::getString) ?: emptyList()
            medicalHistoryList = data.optArrayField(PatientField.MEDICAL_HISTORY)
                ?.toList(JSONArray::getString) ?: emptyList()
            lastEdited = data.optLongField(PatientField.LAST_EDITED)
            base = data.optLongField(PatientField.BASE)
        }
    }
}

/**
 * A database view containing a patient and all readings associated with it.
 *
 * Note that the default constructor for this class is required for
 * constructing from DAO objects but should never be used by user code.
 */
class PatientAndReadings() : Marshal<JSONObject> {
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

    /**
     * Marshals this patient and its readings into a single JSON object.
     *
     * All of the patient fields can be found at the top level of the object
     * with the readings being nested under the "readings" field.
     *
     * @return A JSON object
     */
    override fun marshal() = with(JSONObject()) {
        union(patient)
        put(PatientField.READINGS, readings)
    }

    companion object : Unmarshal<PatientAndReadings, JSONObject> {
        /**
         * Converts a JSON object into a patient and list of readings.
         */
        override fun unmarshal(data: JSONObject): PatientAndReadings {
            val patient = Patient.unmarshal(data)
            val readings = data.optArrayField(PatientField.READINGS)
                ?.map({ arr, i -> arr.getJSONObject(i) }, Reading.Companion::unmarshal)
                ?: emptyList()
            return PatientAndReadings(patient, readings)
        }
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
 */
sealed class GestationalAge(val value: Long) : Marshal<JSONObject>, Serializable {
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
        protected val UNIT_VALUE_WEEKS = "GESTATIONAL_AGE_UNITS_WEEKS"
        @JvmStatic
        protected val UNIT_VALUE_MONTHS = "GESTATIONAL_AGE_UNITS_MONTHS"

        /**
         * Constructs a [GestationalAge] variant from a [JSONObject].
         *
         * @throws JSONException if any of the required fields are missing or
         * if the value for the gestational age unit field is invalid.
         */
        override fun unmarshal(data: JSONObject): GestationalAge {
            val units = data.stringField(PatientField.GESTATIONAL_AGE_UNIT)
            val value = data.stringField(PatientField.GESTATIONAL_AGE_VALUE).toLong()
            return when (units) {
                UNIT_VALUE_WEEKS -> GestationalAgeWeeks(value)
                UNIT_VALUE_MONTHS -> GestationalAgeMonths(value)
                else -> throw JSONException("invalid value for ${PatientField.GESTATIONAL_AGE_UNIT.text}")
            }
        }
    }

    /**
     * True if `this` and [other] are an instance of the same class and have the
     * same [value].
     *
     * This means that a gestational age in weeks is never equal to a
     * gestational age in months even if they represent the same amount of time.
     */
    override fun equals(other: Any?): Boolean {
        if (this.hashCode() != other.hashCode()) {
            return false
        }

        return when (other) {
            is GestationalAgeWeeks -> if (this is GestationalAgeWeeks) {
                this.value == other.value
            } else {
                false
            }
            is GestationalAgeMonths -> if (this is GestationalAgeMonths) {
                this.value == other.value
            } else {
                false
            }
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + age.hashCode()
        return result
    }
}

/**
 * Variant of [GestationalAge] which stores age in number of weeks.
 */
class GestationalAgeWeeks(timestamp: Long) : GestationalAge(timestamp), Serializable {
    override val age: WeeksAndDays
        get() {
            val seconds = Seconds(UnixTimestamp.now - value)
            return WeeksAndDays.weeks(Weeks(seconds).value)
        }

    constructor(duration: Weeks) : this(UnixTimestamp.ago(duration))

    override fun marshal(): JSONObject = with(JSONObject()) {
        // For legacy interop we store the value as a string instead of an int.
        put(PatientField.GESTATIONAL_AGE_VALUE, value.toString())
        put(PatientField.GESTATIONAL_AGE_UNIT, UNIT_VALUE_WEEKS)
    }
}

/**
 * Variant of [GestationalAge] which stores age in number of months.
 */
class GestationalAgeMonths(timestamp: Long) : GestationalAge(timestamp), Serializable {
    override val age: WeeksAndDays
        get() {
            val seconds = Seconds(UnixTimestamp.now - value)
            return WeeksAndDays.months(Months(seconds).value.toLong())
        }

    constructor(duration: Months) : this(UnixTimestamp.ago(duration))

    override fun marshal(): JSONObject = with(JSONObject()) {
        // For legacy interop we store the value as a string instead of an int.
        put(PatientField.GESTATIONAL_AGE_VALUE, value.toString())
        put(PatientField.GESTATIONAL_AGE_UNIT, UNIT_VALUE_MONTHS)
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
        private const val DAYS_PER_MONTH = 30
        private const val DAYS_PER_WEEK = 7
        private const val WEEKS_PER_MONTH = 4.34524

        fun weeks(weeks: Long) = WeeksAndDays(weeks, 0)

        fun months(months: Long): WeeksAndDays {
            val days = DAYS_PER_MONTH * months
            return WeeksAndDays(days / DAYS_PER_WEEK, days % DAYS_PER_WEEK)
        }
    }
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
    AGE("patientAge"),
    GESTATIONAL_AGE_UNIT("gestationalAgeUnit"),
    GESTATIONAL_AGE_VALUE("gestationalTimestamp"),
    SEX("patientSex"),
    IS_PREGNANT("isPregnant"),
    ZONE("zone"),
    VILLAGE_NUMBER("villageNumber"),
    DRUG_HISTORY("drugHistory"),
    MEDICAL_HISTORY("medicalHistory"),
    LAST_EDITED("lastEdited"),
    BASE("base"),
    READINGS("readings")
}

/**
 * data class for patient list we get on a global search
 */
data class GlobalPatient(
    val id: String,
    val initials: String,
    val villageNum: String,
    var isMyPatient: Boolean
) : Serializable {
    companion object : Unmarshal<GlobalPatient, JSONObject> {
        override fun unmarshal(data: JSONObject) = GlobalPatient(
            id = data.stringField(PatientField.ID),
            initials = data.stringField(PatientField.NAME),
            villageNum = data.stringField(PatientField.VILLAGE_NUMBER),
            isMyPatient = false
        )
    }
}
