package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.database.daos.WorkflowInstanceDao
import com.cradleplatform.neptune.database.daos.WorkflowTemplateDao
import com.cradleplatform.neptune.model.WorkflowInstance
import com.cradleplatform.neptune.model.WorkflowInstanceStep
import com.cradleplatform.neptune.model.WorkflowNextStep
import com.cradleplatform.neptune.model.WorkflowNextStepResult
import com.cradleplatform.neptune.model.WorkflowStatus
import com.cradleplatform.neptune.model.WorkflowTemplate
import com.cradleplatform.neptune.utilities.UnixTimestamp
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowManager @Inject constructor(
    private val workflowInstanceDao: WorkflowInstanceDao,
    private val workflowTemplateDao: WorkflowTemplateDao
) {
    suspend fun addWorkflowTemplate(workflowTemplate: WorkflowTemplate) {
        workflowTemplateDao.insert(workflowTemplate)
    }

    suspend fun getWorkflowTemplates(): List<WorkflowTemplate> = workflowTemplateDao.getAll()

    suspend fun getWorkflowTemplate(templateId: String): WorkflowTemplate? =
        workflowTemplateDao.getById(templateId)

    suspend fun addWorkflowInstance(
        workflowInstance: WorkflowInstance,
        isInstanceFromServer: Boolean
    ) {
        workflowInstanceDao.insert(
            workflowInstance.copy(isUploadedToServer = isInstanceFromServer)
        )
    }

    suspend fun getWorkflowInstance(instanceId: String): WorkflowInstance? =
        workflowInstanceDao.getById(instanceId)

    suspend fun getWorkflowInstancesForPatient(patientId: String): List<WorkflowInstance> =
        workflowInstanceDao.getByPatientId(patientId)

    suspend fun getWorkflowInstancesToUpload(): List<WorkflowInstance> =
        workflowInstanceDao.getWorkflowInstancesToUpload()

    suspend fun countWorkflowInstancesToUpload(): Int =
        workflowInstanceDao.countWorkflowInstancesToUpload()

    suspend fun startWorkflowInstance(patientId: String, workflowTemplate: WorkflowTemplate) {
        val template = workflowTemplateDao.getById(workflowTemplate.id) ?: workflowTemplate
        val now = UnixTimestamp.now.toLong()
        val instanceId = UUID.randomUUID().toString()

        val steps = template.steps.map { templateStep ->
            val isStartingStep = templateStep.id == template.startingStepId
            WorkflowInstanceStep(
                id = UUID.randomUUID().toString(),
                workflowInstanceId = instanceId,
                name = templateStep.name.orEmpty(),
                description = templateStep.description.orEmpty(),
                status = if (isStartingStep) WorkflowStatus.ACTIVE else WorkflowStatus.PENDING,
                workflowTemplateStepId = templateStep.id,
                startDate = if (isStartingStep) now else null,
                lastEdited = now
            )
        }
        val startingStep = steps.firstOrNull { it.status == WorkflowStatus.ACTIVE }

        workflowInstanceDao.insert(
            WorkflowInstance(
                id = instanceId,
                name = template.name.orEmpty(),
                status = if (startingStep == null) WorkflowStatus.PENDING else WorkflowStatus.ACTIVE,
                patientId = patientId,
                workflowTemplateId = template.id,
                currentStepId = startingStep?.id,
                startDate = now,
                lastEdited = now,
                steps = steps,
                isUploadedToServer = false
            )
        )
    }

    suspend fun getNextStepCandidates(instanceId: String): WorkflowNextStepResult {
        val instance = workflowInstanceDao.getById(instanceId)
            ?: return WorkflowNextStepResult.Unavailable
        if (instance.status == WorkflowStatus.COMPLETED ||
            instance.status == WorkflowStatus.CANCELLED
        ) {
            return WorkflowNextStepResult.Unavailable
        }

        val templateStepId = instance.currentStep()?.workflowTemplateStepId
        val templateStep = instance.workflowTemplateId
            ?.let { workflowTemplateDao.getById(it) }
            ?.steps
            ?.firstOrNull { it.id == templateStepId }
            ?: return WorkflowNextStepResult.Unavailable

        val targetTemplateStepIds = templateStep.branches.mapNotNull { it.targetStepId }.distinct()
        if (targetTemplateStepIds.isEmpty()) {
            return WorkflowNextStepResult.CompleteWorkflow
        }

        val candidates = targetTemplateStepIds.mapNotNull { targetId ->
            instance.steps.firstOrNull { it.workflowTemplateStepId == targetId }
                ?.let { WorkflowNextStep(instanceStepId = it.id, name = it.name) }
        }

        return if (candidates.isEmpty()) {
            WorkflowNextStepResult.Unavailable
        } else {
            WorkflowNextStepResult.Options(candidates)
        }
    }

    suspend fun advanceWorkflowStep(instanceId: String, chosenInstanceStepId: String?) {
        val instance = workflowInstanceDao.getById(instanceId) ?: return
        val currentStepId = instance.currentStep()?.id ?: return
        val now = UnixTimestamp.now.toLong()

        val updatedSteps = instance.steps.map { step ->
            when (step.id) {
                currentStepId -> step.copy(
                    status = WorkflowStatus.COMPLETED,
                    completionDate = now,
                    lastEdited = now
                )
                chosenInstanceStepId -> step.copy(
                    status = WorkflowStatus.ACTIVE,
                    startDate = now,
                    lastEdited = now
                )
                else -> step
            }
        }

        workflowInstanceDao.insert(
            instance.copy(
                status = if (chosenInstanceStepId == null) {
                    WorkflowStatus.COMPLETED
                } else {
                    WorkflowStatus.ACTIVE
                },
                currentStepId = chosenInstanceStepId ?: currentStepId,
                completionDate = if (chosenInstanceStepId == null) now else null,
                lastEdited = now,
                steps = updatedSteps,
                isUploadedToServer = false
            )
        )
    }

    private fun WorkflowInstance.currentStep(): WorkflowInstanceStep? =
        steps.firstOrNull { it.id == currentStepId && it.status == WorkflowStatus.ACTIVE }
            ?: steps.firstOrNull { it.status == WorkflowStatus.ACTIVE }
}
