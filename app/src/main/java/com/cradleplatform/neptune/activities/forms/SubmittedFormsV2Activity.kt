package com.cradleplatform.neptune.activities.forms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.adapters.patients.ReadingRecyclerViewAdapter
import com.cradleplatform.neptune.viewmodel.forms.SubmittedFormsV2State
import com.cradleplatform.neptune.viewmodel.forms.SubmittedFormsV2ViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SubmittedFormsV2Activity : AppCompatActivity() {
    private val viewModel: SubmittedFormsV2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submitted_forms_v2)
        supportActionBar?.title = getString(R.string.see_submitted_forms_v2)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<RecyclerView>(R.id.recycler_view).layoutManager = LinearLayoutManager(this)
        findViewById<View>(R.id.retry_button).setOnClickListener { viewModel.loadForms() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state -> updateUIFromState(state) }
            }
        }
    }

    private fun updateUIFromState(state: SubmittedFormsV2State) {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        val loadingIndicator = findViewById<View>(R.id.loading_indicator)
        val status = findViewById<TextView>(R.id.status_text)
        val retryButton = findViewById<View>(R.id.retry_button)
        recyclerView.visibility = View.GONE
        loadingIndicator.visibility = View.GONE
        status.visibility = View.GONE
        retryButton.visibility = View.GONE

        when (state) {
            SubmittedFormsV2State.Loading -> loadingIndicator.visibility = View.VISIBLE
            is SubmittedFormsV2State.Success -> {
                if (state.forms.isEmpty()) {
                    status.text = getString(R.string.submitted_forms_v2_empty)
                    status.visibility = View.VISIBLE
                } else {
                    recyclerView.adapter = ReadingRecyclerViewAdapter(state.forms)
                    recyclerView.visibility = View.VISIBLE
                }
            }
            SubmittedFormsV2State.Error -> {
                status.text = getString(R.string.form_v2_records_load_error)
                status.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        fun makeIntent(context: Context, patientId: String): Intent =
            Intent(context, SubmittedFormsV2Activity::class.java).apply {
                putExtra(SubmittedFormsV2ViewModel.EXTRA_PATIENT_ID, patientId)
            }
    }
}
