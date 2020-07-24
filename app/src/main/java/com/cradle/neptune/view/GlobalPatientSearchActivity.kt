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
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.Volley
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.ext.hideKeyboard
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.utilitiles.VolleyUtil
import com.cradle.neptune.viewmodel.GlobalPatientAdapter
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class GlobalPatientSearchActivity : AppCompatActivity() {

    @Inject
    lateinit var urlManager: UrlManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

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
                searchServerForThePatient(searchUrl)
                return true
            }
        })
    }

    private fun searchServerForThePatient(searchUrl: String) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Fetching the patients")
        progressDialog.setCancelable(false)
        progressDialog.show()

        val jsonArrayRequest = VolleyUtil.makeMeJsonArrayRequest(Request.Method.GET, searchUrl, null,
        Response.Listener { response: JSONArray? ->
            setupPatientsRecycler(response as (JSONArray))
            progressDialog.cancel()
            searchView.hideKeyboard()
        }, Response.ErrorListener { error ->
                Log.e(TAG, "error: " + error?.message)
                progressDialog.cancel()
                searchView.hideKeyboard()
                setupPatientsRecycler(null)
                Snackbar.make(searchView, "Sorry unable to fetch the patients", Snackbar.LENGTH_LONG).show()
            }, sharedPreferences)

        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add<JSONArray>(jsonArrayRequest)
    }

    private fun setupPatientsRecycler(response: JSONArray?) {
        val emptyView = findViewById<ImageView>(R.id.emptyView)
        val recyclerView = findViewById<RecyclerView>(R.id.globalPatientrecyclerview)
        if (response == null || response.length() == 0) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        val globalPatientList = ArrayList<GlobalPatient>()

        for (i in 0 until response.length()) {
            val jsonObject: JSONObject = response[i] as JSONObject
            globalPatientList.add(
                GlobalPatient(
                    jsonObject.getString("patientId"),
                    jsonObject.getString("patientName"),
                    jsonObject.getString("villageNumber"),
                    false
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
                val intent = Intent(
                    this@GlobalPatientSearchActivity,
                    GlobalPatientProfileActivity::class.java
                )
                intent.putExtra("globalPatient", patient)
                startActivity(intent)
            }

            override fun onAddToLocalClicked(patient: GlobalPatient) {
                if (patient.isMyPatient) {
                    Snackbar.make(searchView, "This patient already added as your patient", Snackbar.LENGTH_LONG).show()
                    return
                }
                val alertDialog =
                    AlertDialog.Builder(this@GlobalPatientSearchActivity).setTitle("Are you sure?")
                        .setMessage("Are you sure you want to add this patient as your own? ")
                        .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                            // todo make a network call to fetch the patient and update the recyclerview
                            // todo make the O^n better, maybe globalPatientList can be a map?
                            for (it in globalPatientList) {
                                if (it.id == patient.id) {
                                    it.isMyPatient = true
                                    break
                                }
                            }
                            globalPatientAdapter.notifyDataSetChanged()
                        }
                        .setNegativeButton("NO") { _: DialogInterface, _: Int -> }
                        .setIcon(R.drawable.ic_sync)
                alertDialog.show()
            }
        })
    }

    companion object {
        val TAG = GlobalPatientSearchActivity::javaClass.name
        const val networkTimeOutInMS = 8000
    }
}
