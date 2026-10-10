package com.cradleplatform.neptune.activities.forms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.adapters.forms.FormTemplateListV2Adapter
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2State
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateListV2State
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateListV2ViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * displays the list of form templates available to the user. This is the V2 version of the form template list,
 * which uses the new FormTemplateV2 model.
 */
@AndroidEntryPoint
class FormTemplateListV2Activity : AppCompatActivity() {

    private val viewModel: FormTemplateListV2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_template_list_v2)

        setUpActionBar()

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        if (intent.getStringExtra(EXTRA_PATIENT_ID) != null) {
            supportActionBar?.title = getString(R.string.create_new_form)
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.selectedTemplate.collectLatest { state ->
                        val language = findViewById<AutoCompleteTextView>(R.id.v2_language_dropdown)
                        val fetch = findViewById<Button>(R.id.v2_fetch_form_button)
                        val error = findViewById<TextView>(R.id.error_text)
                        language.isEnabled = false
                        fetch.isEnabled = false
                        language.setText("", false)
                        error.visibility = View.GONE
                        findViewById<View>(R.id.loading_indicator).visibility =
                            if (state is FormTemplateDetailV2State.Loading) View.VISIBLE else View.GONE
                        if (state is FormTemplateDetailV2State.Success) {
                            val template = state.template
                            val languages = (template.classification.name.keys +
                                template.questions.orEmpty().flatMap { question ->
                                    question.questionText.keys + question.mcOptions.orEmpty().flatMap { it.translations.keys }
                                }).distinct().sorted()
                            language.setAdapter(ArrayAdapter(this@FormTemplateListV2Activity,
                                R.layout.list_dropdown_menu_item, languages))
                            language.isEnabled = languages.isNotEmpty()
                            viewModel.selectedLanguage?.takeIf { it in languages }?.let {
                                language.setText(it, false)
                                fetch.isEnabled = true
                            }
                            language.setOnItemClickListener { _, _, position, _ ->
                                viewModel.selectedLanguage = languages[position]
                                fetch.isEnabled = true
                            }
                            fetch.setOnClickListener {
                                val selectedLanguage = viewModel.selectedLanguage ?: return@setOnClickListener
                                startActivity(FormV2RenderingActivity.makeIntent(this@FormTemplateListV2Activity,
                                    template.id, intent.getStringExtra(EXTRA_PATIENT_ID), selectedLanguage))
                            }
                        } else if (state is FormTemplateDetailV2State.Error) {
                            error.text = getString(R.string.form_template_v2_error, state.message)
                            error.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state -> updateUIFromState(state) }
            }
        }
    }

    private fun updateUIFromState(state: FormTemplateListV2State) {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val loadingIndicator: View = findViewById(R.id.loading_indicator)
        val errorText: android.widget.TextView = findViewById(R.id.error_text)

        recyclerView.visibility = View.GONE
        loadingIndicator.visibility = View.GONE
        errorText.visibility = View.GONE

        when (state) {
            is FormTemplateListV2State.Loading -> {
                loadingIndicator.visibility = View.VISIBLE
            }
            is FormTemplateListV2State.Success -> {
                if (state.templates.isEmpty()) {
                    errorText.visibility = View.VISIBLE
                    errorText.text = getString(R.string.form_template_v2_empty)
                } else {
                    if (intent.getStringExtra(EXTRA_PATIENT_ID) != null) {
                        findViewById<View>(R.id.form_v2_selection).visibility = View.VISIBLE
                        val dropdown = findViewById<AutoCompleteTextView>(R.id.v2_template_dropdown)
                        dropdown.setAdapter(ArrayAdapter(this, R.layout.list_dropdown_menu_item,
                            state.templates.map { "${it.name} (v${it.version})" }))
                        state.templates.firstOrNull { it.id == viewModel.selectedTemplateId }?.let {
                            dropdown.setText("${it.name} (v${it.version})", false)
                        }
                        dropdown.setOnItemClickListener { _, _, position, _ ->
                            viewModel.selectTemplate(state.templates[position].id)
                        }
                        return
                    }
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.adapter = FormTemplateListV2Adapter(state.templates) { template ->
                        startActivity(
                            FormTemplateDetailV2Activity.makeIntent(
                                this,
                                template.id,
                                intent.getStringExtra(EXTRA_PATIENT_ID),
                            )
                        )
                    }
                }
            }
            is FormTemplateListV2State.Error -> {
                errorText.visibility = View.VISIBLE
                errorText.text = getString(R.string.form_template_v2_error, state.message)
            }
        }
    }

    private fun setUpActionBar() {
        supportActionBar?.title = getString(R.string.form_template_v2_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    @Suppress("DEPRECATION")
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_PATIENT_ID = "form_v2_patient_id"

        @JvmStatic
        fun makeIntent(context: Context, patientId: String? = null): Intent =
            Intent(context, FormTemplateListV2Activity::class.java).apply {
                putExtra(EXTRA_PATIENT_ID, patientId)
            }
    }
}
