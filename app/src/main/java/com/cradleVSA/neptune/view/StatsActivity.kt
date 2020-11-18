package com.cradleVSA.neptune.view

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.manager.ReadingManager
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.ReadingAnalysis
import com.cradleVSA.neptune.utilitiles.BarGraphValueFormatter
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.Collections
import javax.inject.Inject

@Suppress("LargeClass")
@AndroidEntryPoint
class StatsActivity : AppCompatActivity() {
    @Inject
    lateinit var readingManager: ReadingManager
    lateinit var readings: List<Reading>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        lifecycleScope.launch {
            // TODO: Do this as a database query or a database view. Taking all the readings and
            //  them sorting them in memory is not efficient. Reading objects also contain
            //  information that is irrelevant for StatsActivity.
            readings = withContext(Dispatchers.IO) { readingManager.getAllReadings() }
            Collections.sort(readings, Reading.AscendingDataComparator)
            if (readings.isNotEmpty()) {
                setupBasicStats()
                setupLineChart()
                setupBarChart()
            }
        }
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = "Statistics"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupBasicStats() {
        val emptyView = findViewById<TextView>(R.id.emptyView)
        emptyView.visibility = View.GONE
        val totalReadings = readings.size
        var totalRef = 0
        for (i in 0 until totalReadings) {
            if (readings[i].isReferredToHealthFacility) {
                totalRef++
            }
        }
        val readingTV = findViewById<TextView>(R.id.readingTvStats)
        readingTV.text = totalReadings.toString()
        val refTV = findViewById<TextView>(R.id.refTvStats)
        refTV.text = totalRef.toString()
        // todo do the same for the referrals
    }

    @Suppress("MagicNumber")
    private fun setupBarChart() {
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
        var green = 0
        var yellowup = 0
        var yellowDown = 0
        var redDown = 0
        val redUp = 0
        for (i in readings.indices) {
            val analysis = readings[i].bloodPressure.analysis
            if (analysis === ReadingAnalysis.RED_DOWN) {
                redDown++
            } else if (analysis === ReadingAnalysis.GREEN) {
                green++
            } else if (analysis === ReadingAnalysis.RED_UP) {
                redDown++
            } else if (analysis === ReadingAnalysis.YELLOW_UP) {
                yellowup++
            } else if (analysis === ReadingAnalysis.YELLOW_DOWN) {
                yellowDown++
            }
        }
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

    private fun setupLineChart() {
        val lineChart = findViewById<LineChart>(R.id.lineChart)
        val linecard = findViewById<CardView>(R.id.linechartCard)
        linecard.visibility = View.VISIBLE
        val diastolicEntry: MutableList<Entry> =
            ArrayList()
        val systolicEntry: MutableList<Entry> =
            ArrayList()
        val heartrateEntry: MutableList<Entry> =
            ArrayList()
        // start at 0
        for (i in readings.indices) {
            val (_, _, _, bloodPressure) = readings[i]
            diastolicEntry.add(
                Entry(
                    (i + 1).toFloat(),
                    bloodPressure.diastolic.toFloat()
                )
            )
            systolicEntry.add(
                Entry(
                    (i + 1).toFloat(),
                    bloodPressure.systolic.toFloat()
                )
            )
            heartrateEntry.add(
                Entry(
                    (i + 1).toFloat(),
                    bloodPressure.heartRate.toFloat()
                )
            )
        }
        val diastolicDataSet = LineDataSet(diastolicEntry, "BP Diastolic")
        val systolicDataSet = LineDataSet(systolicEntry, "BP Systolic")
        val heartRateDataSet = LineDataSet(heartrateEntry, "Heart Rate BPM")
        diastolicDataSet.color = resources.getColor(R.color.colorAccent)
        systolicDataSet.color = resources.getColor(R.color.purple)
        heartRateDataSet.color = resources.getColor(R.color.orange)
        diastolicDataSet.setCircleColor(resources.getColor(R.color.colorAccent))
        systolicDataSet.setCircleColor(resources.getColor(R.color.purple))
        heartRateDataSet.setCircleColor(resources.getColor(R.color.orange))

        // this is to make the curve smooth
        diastolicDataSet.setDrawCircleHole(false)
        diastolicDataSet.setDrawCircles(false)
        diastolicDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        systolicDataSet.setDrawCircleHole(false)
        systolicDataSet.setDrawCircles(false)
        systolicDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        heartRateDataSet.setDrawCircleHole(false)
        heartRateDataSet.setDrawCircles(false)
        heartRateDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        // remove unneccessy background lines
        lineChart.setDrawBorders(false)
        lineChart.setDrawGridBackground(false)
        lineChart.axisRight.setDrawLabels(false)
        lineChart.axisRight.setDrawGridLines(false)
        lineChart.xAxis.setDrawGridLines(false)
        lineChart.axisLeft.setDrawGridLines(false)
        val lineData = LineData(diastolicDataSet, systolicDataSet, heartRateDataSet)
        lineData.setDrawValues(false)
        lineData.isHighlightEnabled = false
        lineChart.xAxis.setDrawAxisLine(true)
        lineChart.data = lineData
        lineChart.xAxis.isEnabled = false
        lineChart.description.text =
            "Cardiovascular Data from last " + readings.size + " readings"
        lineChart.invalidate()
    }
}
