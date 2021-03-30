package com.cradleVSA.neptune.view

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.model.GlobalPatient
import com.cradleVSA.neptune.net.Success
import com.cradleVSA.neptune.utilitiles.livedata.NetworkAvailableLiveData
import com.cradleVSA.neptune.viewmodel.ReadingRecyclerViewAdapter
import com.cradleVSA.neptune.viewmodel.ReadingRecyclerViewAdapter.OnClickElement
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This is a child class of [PatientProfileActivity] and uses some functions from the parent class.
 */
@AndroidEntryPoint
class GlobalPatientProfileActivity : PatientProfileActivity() {

    private lateinit var isNetworkAvailable: NetworkAvailableLiveData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getGlobalPatient()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * starts the process to add the patient to current user's patient's list
     */
    private fun setupAddToMyPatientList() {
        val addToMyListButton = findViewById<Button>(R.id.addToMyPatientButton)
        isNetworkAvailable = NetworkAvailableLiveData(this)
        isNetworkAvailable.observe(this) {}

        addToMyListButton.visibility = VISIBLE
        addToMyListButton.setOnClickListener {
            if (!isInternetAvailable(R.string.global_patient_profile_internet_needed_to_associate)) {
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.global_patient_profile_dialog_title)
                .setMessage(getString(R.string.global_patient_profile_dialog_message))
                .setPositiveButton(android.R.string.yes) { _: DialogInterface, _: Int ->
                    setupAddingPatientLocally()
                }.setNegativeButton(android.R.string.no) { _: DialogInterface, _: Int ->
                }.show()
        }
    }

    private fun isInternetAvailable(@StringRes res: Int): Boolean {
        if (isNetworkAvailable.value != true) {
            Toast.makeText(this, res, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    /**
     * adds current patient to the current vht list.
     */
    private fun setupAddingPatientLocally() {
        if (!isInternetAvailable(R.string.global_patient_profile_internet_needed_to_associate)) {
            return
        }

        val progressDialog = ProgressDialog(this)
        progressDialog.setCancelable(false)
        progressDialog.setMessage(getString(R.string.global_patient_profile_add_dialog_message))
        progressDialog.show()

        lifecycleScope.launch {
            val result = patientManager.associatePatientWithUser(currPatient.id)
            if (result.failed) {
                progressDialog.cancel()
                Toast.makeText(
                    this@GlobalPatientProfileActivity,
                    R.string.global_patient_profile_add_failed,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            addThePatientInfoToLocalDb(progressDialog)
        }
    }

    /**
     * adds patient info to local db and starts [PatientProfileActivity]
     */
    private suspend fun addThePatientInfoToLocalDb(progressDialog: ProgressDialog) {
        patientManager.addPatientWithReadings(
            currPatient,
            patientReadings,
            areReadingsFromServer = true
        )

        val intent =
            Intent(this@GlobalPatientProfileActivity, PatientProfileActivity::class.java)
        intent.putExtra("patient", currPatient)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
        withContext(Dispatchers.Main) {
            progressDialog.cancel()
        }
        finish()
    }

    /**
     * makes a web request to get full patient info
     */
    private fun getGlobalPatient() {
        val globalPatient = intent.getParcelableExtra<GlobalPatient>(EXTRA_GLOBAL_PATIENT)
        check(globalPatient != null)

        val progressDialog = ProgressDialog(this)
        progressDialog.setCancelable(false)
        progressDialog.setMessage(
            getString(R.string.global_patient_profile_fetch_patient_dialog_message)
        )
        progressDialog.show()
        lifecycleScope.launch {
            val result = patientManager.downloadPatientAndReading(globalPatient.id)
            if (result !is Success) {
                progressDialog.cancel()
                Snackbar.make(
                    readingRecyclerview,
                    R.string.global_patient_profile_fetch_patient_fail,
                    Snackbar.LENGTH_INDEFINITE
                ).show()
                return@launch
            }

            currPatient = result.value.patient
            patientReadings = result.value.readings
            setupAddToMyPatientList()
            setupToolBar()
            populatePatientInfo(currPatient)
            setupReadingsRecyclerView()
            setupLineChart()
            progressDialog.cancel()
        }
    }

    /**
     * Should always return false here since we dont have any local patient
     */
    override fun getLocalPatient(): Boolean {
        return false
    }

    /**
     * simple function to setup the recyclerview
     */
    override fun setupReadingsRecyclerView() {
        val listAdapter = ReadingRecyclerViewAdapter(patientReadings)
        listAdapter.setOnClickElementListener(
            object : OnClickElement {
                override fun onClick(readingId: String) {
                    Snackbar.make(
                        readingRecyclerview,
                        R.string.global_patient_profile_you_must_add_to_list_before_editing,
                        Snackbar.LENGTH_LONG
                    ).show()
                }

                override fun onLongClick(readingId: String): Boolean {
                    return true
                }

                override fun onClickRecheckReading(readingId: String) {
                    Snackbar.make(
                        readingRecyclerview,
                        R.string.global_patient_profile_you_must_add_to_list_before_reading,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        )

        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        readingRecyclerview.layoutManager = layoutManager
        readingRecyclerview.isNestedScrollingEnabled = false
        readingRecyclerview.adapter = listAdapter
    }

    companion object {
        private const val EXTRA_GLOBAL_PATIENT = "globalPatient"

        fun makeIntent(context: Context, globalPatient: GlobalPatient) =
            Intent(context, GlobalPatientProfileActivity::class.java)
                .putExtra(EXTRA_GLOBAL_PATIENT, globalPatient)
    }
}
