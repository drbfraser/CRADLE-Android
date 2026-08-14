package com.cradleplatform.neptune.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cradleplatform.neptune.utilities.DateUtil
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.Parcelize

object WorkflowStatus {
    const val PENDING = "Pending"
    const val ACTIVE = "Active"
    const val COMPLETED = "Completed"
    const val CANCELLED = "Cancelled"
}

@Entity(
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["patientId"])
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowInstance(
    @PrimaryKey @ColumnInfo @JsonProperty("id")
    val id: String,
    @ColumnInfo @JsonProperty("name")
    val name: String = "",
    @ColumnInfo @JsonProperty("description")
    val description: String = "",
    @ColumnInfo @JsonProperty("status")
    val status: String = WorkflowStatus.PENDING,
    @ColumnInfo @JsonProperty("patientId")
    val patientId: String? = null,
    @ColumnInfo @JsonProperty("workflowTemplateId")
    val workflowTemplateId: String? = null,
    @ColumnInfo @JsonProperty("currentStepId")
    val currentStepId: String? = null,
    @ColumnInfo @JsonProperty("startDate")
    val startDate: Long? = null,
    @ColumnInfo @JsonProperty("completionDate")
    val completionDate: Long? = null,
    @ColumnInfo @JsonProperty("lastEdited")
    val lastEdited: Long? = null,
    @ColumnInfo @JsonProperty("steps")
    val steps: List<WorkflowInstanceStep> = emptyList(),
    @ColumnInfo(defaultValue = "1") @JsonIgnore
    val isUploadedToServer: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowInstanceStep(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("workflowInstanceId")
    val workflowInstanceId: String,
    @JsonProperty("name")
    val name: String = "",
    @JsonProperty("description")
    val description: String = "",
    @JsonProperty("status")
    val status: String = WorkflowStatus.PENDING,
    @JsonProperty("workflowTemplateStepId")
    val workflowTemplateStepId: String? = null,
    @JsonProperty("startDate")
    val startDate: Long? = null,
    @JsonProperty("completionDate")
    val completionDate: Long? = null,
    @JsonProperty("lastEdited")
    val lastEdited: Long? = null
)

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowTemplate(
    @PrimaryKey @ColumnInfo @JsonProperty("id")
    val id: String,
    @ColumnInfo @JsonProperty("name")
    val name: String? = null,
    @ColumnInfo @JsonProperty("startingStepId")
    val startingStepId: String? = null,
    @ColumnInfo @JsonProperty("lastEdited")
    val lastEdited: Long? = null,
    @ColumnInfo(defaultValue = "[]") @JsonProperty("steps")
    val steps: List<WorkflowTemplateStep> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowTemplateStep(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("description")
    val description: String? = null,
    @JsonProperty("branches")
    val branches: List<WorkflowTemplateStepBranch> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowTemplateStepBranch(
    @JsonProperty("targetStepId")
    val targetStepId: String? = null
)

data class WorkflowNextStep(
    val instanceStepId: String,
    val name: String
)

sealed class WorkflowNextStepResult {
    data class Options(val steps: List<WorkflowNextStep>) : WorkflowNextStepResult()
    object CompleteWorkflow : WorkflowNextStepResult()
    object Unavailable : WorkflowNextStepResult()
}

@Parcelize
data class WorkflowStepRow(
    val name: String,
    val status: String,
    val startedDate: String,
    val completedDate: String?
) : Parcelable

@Parcelize
data class WorkflowRow(
    val instanceId: String,
    val templateName: String,
    val status: String,
    val lastEdited: String,
    val stepCount: Int,
    val completedSteps: Int,
    val currentStep: String,
    val currentStepId: String? = null,
    val steps: List<WorkflowStepRow> = emptyList()
) : Parcelable

fun WorkflowInstance.toWorkflowRow(): WorkflowRow {
    val activeStep = steps.firstOrNull { it.status == WorkflowStatus.ACTIVE }
    val stepRows = steps.map { step ->
        WorkflowStepRow(
            name = step.name,
            status = step.status,
            startedDate = step.startDate
                ?.let { DateUtil.getDateStringFromTimestamp(it) }
                ?: NOT_AVAILABLE,
            completedDate = step.completionDate
                ?.let { DateUtil.getDateStringFromTimestamp(it) }
        )
    }
    return WorkflowRow(
        instanceId = id,
        templateName = name.ifBlank { NOT_AVAILABLE },
        status = status,
        lastEdited = lastEdited
            ?.let { DateUtil.getDateStringFromTimestamp(it) }
            ?: NOT_AVAILABLE,
        stepCount = steps.size,
        completedSteps = steps.count { it.status == WorkflowStatus.COMPLETED },
        currentStep = activeStep?.name ?: NOT_AVAILABLE,
        currentStepId = activeStep?.id,
        steps = stepRows
    )
}

private const val NOT_AVAILABLE = "N/A"
