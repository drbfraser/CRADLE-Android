package com.cradle.neptune.view

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.ext.hideKeyboard
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.Success
import com.cradle.neptune.network.unwrap
import com.cradle.neptune.network.unwrapFailure
import com.cradle.neptune.viewmodel.GlobalPatientAdapter
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Todo this activity is pretty dirty with all the network calls. Once we have a NetworkManager
 * clean this up.
 * BUG: when you click on a patient from the list, go to [GlobalPatientProfileActivity] and save patient locally
 * than coming back to this activity will not update the recyclerview
 */
class GlobalPatientSearchActivity : AppCompatActivity() {

    @Inject
    lateinit var patientManager: PatientManager
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    private lateinit var searchView: SearchView

    // local patient set to compare agains
    private lateinit var localPatientSet: HashSet<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_patient_search)
        // inject:
        (application as MyApp).appComponent.inject(this)
        MainScope().launch {
            localPatientSet = patientManager.getPatientIdsOnly().toHashSet()
        }
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
                searchServerForThePatients(query.trim())
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

        volleyRequestManager.getAllPatientsFromServerByQuery(searchUrl) { result ->
            when (result) {
                is Success -> {
                    val globalList = result.unwrap()
                    MainScope().launch {
                        setupPatientsRecycler(globalList)
                    }
                    progressDialog.cancel()
                    searchView.hideKeyboard()
                }
                is Failure -> {
                    Log.e(TAG, "error: " + result.unwrapFailure().message)
                    progressDialog.cancel()
                    searchView.hideKeyboard()
                    MainScope().launch {
                        setupPatientsRecycler(null)
                    }
                    Snackbar.make(searchView, "Sorry unable to fetch the patients", Snackbar.LENGTH_LONG).show()
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

        globalPatientList.forEach {
            if (localPatientSet.contains(it.id)) {
                it.isMyPatient = true
            }
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

                startActivityForPatient(patient, localPatientSet.contains(patient.id))
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
        globalPatientList: List<GlobalPatient>,
        globalPatientAdapter: GlobalPatientAdapter
    ) {
        val progressDialog = getProgessDialog("Adding to your patient list.")
        progressDialog.show()
        volleyRequestManager.getSinglePatientById(patient.id) { result ->
            when (result) {
                is Success -> {
                    // make another network call to set patient association and than save the results
                    volleyRequestManager.setUserPatientAssociation(patient.id) { isSuccessFul ->
                        if (isSuccessFul) {
                            patientManager.add(result.unwrap().first)
                            readingManager.addAllReadings(result.unwrap().second)
                            // add it to our local set for matching
                            localPatientSet.add(patient.id)
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
                        } else {
                            Toast.makeText(
                                this@GlobalPatientSearchActivity,
                                "Unable to make user-patient relationship", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                is Failure -> {
                    val error = result.unwrapFailure()
                    progressDialog.cancel()
                    error.printStackTrace()
                }
            }
        }
    }

    private fun getProgessDialog(message: String): ProgressDialog {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage(message)
        progressDialog.setCancelable(false)
        return progressDialog
    }
    companion object {
        val TAG = GlobalPatientSearchActivity::javaClass.name
    }
}
