package com.cradleplatform.neptune.activities.statistics

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Statistics
import com.cradleplatform.neptune.model.UserRole
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import com.cradleplatform.neptune.utilities.BarGraphValueFormatter
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.viewmodel.statistics.StatsViewModel
import com.cradleplatform.neptune.sync.SyncReminderHelper
import com.cradleplatform.neptune.sync.views.SyncActivity
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.utilities.connectivity.api24.displayConnectivityToast
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Suppress("LargeClass")
@AndroidEntryPoint
class StatsActivity : AppCompatActivity() {
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var healthFacilityManager: HealthFacilityManager
    @Inject
    lateinit var networkStateManager: NetworkStateManager
    private val viewModel: StatsViewModel by viewModels()

    private lateinit var headerTextPrefix: String
    private lateinit var headerText: String
    private var menu: Menu? = null
    private var toolbar: Toolbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = getString(R.string.dashboard_statistics)
        }

        toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.activity_stats_title)
        }

        headerTextPrefix = getString(R.string.stats_activity_month_string)
        // There will be a redundant setting of text once on create,
        // but otherwise the activity will display a string with $1%s in
        // it while waiting for the data to come in.
        val statsHeaderTv = findViewById<TextView>(R.id.textView32)
        statsHeaderTv.text = getString(R.string.stats_activity_I_made_header, headerTextPrefix)
        updateUi(
            viewModel.savedFilterOption,
            viewModel.savedHealthFacility,
            viewModel.savedStartTime,
            viewModel.savedEndTime
        )
    }

    override fun onRestart() {
        super.onRestart()
        // restart the activity in case data is changed after syncing
        finish()
        // smooth the animation of activity recreation
        overridePendingTransition(0, 0)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun updateUi(
        filterOption: StatisticsFilterOptions,
        newFacility: HealthFacility?,
        startTime: BigInteger,
        endTime: BigInteger
    ) {
        lifecycleScope.launch {
            viewModel.getStatsData(filterOption, newFacility, startTime, endTime).let {
                if (it is NetworkResult.Success) {
                    setupBasicStats(it.value)
                    setupBarChart(it.value)
                } else {
                    finish()
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.stats_activity_api_call_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            val statsHeaderTv = findViewById<TextView>(R.id.textView32)
            statsHeaderTv.text = when (filterOption) {
                StatisticsFilterOptions.JUSTME -> {
                    getString(R.string.stats_activity_I_made_header, headerTextPrefix)
                }
                StatisticsFilterOptions.BYFACILITY -> {
                    getString(
                        R.string.stats_activity_facility_header,
                        headerTextPrefix,
                        viewModel.savedHealthFacility?.name
                    )
                }
                StatisticsFilterOptions.ALL -> {
                    getString(R.string.stats_activity_all_header, headerTextPrefix)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // For filtering by month, etc.
    @com.google.android.material.badge.ExperimentalBadgeUtils
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_stats, menu)
        this.menu = menu
        checkLastSyncTimeAndUpdateSyncIcon()
        return true
    }

    @com.google.android.material.badge.ExperimentalBadgeUtils
    private fun checkLastSyncTimeAndUpdateSyncIcon() {
        val lastSyncTime = BigInteger(
            sharedPreferences.getString(
                SyncAllWorker.LAST_PATIENT_SYNC,
                SyncAllWorker.LAST_SYNC_DEFAULT.toString()
            )!!
        )

        val menuItem: MenuItem = menu!!.findItem(R.id.syncPatients)
        val badge = BadgeDrawable.create(this)

        if (!SyncReminderHelper.checkIfOverTime(this, sharedPreferences)) {
            toolbar?.let {
                BadgeUtils.detachBadgeDrawable(
                    badge,
                    it, menuItem.itemId
                )
            }
        } else {
            toolbar?.let {
                BadgeUtils.attachBadgeDrawable(
                    badge,
                    it, menuItem.itemId
                )
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.stats_time_picker -> {
                val rangePicker = MaterialDatePicker.Builder.dateRangePicker().setSelection(
                    Pair(
                        viewModel.savedStartTime.toLong() * SEC_TO_MSEC,
                        viewModel.savedEndTime.toLong() * SEC_TO_MSEC
                    )
                ).build()
                rangePicker.addOnPositiveButtonClickListener { startEndPair ->
                    // rangePicker returns values in msec... and the API expects values in seconds.
                    // We must convert incoming msec Longs to seconds BigIntegers.
                    startEndPair?.let { startEndPairNotNull ->
                        val startDate =
                            TimeUnit.MILLISECONDS.toSeconds(startEndPairNotNull.first!!).toBigInteger()
                        val endDate =
                            TimeUnit.MILLISECONDS.toSeconds(startEndPairNotNull.second!!).toBigInteger()
                        headerTextPrefix = getString(
                            R.string.stats_activity_epoch_string,
                            DateUtil.getDateStringFromUTCTimestamp(startDate.toLong()),
                            DateUtil.getDateStringFromUTCTimestamp(endDate.toLong())
                        )
                        updateUi(viewModel.savedFilterOption, viewModel.savedHealthFacility, startDate, endDate)
                    }
                }
                rangePicker.show(supportFragmentManager, rangePicker.toString())
                return true
            }
            R.id.stats_filters -> {
                setupFilterDialog()
                return true
            }
            R.id.syncPatients -> {
                displayConnectivityToast(this, networkStateManager) {
                    startActivity(Intent(this, SyncActivity::class.java))
                }
                return true
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
    }

    private fun setupFilterDialog() {
        val builder = MaterialAlertDialogBuilder(this@StatsActivity)
        builder.setTitle(getString(R.string.stats_activity_filter_header))

        val dialogView = layoutInflater.inflate(R.layout.dialog_stats_filter_picker, null)
        builder.setView(dialogView)

        val healthFacilityPicker = dialogView.findViewById<AutoCompleteTextView>(
            R.id.health_facility_auto_complete_text
        )
        var healthFacilityPickerHasSelection = false
        val healthFacilityLayout = dialogView.findViewById<TextInputLayout>(R.id.health_facility_input_layout)
        val healthTextView = dialogView.findViewById<TextView>(R.id.filterPickerTextView)

        var tmpCheckedItem = viewModel.savedFilterOption
        var tmpHealthFacility: HealthFacility? = viewModel.savedHealthFacility

        // Ignore any changes on "cancel"
        builder.setNegativeButton(getString(android.R.string.cancel), null)
        builder.setPositiveButton(getString(android.R.string.ok)) { _, _ ->
            // OK was clicked, save the choice
            // (and reload only if we are saving a new option):

            if (tmpCheckedItem != viewModel.savedFilterOption) {
                if (tmpCheckedItem == StatisticsFilterOptions.BYFACILITY) {
                    updateUi(tmpCheckedItem, tmpHealthFacility, viewModel.savedStartTime, viewModel.savedEndTime)
                } else {
                    updateUi(
                        tmpCheckedItem,
                        viewModel.savedHealthFacility,
                        viewModel.savedStartTime,
                        viewModel.savedEndTime
                    )
                }
            } else if (tmpCheckedItem == StatisticsFilterOptions.BYFACILITY) {
                if (tmpHealthFacility?.name != viewModel.savedHealthFacility?.name) {
                    // If user has selected a different health facility after previously
                    // viewing another health facility:
                    updateUi(tmpCheckedItem, tmpHealthFacility, viewModel.savedStartTime, viewModel.savedEndTime)
                }
            }
        }

        val dialog = builder.create()

        val allStatsButton = dialogView.findViewById<RadioButton>(R.id.statFilterDialog_showAllButton)
        val facilityButton = dialogView.findViewById<RadioButton>(R.id.statFilterDialog_healthFacilityButton)

        // Button enable/disable based on role:
        val roleString = sharedPreferences.getString(getString(R.string.key_role), UserRole.VHT.toString())
        roleString?.let {
            when (UserRole.safeValueOf(it)) {
                UserRole.VHT -> {
                    allStatsButton.isEnabled = false
                    facilityButton.isEnabled = false
                }
                UserRole.CHO -> {
                    allStatsButton.isEnabled = false
                }
                UserRole.HCW -> {
                    allStatsButton.isEnabled = false
                }
                UserRole.UNKNOWN -> {
                    healthTextView.text = getString(R.string.stats_activity_unknown_role)
                    healthTextView.setTextColor(Color.RED)
                    healthTextView.visibility = View.VISIBLE
                }
                else -> {
                    // Leave them all open for ADMIN.
                }
            }
        }

        healthFacilityLayout.visibility = View.GONE
        healthTextView.visibility = View.GONE

        val facilityStringArray = viewModel.healthFacilityArray.map { it.name }.toTypedArray()
        healthFacilityPicker.setAdapter(
            ArrayAdapter(
                this@StatsActivity,
                R.layout.support_simple_spinner_dropdown_item,
                facilityStringArray
            )
        )
        healthFacilityPicker.setOnItemClickListener { _, _: View, position: Int, _: Long ->
            tmpHealthFacility = viewModel.healthFacilityArray[position]
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
            healthFacilityPickerHasSelection = true
        }

        // If we have already set a health facility previously, set it as selected
        // if it is in the list of selected health facilities:
        viewModel.savedHealthFacility?.let {
            // False here means do not filter other values in the dropdown
            // based on what we setText to...
            healthFacilityPicker.setText(it.name, false)
            healthFacilityPickerHasSelection = true
            // tmpHealthFacility is already set to viewModel.savedHealthFacility
            // so pressing "OK" essentially has no effect.
        }

        val buttonGroup = dialogView.findViewById<RadioGroup>(R.id.statFilterDialog_radioGroup)
        when (viewModel.savedFilterOption) {
            StatisticsFilterOptions.ALL -> buttonGroup.check(R.id.statFilterDialog_showAllButton)
            StatisticsFilterOptions.JUSTME -> buttonGroup.check(R.id.statFilterDialog_userIDButton)
            StatisticsFilterOptions.BYFACILITY -> {
                buttonGroup.check(R.id.statFilterDialog_healthFacilityButton)
                healthFacilityLayout.visibility = View.VISIBLE
                healthTextView.visibility = View.VISIBLE
            }
        }

        buttonGroup.setOnCheckedChangeListener { radioGroup: RadioGroup, _: Int ->
            when (radioGroup.checkedRadioButtonId) {
                R.id.statFilterDialog_healthFacilityButton -> {
                    if (!healthFacilityPickerHasSelection) {
                        // Disable positive button
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
                    }
                    healthFacilityLayout.visibility = View.VISIBLE
                    healthTextView.visibility = View.VISIBLE
                    tmpCheckedItem = StatisticsFilterOptions.BYFACILITY
                }
                R.id.statFilterDialog_showAllButton -> {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    healthFacilityLayout.visibility = View.GONE
                    healthTextView.visibility = View.GONE
                    tmpCheckedItem = StatisticsFilterOptions.ALL
                }
                R.id.statFilterDialog_userIDButton -> {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    healthFacilityLayout.visibility = View.GONE
                    healthTextView.visibility = View.GONE
                    tmpCheckedItem = StatisticsFilterOptions.JUSTME
                }
            }
        }

        dialog.show()
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

    companion object {
        private const val SEC_TO_MSEC = 1000
    }
}

enum class StatisticsFilterOptions {
    ALL,
    JUSTME,
    BYFACILITY
}
