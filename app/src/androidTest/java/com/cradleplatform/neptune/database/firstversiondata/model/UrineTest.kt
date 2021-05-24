package com.cradleplatform.neptune.database.firstversiondata.model

import android.content.Context
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.Field
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import kotlin.reflect.KProperty

/**
 * Holds information about a urine test.
 *
 * DO NOT EDIT
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UrineTest(
    @JsonProperty("urineTestLeuc") var leukocytes: String,
    @JsonProperty("urineTestNit") var nitrites: String,
    @JsonProperty("urineTestPro") var protein: String,
    @JsonProperty("urineTestBlood") var blood: String,
    @JsonProperty("urineTestGlu") var glucose: String
) : Serializable, Verifiable<UrineTest> {
    companion object : Verifiable.Verifier<UrineTest> {

        override fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context?,
            instance: UrineTest?,
            currentValues: Map<String, Any?>?
        ): Verifiable.Result = with(value as? String) {
            // All the properties for [UrineTest} are Strings, so if the cast fails, it's
            // null and hence will fall into here.
            if (this == null || this.isBlank()) {
                val errorMessage = context?.let {
                    when (property) {
                        UrineTest::blood -> it.getString(R.string.urine_test_error_blood_missing)
                        UrineTest::glucose -> it.getString(R.string.urine_test_error_glucose_missing)
                        UrineTest::leukocytes -> it.getString(R.string.urine_test_error_leukocytes_missing)
                        UrineTest::nitrites -> it.getString(R.string.urine_test_error_nitrites_missing)
                        UrineTest::protein -> it.getString(R.string.urine_test_error_protein_missing)
                        else -> it.getString(R.string.urine_test_error_invalid_value)
                    }
                }
                return Verifiable.Invalid(property, errorMessage)
            }

            val urineTestStrings = context?.resources?.getStringArray(R.array.urine_test_symbols)
                ?: arrayOf("NAD", "+", "++", "+++")
            return if (this in urineTestStrings) {
                Verifiable.Valid
            } else {
                Verifiable.Invalid(
                    property,
                    context?.getString(R.string.urine_test_error_invalid_value)
                )
            }
        }
    }

    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context?
    ): Verifiable.Result = isValueValid(property, value, context)
}

/**
 * JSON fields for marshalling [UrineTest] objects.
 */
private enum class UrineTestField(override val text: String) : Field {
    LEUKOCYTES("urineTestLeuc"),
    NITRITES("urineTestNit"),
    PROTEIN("urineTestPro"),
    BLOOD("urineTestBlood"),
    GLUCOSE("urineTestGlu"),
}
