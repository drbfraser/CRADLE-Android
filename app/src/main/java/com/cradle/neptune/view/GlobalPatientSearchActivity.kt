package com.cradle.neptune.view

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.Volley
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.ext.hideKeyboard
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.utilitiles.VolleyUtil
import com.cradle.neptune.viewmodel.GlobalPatientAdapter
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Todo this activity is pretty dirty with all the network calls. Once we have a NetworkManager
 * clean this up.
 * BUG: when you click on a patient from the list, go to [GlobalPatientProfileActivity] and save patient locally
 * than coming back to this activity will not update the recyclerview
 */
class GlobalPatientSearchActivity : AppCompatActivity() {

    @Inject
    lateinit var urlManager: UrlManager
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var patientManager: PatientManager
    @Inject
    lateinit var readingManager: ReadingManager

    private lateinit var searchView: SearchView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_patient_search)
        // inject:
        (application as MyApp).appComponent.inject(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.global_patient_search)
        setupGlobalPatientSearch()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupGlobalPatientSearch() {
        searchView = findViewById(R.id.globalPatientSearchView)

        searchView.setOnQueryTextListener(object : OnQueryTextListener {

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                if (query == "")
                    return false
                val searchUrl = urlManager.getGlobalPatientSearch(query)
                searchServerForThePatients(searchUrl)
                return true
            }
        })
    }

    /**
     * Makes an api call to fetch for a list of patients based on the query
     */
    private fun searchServerForThePatients(searchUrl: String) {
        val progressDialog = getProgessDialog("Fetching the patients")
        progressDialog.show()

        val jsonArrayRequest = VolleyUtil.makeJsonArrayRequest(Request.Method.GET, searchUrl, null,
        Response.Listener { response: JSONArray? ->
            MainScope().launch {
                setupPatientsRecycler(response as (JSONArray))
                progressDialog.cancel()
            }
            searchView.hideKeyboard()
        }, Response.ErrorListener { error ->
                Log.e(TAG, "error: " + error?.message)
                progressDialog.cancel()
                searchView.hideKeyboard()
                MainScope().launch {
                    setupPatientsRecycler(null)
                }
                Snackbar.make(searchView, "Sorry unable to fetch the patients", Snackbar.LENGTH_LONG).show()
            }, sharedPreferences)
        // incase there are alot of patients
        jsonArrayRequest.retryPolicy = DefaultRetryPolicy(networkTimeOutInMS,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add<JSONArray>(jsonArrayRequest)
    }

    private suspend fun setupPatientsRecycler(response: JSONArray?) {
        val emptyView = findViewById<ImageView>(R.id.emptyView)
        val recyclerView = findViewById<RecyclerView>(R.id.globalPatientrecyclerview)
        if (response == null || response.length() == 0) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        // get patient set so we can compare for our patients
        val patientSet = patientManager.getPatientIdsOnly().toSet()
        val globalPatientList = ArrayList<GlobalPatient>()

        for (i in 0 until response.length()) {
            val jsonObject: JSONObject = response[i] as JSONObject
            val patientId = jsonObject.getString("patientId")
            globalPatientList.add(
                GlobalPatient(
                    patientId,
                    jsonObject.getString("patientName"),
                    jsonObject.getString("villageNumber"),
                    patientSet.contains(patientId)
                )
            )
        }
        val globalPatientAdapter = GlobalPatientAdapter(globalPatientList)
        val layout = LinearLayoutManager(this)
        recyclerView.layoutManager = layout
        recyclerView.adapter = globalPatientAdapter
        globalPatientAdapter.notifyDataSetChanged()

        // adding onclick for adapter
        globalPatientAdapter.addPatientClickObserver(object :
            GlobalPatientAdapter.OnGlobalPatientClickListener {
            override fun onCardClick(patient: GlobalPatient) {
                startActivityForPatient(patient, patientSet.contains(patient.id))
            }

            override fun onAddToLocalClicked(patient: GlobalPatient) {
                if (patient.isMyPatient) {
                    Snackbar.make(searchView, "This patient already added as your patient",
                        Snackbar.LENGTH_LONG).show()
                    return
                }
                val alertDialog =
                    AlertDialog.Builder(this@GlobalPatientSearchActivity).setTitle("Are you sure?")
                        .setMessage("Are you sure you want to add this patient as your own? ")
                        .setPositiveButton("OK") { _: DialogInterface, _: Int ->

                            fetchInformationForThisPatient(patient, globalPatientList, globalPatientAdapter)
                        }
                        .setNegativeButton("NO") { _: DialogInterface, _: Int -> }
                        .setIcon(R.drawable.ic_sync)
                alertDialog.show()
            }
        })
    }

    /**
     * if patient is local start [PatientProfileActivity] else [GlobalPatientProfileActivity]
     */
    private fun startActivityForPatient(patient: GlobalPatient, isLocal: Boolean) {
        if (isLocal) {
            MainScope().launch { withContext(Dispatchers.IO) {
                val localPatient = patientManager.getPatientById(patient.id)
                val intent =
                    Intent(this@GlobalPatientSearchActivity, PatientProfileActivity::class.java)
                intent.putExtra("patient", localPatient)
                startActivity(intent)
                }
            }
        } else {
            val intent =
                Intent(this@GlobalPatientSearchActivity, GlobalPatientProfileActivity::class.java)
            intent.putExtra("globalPatient", patient)
            startActivity(intent)
        }
    }

    /**
     * Make an api call to get this patient's information and update the recyclerview
     */
    private fun fetchInformationForThisPatient(
        patient: GlobalPatient,
        globalPatientList: ArrayList<GlobalPatient>,
        globalPatientAdapter: GlobalPatientAdapter
    ) {
        val progressDialog = getProgessDialog("Adding to your patient list.")
        progressDialog.show()
        val jsonObjectRequest = VolleyUtil.makeJsonObjectRequest(Request.Method.GET,
            urlManager.getPatientInfoById(patient.id), null, Response.Listener {
                patientManager.add(Patient.unmarshal(it))
                val readingArray = it.getJSONArray("readings")
                for (i in 0 until readingArray.length()) {
                    readingManager.addReading(Reading.unmarshal(readingArray[i] as JsonObject))
                }
                // todo make the O^n better, maybe globalPatientList can be a map?
                // updating current adapter
                for (it in globalPatientList) {
                    if (it.id == patient.id) {
                        it.isMyPatient = true
                        break
                    }
                }
                globalPatientAdapter.notifyDataSetChanged()
                progressDialog.cancel()
            }, Response.ErrorListener {
                progressDialog.cancel()
                it.printStackTrace()
            }, sharedPreferences)
        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(jsonObjectRequest)
    }

    private fun getProgessDialog(message: String): ProgressDialog {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage(message)
        progressDialog.setCancelable(false)
        return progressDialog
    }
    companion object {
        val TAG = GlobalPatientSearchActivity::javaClass.name
        const val networkTimeOutInMS = 8000
    }
}
