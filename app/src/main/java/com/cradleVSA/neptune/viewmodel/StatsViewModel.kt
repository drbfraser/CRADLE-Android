package com.cradleVSA.neptune.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Statistics
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.utilitiles.UnixTimestamp
import com.cradleVSA.neptune.view.StatisticsFilterOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val restApi: RestApi,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {
    private var savedStatsData: NetworkResult<Statistics>? = null
    private var savedStartTime: BigInteger = BigInteger.valueOf(0)
    private var savedEndTime: BigInteger = BigInteger.valueOf(0)
    private var savedHealthFacility: HealthFacility? = null
    private var savedFilterOption: StatisticsFilterOptions = StatisticsFilterOptions.JUSTME
    var endTime: BigInteger = UnixTimestamp.now
    var startTime: BigInteger = endTime.subtract(
        BigInteger.valueOf(
            TimeUnit.DAYS.toSeconds(
                DEFAULT_NUM_DAYS
            )
        )
    )
    var currentFilterOption = StatisticsFilterOptions.JUSTME
    var currentHealthFacility: HealthFacility? = null

    // The activity's RangePicker returns values in msec... and the API expects values in seconds.
    // We must convert incoming msec Longs to seconds BigIntegers.
    fun setStartEndTimesMsec(newStartTime: Long?, newEndTime: Long?) {
        newStartTime?.let {
            startTime = BigInteger.valueOf(TimeUnit.MILLISECONDS.toSeconds(it))
        }
        newEndTime?.let {
            endTime = BigInteger.valueOf(TimeUnit.MILLISECONDS.toSeconds(it))
        }
    }

    suspend fun getStatsData(): NetworkResult<Statistics>? {
        if ((currentFilterOption == savedFilterOption) && (startTime == savedStartTime) &&
            (endTime == savedEndTime)
        ) {
            savedStatsData?.let {
                if (savedFilterOption == StatisticsFilterOptions.BYFACILITY) {
                    if (currentHealthFacility?.name == savedHealthFacility?.name) {
                        return it
                    }
                } else {
                    return it
                }
            }
        }
        when (currentFilterOption) {
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
                savedStatsData = null
                currentHealthFacility?.let {
                    savedStatsData = restApi.getStatisticsForFacilityBetween(startTime, endTime, it)
                }
                // If savedFacility is null, we return a null statsData value
                // Which will cause the UI to display an error.
                // UI code should not allow choosing filter-by-facility without a valid health
                // facility chosen though, but it's good to handle these cases.
                savedHealthFacility = currentHealthFacility
            }
        }
        savedStartTime = startTime
        savedEndTime = endTime
        savedFilterOption = currentFilterOption
        return savedStatsData
    }

    companion object {
        const val DEFAULT_NUM_DAYS = 30L
    }
}
