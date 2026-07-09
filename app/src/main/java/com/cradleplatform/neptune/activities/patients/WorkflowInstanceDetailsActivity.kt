package com.cradleplatform.neptune.activities.patients

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.WorkflowRow

class WorkflowInstanceDetailsActivity : AppCompatActivity() {

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

        val remaining = (workflow.stepCount - workflow.completedSteps).coerceAtLeast(0)
        findViewById<TextView>(R.id.workflowProgressValue).text = getString(
            R.string.workflow_details_progress_value,
            workflow.completedSteps,
            workflow.stepCount
        )
        findViewById<TextView>(R.id.workflowRemainingValue).text = resources.getQuantityString(
            R.plurals.workflow_details_remaining,
            remaining,
            remaining
        )

        val stepSection = findViewById<View>(R.id.workflowCurrentStepSection)
        if (workflow.currentStep == getString(R.string.workflow_details_na)) {
            stepSection.visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.workflowCurrentStepName).text = workflow.currentStep
            findViewById<TextView>(R.id.workflowCurrentStepStatus).text =
                getString(R.string.workflow_details_step_status, workflow.lastEdited)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
