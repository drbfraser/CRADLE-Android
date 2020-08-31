package com.cradle.neptune.view

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.viewmodel.PatientsViewAdapter
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PatientsActivity : AppCompatActivity() {
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var patientManager: PatientManager

    private lateinit var patientRecyclerview: RecyclerView
    private lateinit var patientsViewAdapter: PatientsViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        // inject:
        (application as MyApp).appComponent.inject(this)

        // setup UI
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patients)
        val toolbar =
            findViewById<Toolbar>(R.id.toolbar)
        patientRecyclerview = findViewById(R.id.patientListRecyclerview)
        MainScope().launch {
            setupPatientRecyclerview()
        }
        setSupportActionBar(toolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = "My Patients"
        }
        setupGlobalPatientSearchButton()
    }

    private fun setupGlobalPatientSearchButton() {
        val globalSearchButton =
            findViewById<ExtendedFloatingActionButton>(R.id.globalPatientSearch)
        globalSearchButton.setOnClickListener { v: View? ->
            startActivity(
                Intent(
                    this@PatientsActivity,
                    GlobalPatientSearchActivity::class.java
                )
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_patients, menu)

        // Associate searchable configuration with the SearchView
        val searchManager =
            getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.searchPatients)
            .actionView as SearchView
        searchView.setSearchableInfo(
            searchManager
                .getSearchableInfo(componentName)
        )
        searchView.maxWidth = Int.MAX_VALUE

        // listening to search query text change
        searchView.setOnQueryTextListener(object :
            SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                // filter recycler view when query submitted
                patientsViewAdapter.filter.filter(query)
                return false
            }

            override fun onQueryTextChange(query: String): Boolean {
                // filter recycler view when text is changed
                patientsViewAdapter.filter.filter(query)
                return false
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.searchPatients) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private suspend fun setupPatientRecyclerview() {
        val patientList = withContext(Dispatchers.IO) {
            patientManager.getAllPatients()
        }
        val patients: ArrayList<Pair<Patient, Reading?>> =
            ArrayList()
        for (patient in patientList) {
            patients.add(
                Pair(
                    patient,
                    readingManager.getNewestReadingByPatientIdBlocking(patient.id)
                )
            )
        }
        val textView = findViewById<TextView>(R.id.emptyView)
        if (patients.size == 0) {
            textView.visibility = View.VISIBLE
        } else {
            textView.visibility = View.GONE
        }
        patientsViewAdapter = PatientsViewAdapter(patients.toList(), this)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        patientRecyclerview.adapter = patientsViewAdapter
        patientRecyclerview.layoutManager = layoutManager
        patientRecyclerview.addItemDecoration(
            DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
            )
        )
        patientsViewAdapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        MainScope().launch {
            setupPatientRecyclerview()
        }
    }

    companion object {
        fun makeIntent(context: Context?): Intent {
            return Intent(context, PatientsActivity::class.java)
        }
    }
}
