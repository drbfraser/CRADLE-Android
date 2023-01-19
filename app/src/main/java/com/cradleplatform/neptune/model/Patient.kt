package com.cradleplatform.neptune.model

import android.content.Context
import android.os.Parcelable
import androidx.core.text.isDigitsOnly
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.ext.jackson.get
import com.cradleplatform.neptune.ext.jackson.getOptObjectArray
import com.cradleplatform.neptune.ext.jackson.writeBooleanField
import com.cradleplatform.neptune.ext.jackson.writeObjectField
import com.cradleplatform.neptune.ext.jackson.writeOptIntField
import com.cradleplatform.neptune.ext.jackson.writeOptLongField
import com.cradleplatform.neptune.ext.jackson.writeOptStringField
import com.cradleplatform.neptune.ext.jackson.writeStringField
import com.cradleplatform.neptune.utilities.Months
import com.cradleplatform.neptune.utilities.Seconds
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.cradleplatform.neptune.utilities.Weeks
import com.cradleplatform.neptune.utilities.WeeksAndDays
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
import java.io.IOException
import java.io.Serializable
import java.math.BigInteger
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.round
import kotlin.reflect.KProperty

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
 * @property pregnancyId Id of either the current or previous pregnancy saved on android
 *           prevPregnancyEndDate and outcome is only saved on database if created offline,
 *           therefore the pregnancyId would be related to these changes that need to
 *           be sent to server on sync.
 *           Otherwise, pregnancyId would be related to the gestationalAge value
 *           (when gestationalAge is set on android offline, server gives it an Id on sync)
 * @property prevPregnancyEndDate End date of the previous pregnancy - only used when pregnancy closed offline
 * @property prevPregnancyOutcome Outcome of the previous pregnancy - only used when pregnancy closed offline
 * @property zone The zone in which this patient lives.
 * @property villageNumber The number of the village in which this patient lives.
 * @property drugHistory Drug history for the patient (paragraph form expected).
 * @property medicalHistory Medical history for the patient (paragraph form expected).
 * @property allergy drug allergies for the patient (paragraph form expected).
 * @property lastEdited Last time patient info was edited on android
 * @property drugLastEdited Last time drug info was edited OFFLINE -> will be null if no offline edits
 * @property medicalLastEdited Last time medical info was edited OFFLINE -> will be null if no offline edits
 * @property lastServerUpdate Last time the patient has gotten updated from the server.
 * @property isArchive The flag of either the patient is archived or not
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
    @ColumnInfo var pregnancyId: Int? = null,
    @ColumnInfo var prevPregnancyEndDate: Long? = null,
    @ColumnInfo var prevPregnancyOutcome: String? = null,
    @ColumnInfo var zone: String? = null,
    @ColumnInfo var villageNumber: String? = null,
    @ColumnInfo var householdNumber: String? = null,
    @ColumnInfo var drugHistory: String = "",
    @ColumnInfo var medicalHistory: String = "",
    @ColumnInfo var allergy: String = "",
    @ColumnInfo var lastEdited: Long? = null,
    @ColumnInfo var drugLastEdited: Long? = null,
    @ColumnInfo var medicalLastEdited: Long? = null,
    @ColumnInfo var lastServerUpdate: Long? = null,
    @ColumnInfo var isArchive: Boolean = false
) : Serializable, Verifiable<Patient> {
    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context?
    ): Verifiable.Result = isValueValid(property, value, context, instance = this)

    companion object : Verifiable.Verifier<Patient> {
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
            context: Context?,
            instance: Patient?,
            currentValues: Map<String, Any?>?
        ): Verifiable.Result = when (property) {
            // doesn't depend on other properties
            Patient::id -> with(value as String) {
                if (isBlank()) {
                    return Verifiable.Invalid(
                        property, context?.getString(R.string.patient_error_id_missing)
                    )
                }
                if (length > ID_MAX_LENGTH) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(
                            R.string.patient_error_id_too_long_max_n_digits,
                            ID_MAX_LENGTH
                        )
                    )
                }
                if (!isDigitsOnly()) {
                    return Verifiable.Invalid(property, context?.getString(R.string.patient_error_id_must_be_number))
                }

                return Verifiable.Valid
            }

            // doesn't depend on other properties
            Patient::name -> with(value as String) {
                if (isBlank()) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(R.string.patient_error_name_missing)
                    )
                }
                if (!VALID_NAME_MATCHER.matches(this)) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(R.string.patient_error_name_must_be_characters)
                    )
                }
                if (length > NAME_MAX_LENGTH) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(
                            R.string.error_too_long_over_n_chars,
                            NAME_MAX_LENGTH
                        )
                    )
                }
                return Verifiable.Valid
            }

            // validity of dob depends on age
            Patient::dob -> with(value as String?) {
                if (this == null || isBlank()) {
                    return@with Verifiable.Invalid(
                        property,
                        context?.getString(R.string.patient_error_age_or_dob_missing)
                    )
                }

                val age = try {
                    calculateAgeFromDateString(this)
                } catch (e: ParseException) {
                    return@with Verifiable.Invalid(
                        property,
                        context?.getString(
                            R.string.patient_error_dob_format,
                            DOB_FORMAT_SIMPLEDATETIME
                        )
                    )
                }

                if (age > AGE_UPPER_BOUND || age < AGE_LOWER_BOUND) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(
                            R.string.patient_error_age_between_n_and_m,
                            AGE_LOWER_BOUND,
                            AGE_UPPER_BOUND
                        )
                    )
                }
                return Verifiable.Valid
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
                        Verifiable.Valid
                    } else {
                        // Can't have gestational age if we're not pregnant.
                        Verifiable.Invalid(property, context?.getString(R.string.patient_error_gestation_for_no_preg))
                    }
                }
                if (this == null) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(R.string.patient_error_gestational_age_missing)
                    )
                }
                // to see where the logic is derived from, run
                // $ cat cradle-platform/server/validation/patients.py
                // $ cat cradle-platform/client/src/pages/newReading/demographic/index.tsx
                if (this.ageFromNow.weeks < 1) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(R.string.patient_error_gestation_must_be_not_zero)
                    )
                }

                if (this.ageFromNow.weeks > GESTATIONAL_AGE_WEEKS_MAX) {
                    return if (this is GestationalAgeWeeks) {
                        Verifiable.Invalid(
                            property,
                            context?.getString(
                                R.string.patient_error_gestation_greater_than_n_weeks,
                                GESTATIONAL_AGE_WEEKS_MAX
                            )
                        )
                    } else {
                        Verifiable.Invalid(
                            property,
                            context?.getString(
                                R.string.patient_error_gestation_greater_than_n_months,
                                GESTATIONAL_AGE_MONTHS_MAX
                            )
                        )
                    }
                }
                return Verifiable.Valid
            }

            Patient::sex -> with(value as? Sex?) {
                if (this != null) {
                    Verifiable.Valid
                } else {
                    Verifiable.Invalid(property, context?.getString(R.string.patient_error_sex_missing))
                }
            }

            Patient::zone -> with(value as? String?) {
                if (this == null) {
                    // Zone is optional
                    return Verifiable.Valid
                }

                if (length > ZONE_MAX_LENGTH) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(
                            R.string.error_too_long_over_n_chars,
                            ZONE_MAX_LENGTH
                        )
                    )
                }

                return Verifiable.Valid
            }

            Patient::villageNumber -> with(value as? String?) {
                if (isNullOrBlank()) {
                    // Village number is optional
                    return Verifiable.Valid
                }

                if (!isDigitsOnly()) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(R.string.patient_error_must_be_number)
                    )
                }

                if (length > VILLAGE_NUMBER_MAX_LENGTH) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(
                            R.string.error_too_long_over_n_chars,
                            VILLAGE_NUMBER_MAX_LENGTH
                        )
                    )
                }

                return Verifiable.Valid
            }

            Patient::householdNumber -> with(value as? String?) {
                if (this == null) {
                    // Household number is optional, so this is valid
                    return Verifiable.Valid
                }

                if (!isDigitsOnly()) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(R.string.patient_error_must_be_number)
                    )
                }

                if (length > HOUSEHOLD_NUMBER_MAX_LENGTH) {
                    return Verifiable.Invalid(
                        property,
                        context?.getString(
                            R.string.error_too_long_over_n_chars,
                            HOUSEHOLD_NUMBER_MAX_LENGTH
                        )
                    )
                }

                return Verifiable.Valid
            }

            // Default to true for all other fields / stuff that isn't implemented.
            else -> Verifiable.Valid
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
                    patient.gestationalAge?.let { GestationalAge.Serializer.write(gen, it) }
                }
                gen.writeOptIntField(PatientField.PREGNANCY_ID, pregnancyId)
                gen.writeOptLongField(PatientField.PREGNANCY_END_DATE, prevPregnancyEndDate)
                gen.writeOptStringField(PatientField.PREGNANCY_OUTCOME, prevPregnancyOutcome)
                gen.writeOptStringField(PatientField.ZONE, zone)
                gen.writeOptStringField(PatientField.VILLAGE_NUMBER, villageNumber)
                gen.writeOptStringField(PatientField.HOUSEHOLD_NUMBER, householdNumber)
                gen.writeOptStringField(PatientField.DRUG_HISTORY, drugHistory)
                gen.writeOptStringField(PatientField.MEDICAL_HISTORY, medicalHistory)
                gen.writeOptStringField(PatientField.ALLERGY, allergy)
                gen.writeOptLongField(PatientField.LAST_EDITED, lastEdited)
                gen.writeOptLongField(PatientField.DRUG_LAST_EDITED, drugLastEdited)
                gen.writeOptLongField(PatientField.MEDICAL_LAST_EDITED, medicalLastEdited)
                gen.writeOptLongField(PatientField.LAST_SERVER_UPDATE, lastServerUpdate)
                gen.writeBooleanField(PatientField.IS_ARCHIVE, isArchive)
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

            // Some backend responses send gestationalTimestamp = null when not pregnant
            val gestationalAge = if (
                has(PatientField.GESTATIONAL_AGE_UNIT.text) &&
                has(PatientField.GESTATIONAL_AGE_VALUE.text) &&
                get(PatientField.GESTATIONAL_AGE_VALUE).toString() != "null"
            ) {
                GestationalAge.Deserializer.get(this)
            } else {
                null
            }

            val isPregnant = if (has(PatientField.IS_PREGNANT.text)) {
                get(PatientField.IS_PREGNANT)!!.booleanValue()
            } else {
                gestationalAge != null
            }

            val sex = Sex.valueOf(get(PatientField.SEX)!!.textValue())
            val pregnancyId = get(PatientField.PREGNANCY_ID)?.asInt()
            val zone = get(PatientField.ZONE)?.textValue()
            val villageNumber = get(PatientField.VILLAGE_NUMBER)?.textValue()
            val householdNumber = get(PatientField.HOUSEHOLD_NUMBER)?.textValue()
            val drugHistory = get(PatientField.DRUG_HISTORY)?.textValue() ?: ""
            val medicalHistory = get(PatientField.MEDICAL_HISTORY)?.textValue() ?: ""
            val allergy = get(PatientField.ALLERGY)?.textValue() ?: ""
            val lastEdited = get(PatientField.LAST_EDITED)?.asLong()
            val lastServerUpdate = get(PatientField.LAST_SERVER_UPDATE)?.asLong()
            val isArchive = get(PatientField.IS_ARCHIVE)?.asBoolean() ?: false

            // The following fields are set to null because if we are receiving patient information
            // from the server, it guarantees there are no un-uploaded edits on android
            // prevPregnancyEndDate, prevPregnancyOutcome, drugLastEdited, medicalLastEdited
            return@run Patient(
                id = id,
                name = name,
                dob = dob,
                isExactDob = isExactDob,
                gestationalAge = gestationalAge,
                sex = sex,
                isPregnant = isPregnant,
                pregnancyId = pregnancyId,
                prevPregnancyEndDate = null,
                prevPregnancyOutcome = null,
                zone = zone,
                villageNumber = villageNumber,
                householdNumber = householdNumber,
                drugHistory = drugHistory,
                medicalHistory = medicalHistory,
                allergy = allergy,
                lastEdited = lastEdited,
                drugLastEdited = null,
                medicalLastEdited = null,
                lastServerUpdate = lastServerUpdate,
                isArchive = isArchive
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
 */
@JsonSerialize(using = PatientAndReadings.Serializer::class)
@JsonDeserialize(using = PatientAndReadings.Deserializer::class)
data class PatientAndReadings(
    @Embedded
    val patient: Patient,
    @Relation(parentColumn = "id", entityColumn = "patientId")
    val readings: List<Reading>
) {
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
 * A database view containing a patient and all readings associated with it.
 */
@JsonSerialize(using = PatientAndReferrals.Serializer::class)
@JsonDeserialize(using = PatientAndReferrals.Deserializer::class)
data class PatientAndReferrals(
    @Embedded
    val patient: Patient,
    @Relation(parentColumn = "id", entityColumn = "patientId")
    val referrals: List<Referral>
) {
    class Serializer : StdSerializer<PatientAndReferrals>(PatientAndReferrals::class.java) {
        override fun serialize(
            PatientAndReferrals: PatientAndReferrals,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            PatientAndReferrals.run {
                gen.writeStartObject()
                Patient.Serializer.write(PatientAndReferrals.patient, gen)
                gen.writeObjectField(PatientField.REFERRALS, referrals)
                gen.writeEndObject()
            }
        }
    }

    class Deserializer : StdDeserializer<PatientAndReferrals>(PatientAndReferrals::class.java) {
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext
        ): PatientAndReferrals = p.codec.readTree<JsonNode>(p)!!.run {
            val patient = Patient.Deserializer.get(this)
            val referrals = getOptObjectArray<Referral>(PatientField.REFERRALS, p.codec) ?: emptyList()
            return PatientAndReferrals(patient, referrals)
        }
    }

    /**
     * Generated by Android Studio
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PatientAndReferrals

        if (patient != other.patient) return false
        if (referrals != other.referrals) return false

        return true
    }

    /**
     * Generated by Android Studio
     */
    override fun hashCode(): Int {
        var result = patient.hashCode()
        result = 31 * result + referrals.hashCode()
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
@JsonSerialize(using = GestationalAge.Serializer::class)
@JsonDeserialize(using = GestationalAge.Deserializer::class)
sealed class GestationalAge(val timestamp: BigInteger) : Serializable {
    /**
     * The age in [WeeksAndDays] from the current Unix timestamp.
     */
    val ageFromNow: WeeksAndDays
        get() = WeeksAndDays.fromSeconds(Seconds(UnixTimestamp.now - timestamp))

    object Deserializer : StdDeserializer<GestationalAge>(GestationalAge::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): GestationalAge =
            p.codec.readTree<JsonNode>(p)!!.run { get(this) }

        /**
         * Nested deserialization from the given [jsonNode]
         */
        fun get(jsonNode: JsonNode): GestationalAge = jsonNode.run {
            val units = get(PatientField.GESTATIONAL_AGE_UNIT)!!.asText()
            val value = get(PatientField.GESTATIONAL_AGE_VALUE)!!.asLong()
            return when (units) {
                UNIT_VALUE_WEEKS -> GestationalAgeWeeks(BigInteger.valueOf(value))
                UNIT_VALUE_MONTHS, "DAYS" -> GestationalAgeMonths(BigInteger.valueOf(value))
                else -> {
                    throw InvalidUnitsException(
                        "invalid value for ${PatientField.GESTATIONAL_AGE_UNIT.text}"
                    )
                }
            }
        }
    }

    object Serializer : StdSerializer<GestationalAge>(GestationalAge::class.java) {
        override fun serialize(
            gestationalAge: GestationalAge,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            gen.writeStartObject()
            write(gen, gestationalAge)
            gen.writeEndObject()
        }

        /**
         * Nested serialization into the given [gen]
         */
        fun write(gen: JsonGenerator, gestationalAge: GestationalAge) {
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

    companion object {
        // These need to be marked static so we can share them with implementors.
        @JvmStatic
        protected val UNIT_VALUE_WEEKS = "WEEKS"
        @JvmStatic
        protected val UNIT_VALUE_MONTHS = "MONTHS"
    }

    override fun toString(): String {
        return "GestationalAge($ageFromNow, value=$timestamp)"
    }

    /** Generated by Android Studio */
    override fun hashCode(): Int {
        return timestamp.hashCode()
    }

    /** Generated by Android Studio */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        // this differentiates between GestationalAgeWeeks and Months
        if (javaClass != other?.javaClass) return false

        other as GestationalAge
        if (timestamp != other.timestamp) return false
        return true
    }

    class InvalidUnitsException(message: String) : IOException(message)
}

/**
 * Variant of [GestationalAge] which stores age in number of weeks.
 */
class GestationalAgeWeeks(timestamp: BigInteger) : GestationalAge(timestamp), Serializable {
    constructor(duration: Weeks) : this(UnixTimestamp.ago(duration))

    override fun toString(): String {
        return "GestationalAgeWeeks($ageFromNow, value=$timestamp)"
    }
}

/**
 * Variant of [GestationalAge] which stores age in number of months.
 */
class GestationalAgeMonths(timestamp: BigInteger) : GestationalAge(timestamp), Serializable {
    constructor(duration: Months) : this(UnixTimestamp.ago(duration))

    override fun toString(): String {
        return "GestationalAgeMonths($ageFromNow, value=$timestamp)"
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
    IS_EXACT_DOB("isExactDob"),
    GESTATIONAL_AGE_UNIT("gestationalAgeUnit"),
    GESTATIONAL_AGE_VALUE("pregnancyStartDate"),
    SEX("patientSex"),
    IS_PREGNANT("isPregnant"),
    PREGNANCY_ID("pregnancyId"),
    PREGNANCY_END_DATE("pregnancyEndDate"),
    PREGNANCY_OUTCOME("pregnancyOutcome"),
    ZONE("zone"),
    VILLAGE_NUMBER("villageNumber"),
    HOUSEHOLD_NUMBER("householdNumber"),
    DRUG_HISTORY("drugHistory"),
    MEDICAL_HISTORY("medicalHistory"),
    ALLERGY("allergy"),
    LAST_EDITED("lastEdited"),
    DRUG_LAST_EDITED("drugLastEdited"),
    MEDICAL_LAST_EDITED("medicalLastEdited"),
    LAST_SERVER_UPDATE("base"),
    READINGS("readings"),
    REFERRALS("referrals"),
    IS_ARCHIVE("isArchived")
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
