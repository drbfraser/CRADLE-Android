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
import com.cradleplatform.neptune.adapters.forms.DraftFormsV2Adapter
import com.cradleplatform.neptune.viewmodel.forms.DraftFormsV2State
import com.cradleplatform.neptune.viewmodel.forms.DraftFormsV2ViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DraftFormsV2Activity : AppCompatActivity() {
    private val viewModel: DraftFormsV2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_draft_forms_v2)
        supportActionBar?.title = getString(R.string.see_draft_forms_v2)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<RecyclerView>(R.id.recycler_view).layoutManager = LinearLayoutManager(this)
        findViewById<View>(R.id.retry_button).setOnClickListener { viewModel.loadDrafts() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state -> updateUIFromState(state) }
            }
        }
    }

    private fun updateUIFromState(state: DraftFormsV2State) {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        val loadingIndicator = findViewById<View>(R.id.loading_indicator)
        val status = findViewById<TextView>(R.id.status_text)
        val retryButton = findViewById<View>(R.id.retry_button)
        recyclerView.visibility = View.GONE
        loadingIndicator.visibility = View.GONE
        status.visibility = View.GONE
        retryButton.visibility = View.GONE

        when (state) {
            DraftFormsV2State.Loading -> loadingIndicator.visibility = View.VISIBLE
            is DraftFormsV2State.Success -> {
                if (state.drafts.isEmpty()) {
                    status.text = getString(R.string.draft_forms_v2_empty)
                    status.visibility = View.VISIBLE
                } else {
                    recyclerView.adapter = DraftFormsV2Adapter(state.drafts)
                    recyclerView.visibility = View.VISIBLE
                }
            }
            DraftFormsV2State.Error -> {
                status.text = getString(R.string.draft_forms_v2_load_error)
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
            Intent(context, DraftFormsV2Activity::class.java).apply {
                putExtra(DraftFormsV2ViewModel.EXTRA_PATIENT_ID, patientId)
            }
    }
}
