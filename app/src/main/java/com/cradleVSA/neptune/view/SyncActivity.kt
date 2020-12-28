package com.cradleVSA.neptune.view

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.databinding.ActivitySyncBinding
import com.cradleVSA.neptune.sync.SyncWorker
import com.cradleVSA.neptune.utilitiles.DateUtil
import com.cradleVSA.neptune.viewmodel.SyncViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * TODO: redesign
 */
@AndroidEntryPoint
class SyncActivity : AppCompatActivity() {
    private val viewModel: SyncViewModel by viewModels()

    private var binding: ActivitySyncBinding? = null

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onStop() {
        super.onStop()
        viewModel.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sync)
        binding?.apply {
            viewModel = this@SyncActivity.viewModel
            lifecycleOwner = this@SyncActivity
            executePendingBindings()
        }

        setSupportActionBar(findViewById(R.id.toolbar4))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.sync_activity_title)
        }

        val syncButton = findViewById<Button>(R.id.sync_button)
        syncButton.setOnClickListener {
            syncButton.isEnabled = false
            viewModel.startSyncing()
        }

        viewModel.syncStatus.observe(this) { workInfo ->
            workInfo ?: return@observe
            val syncStatusText = findViewById<TextView>(R.id.sync_status_text)
            val syncProgressBar = findViewById<ProgressBar>(R.id.sync_progress_bar)
            val downloadProgressText = findViewById<TextView>(R.id.download_progress_text_view)
            val lastSyncStatusText = findViewById<TextView>(R.id.latest_sync_status_text_view)

            if (workInfo.state.isFinished) {
                syncProgressBar.visibility = View.INVISIBLE
                downloadProgressText.visibility = View.INVISIBLE

                val lastSyncTime = sharedPreferences.getLong(SyncWorker.LAST_PATIENT_SYNC, -1L)
                val date = if (lastSyncTime == -1L) {
                    getString(R.string.sync_activity_date_never)
                } else {
                    DateUtil.getConciseDateString(lastSyncTime, false)
                }

                syncStatusText.text = getString(
                    R.string.sync_activity_waiting_to_sync_last_synced__s,
                    date
                )

                lastSyncStatusText.apply {
                    text = SyncWorker.getSyncResultMessage(workInfo)
                    visibility = View.VISIBLE
                }
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    companion object {
        private const val TAG = "SyncActivity"
    }
}
