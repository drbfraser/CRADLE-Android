package com.cradleplatform.neptune.view

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.viewmodel.SavedFormAdapter
import com.cradleplatform.neptune.viewmodel.SavedFormsViewModel
import dagger.hilt.android.AndroidEntryPoint
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
@AndroidEntryPoint
class SavedFormsActivity : AppCompatActivity() {

    private val viewModel: SavedFormsViewModel by viewModels()
    private var patient: Patient? = null
    private var patientId: String? = null
    private var savedAsDraft: Boolean? = null
    private var previousPage: Boolean? = null

    private var adapter: SavedFormAdapter? = null
    private var formList: MutableList<FormResponse>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_forms)

        //Getting the patient from the intent (ID and Patient Object)
        patientId = intent.getStringExtra(EXTRA_PATIENT_ID)
        savedAsDraft = intent.getBooleanExtra(EXTRA_SAVED_FORM, false)
        previousPage = intent.getBooleanExtra(EXTRA_PREVIOUS_PAGE, true)

        setUpSavedFormsRecyclerView()
        setUpActionBar()
    }

    private fun setUpSavedFormsRecyclerView() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        lifecycleScope.launch {
            // Remove any saved forms from database whose versions are out-of-date
            viewModel.purgeOutdatedFormResponses()

            // Grab patient object
            if (patientId != "") {
                patient = viewModel.getPatientByPatientId(patientId!!)
                formList = if (savedAsDraft == true) {
                    viewModel.searchForDraftFormsByPatientId(patientId!!)
                } else {
                    viewModel.searchForSubmittedFormsByPatientId(patientId!!)
                }
            } else {
                patient = null
                patientId = null
                formList = viewModel.searchForDraftForms()
            }

            adapter = formList?.let { SavedFormAdapter(it, patient) }

            recyclerView.adapter = adapter
        }
        val itemTouchHelper =
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    source: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val swipedPosition = viewHolder.absoluteAdapterPosition
                    val swipedFormResponse = formList?.get(swipedPosition)
                    // Show confirmation dialog before deletion
                    showDeleteConfirmationDialog(swipedFormResponse, swipedPosition)
                }

                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    RecyclerViewSwipeDecorator.Builder(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                        .addBackgroundColor(
                            ContextCompat.getColor(
                                this@SavedFormsActivity,
                                R.color.redDown
                            )
                        )
                        .addActionIcon(R.drawable.baseline_delete)
                        .create()
                        .decorate()

                    super.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            })
        // Populate the recyclerView with the list of saved forms, using SavedFormAdapter

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun showDeleteConfirmationDialog(swipedFormResponse: FormResponse?, swipedPosition: Int) {
        AlertDialog.Builder(this@SavedFormsActivity)
            .setTitle("Confirm Delete")
            .setMessage("Are you sure you want to delete this form?")
            .setPositiveButton("Delete") { _, _ ->
                // Delete the item from the list and database
                swipedFormResponse?.formResponseId?.let { formResponseId ->
                    CoroutineScope(Dispatchers.IO).launch {
                        this@SavedFormsActivity.viewModel.formResponseManager.deleteFormResponseById(formResponseId)
                    }
                }
                //formList?.removeAt(swipedPosition)
                adapter?.deleteItem(swipedPosition)
                adapter?.notifyItemRemoved(swipedPosition)
                Toast.makeText(this@SavedFormsActivity, "Form deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                // Cancel the swipe and restore the item
                adapter?.notifyItemChanged(swipedPosition)
                dialog.dismiss()
            }
            .setOnCancelListener {
                // Handle cancellation if the dialog is canceled
                adapter?.notifyItemChanged(swipedPosition)
            }
            .show()
    }
    private fun setUpActionBar() {
        supportActionBar?.title = getString(R.string.see_saved_forms)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    companion object {
            private const val EXTRA_PATIENT_ID = "Patient ID that the forms are saved for"
            private const val EXTRA_PATIENT_OBJECT =
                "The Patient object that the forms are saved for"
            private const val EXTRA_SAVED_FORM =
                "Boolean value indicating whether the forms are saved"
            private const val EXTRA_PREVIOUS_PAGE = "The previous page the backspace leads to"

            @JvmStatic
            fun makeIntent(
                context: Context,
                patientId: String,
                patient: Patient,
                savedForm: Boolean,
                previousPage: Boolean
            ): Intent =
                Intent(context, SavedFormsActivity::class.java).apply {
                    putExtra(EXTRA_PATIENT_ID, patientId)
                    putExtra(EXTRA_PATIENT_OBJECT, patient)
                    putExtra(EXTRA_SAVED_FORM, savedForm)
                    putExtra(EXTRA_PREVIOUS_PAGE, previousPage)
                }
    }
}
