package com.cradleplatform.neptune.activities.patients

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ActivityEditPregnancyBinding
import com.cradleplatform.neptune.fragments.patients.AddPregnancyFragment
import com.cradleplatform.neptune.fragments.patients.ClosePregnancyFragment
import com.cradleplatform.neptune.viewmodel.patients.EditPatientViewModel.SaveResult
import com.cradleplatform.neptune.viewmodel.patients.EditPregnancyViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditPregnancyActivity : AppCompatActivity() {
    private val viewModel: EditPregnancyViewModel by viewModels()
    private var binding: ActivityEditPregnancyBinding? = null
    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

    companion object {
        private const val TAG = "EditPregnancyActivity"
        private const val EXTRA_PATIENT_ID = "patientId"
        private const val EXTRA_IS_PREGNANT = "isPregnant"

        fun makeIntentWithPatientId(context: Context, patientId: String, isPregnant: Boolean): Intent {
            val intent = Intent(context, EditPregnancyActivity::class.java)
            intent.putExtra(EXTRA_PATIENT_ID, patientId)
            intent.putExtra(EXTRA_IS_PREGNANT, isPregnant)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_edit_pregnancy, dataBindingComponent)
        binding?.apply {
            viewModel = this@EditPregnancyActivity.viewModel
            lifecycleOwner = this@EditPregnancyActivity
            executePendingBindings()
        }

        if (intent.hasExtra(EXTRA_PATIENT_ID)) {
            val patientId = intent.getStringExtra(EXTRA_PATIENT_ID)
                ?: error("no patient with given id")
            viewModel.initialize(patientId, intent.getBooleanExtra(EXTRA_IS_PREGNANT, false))
        }

        setupFragment(savedInstanceState)
        setupSaveButton()
    }

    private fun setupFragment(savedInstanceState: Bundle?) {
        var title = getString(R.string.edit_pregnancy)
        if (savedInstanceState == null && intent.hasExtra(EXTRA_IS_PREGNANT)) {

            if (intent.getBooleanExtra(EXTRA_IS_PREGNANT, false)) {
                title = getString(R.string.close_pregnancy)
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    add<ClosePregnancyFragment>(R.id.frag_edit_pregnancy)
                }
            } else {
                title = getString(R.string.add_pregnancy)
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    add<AddPregnancyFragment>(R.id.frag_edit_pregnancy)
                }
            }
        }
        setupToolBar(title)
    }

    private fun setupToolBar(title: String) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(title)
    }

    private fun setupSaveButton() {
        val btnSave = findViewById<Button>(R.id.btn_save_pregnancy)
        btnSave.setOnClickListener {
            lifecycleScope.launch {
                when (viewModel.saveAndUploadPregnancy()) {
                    is SaveResult.SavedAndUploaded -> {
                        Toast.makeText(
                            it.context,
                            getString(R.string.edit_online_success_msg),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    is SaveResult.SavedOffline -> {
                        Toast.makeText(
                            it.context,
                            getString(R.string.edit_offline_success_msg),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    is SaveResult.ServerReject -> {
                        MaterialAlertDialogBuilder(it.context)
                            .setTitle(R.string.server_error)
                            .setMessage(R.string.data_invalid_please_sync)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    else -> {
                        Toast.makeText(
                            it.context,
                            getString(R.string.edit_fail_msg),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }
}
