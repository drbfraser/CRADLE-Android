package com.cradle.neptune.model

import java.io.Serializable

/**
 * Holds information about a urine test.
 */
data class UrineTest(
    var leukocytes: String,
    var nitrites: String,
    var protein: String,
    var blood: String,
    var glucose: String
) : Serializable, Marshal<JsonObject> {

    /**
     * Converts this object to JSON.
     */
    override fun marshal(): JsonObject = with(JsonObject()) {
        put(UrineTestField.LEUKOCYTES, leukocytes)
        put(UrineTestField.NITRITES, nitrites)
        put(UrineTestField.PROTEIN, protein)
        put(UrineTestField.BLOOD, blood)
        put(UrineTestField.GLUCOSE, glucose)
    }

    companion object FromJson : Unmarshal<UrineTest, JsonObject> {
        /**
         * Constructs a [UrineTest] from a [JsonObject].
         *
         * @throws JsonException If any of the required fields are not present
         * in [data].
         */
        override fun unmarshal(data: JsonObject): UrineTest = UrineTest(
            leukocytes = data.stringField(UrineTestField.LEUKOCYTES),
            nitrites = data.stringField(UrineTestField.NITRITES),
            protein = data.stringField(UrineTestField.PROTEIN),
            blood = data.stringField(UrineTestField.BLOOD),
            glucose = data.stringField(UrineTestField.GLUCOSE)
        )
    }
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
