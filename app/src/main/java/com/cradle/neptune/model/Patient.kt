package com.cradle.neptune.model

import android.content.Context
import android.os.Parcelable
import androidx.core.text.isDigitsOnly
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.cradle.neptune.R
import com.cradle.neptune.ext.Field
import com.cradle.neptune.ext.map
import com.cradle.neptune.ext.mapField
import com.cradle.neptune.ext.optArrayField
import com.cradle.neptune.ext.optBooleanField
import com.cradle.neptune.ext.optLongField
import com.cradle.neptune.ext.optStringField
import com.cradle.neptune.ext.put
import com.cradle.neptune.ext.stringField
import com.cradle.neptune.ext.union
import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Seconds
import com.cradle.neptune.utilitiles.UnixTimestamp
import com.cradle.neptune.utilitiles.Weeks
import kotlinx.android.parcel.Parcelize
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
 * @property drugHistoryList A list of drug history for the patient.
 * @property medicalHistoryList A list of medical history for the patient.
 * @property lastEdited Last time patient info was edited
 *
 * TODO: Move drug and medical history to just be Strings, or get the frontend and backend to change
 *  this into a list. There's no point in doing things different on Android and the frontend /
 *  backend.
 * TODO: Make [isExactDob] and [dob] not optional. Requires backend work to enforce it.
 */
@Entity
data class Patient(
    @PrimaryKey
    @ColumnInfo
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
    @ColumnInfo var drugHistoryList: List<String> = emptyList(),
    @ColumnInfo var medicalHistoryList: List<String> = emptyList(),
    @ColumnInfo var lastEdited: Long? = null,
    @ColumnInfo var base: Long? = null
) : Marshal<JSONObject>, Serializable, Verifiable<Patient> {

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
        put(PatientField.IS_EXACT_DOB, isExactDob)
        put(PatientField.SEX, sex.name)
        put(PatientField.IS_PREGNANT, isPregnant)
        put(PatientField.ZONE, zone)
        put(PatientField.VILLAGE_NUMBER, villageNumber)
        put(PatientField.HOUSEHOLD_NUMBER, householdNumber)
        // server only takes string
        put(PatientField.DRUG_HISTORY, drugHistoryList.joinToString())
        put(PatientField.MEDICAL_HISTORY, medicalHistoryList.joinToString())
        put(PatientField.LAST_EDITED, lastEdited)
        put(PatientField.BASE, base)
    }

    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context
    ): Pair<Boolean, String> = isValueValid(property, value, context, instance = this)

    @Suppress("LargeClass")
    companion object : Unmarshal<Patient, JSONObject>, Verifier<Patient> {
        private const val ID_MAX_LENGTH = 14
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

            Patient::villageNumber -> with(value as? String?) {
                // TODO: make village number an Int, not a String
                if (this.isNullOrBlank()) {
                    return Pair(
                        false,
                        context.getString(R.string.patient_error_village_number_missing)
                    )
                }

                if (!isDigitsOnly()) {
                    return Pair(
                        false,
                        context.getString(R.string.patient_error_village_number_not_number)
                    )
                }

                return true to ""
            }

            // Default to true for all other fields / stuff that isn't implemented.
            else -> {
                Pair(true, "")
            }
        }

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
            isExactDob = data.optBooleanField(PatientField.IS_EXACT_DOB)
            gestationalAge = maybeUnmarshal(GestationalAge, data)
            sex = data.mapField(PatientField.SEX, Sex::valueOf)
            isPregnant = data.optBooleanField(PatientField.IS_PREGNANT)
            zone = data.optStringField(PatientField.ZONE)
            villageNumber = data.optStringField(PatientField.VILLAGE_NUMBER)
            householdNumber = data.optStringField(PatientField.HOUSEHOLD_NUMBER)
            // Server returns a String for drug and medical histories.
            // TODO: see Patient doc comment for the note
            drugHistoryList = data.optStringField(PatientField.DRUG_HISTORY)
                ?.run { if (isBlank()) emptyList() else listOf(this) } ?: emptyList()
            medicalHistoryList = data.optStringField(PatientField.MEDICAL_HISTORY)
                ?.run { if (isBlank()) emptyList() else listOf(this) } ?: emptyList()
            lastEdited = data.optLongField(PatientField.LAST_EDITED)
            base = data.optLongField(PatientField.BASE)
        }

        /**
         * Given a date string of the from specified by [DOB_FORMAT_SIMPLEDATETIME], returns the
         * age. This logic is the same logic that is used in the frontend
         *
         * @throws ParseException if date is invalid, or not in the specified form.
         */
        @JvmStatic
        fun calculateAgeFromDateString(dateString: String): Int = with(dateString) {
            if (dateString.length != DOB_FORMAT_SIMPLEDATETIME.length) {
                throw ParseException("wrong format length", 0)
            }

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
data class GlobalPatient(
    val id: String,
    val initials: String,
    val villageNum: String,
    var isMyPatient: Boolean,
    var index: Int?
) : Parcelable {
    companion object : Unmarshal<GlobalPatient, JSONObject> {
        override fun unmarshal(data: JSONObject) = GlobalPatient(
            id = data.stringField(PatientField.ID),
            initials = data.stringField(PatientField.NAME),
            villageNum = data.stringField(PatientField.VILLAGE_NUMBER),
            isMyPatient = false,
            index = null
        )
    }
}
