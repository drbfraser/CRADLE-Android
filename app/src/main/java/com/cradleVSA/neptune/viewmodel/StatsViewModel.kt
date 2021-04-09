package com.cradleVSA.neptune.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Statistics
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.view.statisticsFilterOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val restApi: RestApi
): ViewModel() {
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    private var statsData: NetworkResult<Statistics>? = null
    private var lastStartTime: Long = 0
    private var lastEndTime: Long = 0
    private var lastFilterOptions = statisticsFilterOptions.filterOptionsShowAll
    private var lastHealthFacility: HealthFacility? = null

    suspend fun getStatsData(filterOptions: statisticsFilterOptions, startTime: Long, endTime: Long,
        savedFacility: HealthFacility?): NetworkResult<Statistics>? {
        if ((filterOptions == lastFilterOptions)  && (startTime == lastStartTime)
            && (endTime == lastEndTime) && (statsData != null)) {
                if ((filterOptions == statisticsFilterOptions.filterOptionsFilterByFacility) && (lastHealthFacility?.name == savedFacility?.name)) {
                        return statsData
                }
        }
        when (filterOptions) {
            statisticsFilterOptions.filterOptionsShowAll -> {
                // Get all stats:
                statsData = restApi.getAllStatisticsBetween(startTime, endTime)
            }
            statisticsFilterOptions.filterOptionsFilterUser -> {
                // Get stats for the current user ID:
                // TODO: Determine a sane failure value for USER_ID_KEY
                statsData = restApi.getStatisticsForUserBetween(
                    startTime,
                    endTime,
                    sharedPreferences.getInt(
                        LoginManager.USER_ID_KEY,
                        -1
                    )
                )
            }
            statisticsFilterOptions.filterOptionsFilterByFacility -> {
                // Get stats for the currently saved Facility:
                statsData = null
                savedFacility?.let {
                    statsData = restApi.getStatisticsForFacilityBetween(startTime, endTime, it)
                }
                // If savedFacility is null, we return a null statsData value
                // Which will cause the UI to display an error.
                // UI code should not allow choosing filter-by-facility without a valid health
                // facility chosen though, but it's good to handle these cases.
                lastHealthFacility = savedFacility
            }
        }
        lastStartTime = startTime
        lastEndTime = endTime
        lastFilterOptions = filterOptions
        return statsData
    }
}