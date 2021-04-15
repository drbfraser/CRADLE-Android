package com.cradleVSA.neptune.view

import android.content.DialogInterface
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
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.manager.ReadingManager
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Statistics
import com.cradleVSA.neptune.model.UserRole
import com.cradleVSA.neptune.net.Success
import com.cradleVSA.neptune.utilitiles.BarGraphValueFormatter
import com.cradleVSA.neptune.viewmodel.StatsViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.util.ArrayList
import java.util.Calendar.MILLISECOND
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.floor
import kotlin.time.milliseconds

@Suppress("LargeClass")
@AndroidEntryPoint
class StatsActivity : AppCompatActivity() {
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var healthFacilityManager: HealthFacilityManager
    private val viewModel: StatsViewModel by viewModels()

    lateinit var headerTextPrefix: String
    lateinit var headerText: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = getString(R.string.dashboard_statistics)
        }

        headerTextPrefix = getString(com.cradleVSA.neptune.R.string.stats_activity_month_string)
        headerText = getString(com.cradleVSA.neptune.R.string.stats_activity_I_made_header, headerTextPrefix)
        updateUi()
    }

     fun updateUi() {
        lifecycleScope.launch {
            val statsHeaderTv = findViewById<TextView>(R.id.textView32)
            statsHeaderTv.text = when (viewModel.currentFilterOption) {
                StatisticsFilterOptions.JUSTME -> {
                    getString(R.string.stats_activity_I_made_header, headerTextPrefix)
                }
                StatisticsFilterOptions.BYFACILITY -> {
                    getString(
                        R.string.stats_activity_facility_header,
                        headerTextPrefix,
                        viewModel.currentHealthFacility?.name
                    )
                }
                StatisticsFilterOptions.ALL -> {
                    getString(R.string.stats_activity_all_header, headerTextPrefix)
                }
            }
            viewModel.getStatsData()?.let {
                if (it is Success) {
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
            } ?: run {
                // Getting stats data from the
                // viewModel returned null, somehow.
                // Display the same error as above to the user.
                finish()
                Toast.makeText(
                    applicationContext,
                    getString(R.string.stats_activity_api_call_failed),
                    Toast.LENGTH_LONG
                ).show()
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
                    // Use the viewModel function that expects that behavior.
                    viewModel.setStartEndTimesMsec(it.first, it.second)
                    headerTextPrefix = getString(
                        R.string.stats_activity_epoch_string,
                        TimeUnit.SECONDS.toDays((viewModel.endTime.subtract(viewModel.startTime)).toLong())
                    )
                    updateUi()
                }
                rangePicker.show(supportFragmentManager, rangePicker.toString())
                return true
            }
            R.id.stats_filters -> {
                setupFilterDialog()
                return true
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
    }

    private fun setupFilterDialog() {
        val builder = AlertDialog.Builder(this@StatsActivity)
        builder.setTitle(getString(R.string.stats_activity_filter_header))

        val dialogView = layoutInflater.inflate(R.layout.dialog_stats_filter_picker, null)
        builder.setView(dialogView)

        val healthFacilityPicker = dialogView.findViewById<AutoCompleteTextView>(
            R.id.health_facility_auto_complete_text
        )
        val healthFacilityLayout = dialogView.findViewById<TextInputLayout>(R.id.health_facility_input_layout)
        val healthTextView = dialogView.findViewById<TextView>(R.id.filterPickerTextView)

        var tmpCheckedItem = viewModel.currentFilterOption
        var tmpHealthFacility: HealthFacility? = viewModel.currentHealthFacility

        // Ignore any changes on "cancel"
        builder.setNegativeButton(getString(android.R.string.cancel), null)
        builder.setPositiveButton(getString(android.R.string.ok)) { _, _ ->
            // OK was clicked, save the choice
            // (and reload only if we are saving a new option):

            if (tmpCheckedItem != viewModel.currentFilterOption) {
                if (tmpCheckedItem == StatisticsFilterOptions.BYFACILITY) {
                    viewModel.currentHealthFacility = tmpHealthFacility
                }
                viewModel.currentFilterOption = tmpCheckedItem
                updateUi()
            } else if (tmpCheckedItem == StatisticsFilterOptions.BYFACILITY) {
                if (tmpHealthFacility?.name != viewModel.currentHealthFacility?.name) {
                    // If user has selected a different health facility after previously
                    // viewing another health facility:
                    viewModel.currentHealthFacility = tmpHealthFacility
                    updateUi()
                }
            }
        }

        val dialog = builder.create()

        val statsForMeButton = dialogView.findViewById<RadioButton>(R.id.statFilterDialog_userIDButton)
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
        val healthFacilityArray: List<HealthFacility>
        runBlocking {
            healthFacilityArray = healthFacilityManager.getAllSelectedByUser()

            val facilityStringArray = healthFacilityArray.map { it.name }.toTypedArray()
            healthFacilityPicker.setAdapter(
                ArrayAdapter<String>(
                    this@StatsActivity,
                    R.layout.support_simple_spinner_dropdown_item,
                    facilityStringArray
                )
            )
            healthFacilityPicker.setOnItemClickListener { _, _: View, position: Int, _: Long ->
                tmpHealthFacility = healthFacilityArray[position]
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
            }

            // If we have already set a health facility previously, set it as selected
            // if it is in the list of selected health facilities:
            viewModel.currentHealthFacility?.let{
                // False here means do not filter other values in the dropdown
                // based on what we setText to...
                healthFacilityPicker.setText(it.name, false)
                // tmpHealthFacility is already set to viewModel.currentHealthFacility
                // so pressing "OK" essentially has no effect.
            }
        }

        val buttonGroup = dialogView.findViewById<RadioGroup>(R.id.statFilterDialog_radioGroup)
        when (viewModel.currentFilterOption) {
            StatisticsFilterOptions.ALL -> buttonGroup.check(R.id.statFilterDialog_showAllButton)
            StatisticsFilterOptions.JUSTME -> buttonGroup.check(R.id.statFilterDialog_userIDButton)
            StatisticsFilterOptions.BYFACILITY -> {
                buttonGroup.check(R.id.statFilterDialog_healthFacilityButton)
                healthFacilityLayout.visibility = View.VISIBLE
                healthTextView.visibility = View.VISIBLE
            }
        }

        buttonGroup.setOnCheckedChangeListener { radioGroup: RadioGroup, checkedID: Int ->
            when (radioGroup.checkedRadioButtonId) {
                R.id.statFilterDialog_healthFacilityButton -> {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
                    healthFacilityLayout.visibility = View.VISIBLE
                    healthTextView.visibility = View.VISIBLE
                    tmpCheckedItem = StatisticsFilterOptions.BYFACILITY
                }
                R.id.statFilterDialog_showAllButton -> {
                    healthFacilityLayout.visibility = View.GONE
                    healthTextView.visibility = View.GONE
                    tmpCheckedItem = StatisticsFilterOptions.ALL
                }
                R.id.statFilterDialog_userIDButton -> {
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
}

enum class StatisticsFilterOptions {
    ALL,
    JUSTME,
    BYFACILITY
}
