package com.cradleplatform.neptune.sync.workers

import com.cradleplatform.neptune.http_sms_service.http.READING_ID_FIELD
import com.cradleplatform.neptune.http_sms_service.http.SyncError
import com.cradleplatform.neptune.http_sms_service.http.WORKFLOW_INSTANCE_ID_FIELD
import com.cradleplatform.neptune.http_sms_service.http.formatPatientSyncErrors
import com.cradleplatform.neptune.http_sms_service.http.parseSyncErrors
import com.cradleplatform.neptune.model.Patient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SyncErrorsTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `parseSyncErrors reads the record ID and message of each error`() {
        val errors = mapper.readTree(
            """
            [
                {"readingId": "abc-123", "errors": "Not authorized to sync this patient."},
                {"readingId": "def-456", "errors": "Patient does not exist."}
            ]
            """.trimIndent()
        )

        assertEquals(
            listOf(
                SyncError("abc-123", "Not authorized to sync this patient."),
                SyncError("def-456", "Patient does not exist.")
            ),
            parseSyncErrors(errors, READING_ID_FIELD)
        )
    }

    @Test
    fun `parseSyncErrors removes the list brackets around a message`() {
        val errors = mapper.readTree(
            """[{"workflowInstanceId": "wf-1", "errors": "['Some message.']"}]"""
        )

        assertEquals(
            listOf(SyncError("wf-1", "Some message.")),
            parseSyncErrors(errors, WORKFLOW_INSTANCE_ID_FIELD)
        )
    }

    @Test
    fun `parseSyncErrors returns nothing for an empty errors list`() {
        assertEquals(
            emptyList<SyncError>(),
            parseSyncErrors(mapper.readTree("[]"), READING_ID_FIELD)
        )
    }

    @Test
    fun `formatPatientSyncErrors shows each patient by its patient number`() {
        val errors = mapper.readTree(
            """
            [
                {"patientId": "uuid-1", "errors": "['Not authorized to sync this patient.']"},
                {"patientId": "uuid-2", "errors": "Some message."}
            ]
            """.trimIndent()
        )
        val uploadedPatients = listOf(
            Patient(id = "uuid-1", patientNumber = "12345", name = "AA"),
            Patient(id = "uuid-2", patientNumber = "67890", name = "BB")
        )

        assertEquals(
            "Patient 12345: Not authorized to sync this patient.\nPatient 67890: Some message.",
            formatPatientSyncErrors(errors, uploadedPatients)
        )
    }

    @Test
    fun `formatPatientSyncErrors falls back to the name, then to the ID`() {
        val errors = mapper.readTree(
            """
            [
                {"patientId": "uuid-1", "errors": "Some message."},
                {"patientId": "uuid-2", "errors": "Some message."}
            ]
            """.trimIndent()
        )
        val uploadedPatients = listOf(Patient(id = "uuid-1", patientNumber = null, name = "AA"))

        assertEquals(
            "Patient AA: Some message.\nPatient uuid-2: Some message.",
            formatPatientSyncErrors(errors, uploadedPatients)
        )
    }

    @Test
    fun `formatRecordSyncErrors names the patient of each record`() {
        val lines = formatRecordSyncErrors(
            recordName = "Reading",
            errors = listOf(
                SyncError("abc-123", "Not authorized to sync this patient."),
                SyncError("def-456", "Patient does not exist.")
            ),
            patientLabelByRecordId = mapOf("abc-123" to "12345", "def-456" to "67890")
        )

        assertEquals(
            listOf(
                "Reading for patient 12345: Not authorized to sync this patient.",
                "Reading for patient 67890: Patient does not exist."
            ),
            lines
        )
    }

    @Test
    fun `formatRecordSyncErrors falls back to the record ID when the patient is unknown`() {
        val lines = formatRecordSyncErrors(
            recordName = "Referral",
            errors = listOf(SyncError("abc-123", "Not authorized to sync this patient.")),
            patientLabelByRecordId = emptyMap()
        )

        assertEquals(listOf("Referral abc-123: Not authorized to sync this patient."), lines)
    }

    @Test
    fun `formatRecordSyncErrors returns nothing when there are no errors`() {
        assertEquals(
            emptyList<String>(),
            formatRecordSyncErrors("Reading", emptyList(), mapOf("abc-123" to "12345"))
        )
    }
}
