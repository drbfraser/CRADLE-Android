package com.cradleplatform.neptune.view

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.viewmodel.LocalSearchPatientAdapter
import com.cradleplatform.neptune.viewmodel.PatientListViewModel
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

    private var searchView: SearchView? = null

    private val closeSearchViewCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                isEnabled = false
                hideSearchView()
            }
        }

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

        setupGlobalPatientSearchButton()
        val globalSearchButton =
            findViewById<ExtendedFloatingActionButton>(R.id.globalPatientSearch)

        // Pad the bottom of the recyclerView based on the height of the floating button (MOB-123)
        val searchButtonObserver = globalSearchButton.viewTreeObserver
        if (searchButtonObserver.isAlive) {
            searchButtonObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        patientRecyclerview.setPadding(
                            patientRecyclerview.paddingLeft,
                            patientRecyclerview.paddingTop,
                            patientRecyclerview.paddingRight,
                            globalSearchButton.height * 2
                        )
                        val removeObserver = globalSearchButton.viewTreeObserver
                        if (removeObserver.isAlive) {
                            removeObserver.removeOnGlobalLayoutListener(this)
                        }
                    }
                }
            )
        }

        val emptyMessage = findViewById<TextView>(R.id.emptyView)
        val progressBar = findViewById<ProgressBar>(R.id.patients_list_progress_bar)
        val retryButton = findViewById<Button>(R.id.retry_button)
        localSearchPatientAdapter.addLoadStateListener { loadState ->
            val isLoading = loadState.refresh is LoadState.Loading
            patientRecyclerview.isVisible = loadState.refresh is LoadState.NotLoading
            progressBar.isVisible = loadState.refresh is LoadState.Loading
            retryButton.isVisible = loadState.refresh is LoadState.Error

            if (loadState.refresh is LoadState.Error) {
                emptyMessage.text = getString(R.string.activity_patients_error_loading_patients)
                emptyMessage.isVisible = true
            } else if (isFirstLoadDone && !isLoading && localSearchPatientAdapter.itemCount == 0) {
                // Show message when there are no results. Do not show the empty message if the
                // user hasn't loaded it first.
                emptyMessage.text = getString(
                    when {
                        loadState.refresh is LoadState.Error -> {
                            R.string.activity_patients_error_loading_patients
                        }
                        viewModel.isUsingSearch() -> {
                            R.string.activity_patients_no_results_for_search
                        }
                        else -> {
                            // Here, the user has no patients in the database at all.
                            R.string.activity_patients_no_patients_message
                        }
                    }
                )
                emptyMessage.isVisible = true
            } else {
                emptyMessage.isVisible = false
            }

            if (!isLoading) {
                isFirstLoadDone = true
            } else {
                patientRecyclerview.scrollToPosition(0)
            }
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.activity_patients_title)
        }

        onBackPressedDispatcher.addCallback(this, closeSearchViewCallback)

        // If not restoring, display all patients by using a blank query.
        // Otherwise, the saved query will be used to search, and the
        // SearchView will be restored in onCreateOptionsMenu.
        val savedQuery = savedInstanceState?.getString(EXTRA_CURRENT_QUERY)
        if (savedQuery == null) {
            search("", shouldDelay = false)
        } else {
            search(savedQuery, shouldDelay = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (viewModel.isUsingSearch()) {
            outState.putString(EXTRA_CURRENT_QUERY, viewModel.currentQueryString)
        } else {
            outState.remove(EXTRA_CURRENT_QUERY)
        }
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

    private fun search(query: String, shouldDelay: Boolean) {
        val queryToUse = query.trim()

        // Adapted from https://developer.android.com/codelabs/android-paging
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (shouldDelay) {
                // Wait some time for the typing to stop. The delay function is a
                // cancellation point.
                delay(SEARCH_JOB_DELAY_MILLIS)
            }
            viewModel.searchPatientsFlow(queryToUse).collectLatest {
                localSearchPatientAdapter.submitData(it)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_patients, menu)

        // Associate searchable configuration with the SearchView
        val searchManager = ContextCompat.getSystemService(this, SearchManager::class.java)
            ?: error("missing SearchManager")
        val searchView = menu.findItem(R.id.searchPatients).actionView as SearchView
        this.searchView = searchView

        searchView.apply {
            // Load any queries from the saved state. The onCreate function would have loaded the
            // query string into the ViewModel from the saved instance state.
            // Do this before setting setOnQueryTextListener
            viewModel.currentQueryString?.let {
                if (!viewModel.isUsingSearch()) {
                    return@let
                }
                // Show the SearchView and populate the query.
                val searchViewMenuItem = menu.findItem(R.id.searchPatients)
                searchViewMenuItem.expandActionView()
                isIconified = true
                onActionViewExpanded()
                // Populate the query. This won't trigger a search, as this is done before
                // setOnQueryTextListener.
                setQuery(viewModel.currentQueryString, false)
                // Don't open the keyboard automatically
                clearFocus()
            }

            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            queryHint = getString(R.string.activity_patients_search_view_title)
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        search(query, shouldDelay = false)
                        return false
                    }

                    override fun onQueryTextChange(query: String): Boolean {
                        search(query, shouldDelay = true)
                        return false
                    }
                }
            )
            setOnSearchClickListener {
                closeSearchViewCallback.isEnabled = true
            }
            setOnCloseListener {
                closeSearchViewCallback.isEnabled = false
                // Don't want to override default behavior of clearing the query.
                return@setOnCloseListener false
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.searchPatients) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        searchView.let {
            // If the search icon is not showing, the user is searching something,
            // so we should remove that first.
            return if (hideSearchView()) {
                false
            } else {
                onBackPressed()
                true
            }
        }
    }

    /**
     * @return true if the [searchView] wasn't iconified and had to be hidden,
     * false otherwise.
     */
    private fun hideSearchView(): Boolean {
        searchView.let {
            return if (it?.isIconified == false) {
                // Clear the search query
                search("", shouldDelay = false)
                it.isIconified = true
                it.onActionViewCollapsed()
                closeSearchViewCallback.isEnabled = false
                true
            } else {
                false
            }
        }
    }

    companion object {
        /* How long it takes after a user types something for a database query to be made */
        private const val SEARCH_JOB_DELAY_MILLIS = 650L
        private const val EXTRA_CURRENT_QUERY = "current_query"

        fun makeIntent(context: Context?): Intent {
            return Intent(context, PatientsActivity::class.java)
        }
    }
}
