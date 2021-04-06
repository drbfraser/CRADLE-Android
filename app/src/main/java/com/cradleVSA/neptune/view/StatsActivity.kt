package com.cradleVSA.neptune.view

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.manager.ReadingManager
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.Statistics
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.utilitiles.BarGraphValueFormatter
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.ArrayList
import javax.inject.Inject
import kotlin.math.floor

@Suppress("LargeClass")
@AndroidEntryPoint
class StatsActivity : AppCompatActivity() {
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    lateinit var readings: List<Reading>
    lateinit var statsData: NetworkResult<Statistics>
    @Inject
    lateinit var restApi: RestApi
    @Inject
    lateinit var healthFacilityManager: HealthFacilityManager

    @Suppress("MagicNumber")
    val msecInSec = 1000L // This is the conversion value.

    val filterOptionsShowAll = 0
    val filterOptionsFilterUser = 1
    val filterOptionsFilterByFacility = 2
    var filterOptionsCheckedItem = filterOptionsShowAll // Persistent (within activity) choice of filter option.
    var filterOptionsSavedFacility: HealthFacility? = null

    // TODO: discuss what the initial values of the date range should be.
    // These currently correspond to right now in MS, and current time minus 30 days in MS
    @Suppress("MagicNumber")
    var endTimeEpoch: Long = System.currentTimeMillis() / msecInSec
    @Suppress("MagicNumber")
    var startTimeEpoch: Long = endTimeEpoch - 2592000L // 30 days in seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = getString(R.string.dashboard_statistics)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            var statsData: NetworkResult<Statistics>? = null
            when (filterOptionsCheckedItem) {
                filterOptionsShowAll -> {
                    // Get all stats:
                    statsData = restApi.getAllStatisticsBetween(startTimeEpoch, endTimeEpoch)
                }
                filterOptionsFilterUser -> {
                    // Get stats for the current user ID:
                    // TODO: Determine a sane failure value for USER_ID_KEY
                    statsData = restApi.getStatisticsForUserBetween(
                        startTimeEpoch,
                        endTimeEpoch,
                        sharedPreferences.getInt(
                            LoginManager.USER_ID_KEY,
                            -1
                        )
                    )
                }
                filterOptionsFilterByFacility -> {
                    // Get stats for the currently saved Facility:
                    filterOptionsSavedFacility?.let {
                        statsData = restApi.getStatisticsForFacilityBetween(startTimeEpoch, endTimeEpoch, it)
                    }
                }
            }
            statsData?.let {
                if (!it.failed) {
                    setupBasicStats(it.unwrapped!!)
                    setupBarChart(it.unwrapped!!)
                } else {
                    finish()
                    Toast.makeText(applicationContext, getString(R.string.stats_activity_api_call_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // For filtering by month, etc.
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_stats, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.stats_time_picker -> {
                val rangePickerBuilder = MaterialDatePicker.Builder.dateRangePicker()
                val rangePicker = rangePickerBuilder.build()
                rangePicker.addOnPositiveButtonClickListener {
                    // rangePicker returns values in msec... and the API expects values in seconds.
                    it.first?.let {
                        startTimeEpoch = it / msecInSec
                    }
                    it.second?.let {
                        endTimeEpoch = it / msecInSec
                    }
                    val statsHeaderTv = findViewById<TextView>(R.id.textView32)
                    // TODO: change text in header to human-readable, this is just for testing now
                    statsHeaderTv.text = getString(
                        R.string.stats_activity_epoch_header,
                        @Suppress("MagicNumber")
                        floor(((endTimeEpoch - startTimeEpoch) / 86400).toDouble()).toInt()
                    )
                    onResume()
                }
                rangePicker.show(supportFragmentManager, rangePicker.toString())
                return true
            }
            R.id.stats_filters -> {
                val builder = AlertDialog.Builder(this@StatsActivity)
                builder.setTitle(getString(R.string.stats_activity_filter_header))

                // Use a radio button list for choosing the filtering method.
                val filterOptions = arrayOf(
                    getString(R.string.stats_activity_filter_showAll),
                    getString(R.string.stats_activity_filter_byUserID),
                    getString(R.string.stats_activity_filter_byFacilityID)
                )
                var tmpCheckedItem = 0
                builder.setSingleChoiceItems(
                    filterOptions,
                    filterOptionsCheckedItem
                ) { dialog, which ->
                    // For now, save the enum value - only change filter options
                    // (and thus force a new network request) when "OK" is chosen.
                    tmpCheckedItem = which
                    if (tmpCheckedItem == filterOptionsFilterByFacility) {
                        // Do another AlertDialog for picking the Facility:
                        setupFacilityDialog()
                    }
                }

                builder.setPositiveButton("OK") { dialog, which ->
                    // OK was clicked, save the choice
                    // (and reload only if we are saving a new option):
                    if (tmpCheckedItem != filterOptionsCheckedItem) {
                        filterOptionsCheckedItem = tmpCheckedItem
                        onResume()
                    }
                }
                // Ignore any changes on "cancel"
                builder.setNegativeButton("Cancel", null)

                val dialog = builder.create()
                dialog.show()
                return true
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
    }

    private fun setupFacilityDialog() {
        val builder = AlertDialog.Builder(this@StatsActivity)
        builder.setTitle(getString(R.string.stats_activity_pickFacility_header))

        // Use a radio button list for choosing the health facility.
        runBlocking {
            var facilityIDs: Array<String?>?
            var checkedItemIndex = 0
            var dialog: AlertDialog? = null

            val healthFacilities = healthFacilityManager.getAllSelectedByUser()
            val facilityNameArray = healthFacilities.map { it.name }.toTypedArray()

            builder.setSingleChoiceItems(facilityNameArray, checkedItemIndex) { dialog, which ->
                // For now, save the enum value - only change filter options
                // (and thus force a new network request) when "OK" is chosen.
                checkedItemIndex = which
            }

            builder.setPositiveButton("OK") { dialog, which ->
                // OK was clicked, save the facility:
                filterOptionsSavedFacility = healthFacilities?.get(checkedItemIndex)
            }

            // Ignore any changes on "cancel"
            builder.setNegativeButton("Cancel", null)
        }
        builder.create().show()
    }

    private fun setupBasicStats(statsData: Statistics) {
        val emptyView = findViewById<TextView>(R.id.emptyView)
        emptyView.visibility = View.GONE
        val totalReadingTV = findViewById<TextView>(R.id.totalReadingTvStats)
        val uniqueReadingTV = findViewById<TextView>(R.id.uniqueReadingTvStats)
        val referralsSentTV = findViewById<TextView>(R.id.refTvStats)
        val referralsAssessedTV = findViewById<TextView>(R.id.assessmentTvStats)
        val daysWithReadingsTV = findViewById<TextView>(R.id.daysWithReadingsTvStats)

        totalReadingTV.text = statsData.totalReadings.toString()
        uniqueReadingTV.text = statsData.uniquePatientReadings.toString()
        referralsSentTV.text = statsData.sentReferrals.toString()
        referralsAssessedTV.text = statsData.patientsReferred.toString()
        daysWithReadingsTV.text = statsData.daysWithReadings.toString()
    }

    @Suppress("MagicNumber")
    private fun setupBarChart(statsData: Statistics) {
        val barChart = findViewById<BarChart>(R.id.bargraph)
        val barCard = findViewById<CardView>(R.id.bargraphCard)
        barCard.visibility = View.VISIBLE
        val greenEntry: ArrayList<BarEntry> = ArrayList()
        val yellowUpEntry: ArrayList<BarEntry> =
            ArrayList()
        val yellowDownEntry: ArrayList<BarEntry> =
            ArrayList()
        val redUpEntry: ArrayList<BarEntry> = ArrayList()
        val redDownEntry: ArrayList<BarEntry> = ArrayList()
        val green = statsData.colorReadings.greenReadings
        val yellowup = statsData.colorReadings.yellowUpReadings
        val yellowDown = statsData.colorReadings.yellowDownReadings
        val redDown = statsData.colorReadings.redDownReadings
        val redUp = statsData.colorReadings.redUpReadings

        greenEntry.add(BarEntry(1.toFloat(), green.toFloat()))
        yellowUpEntry.add(BarEntry(2.toFloat(), yellowup.toFloat()))
        yellowDownEntry.add(BarEntry(3.toFloat(), yellowDown.toFloat()))
        redUpEntry.add(BarEntry(4.toFloat(), redUp.toFloat()))
        redDownEntry.add(BarEntry(5.toFloat(), redDown.toFloat()))

        val greenDataSet = BarDataSet(greenEntry, "GREEN")
        val yellowUpDataSet = BarDataSet(yellowUpEntry, "YELLOW UP")
        val yellowDownDataSet = BarDataSet(yellowDownEntry, "YELLOW DOWN")
        val redUpDataSet = BarDataSet(redUpEntry, "RED UP")
        val redDownDataSet = BarDataSet(redDownEntry, "RED DOWN")
        greenDataSet.valueFormatter = BarGraphValueFormatter("Green")
        yellowDownDataSet.valueFormatter = BarGraphValueFormatter("Yellow Down")
        yellowUpDataSet.valueFormatter = BarGraphValueFormatter("Yellow Up")
        redDownDataSet.valueFormatter = BarGraphValueFormatter("Red Down")
        redUpDataSet.valueFormatter = BarGraphValueFormatter("Red Up")
        greenDataSet.color = Color.GREEN
        yellowUpDataSet.color = Color.YELLOW
        yellowDownDataSet.color = resources.getColor(R.color.yellowDown)
        redDownDataSet.color = resources.getColor(R.color.redDown)
        redUpDataSet.color = Color.RED
        barChart.setDrawBorders(false)
        barChart.setDrawGridBackground(false)
        barChart.axisRight.setDrawLabels(false)
        barChart.xAxis.setDrawAxisLine(false)
        barChart.axisRight.setDrawGridLines(false)
        barChart.xAxis.setDrawGridLines(false)
        barChart.axisLeft.setDrawGridLines(false)
        barChart.xAxis.setDrawLabels(false)
        barChart.axisRight.axisMinimum = 0f
        barChart.axisLeft.axisMinimum = 0f
        val lineData =
            BarData(greenDataSet, yellowUpDataSet, yellowDownDataSet, redUpDataSet, redDownDataSet)
        barChart.description.text = ""
        barChart.data = lineData
        barChart.legend.isEnabled = false
        barChart.isHighlightPerTapEnabled = false
        barChart.invalidate()
    }
}
