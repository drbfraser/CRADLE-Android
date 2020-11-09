package com.cradle.neptune.utilities

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class JacksonTests {
    private val mapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `test embedded fields`() {
        val patient = TestPatient(id = "1234", dob = "1984-03-01", isExactDob = true)

        val readingsToTest = arrayOf(
            TestReading(
                id = UUID.randomUUID().toString(),
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(5, 6, 7),
                previousIds = emptyList(),
                somethingOptional = 0,
                assessment = TestAssessment("", "", 1),
            ),
            TestReading(
                id = UUID.randomUUID().toString(),
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(1, 2, 3),
                previousIds = listOf(UUID.randomUUID().toString()),
                assessment = null
            ),
            TestReading(
                id = UUID.randomUUID().toString(),
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(5, 6, 7),
                assessment = null
            ),
            TestReading(
                id = UUID.randomUUID().toString(),
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(5, 6, 7),
                previousIds = listOf(),
                assessment = TestAssessment("this is a fllow up..}{ [] [}{ ][ [[] }", "", 5)
            ),
            TestReading(
                id = UUID.randomUUID().toString(),
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(5, 6, 7),
                previousIds = emptyList(),
                somethingOptional = 50546456,
                assessment = TestAssessment("th DS    sdf", "comments", 3),
            ),
        )

        readingsToTest.forEach { reading ->
            val readingString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(reading)
            //error("$readingString")
            val parsedReading = mapper.readValue<TestReading>(readingString)
            assertEquals(reading, parsedReading)
        }
        val parsedTestJson = mapper.readValue<TestReading>(TEST_JSON)
        val expectedFromTestJson = TestReading(
            id = "cb360c77-57d4-4c5f-94de-cf199d5e2dcd",
            patientId = "345",
            testBloodPressure = TestBloodPressure(114, 89, 96),
            previousIds = emptyList(),
            somethingOptional = 0,
            assessment = TestAssessment("Hello there", "Sweets", 1)
        )
        assertEquals(expectedFromTestJson, parsedTestJson)

        //TestBloodPressure::class.primaryConstructor?.parameters?.find { it.hasAnnotation<JsonProperty>() }?.run {
        //   error("$annotations")
        //}
    }

    @Test
    fun `test patient and readings`() {
        val patient = TestPatient(id = "1234", dob = "1984-03-01", isExactDob = true)
        val readingsToTest = arrayOf(
            TestReading(
                id = "A",
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(5, 6, 7),
                previousIds = emptyList(),
                somethingOptional = 0,
                assessment = TestAssessment("", "", 1),
            ),
            TestReading(
                id = "b",
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(1, 2, 3),
                previousIds = listOf("c"),
                assessment = null
            ),
            TestReading(
                id = "C",
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(5, 6, 7),
                assessment = null
            ),
            TestReading(
                id = "D",
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(5, 6, 7),
                previousIds = listOf(),
                assessment = TestAssessment("this is a fllow up..}{ [] [}{ ][ [[] }", "", 5)
            ),
            TestReading(
                id = "E",
                patientId = patient.id,
                testBloodPressure = TestBloodPressure(5, 6, 7),
                previousIds = emptyList(),
                somethingOptional = 50546456,
                assessment = TestAssessment("th DS    sdf", "comments", 3),
            ),
        )

        val patientAndReadings = TestPatientAndReadings(patient, readingsToTest)
        val string = mapper.writer().writeValueAsString(patientAndReadings)
        val parsedPatientAndReadings = mapper.readValue<TestPatientAndReadings>(string)

        assertEquals(patientAndReadings.patient, parsedPatientAndReadings.patient)
        assert(patientAndReadings.readings.contentEquals(parsedPatientAndReadings.readings))

        val parsedTestJson =
            mapper.readValue<TestPatientAndReadings>(TEST_PATIENT_AND_READINGS_JSON)

        assertEquals(patientAndReadings.patient, parsedTestJson.patient)
        assert(patientAndReadings.readings.contentEquals(parsedTestJson.readings))
    }
}

private const val TEST_PATIENT_AND_READINGS_JSON = """
{
    "id": "1234",
    "dob": "1984-03-01",
    "isExactDob": true,
    "readings": [
        {
            "id": "A",
            "patient_id": "1234",
            "bpSystolic": 5,
            "bpDiastolic": 6,
            "heartRateBPM": 7,
            "previous_ids": [],
            "something_optional": 0,
            "assessment": {
                "follow_up": "",
                "comments": "",
                "user_id": 1
            }
        },
        {
            "id": "b",
            "patient_id": "1234",
            "bpSystolic": 1,
            "bpDiastolic": 2,
            "heartRateBPM": 3,
            "previous_ids": ["c"]
        },
        {
            "id": "C",
            "patient_id": "1234",
            "bpSystolic": 5,
            "bpDiastolic": 6,
            "heartRateBPM": 7,
            "previous_ids": []
        },
        {
            "id": "D",
            "patient_id": "1234",
            "bpSystolic": 5,
            "bpDiastolic": 6,
            "heartRateBPM": 7,
            "previous_ids": [],
            "assessment": {
                "follow_up": "this is a fllow up..}{ [] [}{ ][ [[] }",
                "comments": "",
                "user_id": 5
            }
        },
        {
            "id": "E",
            "patient_id": "1234",
            "bpSystolic": 5,
            "bpDiastolic": 6,
            "heartRateBPM": 7,
            "previous_ids": [],
            "something_optional": 50546456,
            "assessment": {
                "follow_up": "th DS    sdf",
                "comments": "comments",
                "user_id": 3
            }
        }
    ]
}
}
"""

private const val TEST_JSON = """
{
  "id" : "cb360c77-57d4-4c5f-94de-cf199d5e2dcd",
  "patient_id" : "345",
  "bpSystolic" : 114,
  "bpDiastolic" : 89,
  "heartRateBPM" : 96,
  "previous_ids" : [ ],
  "something_optional" : 0,
  "assessment" : {
    "follow_up" : "Hello there",
    "comments" : "Sweets",
    "user_id" : 1
  }
}
"""

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class TestBloodPressure(
    @JsonProperty(Fields.SYSTOLIC) val systolic: Int,
    @JsonProperty(Fields.DIASTOLIC) val diastolic: Int,
    @JsonProperty(Fields.HEART_RATE) val heartRate: Int
) {
    object Fields {
        const val SYSTOLIC = "bpSystolic"
        const val DIASTOLIC = "bpDiastolic"
        const val HEART_RATE = "heartRateBPM"
    }
}

internal data class TestAssessment(
    @JsonProperty("follow_up") val followUp: String,
    @JsonProperty("comments") val comments: String,
    @JsonProperty("user_id") val userId: Long,
)

internal data class TestPatient(
    @JsonProperty val id: String,
    @JsonProperty val dob: String,
    @JsonProperty val isExactDob: Boolean
)

internal class TestPatientAndReadings() {
    constructor(patient: TestPatient, readings: Array<TestReading>) : this() {
        this.patient = patient
        this.readings = readings
    }

    @JsonUnwrapped
    lateinit var patient: TestPatient

    @JsonProperty("readings")
    lateinit var readings: Array<TestReading>
}

//@JsonDeserialize(using = TestReadingDeserializer::class)
internal data class TestReading(
    @JsonProperty(Fields.ID)
    val id: String,
    @JsonProperty(Fields.PATIENT_ID)
    val patientId: String,
    @JsonUnwrapped
    val testBloodPressure: TestBloodPressure,
    @JsonProperty(Fields.PREVIOUS_IDS)
    val previousIds: List<String> = emptyList(),
    @JsonProperty(Fields.SOMETHING_OPTIONAL) @JsonInclude(JsonInclude.Include.NON_NULL)
    val somethingOptional: Long? = null,
    @JsonProperty(Fields.ASSESSMENT) @JsonInclude(JsonInclude.Include.NON_NULL)
    val assessment: TestAssessment?
) {

    @JsonCreator(mode = JsonCreator.Mode.DEFAULT)
    @Suppress("unused")
    constructor(
        @JsonProperty(Fields.ID) id: String,
        @JsonProperty(Fields.PATIENT_ID) patientId: String,
        @JsonProperty(TestBloodPressure.Fields.SYSTOLIC) bpSystolic: Int,
        @JsonProperty(TestBloodPressure.Fields.DIASTOLIC) bpDiastolic: Int,
        @JsonProperty(TestBloodPressure.Fields.HEART_RATE) heartRateBPM: Int,
        @JsonProperty(Fields.PREVIOUS_IDS) previousIds: List<String>,
        @JsonProperty(Fields.SOMETHING_OPTIONAL) somethingOptional: Long?,
        @JsonProperty(Fields.ASSESSMENT) assessment: TestAssessment?
    ) : this(
        id, patientId, TestBloodPressure(bpSystolic, bpDiastolic, heartRateBPM), previousIds,
        somethingOptional, assessment
    )

    object Fields {
        const val ID = "id"
        const val PATIENT_ID = "patient_id"
        const val PREVIOUS_IDS = "previous_ids"
        const val SOMETHING_OPTIONAL = "something_optional"
        const val ASSESSMENT = "assessment"
    }
}

private enum class TestField(s: String) {
    HELLO("ABC")
}

/**
 * Have to write a custom deserializer, because @[JsonUnwrapped] annotation fails to work
 * with Kotlin data classes.
 */
private class TestReadingDeserializer : StdDeserializer<TestReading>(TestReading::class.java) {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): TestReading =
        parser.codec.readTree<ObjectNode>(parser).run {
            val id = get(TestReading.Fields.ID).textValue()
            val patientId = get(TestReading.Fields.PATIENT_ID).textValue()
            val previousIds = get(TestReading.Fields.PREVIOUS_IDS).toList().map { it.asText() }
            val somethingOptional = get(TestReading.Fields.SOMETHING_OPTIONAL)?.longValue()

            val reader = mapper.readerFor(TestBloodPressure::class.java)
            val bloodPressure = reader.readValue<TestBloodPressure>(this)
            val assessment = get(TestReading.Fields.ASSESSMENT)?.let {
                mapper.readerFor(TestAssessment::class.java).readValue<TestAssessment>(it)
            }

            return@run TestReading(
                id = id,
                patientId = patientId,
                testBloodPressure = bloodPressure,
                previousIds = previousIds,
                somethingOptional = somethingOptional,
                assessment = assessment
            )
        }

    companion object {
        private val mapper = jacksonObjectMapper()
    }
}
