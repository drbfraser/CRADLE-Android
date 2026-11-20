package com.cradleplatform.neptune.model

import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DeletedPatientsResponseTests {
    @Test
    fun `reads the deleted patient IDs from the server response`() {
        val serverResponse = """{"deletedPatientIds": ["49300028161", "12345678"]}"""

        val response = JacksonMapper.readerForDeletedPatientsResponse
            .readValue<DeletedPatientsResponse>(serverResponse)

        assertEquals(listOf("49300028161", "12345678"), response.deletedPatientIds)
    }

    @Test
    fun `reads an empty list when no patients were deleted`() {
        val serverResponse = """{"deletedPatientIds": []}"""

        val response = JacksonMapper.readerForDeletedPatientsResponse
            .readValue<DeletedPatientsResponse>(serverResponse)

        assertEquals(emptyList<String>(), response.deletedPatientIds)
    }
}
