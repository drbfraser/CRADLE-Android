package com.cradleplatform.neptune.database

import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.model.WorkflowInstanceStep
import com.cradleplatform.neptune.model.WorkflowStatus
import com.cradleplatform.neptune.model.WorkflowTemplateStep
import com.cradleplatform.neptune.model.WorkflowTemplateStepBranch
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DatabaseTypeConvertersTests {
    private val typeConverter = DatabaseTypeConverters()

    @Test
    fun `referral creator is preserved in Room but omitted from network JSON`() {
        val referral = CommonPatientReferralJsons.patientWithStandaloneReferral.second.referrals[0]
        val referralsToTest = listOf(null, referral, referral.copy(userId = null))

        referralsToTest.forEach { originalReferral ->
            val restoredReferral = typeConverter.toReferral(typeConverter.fromReferral(originalReferral))
            assertEquals(originalReferral, restoredReferral)
        }

        // Database conversion must not change the shared network mapper's behaviour.
        val networkJson = JacksonMapper.mapper.readTree(
            JacksonMapper.writerForReferral.writeValueAsString(referral)
        )
        assertFalse(networkJson.has("userId"))
    }

    @Test
    fun `string list is preserved`() {

        val listsToTest: Array<List<String>?> = arrayOf(
            null,
            listOf(),
            listOf(""),
            listOf("abc"),
            listOf(
                "df4565\"asd", "SHAdkjahkasjhkasjlk{asdsa, asdas}", "ij32809 OI po fiopu",
                "uysadkja sjkh ,,.m.m49p932][po]80=-*/-*+631276%@#^%#@5831876419878967341"
            ),
            listOf(
                """
                \"comment\":\"These are my referral comments\",
                \"dateReferred\":1604530201,
                \"healthFacilityName\":\"H5123\",
                \"id\":96,
                \"isAssessed\":false,
                \"patientId\":\"3295976464\",
                \"readingId\":\"46ba023a-b85d-4aad-8fa4-efe2e61ffe5e\",
                \"userId\":5}
            """.trimIndent()
            )
        )

        listsToTest.forEach { originalList ->
            val convertedList = runStringListConversion(originalList)
            assert(originalList == convertedList) {
                "expected $originalList, but got $convertedList"
            }
        }
    }

    private fun runStringListConversion(list: List<String>?): List<String>? =
        typeConverter.toStringList(typeConverter.fromStringList(list))

    @Test
    fun `workflow instance step list is preserved`() {

        val listsToTest: Array<List<WorkflowInstanceStep>> = arrayOf(
            listOf(),
            listOf(
                WorkflowInstanceStep(
                    id = "step-1",
                    workflowInstanceId = "instance-1",
                    name = "Assessment"
                )
            ),
            listOf(
                WorkflowInstanceStep(
                    id = "step-1",
                    workflowInstanceId = "instance-1",
                    name = "Assessment",
                    description = "Initial assessment",
                    status = WorkflowStatus.COMPLETED,
                    startDate = 1604530201,
                    completionDate = 1604616601,
                    lastEdited = 1604616601,
                    workflowTemplateStepId = "template-step-1"
                ),
                WorkflowInstanceStep(
                    id = "step-2",
                    workflowInstanceId = "instance-1",
                    name = "Follow up"
                )
            )
        )

        listsToTest.forEach { originalList ->
            val convertedList =
                typeConverter.toWorkflowInstanceStepList(
                    typeConverter.fromWorkflowInstanceStepList(originalList)
                )
            assert(originalList == convertedList) {
                "expected $originalList, but got $convertedList"
            }
        }
    }

    @Test
    fun `workflow template step list is preserved`() {

        val listsToTest: Array<List<WorkflowTemplateStep>> = arrayOf(
            listOf(),
            listOf(WorkflowTemplateStep(id = "template-step-1")),
            listOf(
                WorkflowTemplateStep(
                    id = "template-step-1",
                    name = "Assessment",
                    description = "Initial assessment",
                    branches = listOf(
                        WorkflowTemplateStepBranch(targetStepId = "template-step-2"),
                        WorkflowTemplateStepBranch(targetStepId = null)
                    )
                ),
                WorkflowTemplateStep(id = "template-step-2", name = "Follow up")
            )
        )

        listsToTest.forEach { originalList ->
            val convertedList =
                typeConverter.toWorkflowTemplateStepList(
                    typeConverter.fromWorkflowTemplateStepList(originalList)
                )
            assert(originalList == convertedList) {
                "expected $originalList, but got $convertedList"
            }
        }
    }
}
