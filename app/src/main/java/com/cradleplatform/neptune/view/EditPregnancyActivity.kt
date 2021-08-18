package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.cradleplatform.neptune.view.ui.editPregnancy.AddPregnancyFragment
import com.cradleplatform.neptune.view.ui.editPregnancy.ClosePregnancyFragment
import com.cradleplatform.neptune.viewmodel.EditPregnancyViewModel
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

        var title = "Edit Pregnancy"
        if (savedInstanceState == null) {
            if (intent.hasExtra(EXTRA_IS_PREGNANT)) {
                if (intent.getBooleanExtra(EXTRA_IS_PREGNANT, false)) {
                    title = "Close Pregnancy"
                    Log.d(TAG, "sending to close pregnancy")
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        add<ClosePregnancyFragment>(R.id.frag_edit_pregnancy)
                    }
                } else {
                    title = "Add Pregnancy"
                    Log.d(TAG, "sending to add pregnancy")
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        add<AddPregnancyFragment>(R.id.frag_edit_pregnancy)
                    }
                }
            }
        }

        if (intent.hasExtra(EXTRA_PATIENT_ID)) {
            val patientId = intent.getStringExtra(EXTRA_PATIENT_ID)
                ?: error("no patient with given id")
            viewModel.initialize(patientId, intent.getBooleanExtra(EXTRA_IS_PREGNANT, false))
        }

        setupToolBar(title)
        setupSaveButton()
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
                    is EditPregnancyViewModel.SaveResult.SavedAndUploaded -> {
                        Toast.makeText(
                            it.context,
                            "Success - changes saved online",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    is EditPregnancyViewModel.SaveResult.SavedOffline -> {
                        Toast.makeText(
                            it.context,
                            "Please sync! Edits weren't pushed to server",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    is EditPregnancyViewModel.SaveResult.ServerReject -> {
                        MaterialAlertDialogBuilder(it.context)
                            .setTitle(R.string.server_error)
                            .setMessage(R.string.data_invalid_please_sync)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    else -> {
                        Toast.makeText(
                            it.context,
                            "Invalid - check errors or try syncing",
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
