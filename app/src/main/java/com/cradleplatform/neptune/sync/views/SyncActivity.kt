package com.cradleplatform.neptune.sync.views

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.work.WorkInfo
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.ActivitySyncBinding
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.sync.views.viewmodels.SyncViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigInteger
import javax.inject.Inject

@AndroidEntryPoint
class SyncActivity : AppCompatActivity() {
    private val viewModel: SyncViewModel by viewModels()

    private var binding: ActivitySyncBinding? = null

    @Inject
    lateinit var sharedPreferences: SharedPreferences

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

        showLastSyncStatus(null)
        viewModel.syncStatus.observe(this) { workInfo ->
            val syncStatusText = findViewById<TextView>(R.id.sync_status_text)
            val syncProgressBar = findViewById<ProgressBar>(R.id.sync_progress_bar)
            val downloadProgressText = findViewById<TextView>(R.id.download_progress_text_view)
            val lastSyncResultText = findViewById<TextView>(R.id.last_sync_result_text_view)

            if (workInfo == null
                || workInfo.state.isFinished
                || workInfo.state == WorkInfo.State.ENQUEUED) {
                showLastSyncStatus(workInfo)
            } else {
                lastSyncResultText.apply {
                    text = ""
                    visibility = View.INVISIBLE
                }
                val state = SyncAllWorker.getState(workInfo)
                val progressPair = SyncAllWorker.getProgress(workInfo)
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
                    SyncAllWorker.State.AFK -> {
                        getString(
                            R.string.sync_activity_waiting_to_sync_last_synced__s,
                            getLastSyncTimeStamp()
                        )
                    }
                    SyncAllWorker.State.STARTING -> {
                        getString(R.string.sync_activity_status_beginning_upload)
                    }
                    SyncAllWorker.State.CHECKING_SERVER_PATIENTS -> getString(
                        R.string.sync_activity_status_checking_for_new_patients
                    )
                    SyncAllWorker.State.UPLOADING_PATIENTS -> {
                        getString(R.string.sync_activity_status_uploading_patients)
                    }
                    SyncAllWorker.State.DOWNLOADING_PATIENTS -> getString(
                        R.string.sync_activity_status_downloading_patients
                    )
                    SyncAllWorker.State.CHECKING_SERVER_READINGS -> getString(
                        R.string.sync_activity_status_checking_for_new_readings_referrals_and_assessments
                    )
                    SyncAllWorker.State.UPLOADING_READINGS -> getString(
                        R.string.sync_activity_status_uploading_readings_referrals
                    )
                    SyncAllWorker.State.DOWNLOADING_READINGS -> getString(
                        R.string.sync_activity_status_downloading_readings_referrals_and_assessments
                    )

                    SyncAllWorker.State.CHECKING_SERVER_REFERRALS -> getString(
                        R.string.sync_activity_status_checking_for_new_referrals
                    )
                    SyncAllWorker.State.UPLOADING_REFERRALS -> getString(
                        R.string.sync_activity_status_uploading_referrals
                    )
                    SyncAllWorker.State.DOWNLOADING_REFERRALS -> getString(
                        R.string.sync_activity_status_downloading_referrals
                    )

                    SyncAllWorker.State.CHECKING_SERVER_ASSESSMENTS -> getString(
                        R.string.sync_activity_status_checking_for_new_assessments
                    )
                    SyncAllWorker.State.UPLOADING_ASSESSMENTS -> getString(
                        R.string.sync_activity_status_uploading_assessments
                    )
                    SyncAllWorker.State.DOWNLOADING_ASSESSMENTS -> getString(
                        R.string.sync_activity_status_downloading_assessments
                    )
                    SyncAllWorker.State.DOWNLOADING_HEALTH_FACILITIES -> getString(
                        R.string.sync_activity_status_downloading_health_facilities
                    )
                    SyncAllWorker.State.DOWNLOADING_FORM_TEMPLATES -> getString(
                        R.string.sync_activitiy_status_downloading_form_templates
                    )
                }
                if (syncStatusText.text != newStateString) {
                    syncStatusText.text = newStateString
                }
            }
        }
    }

    private fun getLastSyncTimeStamp(): String {
        val lastSyncTime = BigInteger(
            sharedPreferences.getString(
                SyncAllWorker.LAST_PATIENT_SYNC,
                SyncAllWorker.LAST_SYNC_DEFAULT.toString()
            )!!
        )
        val date = if (lastSyncTime.toString() == SyncAllWorker.LAST_SYNC_DEFAULT) {
            getString(R.string.sync_activity_date_never)
        } else {
            DateUtil.getConciseDateString(lastSyncTime, false)
        }
        return date
    }

    private fun showLastSyncStatus(workInfo: WorkInfo?) {
        val syncProgressBar = findViewById<ProgressBar>(R.id.sync_progress_bar)
        val downloadProgressText = findViewById<TextView>(R.id.download_progress_text_view)
        val syncStatusText = findViewById<TextView>(R.id.sync_status_text)
        val lastSyncResultText = findViewById<TextView>(R.id.last_sync_result_text_view)

        syncProgressBar.visibility = View.INVISIBLE
        downloadProgressText.visibility = View.INVISIBLE
        syncStatusText.text = getString(
            R.string.sync_activity_waiting_to_sync_last_synced__s,
            getLastSyncTimeStamp()
        )

        workInfo?.let {
            lastSyncResultText.apply {
                text = SyncAllWorker.getSyncResultMessage(it)
                visibility = View.VISIBLE
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
