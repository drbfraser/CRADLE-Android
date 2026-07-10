package com.cradleplatform.neptune.model

import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.Parcelize

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowInstance(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("status")
    val status: String,
    @JsonProperty("workflowTemplateId")
    val workflowTemplateId: String? = null,
    @JsonProperty("currentStepId")
    val currentStepId: String? = null,
    @JsonProperty("lastEdited")
    val lastEdited: Long? = null,
    @JsonProperty("steps")
    val steps: List<WorkflowInstanceStep> = emptyList()
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowTemplate(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("classification")
    val classification: WorkflowClassification? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowClassification(
    @JsonProperty("name")
    val name: String
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
    val steps: List<WorkflowStepRow> = emptyList()
) : Parcelable
