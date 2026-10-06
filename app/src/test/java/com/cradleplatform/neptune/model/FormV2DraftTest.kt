package com.cradleplatform.neptune.model

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class FormV2DraftTest {
    @Test
    fun `draft identity and timestamps must match its template snapshot`() {
        val draft = validDraft()

        assertThrows(IllegalArgumentException::class.java) { draft.copy(patientId = " ") }
        assertThrows(IllegalArgumentException::class.java) { draft.copy(formTemplateId = "different") }
        assertThrows(IllegalArgumentException::class.java) { draft.copy(formTemplateVersion = 3) }
        assertThrows(IllegalArgumentException::class.java) { draft.copy(updatedAt = 999L) }
    }

    private fun validDraft(): FormV2Draft {
        val template = FormTemplateV2(
            id = "template-1",
            version = 2,
            archived = false,
            classification = FormClassificationV2(
                id = "class-1",
                name = mapOf("english" to "Antenatal"),
                nameStringId = "classification-name"
            ),
            dateCreated = 1700000000L,
            questions = emptyList()
        )
        return FormV2Draft(
            patientId = "patient-1",
            formTemplateId = template.id,
            formTemplateVersion = template.version,
            formTemplate = template,
            answers = emptyList(),
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }
}
