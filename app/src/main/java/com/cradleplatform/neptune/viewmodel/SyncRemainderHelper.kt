package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.content.SharedPreferences
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.sync.SyncWorker
import com.cradleplatform.neptune.utilities.DateUtil
import java.math.BigInteger

class SyncRemainderHelper {
    companion object {
        fun checkIfOverTime(context: Context, sharedPreferences: SharedPreferences): Boolean {
            val lastSyncTime = BigInteger(
                sharedPreferences.getString(
                    SyncWorker.LAST_PATIENT_SYNC,
                    SyncWorker.LAST_SYNC_DEFAULT.toString()
                )!!
            )

            return lastSyncTime.toString() == SyncWorker.LAST_SYNC_DEFAULT || DateUtil.isOverTime(
                lastSyncTime,
                context.resources.getInteger(R.integer.settings_default_sync_period_hours)
            )
        }
    }
}
