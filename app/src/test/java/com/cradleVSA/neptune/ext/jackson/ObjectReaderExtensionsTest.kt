package com.cradleVSA.neptune.ext.jackson

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

internal class ObjectReaderExtensionsTest {

    data class TestHealthFacility(
        val location: String,
        val facilityType: String,
        val about: String,
        val healthFacilityPhoneNumber: String,
        val healthFacilityName: String
    )

    @Test
    fun throwIOExceptionIfNotArray() {
        val reader = jacksonObjectMapper().readerForArrayOf(TestHealthFacility::class.java)
        val notArrayStream = """
    {
        "location": "Sample Location",
        "facilityType": "HOSPITAL",
        "about": "Sample health centre",
        "healthFacilityPhoneNumber": "555-555-55555",
        "healthFacilityName": "H0000"
    }
    """.byteInputStream()

        val healthFacilities = mutableListOf<TestHealthFacility>()
        assertThrows(IOException::class.java) {
            reader.parseJsonArrayFromStream(notArrayStream) {
                healthFacilities.add(it.readValueAs(TestHealthFacility::class.java))
            }
        }
    }

    @Test
    fun parseJsonArrayFromStream() {
        val reader = jacksonObjectMapper().readerForArrayOf(TestHealthFacility::class.java)
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

        val healthFacilities = mutableListOf<TestHealthFacility>()
        reader.parseJsonArrayFromStream(exampleJsonAsInputStream) {
            healthFacilities.add(it.readValueAs(TestHealthFacility::class.java))
        }

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