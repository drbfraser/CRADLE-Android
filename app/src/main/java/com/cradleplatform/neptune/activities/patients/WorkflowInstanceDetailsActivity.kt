package com.cradleplatform.neptune.activities.patients

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.WorkflowNextStep
import com.cradleplatform.neptune.model.WorkflowNextStepResult
import com.cradleplatform.neptune.model.WorkflowRow
import com.cradleplatform.neptune.model.WorkflowStepRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WorkflowInstanceDetailsActivity : AppCompatActivity() {

    @Inject
    lateinit var patientManager: PatientManager

    companion object {
        private const val EXTRA_WORKFLOW = "workflow_row"

        fun makeIntent(context: Context, workflow: WorkflowRow): Intent {
            val intent = Intent(context, WorkflowInstanceDetailsActivity::class.java)
            intent.putExtra(EXTRA_WORKFLOW, workflow)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workflow_instance_details)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val workflow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_WORKFLOW, WorkflowRow::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_WORKFLOW)
        } ?: run {
            finish()
            return
        }

        supportActionBar?.title = getString(R.string.workflow_details_title, workflow.templateName)

        findViewById<TextView>(R.id.workflowDetailsTitle).text =
            getString(R.string.workflow_details_title, workflow.templateName)

        findViewById<TextView>(R.id.workflowStartedValue).text = workflow.lastEdited
        findViewById<TextView>(R.id.workflowLastEditedValue).text = workflow.lastEdited

        findViewById<TextView>(R.id.workflowProgressValue).text = getString(
            R.string.workflow_details_progress_value,
            workflow.completedSteps,
            workflow.stepCount
        )
        val remainingView = findViewById<TextView>(R.id.workflowRemainingValue)
        val isFinished = workflow.status.equals("Completed", ignoreCase = true) ||
            workflow.status.equals("Cancelled", ignoreCase = true)
        if (isFinished) {
            remainingView.visibility = View.GONE
        } else {
            val remaining = (workflow.stepCount - workflow.completedSteps).coerceAtLeast(0)
            remainingView.visibility = View.VISIBLE
            remainingView.text = resources.getQuantityString(
                R.plurals.workflow_details_remaining,
                remaining,
                remaining
            )
        }

        val stepSection = findViewById<View>(R.id.workflowCurrentStepSection)
        if (!workflow.currentStepActive) {
            stepSection.visibility = View.GONE
        } else {
            stepSection.visibility = View.VISIBLE
            findViewById<TextView>(R.id.workflowCurrentStepName).text = workflow.currentStep
            findViewById<TextView>(R.id.workflowCurrentStepStatus).text =
                getString(R.string.workflow_details_step_status, workflow.lastEdited)
        }

        setupNextStepButton(workflow)

        populateStepHistory(workflow.steps)
    }

    private fun setupNextStepButton(workflow: WorkflowRow) {
        val nextStepButton = findViewById<Button>(R.id.workflowNextStepButton)

        if (!workflow.currentStepActive || workflow.currentStepId.isNullOrBlank()) {
            nextStepButton.visibility = View.GONE
            return
        }

        nextStepButton.visibility = View.VISIBLE
        nextStepButton.isEnabled = false
        lifecycleScope.launch {
            when (val result = patientManager.getNextStepCandidates(workflow.instanceId)) {
                is WorkflowNextStepResult.Options -> {
                    nextStepButton.setText(R.string.workflow_details_next_step_button)
                    nextStepButton.isEnabled = true
                    nextStepButton.setOnClickListener {
                        nextStepButton.isEnabled = false
                        showNextStepPicker(workflow.instanceId, result.steps, nextStepButton)
                    }
                }
                WorkflowNextStepResult.CompleteWorkflow -> {
                    nextStepButton.setText(R.string.workflow_complete_button)
                    nextStepButton.isEnabled = true
                    nextStepButton.setOnClickListener {
                        confirmCompleteWorkflow(workflow.instanceId)
                    }
                }
                WorkflowNextStepResult.Unavailable ->
                    nextStepButton.visibility = View.GONE
            }
        }
    }

    private fun showNextStepPicker(
        instanceId: String,
        steps: List<WorkflowNextStep>,
        nextStepButton: Button
    ) {
        val names = steps.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workflow_next_step_choose_title)
            .setItems(names) { _, index ->
                advanceWorkflowStep(instanceId, steps[index].instanceStepId)
            }
            .setOnCancelListener { nextStepButton.isEnabled = true }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                nextStepButton.isEnabled = true
            }
            .show()
    }

    private fun confirmCompleteWorkflow(instanceId: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workflow_complete_confirm_title)
            .setMessage(R.string.workflow_complete_confirm_message)
            .setPositiveButton(R.string.workflow_complete_button) { _, _ ->
                advanceWorkflowStep(instanceId, null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun advanceWorkflowStep(instanceId: String, chosenInstanceStepId: String?) {
        lifecycleScope.launch {
            patientManager.advanceWorkflowStep(instanceId, chosenInstanceStepId)
            val message = if (chosenInstanceStepId == null) {
                R.string.workflow_complete_success
            } else {
                R.string.workflow_details_next_step_success
            }
            Toast.makeText(
                this@WorkflowInstanceDetailsActivity,
                message,
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun populateStepHistory(steps: List<WorkflowStepRow>) {
        val container = findViewById<LinearLayout>(R.id.workflowStepHistoryContainer)
        val emptyView = findViewById<TextView>(R.id.workflowStepHistoryEmpty)

        if (steps.isEmpty()) {
            container.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }

        container.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        steps.forEachIndexed { index, step ->
            val row = inflater.inflate(R.layout.item_workflow_step_history, container, false)

            row.findViewById<TextView>(R.id.stepName).text = step.name

            val statusView = row.findViewById<TextView>(R.id.stepStatus)
            statusView.text =
                getString(R.string.workflow_step_status_label, stepStatusLabel(step.status))
            statusView.setTextColor(stepStatusColor(step.status))
            val dot = row.findViewById<View>(R.id.stepStatusDot)
            val dotBackground = dot.background.mutate()
            dotBackground.setTint(stepStatusColor(step.status))
            dot.background = dotBackground

            row.findViewById<TextView>(R.id.stepStarted).text =
                getString(R.string.workflow_step_started, step.startedDate)

            val completedView = row.findViewById<TextView>(R.id.stepCompleted)
            if (step.completedDate != null) {
                completedView.visibility = View.VISIBLE
                completedView.text =
                    getString(R.string.workflow_step_completed, step.completedDate)
            } else {
                completedView.visibility = View.GONE
            }

            container.addView(row)

            if (index < steps.size - 1) {
                container.addView(makeStepDivider())
            }
        }
    }

    private fun makeStepDivider(): View {
        val divider = View(this)
        val height = (resources.displayMetrics.density).toInt().coerceAtLeast(1)
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.listDivider, typedValue, true)
        divider.setBackgroundResource(typedValue.resourceId)
        return divider
    }

    private fun stepStatusLabel(status: String): String = when (status.lowercase()) {
        "completed" -> getString(R.string.workflow_step_status_completed)
        "active" -> getString(R.string.workflow_step_status_active)
        "pending" -> getString(R.string.workflow_step_status_pending)
        "cancelled" -> getString(R.string.workflow_step_status_cancelled)
        else -> getString(R.string.workflow_details_na)
    }

    private fun stepStatusColor(status: String): Int = when (status.lowercase()) {
        "completed" -> ContextCompat.getColor(this, R.color.success_green)
        "active" -> ContextCompat.getColor(this, R.color.colorPrimary)
        "cancelled" -> ContextCompat.getColor(this, R.color.redDown)
        else -> ContextCompat.getColor(this, android.R.color.darker_gray)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
