package com.cradleplatform.neptune.activities.forms

import android.content.Context
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.viewmodel.forms.FormV2SubmissionConflict
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FormV2ConflictMessageTest {
    private val context = mockk<Context>()
    private val gson = Gson()

    init {
        every { context.getString(R.string.form_v2_conflict_message) } returns "Review"
        every { context.getString(R.string.form_v2_conflict_unanswered) } returns "No answer"
        every { context.getString(R.string.form_v2_conflict_answer_pair, *anyVararg()) } answers {
            val values = secondArg<Array<Any>>()
            "${values[0]}\nServer: ${values[1]}\nYour draft: ${values[2]}"
        }
        every { context.getString(R.string.form_v2_conflict_answer_comment, *anyVararg()) } answers {
            val values = secondArg<Array<Any>>()
            "${values[0]}\nComment: ${values[1]}"
        }
    }

    @Test
    fun `review shows additions removals choice labels and comments but skips equal numbers`() {
        val text = message(
            """[{"questionId":"q1","answer":{"mcIdArray":[1],"comment":"new"}},
                {"questionId":"q2","answer":{"number":5}},
                {"questionId":"q4","answer":{"text":"New note"}}]""",
            """[{"questionId":"q1","questionText":"Symptoms","mcOptions":["Headache","Fever"],
                  "answer":{"mcIdArray":[0],"comment":"old"}},
                {"questionId":"q2","questionText":"Number","answer":{"number":5.0}},
                {"questionId":"q3","questionText":"Optional note","answer":{"text":"Old note"}}]"""
        )
        assertTrue(text.contains("Server: Headache\nComment: old"))
        assertTrue(text.contains("Your draft: Fever\nComment: new"))
        assertEquals(1, text.lineSequence().count { it == "Symptoms" })
        assertTrue(text.contains("Optional note\nServer: Old note\nYour draft: No answer"))
        assertTrue(text.contains("q4\nServer: No answer\nYour draft: New note"))
        assertFalse(text.contains("Number"))
    }

    @Test
    fun `review displays dates in the original submission zone`() {
        val text = message(
            """[{"questionId":"q1","answer":{"date":"946713600"}}]""",
            """[{"questionId":"q1","questionText":"Date","questionType":"DATE",
                "answer":{"date":"946800000"}}]"""
        )
        assertTrue(text.contains("Server: 2000-01-02"))
        assertTrue(text.contains("Your draft: 2000-01-01"))
    }

    private fun message(local: String, server: String): String {
        val request = gson.fromJson("""{"lang":"English","answers":$local}""",
            CreateFormSubmissionRequestV2::class.java)
        val submission = gson.fromJson("""{"answers":$server}""", FormSubmissionWithAnswersV2::class.java)
        val template = gson.fromJson("""{"questions":[]}""", FormTemplateV2::class.java)
        return FormV2ConflictMessage.build(context,
            FormV2SubmissionConflict(request, request, submission, "America/Vancouver"), template)
    }
}
