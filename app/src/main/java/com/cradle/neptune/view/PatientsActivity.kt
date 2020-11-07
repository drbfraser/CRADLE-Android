package com.cradle.neptune.view

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.viewmodel.LocalSearchPatientAdapter
import com.cradle.neptune.viewmodel.PatientListViewModel
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PatientsActivity : AppCompatActivity() {
    private val viewModel: PatientListViewModel by viewModels()

    private var isFirstLoadDone: Boolean = false

    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var patientManager: PatientManager

    private lateinit var patientRecyclerview: RecyclerView
    private val localSearchPatientAdapter = LocalSearchPatientAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patients)

        patientRecyclerview = findViewById(R.id.patientListRecyclerview)
        patientRecyclerview.apply {
            addItemDecoration(
                DividerItemDecoration(this@PatientsActivity, DividerItemDecoration.VERTICAL)
            )
            this.adapter = localSearchPatientAdapter
        }
        val emptyMessage = findViewById<TextView>(R.id.emptyView)
        val progressBar = findViewById<ProgressBar>(R.id.patients_list_progress_bar)
        localSearchPatientAdapter.addLoadStateListener { loadState ->
            val isLoading = loadState.refresh is LoadState.Loading
            patientRecyclerview.isVisible = !isLoading
            progressBar.isVisible = isLoading

            // Show message when there are no results. Do not show the empty message if the
            // user hasn't loaded it first.
            if (isFirstLoadDone && !isLoading && localSearchPatientAdapter.itemCount == 0) {
                emptyMessage.text = getString(
                    if (viewModel.isUsingSearch()) {
                        R.string.activity_patients_no_search_result
                    } else {
                        // Here, the user has no patients in the database at all.
                        R.string.empty_text_for_patient
                    }
                )
                emptyMessage.isVisible = true
            } else {
                emptyMessage.isVisible = false
            }

            if (!isLoading) {
                isFirstLoadDone = true
                patientRecyclerview.scrollToPosition(0)
            }
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.activity_patients_title)
        }

        setupGlobalPatientSearchButton()

        // Trigger all the patients to be loaded with blank query.
        search("")
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

    private var searchJob: Job? = null

    private fun search(query: String) {
        val queryToUse = query.trim()

        // Adapted from https://developer.android.com/codelabs/android-paging
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            // Wait some time for the typing to stop. The delay function is a cancellation point.
            delay(SEARCH_JOB_DELAY_MILLIS)
            viewModel.searchPatientsFlow(queryToUse).collectLatest {
                localSearchPatientAdapter.submitData(it)
            }
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
                    search(query)
                    return false
                }

                override fun onQueryTextChange(query: String): Boolean {
                    search(query)
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
        /* How long it takes after a user types something for a database query to be made */
        private const val SEARCH_JOB_DELAY_MILLIS = 650L

        fun makeIntent(context: Context?): Intent {
            return Intent(context, PatientsActivity::class.java)
        }
    }
}
