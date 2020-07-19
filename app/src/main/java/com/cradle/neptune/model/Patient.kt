package com.cradle.neptune.model

import java.io.Serializable

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
data class Patient(
    var id: String = "",
    var name: String = "",
    var dob: String? = null,
    var age: Int? = null,
    var gestationalAge: GestationalAge? = null,
    var sex: Sex = Sex.OTHER,
    var isPregnant: Boolean = false,
    var zone: String? = null,
    var villageNumber: String? = null,
    var drugHistoryList: List<String> = emptyList(),
    var medicalHistoryList: List<String> = emptyList(),
    var lastEdited: Long? = null
) : Marshal<JsonObject>, Serializable {

    /**
     * Constructs a [JsonObject] from this object.
     */
    override fun marshal(): JsonObject = with(JsonObject()) {
        if (gestationalAge != null) {
            union(gestationalAge!!)
        }

        put(PatientField.ID, id)
        put(PatientField.NAME, name)
        put(PatientField.DOB, dob)
        put(PatientField.AGE, age)
        put(PatientField.SEX, sex.name)
        put(PatientField.IS_PREGNANT, isPregnant)
        put(PatientField.ZONE, zone)
        put(PatientField.VILLAGE_NUMBER, villageNumber)
        put(PatientField.DRUG_HISTORY, drugHistoryList)
        put(PatientField.MEDICAL_HISTORY, medicalHistoryList)
        put(PatientField.LAST_EDITED, lastEdited)
    }

    companion object : Unmarshal<Patient, JsonObject> {
        /**
         * Constructs a [Patient] object from a [JsonObject].
         *
         * @param data The JSON data to unmarshal.
         * @return A new patient.
         *
         * @throws JsonException If any of the required patient fields are
         * missing from [data].
         *
         * @throws IllegalArgumentException If the value for an enum field
         * cannot be converted into said enum.
         */
        override fun unmarshal(data: JsonObject): Patient = Patient().apply {
            id = data.optStringField(PatientField.ID) ?: ""
            name = data.optStringField(PatientField.NAME) ?: ""
            dob = data.optStringField(PatientField.DOB)
            age = data.optIntField(PatientField.AGE)
            gestationalAge = maybeUnmarshal(GestationalAge, data)
            sex = data.mapField(PatientField.SEX, Sex::valueOf)
            isPregnant = data.booleanField(PatientField.IS_PREGNANT)
            // needsAssessment = data.booleanField(PatientField.NEEDS_ASSESSMENT)
            zone = data.optStringField(PatientField.ZONE)
            villageNumber = data.optStringField(PatientField.VILLAGE_NUMBER)

            drugHistoryList = data.optArrayField(PatientField.DRUG_HISTORY)
                ?.toList(JsonArray::getString) ?: emptyList()
            medicalHistoryList = data.optArrayField(PatientField.MEDICAL_HISTORY)
                ?.toList(JsonArray::getString) ?: emptyList()
            lastEdited = data.optLongField(PatientField.LAST_EDITED)
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
sealed class GestationalAge(val value: Int) : Marshal<JsonObject>, Serializable {
    /**
     * The age in weeks and days.
     *
     * Implementors must be able to convert whatever internal value they store
     * into this format.
     */
    abstract val age: WeeksAndDays

    companion object : Unmarshal<GestationalAge, JsonObject> {

        // These need to be marked static so we can share them with implementors.
        @JvmStatic protected val UNIT_VALUE_WEEKS = "GESTATIONAL_AGE_UNITS_WEEKS"
        @JvmStatic protected val UNIT_VALUE_MONTHS = "GESTATIONAL_AGE_UNITS_MONTHS"

        /**
         * Constructs a [GestationalAge] variant from a [JsonObject].
         *
         * @throws JsonException if any of the required fields are missing or
         * if the value for the gestational age unit field is invalid.
         */
        override fun unmarshal(data: JsonObject): GestationalAge {
            val units = data.stringField(PatientField.GESTATIONAL_AGE_UNIT)
            val value = data.stringField(PatientField.GESTATIONAL_AGE_VALUE).toInt()
            return when (units) {
                UNIT_VALUE_WEEKS -> GestationalAgeWeeks(value)
                UNIT_VALUE_MONTHS -> GestationalAgeMonths(value)
                else -> throw JsonException("invalid value for ${PatientField.GESTATIONAL_AGE_UNIT.text}")
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
        var result = value
        result = 31 * result + age.hashCode()
        return result
    }
}

/**
 * Variant of [GestationalAge] which stores age in number of weeks.
 */
class GestationalAgeWeeks(weeks: Int) : GestationalAge(weeks), Serializable {
    override val age = WeeksAndDays.weeks(value)

    override fun marshal(): JsonObject = with(JsonObject()) {
        // For legacy interop we store the value as a string instead of an int.
        put(PatientField.GESTATIONAL_AGE_VALUE, value.toString())
        put(PatientField.GESTATIONAL_AGE_UNIT, UNIT_VALUE_WEEKS)
    }
}

/**
 * Variant of [GestationalAge] which stores age in number of months.
 */
class GestationalAgeMonths(months: Int) : GestationalAge(months), Serializable {
    override val age = WeeksAndDays.months(value)

    override fun marshal(): JsonObject = with(JsonObject()) {
        // For legacy interop we store the value as a string instead of an int.
        put(PatientField.GESTATIONAL_AGE_VALUE, value.toString())
        put(PatientField.GESTATIONAL_AGE_UNIT, UNIT_VALUE_MONTHS)
    }
}

/**
 * A temporal duration expressed in weeks and days.
 */
data class WeeksAndDays(val weeks: Int, val days: Int) : Serializable {

    /**
     * This value in number of weeks.
     */
    fun asWeeks(): Double = weeks.toDouble() + (days.toDouble() / DAYS_PER_WEEK)

    /**
     * This value in number of months.
     */
    fun asMonths(): Double = (weeks.toDouble() / WEEKS_PER_MONTH) + (days.toDouble() / DAYS_PER_MONTH)

    companion object {
        private const val DAYS_PER_MONTH = 30
        private const val DAYS_PER_WEEK = 7
        private const val WEEKS_PER_MONTH = 4.34524

        fun weeks(weeks: Int) = WeeksAndDays(weeks, 0)

        fun months(months: Int): WeeksAndDays {
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
    GESTATIONAL_AGE_VALUE("gestationalAgeValue"),
    SEX("patientSex"),
    IS_PREGNANT("isPregnant"),
    ZONE("zone"),
    VILLAGE_NUMBER("villageNumber"),
    DRUG_HISTORY("drugHistory"),
    MEDICAL_HISTORY("medicalHistory"),
    LAST_EDITED("lastEdited")
}

/**
 * data class for patient list we get on a global search
 */
data class GlobalPatient(
    val id: String,
    val initials: String,
    val villageNum: String,
    var isMyPatient: Boolean
) : Serializable
