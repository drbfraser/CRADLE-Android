package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.viewmodel.SavedFormAdapter
import com.cradleplatform.neptune.viewmodel.SavedFormsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SavedFormsActivity : AppCompatActivity() {

    private val viewModel: SavedFormsViewModel by viewModels()
    private var patient: Patient? = null
    private var patientId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_forms)

        //Getting the patient from the intent (ID and Patient Object)
        patientId = intent.getStringExtra(EXTRA_PATIENT_ID)!!
        patient = intent.getSerializableExtra(EXTRA_PATIENT_OBJECT) as Patient

        setUpSavedFormsRecyclerView()
        setUpActionBar()
    }
    
    private fun setUpSavedFormsRecyclerView() {
        lifecycleScope.launch {
            // Remove any saved forms from database whose versions are out-of-date
            viewModel.purgeOutdatedFormResponses()
            
            // Grab patient object
            if (patient == null) {
                patient = viewModel.getPatientByPatientId(patientId!!)
            }
            
            // Find the list of saved forms for that patient, if any
            val formList = viewModel.searchForFormResponsesByPatientId(patientId!!)

            // Populate the recyclerView with the list of saved forms, using SavedFormAdapter
            val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
            recyclerView.adapter = formList?.let { patient?.let { it1 -> SavedFormAdapter(it, it1) } }
        }
    }

    private fun setUpActionBar() {
        supportActionBar?.title = getString(R.string.see_saved_forms)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val EXTRA_PATIENT_ID = "Patient ID that the forms are saved for"
        private const val EXTRA_PATIENT_OBJECT = "The Patient object that the forms are saved for"
        @JvmStatic
        fun makeIntent(
            context: Context,
            patientId: String,
            patient: Patient
        ): Intent =
            Intent(context, SavedFormsActivity::class.java).apply {
                putExtra(EXTRA_PATIENT_ID, patientId)
                putExtra(EXTRA_PATIENT_OBJECT, patient)
            }
    }

}
