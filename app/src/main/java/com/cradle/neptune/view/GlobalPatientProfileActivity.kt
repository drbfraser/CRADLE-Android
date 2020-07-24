package com.cradle.neptune.view

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.Volley
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.utilitiles.VolleyUtil
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter.OnClickElement
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * This is a child class of [PatientProfileActivity] and uses some functions from the parent class.
 */
class GlobalPatientProfileActivity : PatientProfileActivity() {

    companion object {
        val TAG = GlobalPatientProfileActivity::class.java.canonicalName
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getGlobalPatient()
        setupAddToMyPatientList()
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
        // make the patient- user relationship
        val jsonObject = JSONObject()
        jsonObject.put("patientId", currPatient.id)

        val associationRequest = VolleyUtil.makeJsonObjectRequest(Request.Method.POST,
            urlManager.userPatientAssociation, jsonObject, Response.Listener {
                addThePatientInfoToLocalDb(progressDialog)
            }, Response.ErrorListener {
                Log.i(TAG, "error: " + it.localizedMessage)
                progressDialog.cancel()
            }, sharedPreferences)

        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add<JSONObject>(associationRequest)
    }

    /**
     * adds patient info to local db and starts [PatientProfileActivity]
     */
    private fun addThePatientInfoToLocalDb(progressDialog: ProgressDialog) {
        GlobalScope.launch(Dispatchers.IO) {
            patientReadings.forEach {
                readingManager.addReading(it)
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
        // make a request to fetch all the patient info and readings
        val jsonObjectRequest = VolleyUtil.makeJsonObjectRequest(Request.Method.GET,
            urlManager.getPatientInfoById(globalPatient.id), null,
            Response.Listener { response: JSONObject ->
                // extract all the patient info and readings
                currPatient = Patient.unmarshal(response)
                patientReadings = ArrayList()
                val readingArray = response.getJSONArray("readings")
                for (i in 0 until readingArray.length()) {
                    patientReadings.add(0, Reading.unmarshal(readingArray[i] as JsonObject))
                }
                // follow up with the rest of the initialization
                setupToolBar()
                populatePatientInfo(currPatient)
                setupReadingsRecyclerView()
                setupLineChart()
                progressDialog.cancel()
            }, Response.ErrorListener { error ->
                progressDialog.cancel()
                Snackbar.make(readingRecyclerview, "Unable to fetch the patient Information..." +
                    "\n${error.localizedMessage}", Snackbar.LENGTH_INDEFINITE).show()
            }, sharedPreferences)

        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add<JSONObject>(jsonObjectRequest)
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
                    readingRecyclerview, "You must add this patient to your patient lists " +
                        "before editing anything", Snackbar.LENGTH_LONG
                ).show()
            }

            override fun onLongClick(readingId: String): Boolean {
                return true
            }

            override fun onClickRecheckReading(readingId: String) {
                Snackbar.make(
                    readingRecyclerview, "You must add this patient to your patient lists " +
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
