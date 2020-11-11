package com.cradle.neptune.view

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.ext.hideKeyboard
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import com.cradle.neptune.viewmodel.GlobalPatientAdapter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Todo this activity is pretty dirty with all the network calls. Once we have a NetworkManager
 * clean this up.
 * BUG: when you click on a patient from the list, go to [GlobalPatientProfileActivity] and save patient locally
 * than coming back to this activity will not update the recyclerview
 */
@AndroidEntryPoint
class GlobalPatientSearchActivity : AppCompatActivity() {

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var restApi: RestApi

    private lateinit var searchView: SearchView

    // local patient set to compare agains
    private lateinit var localPatientSet: HashSet<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_patient_search)
        lifecycleScope.launch {
            localPatientSet = patientManager.getPatientIdsOnly().toHashSet()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.global_patient_search_title)
        setupGlobalPatientSearch()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupGlobalPatientSearch() {
        searchView = findViewById(R.id.globalPatientSearchView)

        searchView.setOnQueryTextListener(
            object : OnQueryTextListener {

                override fun onQueryTextChange(newText: String): Boolean {
                    return false
                }

                override fun onQueryTextSubmit(query: String): Boolean {
                    if (query == "") {
                        return false
                    }
                    searchServerForThePatients(query.trim())
                    return true
                }
            }
        )
    }

    /**
     * Makes an api call to fetch for a list of patients based on the query
     */
    private fun searchServerForThePatients(searchUrl: String) {
        val progressDialog = getProgressDialog(
            getString(R.string.global_patient_search_fetching_the_patients)
        )
        progressDialog.show()

        lifecycleScope.launch {
            val result = restApi.searchForPatient(searchUrl)
            progressDialog.cancel()
            searchView.hideKeyboard()
            when (result) {
                is Success -> setupPatientsRecycler(result.value)
                else -> {
                    setupPatientsRecycler(null)
                    Snackbar.make(
                        searchView,
                        R.string.global_patient_search_unable_to_fetch,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun setupPatientsRecycler(globalPatientList: List<GlobalPatient>?) {
        val emptyView = findViewById<ImageView>(R.id.emptyView)
        val recyclerView = findViewById<RecyclerView>(R.id.globalPatientrecyclerview)
        if (globalPatientList == null || globalPatientList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        // get patient set so we can compare for our patients

        globalPatientList.forEachIndexed { index, globalPatient ->
            globalPatient.index = index
            if (localPatientSet.contains(globalPatient.id)) {
                globalPatient.isMyPatient = true
            }
        }

        val globalPatientAdapter = GlobalPatientAdapter(globalPatientList)
        val layout = LinearLayoutManager(this)
        recyclerView.layoutManager = layout
        recyclerView.adapter = globalPatientAdapter
        globalPatientAdapter.notifyDataSetChanged()

        // adding onclick for adapter
        globalPatientAdapter.addPatientClickObserver(
            object : GlobalPatientAdapter.OnGlobalPatientClickListener {
                override fun onCardClick(patient: GlobalPatient) {
                    startActivityForPatient(patient, localPatientSet.contains(patient.id))
                }

                override fun onAddToLocalClicked(patient: GlobalPatient) {
                    if (patient.isMyPatient) {
                        Snackbar.make(
                            searchView,
                            R.string.global_patient_search_patient_already_added,
                            Snackbar.LENGTH_LONG
                        ).show()
                        return
                    }
                    val alertDialog =
                        AlertDialog.Builder(this@GlobalPatientSearchActivity)
                            .setTitle(R.string.global_patient_search_add_patient_dialog_title)
                            .setMessage(R.string.global_patient_search_add_patient_dialog_message)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                fetchInformationForThisPatient(patient, globalPatientAdapter)
                            }
                            .setNegativeButton(android.R.string.no) { _, _ -> }
                            .setIcon(R.drawable.ic_sync)
                    alertDialog.show()
                }
            }
        )
    }

    /**
     * if patient is local start [PatientProfileActivity] else [GlobalPatientProfileActivity]
     */
    private fun startActivityForPatient(patient: GlobalPatient, isLocal: Boolean) {
        if (isLocal) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val localPatient = patientManager.getPatientById(patient.id)
                    val intent =
                        Intent(this@GlobalPatientSearchActivity, PatientProfileActivity::class.java)
                    intent.putExtra("patient", localPatient)
                    startActivity(intent)
                }
            }
        } else {
            val intent = GlobalPatientProfileActivity.makeIntent(
                this@GlobalPatientSearchActivity,
                patient
            )
            startActivity(intent)
        }
    }

    /**
     * Make an api call to get this patient's information and update the recyclerview
     */
    private fun fetchInformationForThisPatient(
        patient: GlobalPatient,
        globalPatientAdapter: GlobalPatientAdapter
    ) {
        val progressDialog = getProgressDialog(
            getString(R.string.global_patient_search_adding_to_your_patient_list)
        )
        progressDialog.show()
        lifecycleScope.launch {
            when (patientManager.downloadAssociateAndSavePatient(patient.id)) {
                is Success -> {
                    localPatientSet.add(patient.id)
                    patient.isMyPatient = true
                    globalPatientAdapter.run {
                        patient.index?.let { notifyItemChanged(it) } ?: notifyDataSetChanged()
                    }
                }
                else -> {
                    Toast.makeText(
                        this@GlobalPatientSearchActivity,
                        R.string.global_patient_search_unable_to_make_user_patient_relationship,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            progressDialog.cancel()
        }
    }

    private fun getProgressDialog(message: String): ProgressDialog {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage(message)
        progressDialog.setCancelable(false)
        return progressDialog
    }

    companion object {
        val TAG = GlobalPatientSearchActivity::javaClass.name
    }
}
