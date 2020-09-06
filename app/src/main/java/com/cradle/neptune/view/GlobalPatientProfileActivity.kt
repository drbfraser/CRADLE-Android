package com.cradle.neptune.view

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.net.Success
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter.OnClickElement
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.reading_card_assesment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This is a child class of [PatientProfileActivity] and uses some functions from the parent class.
 */
class GlobalPatientProfileActivity : PatientProfileActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as MyApp).appComponent.inject(this)
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
        addToMyListButton.visibility = VISIBLE
        addToMyListButton.setOnClickListener(View.OnClickListener {
            AlertDialog.Builder(this).setTitle("Are you sure?").setMessage("This is not reversible")
                .setPositiveButton("YES") { _: DialogInterface, _: Int ->
                    setupAddingPatientLocally()
                }.setNegativeButton("NO") { _: DialogInterface, _: Int ->
                }.show()
        })
    }

    /**
     * adds current patient to the current vht list.
     */
    private fun setupAddingPatientLocally() {
        val progressDialog = ProgressDialog(this)
        progressDialog.setCancelable(false)
        progressDialog.setMessage("Adding to your patient's list")
        progressDialog.show()

        MainScope().launch {
            val result = patientManager.associatePatientWithUser(currPatient.id)
            if (result.failed) {
                progressDialog.cancel()
                Toast.makeText(
                    this@GlobalPatientProfileActivity,
                    "Unable to make user-patient relationship",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            withContext(Dispatchers.IO) {
                addThePatientInfoToLocalDb(progressDialog)
            }
        }
    }

    /**
     * adds patient info to local db and starts [PatientProfileActivity]
     */
    private suspend fun addThePatientInfoToLocalDb(progressDialog: ProgressDialog) {
        patientReadings.forEach {
            readingManager.addReading(it.apply { isUploadedToServer = true })
        }
        patientManager.add(currPatient)
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
        val globalPatient =
            intent.getSerializableExtra("globalPatient") as GlobalPatient

        val progressDialog = ProgressDialog(this)
        progressDialog.setCancelable(false)
        progressDialog.setMessage("Fetching the patient...")
        progressDialog.show()
        MainScope().launch {
            val result = patientManager.downloadPatientAndReading(globalPatient.id)
            if (result !is Success) {
                progressDialog.cancel()
                Snackbar.make(
                    readingRecyclerview,
                    "Unable to fetch patient information",
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
        listAdapter.setOnClickElementListener(object : OnClickElement {
            override fun onClick(readingId: String) {
                Snackbar.make(
                    view, "You must add this patient to your patient lists " +
                        "before editing anything", Snackbar.LENGTH_LONG
                ).show()
            }

            override fun onLongClick(readingId: String): Boolean {
                return true
            }

            override fun onClickRecheckReading(readingId: String) {
                Snackbar.make(
                    view, "You must add this patient to your patient lists " +
                        "before creating a new reading", Snackbar.LENGTH_LONG
                ).show()
            }
        })

        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        readingRecyclerview.layoutManager = layoutManager
        readingRecyclerview.isNestedScrollingEnabled = false
        readingRecyclerview.adapter = listAdapter
    }
}
