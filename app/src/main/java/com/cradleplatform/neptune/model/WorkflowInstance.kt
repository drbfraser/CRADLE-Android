package com.cradleplatform.neptune.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.Parcelize

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
    val name: String? = null,
    @ColumnInfo @JsonProperty("status")
    val status: String,
    @ColumnInfo @JsonProperty("patientId")
    val patientId: String? = null,
    @ColumnInfo @JsonProperty("workflowTemplateId")
    val workflowTemplateId: String? = null,
    @ColumnInfo @JsonProperty("currentStepId")
    val currentStepId: String? = null,
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
    @JsonProperty("name")
    val name: String,
    @JsonProperty("description")
    val description: String? = null,
    @JsonProperty("status")
    val status: String? = null,
    @JsonProperty("startDate")
    val startDate: Long? = null,
    @JsonProperty("completionDate")
    val completionDate: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowInstanceList(
    @JsonProperty("items")
    val items: List<WorkflowInstance> = emptyList()
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowAction(
    @JsonProperty("type")
    val type: String,
    @JsonProperty("stepId")
    val stepId: String? = null
)

data class ApplyActionRequest(
    @JsonProperty("action")
    val action: WorkflowAction
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowAvailableActions(
    @JsonProperty("actions")
    val actions: List<WorkflowAction> = emptyList()
)

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowTemplate(
    @PrimaryKey @ColumnInfo @JsonProperty("id")
    val id: String,
    @ColumnInfo @JsonProperty("name")
    val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowTemplateList(
    @JsonProperty("items")
    val items: List<WorkflowTemplate> = emptyList()
)

data class CreateWorkflowInstanceRequest(
    @JsonProperty("workflowTemplateId")
    val workflowTemplateId: String,
    @JsonProperty("patientId")
    val patientId: String,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("description")
    val description: String
)

@Parcelize
data class WorkflowStepRow(
    val name: String,
    val status: String,
    val startedDate: String,
    val completedDate: String?
) : Parcelable

@Parcelize
data class WorkflowRow(
    val templateName: String,
    val status: String,
    val lastEdited: String,
    val stepCount: Int,
    val currentStep: String,
    val completedSteps: Int,
    val instanceId: String = "",
    val currentStepId: String? = null,
    val currentStepActive: Boolean = false,
    val steps: List<WorkflowStepRow> = emptyList()
) : Parcelable
