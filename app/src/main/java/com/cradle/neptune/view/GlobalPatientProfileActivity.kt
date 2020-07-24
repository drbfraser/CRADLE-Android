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
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.ReadingMetadata
import com.cradle.neptune.utilitiles.VolleyUtil
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter.OnClickElement
import com.google.android.material.snackbar.Snackbar
import java.util.UUID
import kotlinx.android.synthetic.main.reading_card_assesment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.threeten.bp.ZonedDateTime

/**
 * This is a child class of [PatientProfileActivity] and uses some functions from the parent class.
 */
class GlobalPatientProfileActivity : PatientProfileActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getGlobalPatient()
        setupAddToMyPatientList()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupAddToMyPatientList() {
        val addToMyListButton = findViewById<Button>(R.id.addToMyPatientButton)
        addToMyListButton.visibility = VISIBLE
        addToMyListButton.setOnClickListener(View.OnClickListener {
            AlertDialog.Builder(this).setTitle("Are you sure?").setMessage("This is not reversible")
                .setPositiveButton("YES") { _: DialogInterface, _: Int ->
                    setupCallLocalPatientActivity()
                }.setNegativeButton("NO") { _: DialogInterface, _: Int ->
                }.show()
        })
    }

    private fun setupCallLocalPatientActivity() {
        val progressDialog = ProgressDialog(this)
        progressDialog.setCancelable(false)
        progressDialog.setMessage("Adding to your patient's list")
        progressDialog.show()
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

    private fun getGlobalPatient() {
        val globalPatient =
            intent.getSerializableExtra("globalPatient") as GlobalPatient

        val progressDialog = ProgressDialog(this)
        progressDialog.setCancelable(false)
        progressDialog.setMessage("Fetching the patient...")
        progressDialog.show()

        val jsonObjectRequest = VolleyUtil.makeMeJsonObjectRequest(Request.Method.GET,urlManager.getPatientInfoById(globalPatient.id),
        null,Response.Listener { response: JSONObject ->
                Log.d("bugg",response.toString(4))
                currPatient = Patient.unmarshal(response)
                patientReadings = ArrayList()
                val readingArray = response.getJSONArray("readings")
                for (i in 0 until readingArray.length()) {
                    patientReadings.add(0, Reading.unmarshal(readingArray[i] as JsonObject))
                }
                setupToolBar()
                populatePatientInfo(currPatient)
                setupReadingsRecyclerView()
                setupLineChart()
                progressDialog.cancel()
            }, Response.ErrorListener {error ->
                progressDialog.cancel()
                Snackbar.make(readingRecyclerview,"Unable to fetch the patient Information..." +
                    "\n${error.localizedMessage}", Snackbar.LENGTH_INDEFINITE).show()

            },sharedPreferences)

        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add<JSONObject>(jsonObjectRequest)
    }

    /**
     * Should always return false here since we dont have any local patient
     */
    override fun getLocalPatient(): Boolean {
        return false
    }

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
