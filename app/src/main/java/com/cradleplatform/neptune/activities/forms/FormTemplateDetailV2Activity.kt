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
import com.cradleplatform.neptune.adapters.forms.FormTemplateDetailV2Adapter
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2State
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2ViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Displays a single V2 form template's questions, read-only. Reached by tapping a row in
 * FormTemplateListV2Activity.
 */
@AndroidEntryPoint
class FormTemplateDetailV2Activity : AppCompatActivity() {

    private val viewModel: FormTemplateDetailV2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_template_detail_v2)

        supportActionBar?.title = getString(R.string.form_template_detail_v2_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        findViewById<View>(R.id.start_v2_form_button).setOnClickListener {
            startActivity(FormV2RenderingActivity.makeIntent(this, intent.getStringExtra(FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID)!!))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state -> updateUIFromState(state) }
            }
        }
    }

    private fun updateUIFromState(state: FormTemplateDetailV2State) {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val loadingIndicator: View = findViewById(R.id.loading_indicator)
        val errorText: TextView = findViewById(R.id.error_text)

        recyclerView.visibility = View.GONE
        loadingIndicator.visibility = View.GONE
        errorText.visibility = View.GONE

        when (state) {
            is FormTemplateDetailV2State.Loading -> {
                loadingIndicator.visibility = View.VISIBLE
            }
            is FormTemplateDetailV2State.Success -> {
                val questions = state.template.questions.orEmpty()
                if (questions.isEmpty()) {
                    errorText.visibility = View.VISIBLE
                    errorText.text = getString(R.string.form_template_detail_v2_empty)
                } else {
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.adapter = FormTemplateDetailV2Adapter(questions)
                }
            }
            is FormTemplateDetailV2State.Error -> {
                errorText.visibility = View.VISIBLE
                errorText.text = getString(R.string.form_template_detail_v2_error, state.message)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        @JvmStatic
        fun makeIntent(context: Context, templateId: String): Intent =
            Intent(context, FormTemplateDetailV2Activity::class.java).apply {
                putExtra(FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID, templateId)
            }
    }
}
