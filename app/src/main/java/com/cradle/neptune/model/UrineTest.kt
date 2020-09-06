package com.cradle.neptune.model

import com.cradle.neptune.ext.Field
import com.cradle.neptune.ext.put
import com.cradle.neptune.ext.stringField
import java.io.Serializable
import org.json.JSONException
import org.json.JSONObject

/**
 * Holds information about a urine test.
 */
data class UrineTest(
    var leukocytes: String,
    var nitrites: String,
    var protein: String,
    var blood: String,
    var glucose: String
) : Serializable, Marshal<JSONObject> {

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

    companion object FromJson : Unmarshal<UrineTest, JSONObject> {
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
