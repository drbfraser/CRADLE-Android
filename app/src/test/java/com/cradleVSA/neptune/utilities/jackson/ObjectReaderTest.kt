package com.cradleVSA.neptune.utilities.jackson

import com.cradleVSA.neptune.ext.jackson.forEachJackson
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

data class TestHealthFacility(
    val location: String,
    val facilityType: String,
    val about: String,
    val healthFacilityPhoneNumber: String,
    val healthFacilityName: String
)

internal class ObjectReaderTest {

    @Test
    fun throwIOExceptionIfNotValid() {
        val reader = jacksonObjectMapper().readerFor(TestHealthFacility::class.java)
        val notArrayStream = """
    {
        "location": "Sample Location",
        "facilityType": "HOSPITAL",
        "about": "Sample health centre",
        "healthFacilityPhoneNumber": "555-555-55555",
        "healthFacilityName": "H0000"

    """.byteInputStream()

        val healthFacilities = mutableListOf<TestHealthFacility>()
        assertThrows(IOException::class.java) {
            val iterator = reader.readValues<TestHealthFacility>(notArrayStream)
            while (iterator.hasNextValue()) {
                healthFacilities.add(iterator.nextValue())
            }
        }

        val anotherNotArray = "{\"location\": \"Sample Location\","

        // Do NOT use Kotlin's forEach extension function. Kotlin's forEach calls the
        // MappingIterator's next() function instead of hasNextValue(), and the next()
        // function throws JsonEOFException wrapped in a RuntimeException.
        assertThrows(RuntimeException::class.java) {
            reader.readValues<TestHealthFacility>(anotherNotArray.encodeToByteArray()).forEach {
                healthFacilities.add(it)
            }
        }

        // Use the fixed version, forEachJackson, instead, which throws checked exceptions.
        assertThrows(IOException::class.java) {
            reader.readValues<TestHealthFacility>(anotherNotArray.encodeToByteArray()).forEachJackson {
                healthFacilities.add(it)
            }
        }
    }

    @Test
    fun parseJsonArrayFromStream() {
        val reader = jacksonObjectMapper().readerFor(TestHealthFacility::class.java)
        val exampleJsonAsInputStream = """
[
    {
        "location": "Sample Location",
        "facilityType": "HOSPITAL",
        "about": "Sample health centre",
        "healthFacilityPhoneNumber": "555-555-55555",
        "healthFacilityName": "H0000"
    },
    {
        "location": "District 1",
        "facilityType": "HCF_2",
        "about": "Has minimal resources",
        "healthFacilityPhoneNumber": "+256-413-837484",
        "healthFacilityName": "H1233"
    }
]
    """.byteInputStream()


        val iterator = reader.readValues<TestHealthFacility>(exampleJsonAsInputStream)
        val healthFacilities = iterator.readAll()
        // Alternatively, for item-by-item parsing without putting the entire thing in memory:
        //
        // val iterator = reader.readValues<TestHealthFacility>(exampleJsonAsInputStream)
        // while (iterator.hasNextValue()) {
        //     healthFacilities.add(iterator.nextValue())
        // }

        assertEquals(2, healthFacilities.size)

        assertEquals(
            TestHealthFacility(
                location = "Sample Location",
                facilityType = "HOSPITAL",
                about = "Sample health centre",
                healthFacilityPhoneNumber = "555-555-55555",
                healthFacilityName = "H0000"
            ),
            healthFacilities[0]
        )

        assertEquals(
            TestHealthFacility(
                location = "District 1",
                facilityType = "HCF_2",
                about = "Has minimal resources",
                healthFacilityPhoneNumber = "+256-413-837484",
                healthFacilityName = "H1233"
            ),
            healthFacilities[1]
        )
    }
}