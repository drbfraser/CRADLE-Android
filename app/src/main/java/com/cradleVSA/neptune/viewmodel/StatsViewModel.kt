package com.cradleVSA.neptune.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Statistics
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.utilities.UnixTimestamp
import com.cradleVSA.neptune.view.StatisticsFilterOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val restApi: RestApi,
    private val sharedPreferences: SharedPreferences,
    private val healthFacilityManager: HealthFacilityManager
) : ViewModel() {
    private var savedStatsData: NetworkResult<Statistics>? = null
    var savedEndTime: BigInteger = UnixTimestamp.now
        private set
    var savedStartTime: BigInteger = savedEndTime - TimeUnit.DAYS.toSeconds(DEFAULT_NUM_DAYS).toBigInteger()
        private set
    var savedHealthFacility: HealthFacility? = null
        private set
    var savedFilterOption: StatisticsFilterOptions = StatisticsFilterOptions.JUSTME
        private set
    val healthFacilityArray: List<HealthFacility> by lazy {
        runBlocking {
            healthFacilityManager.getAllSelectedByUser()
        }
    }

    suspend fun getStatsData(
        filterOption: StatisticsFilterOptions,
        newFacility: HealthFacility?,
        startTime: BigInteger,
        endTime: BigInteger
    ): NetworkResult<Statistics>? {
        if ((filterOption == savedFilterOption) && (startTime == savedStartTime) &&
            (endTime == savedEndTime)
        ) {
            savedStatsData?.let {
                if (savedFilterOption == StatisticsFilterOptions.BYFACILITY) {
                    if (newFacility?.name == savedHealthFacility?.name) {
                        return it
                    }
                } else {
                    return it
                }
            }
        }
        when (filterOption) {
            StatisticsFilterOptions.ALL -> {
                // Get all stats:
                savedStatsData = restApi.getAllStatisticsBetween(startTime, endTime)
            }
            StatisticsFilterOptions.JUSTME -> {
                // Get stats for the current user ID:
                // TODO: Determine a sane failure value for USER_ID_KEY
                savedStatsData = restApi.getStatisticsForUserBetween(
                    startTime,
                    endTime,
                    sharedPreferences.getInt(
                        LoginManager.USER_ID_KEY,
                        -1
                    )
                )
            }
            StatisticsFilterOptions.BYFACILITY -> {
                // Get stats for the current Facility:
                savedStatsData = newFacility?.let {
                    restApi.getStatisticsForFacilityBetween(startTime, endTime, it)
                }
                // If savedFacility is null, we return a null statsData value
                // Which will cause the UI to display an error.
                // UI code should not allow choosing filter-by-facility without a valid health
                // facility chosen though, but it's good to handle these cases.
                savedHealthFacility = newFacility
            }
        }
        savedStartTime = startTime
        savedEndTime = endTime
        savedFilterOption = filterOption
        return savedStatsData
    }

    companion object {
        const val DEFAULT_NUM_DAYS = 30L
    }
}
