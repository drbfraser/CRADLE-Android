package com.cradleplatform.neptune.sync

import android.content.Context
import android.content.SharedPreferences
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import com.cradleplatform.neptune.utilities.DateUtil
import java.math.BigInteger

class SyncReminderHelper {
    companion object {
        fun checkIfOverTime(context: Context, sharedPreferences: SharedPreferences): Boolean {
            val lastSyncTime = BigInteger(
                sharedPreferences.getString(
                    SyncAllWorker.LAST_PATIENT_SYNC,
                    SyncAllWorker.LAST_SYNC_DEFAULT.toString()
                )!!
            )

            return lastSyncTime.toString() == SyncAllWorker.LAST_SYNC_DEFAULT || DateUtil.isOverTime(
                lastSyncTime,
                context.resources.getInteger(R.integer.settings_default_sync_period_hours)
            )
        }
    }
}
