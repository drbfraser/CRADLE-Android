package com.cradleplatform.neptune.activities.forms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.adapters.forms.FormTemplateListV2Adapter
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
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.adapter = FormTemplateListV2Adapter(state.templates)
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
        @JvmStatic
        fun makeIntent(context: Context): Intent = Intent(context, FormTemplateListV2Activity::class.java)
    }
}
