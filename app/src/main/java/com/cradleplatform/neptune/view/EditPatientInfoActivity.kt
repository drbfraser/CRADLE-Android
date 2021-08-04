package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.ActivityEditPatientInfoBinding
import com.cradleplatform.neptune.viewmodel.EditPatientViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EditPatientInfoActivity : AppCompatActivity() {
    private val viewModel: EditPatientViewModel by viewModels()

    private var binding: ActivityEditPatientInfoBinding? = null

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_edit_patient_info)
        binding?.apply {
            viewModel = this@EditPatientInfoActivity.viewModel
            lifecycleOwner = this@EditPatientInfoActivity
            executePendingBindings()
        }

        // add a toolbar4 to your xml, change names
        setSupportActionBar(findViewById(R.id.edit_patient_toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.edit_patient)
        }
/*
        // make this the save button (make it call viewModel save)
        val syncButton = findViewById<Button>(R.id.sync_button)
        syncButton.setOnClickListener {
            syncButton.isEnabled = false
            viewModel.startSyncing()
        }

        // this will be where you put the new data? damn it this isn't mutable live data :'(((
        showLastSyncStatus(null)
        viewModel.syncStatus.observe(this) { workInfo ->
            val syncStatusText = findViewById<TextView>(R.id.sync_status_text)
            val syncProgressBar = findViewById<ProgressBar>(R.id.sync_progress_bar)
            val downloadProgressText = findViewById<TextView>(R.id.download_progress_text_view)
            val lastSyncStatusText = findViewById<TextView>(R.id.latest_sync_status_text_view)

            if (workInfo == null || workInfo.state.isFinished) {
                showLastSyncStatus(workInfo)
            } else {
                lastSyncStatusText.apply {
                    text = ""
                    visibility = View.INVISIBLE
                }
                val state = SyncWorker.getState(workInfo)
                val progressPair = SyncWorker.getProgress(workInfo)
                syncProgressBar.apply {
                    if (progressPair == null) {
                        isIndeterminate = true
                    } else {
                        isIndeterminate = false
                        max = progressPair.second
                        progress = progressPair.first
                    }

                    if (visibility != View.VISIBLE) {
                        visibility = View.VISIBLE
                    }
                }

                downloadProgressText.apply {
                    if (progressPair == null) {
                        if (visibility != View.INVISIBLE) {
                            visibility = View.INVISIBLE
                        }
                    } else {
                        text = getString(
                            R.string.sync_activity_d_out_of_d_downloaded,
                            progressPair.first,
                            progressPair.second
                        )

                        if (visibility != View.VISIBLE) {
                            visibility = View.VISIBLE
                        }
                    }
                }

                val newStateString = when (state) {
                    SyncWorker.State.STARTING -> {
                        getString(R.string.sync_activity_status_beginning_upload)
                    }
                    SyncWorker.State.CHECKING_SERVER_PATIENTS -> getString(
                        R.string.sync_activity_status_checking_for_new_patients
                    )
                    SyncWorker.State.UPLOADING_PATIENTS -> {
                        getString(R.string.sync_activity_status_uploading_patients)
                    }
                    SyncWorker.State.DOWNLOADING_PATIENTS -> getString(
                        R.string.sync_activity_status_downloading_patients
                    )
                    SyncWorker.State.CHECKING_SERVER_READINGS -> getString(
                        R.string.sync_activity_status_checking_for_new_readings_referrals_and_assessments
                    )
                    SyncWorker.State.UPLOADING_READINGS -> getString(
                        R.string.sync_activity_status_uploading_readings_referrals
                    )
                    SyncWorker.State.DOWNLOADING_READINGS -> getString(
                        R.string.sync_activity_status_downloading_readings_referrals_and_assessments
                    )
                }
                if (syncStatusText.text != newStateString) {
                    syncStatusText.text = newStateString
                }
            }
        }
    }

    private fun showLastSyncStatus(workInfo: WorkInfo?) {
        val syncProgressBar = findViewById<ProgressBar>(R.id.sync_progress_bar)
        val downloadProgressText = findViewById<TextView>(R.id.download_progress_text_view)
        val syncStatusText = findViewById<TextView>(R.id.sync_status_text)
        val lastSyncStatusText = findViewById<TextView>(R.id.latest_sync_status_text_view)

        syncProgressBar.visibility = View.INVISIBLE
        downloadProgressText.visibility = View.INVISIBLE

        val lastSyncTime = BigInteger(
            sharedPreferences.getString(
                SyncWorker.LAST_PATIENT_SYNC,
                SyncWorker.LAST_SYNC_DEFAULT.toString()
            )!!
        )
        val date = if (lastSyncTime.toString() == SyncWorker.LAST_SYNC_DEFAULT) {
            getString(R.string.sync_activity_date_never)
        } else {
            DateUtil.getConciseDateString(lastSyncTime, false)
        }

        syncStatusText.text = getString(
            R.string.sync_activity_waiting_to_sync_last_synced__s,
            date
        )

        workInfo?.let {
            lastSyncStatusText.apply {
                text = SyncWorker.getSyncResultMessage(it)
                visibility = View.VISIBLE
            }
        }*/
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    companion object {
        private const val TAG = "EditPatientInfoActivity"
        private const val EXTRA_PATIENT_ID = "patientId"

        fun makeIntentWithPatientId(context: Context, patientId: String): Intent {
            val intent = Intent(context, EditPatientInfoActivity::class.java)
            intent.putExtra(EXTRA_PATIENT_ID, patientId)
            return intent
        }

    }
}
