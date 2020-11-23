package com.cradleVSA.neptune.model

import android.content.Context
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.ext.Field
import com.cradleVSA.neptune.ext.put
import com.cradleVSA.neptune.ext.stringField
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import kotlin.reflect.KProperty

/**
 * Holds information about a urine test.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UrineTest(
    @JsonProperty("urineTestLeuc") var leukocytes: String,
    @JsonProperty("urineTestNit") var nitrites: String,
    @JsonProperty("urineTestPro") var protein: String,
    @JsonProperty("urineTestBlood") var blood: String,
    @JsonProperty("urineTestGlu") var glucose: String
) : Serializable, Marshal<JSONObject>, Verifiable<UrineTest> {
    /**
     * Converts this object to JSON.
     */
    override fun marshal(): JSONObject = with(JSONObject()) {
        put(UrineTestField.LEUKOCYTES, leukocytes)
        put(UrineTestField.NITRITES, nitrites)
        put(UrineTestField.PROTEIN, protein)
        put(UrineTestField.BLOOD, blood)
        put(UrineTestField.GLUCOSE, glucose)
    }

    companion object FromJson : Unmarshal<UrineTest, JSONObject>, Verifier<UrineTest> {
        /**
         * Constructs a [UrineTest] from a [JSONObject].
         *
         * @throws JSONException If any of the required fields are not present
         * in [data].
         */
        override fun unmarshal(data: JSONObject): UrineTest = UrineTest(
            leukocytes = data.stringField(UrineTestField.LEUKOCYTES),
            nitrites = data.stringField(UrineTestField.NITRITES),
            protein = data.stringField(UrineTestField.PROTEIN),
            blood = data.stringField(UrineTestField.BLOOD),
            glucose = data.stringField(UrineTestField.GLUCOSE)
        )

        override fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context,
            instance: UrineTest?,
            currentValues: Map<String, Any?>?
        ): Pair<Boolean, String> = with(value as? String) {
            // All the properties for [UrineTest} are Strings, so if the cast fails, it's
            // null and hence will fall into here.
            if (this == null || this.isBlank()) {
                val errorMessage = when (property) {
                    UrineTest::blood ->
                        context.getString(R.string.urine_test_error_blood_missing)
                    UrineTest::glucose ->
                        context.getString(R.string.urine_test_error_glucose_missing)
                    UrineTest::leukocytes ->
                        context.getString(R.string.urine_test_error_leukocytes_missing)
                    UrineTest::nitrites ->
                        context.getString(R.string.urine_test_error_nitrites_missing)
                    UrineTest::protein ->
                        context.getString(R.string.urine_test_error_protein_missing)
                    else ->
                        context.getString(R.string.urine_test_error_invalid_value)
                }
                return false to errorMessage
            }

            val urineTestStrings = context.resources.getStringArray(R.array.urine_test_symbols)
            return if (this in urineTestStrings) {
                true to ""
            } else {
                false to context.getString(R.string.urine_test_error_invalid_value)
            }
        }
    }

    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context
    ): Pair<Boolean, String> = isValueValid(property, value, context)
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
