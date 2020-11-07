package com.cradle.neptune.view

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.viewmodel.LocalSearchPatientAdapter
import com.cradle.neptune.viewmodel.PatientListViewModel
import com.cradle.neptune.viewmodel.PatientsViewAdapter
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PatientsActivity : AppCompatActivity() {
    private val viewModel: PatientListViewModel by viewModels()

    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var patientManager: PatientManager

    private lateinit var patientRecyclerview: RecyclerView
    private lateinit var patientsViewAdapter: PatientsViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patients)

        patientRecyclerview = findViewById(R.id.patientListRecyclerview)
        patientRecyclerview.apply {
            addItemDecoration(
                DividerItemDecoration(this@PatientsActivity, DividerItemDecoration.VERTICAL)
            )
            val localSearchPatientAdapter = LocalSearchPatientAdapter()
            this.adapter = localSearchPatientAdapter
            lifecycleScope.launch {
                viewModel.allPatientsFlow.collectLatest {
                    Log.d("PatientsActivity", "DEBUG: FLOW")
                    localSearchPatientAdapter.submitData(it)
                }
            }
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.activity_patients_title)
        }

        setupGlobalPatientSearchButton()
    }

    private fun setupGlobalPatientSearchButton() {
        val globalSearchButton =
            findViewById<ExtendedFloatingActionButton>(R.id.globalPatientSearch)
        globalSearchButton.setOnClickListener {
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
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.searchPatients).actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.maxWidth = Int.MAX_VALUE

        // listening to search query text change
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
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
            }
        )
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

    companion object {
        fun makeIntent(context: Context?): Intent {
            return Intent(context, PatientsActivity::class.java)
        }
    }
}
