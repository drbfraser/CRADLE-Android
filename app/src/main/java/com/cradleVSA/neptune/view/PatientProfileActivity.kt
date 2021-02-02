package com.cradleVSA.neptune.view

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView

import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.manager.PatientManager
import com.cradleVSA.neptune.manager.ReadingManager
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.utilitiles.Util
import com.cradleVSA.neptune.viewmodel.ReadingRecyclerViewAdapter
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

import java.text.ParseException
import java.util.ArrayList
import java.util.Comparator

import javax.inject.Inject

import dagger.hilt.android.AndroidEntryPoint

import com.cradleVSA.neptune.view.DashBoardActivity.Companion.READING_ACTIVITY_DONE
import com.cradleVSA.neptune.view.ReadingActivity.Companion.makeIntentForEditReading
import com.cradleVSA.neptune.view.ReadingActivity.Companion.makeIntentForNewReadingExistingPatient
import com.cradleVSA.neptune.view.ReadingActivity.Companion.makeIntentForRecheck
import kotlinx.coroutines.runBlocking

/**
 * Note:
 * This class was translated from Java to Kotlin by an inexperienced person.
 * I'm still learning the eccentricities of Kotlin and having to think in an asynchronous
 * mindset for the first time, so there may be places this class could be improved.
 * Particularly, calls to runBlocking in non-suspend functions (like onResume)
 * might be a source of slowdown.
 *
 * The currPatient variable is marked as "potentially null"/not lateinit
 * and asserted as not null when passed into many functions. At this point in time
 * I believe these calls to be OK (or at least no worse than the Java versions previously)
 * but at the slightest hint of issues due to NPE, everywhere using "currPatient!!"
 * should be overhauled.
 *
 * 2021-02-01
 */

@AndroidEntryPoint
open class PatientProfileActivity : AppCompatActivity() {

    private lateinit var patientID: TextView
    private lateinit var patientName: TextView
    private lateinit var patientAge: TextView
    private lateinit var patientSex: TextView
    private lateinit var villageNo: TextView
    private lateinit var householdNo: TextView
    private lateinit var patientZone: TextView
    private lateinit var pregnant: TextView
    private lateinit var gestationalAge: TextView
    private lateinit var pregnancyInfoLayout: LinearLayout

    lateinit var readingRecyclerview: RecyclerView
    var currPatient: Patient? = null
    lateinit var patientReadings: List<Reading>

    // Data Model
    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val EXTRA_PATIENT = "patient"
        private const val EXTRA_PATIENT_ID = "patientId"

        fun makeIntentForPatient(context: Context?, patient: Patient?): Intent? {
            val intent = Intent(context, PatientProfileActivity::class.java)
            intent.putExtra(EXTRA_PATIENT, patient)
            return intent
        }

        fun makeIntentForPatientId(context: Context?, patientId: String?): Intent? {
            val intent = Intent(context, PatientProfileActivity::class.java)
            intent.putExtra(EXTRA_PATIENT_ID, patientId)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_profile)
        initAllFields()
        if (!getLocalPatient()) {
            // Not a local patient, might be a child class so we let the child do the init stuff
            return
        }
        populatePatientInfo(currPatient!!)
        setupReadingsRecyclerView()
        setupCreatePatientReadingButton()
        setupLineChart()
        setupToolBar()
    }

    fun setupToolBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.patient_summary)
    }

    private fun initAllFields() {
        patientID = findViewById(R.id.patientId)
        patientName = findViewById(R.id.patientName)
        patientAge = findViewById(R.id.patientAge)
        patientSex = findViewById(R.id.patientSex)
        villageNo = findViewById(R.id.patientVillage)
        householdNo = findViewById(R.id.patientHouseholdNumber)
        patientZone = findViewById(R.id.patientZone)
        pregnant = findViewById(R.id.textView20)
        gestationalAge = findViewById(R.id.gestationalAge)
        pregnancyInfoLayout = findViewById(R.id.pregnancyLayout)
        readingRecyclerview = findViewById(R.id.readingRecyclerview)
    }

    open fun getLocalPatient(): Boolean {
        if (intent.hasExtra(EXTRA_PATIENT_ID)) {
            val patientId = intent.getStringExtra(EXTRA_PATIENT_ID)
            // Assertion that patientId is not null should be safe due to hasExtra check
            currPatient = runBlocking{ patientManager.getPatientById(patientId!!) }
            return currPatient != null
        }
        currPatient = (intent.getSerializableExtra(EXTRA_PATIENT) as Patient?)
        return currPatient != null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!getLocalPatient()) {
            //not a local patient, might be a child class
            return
        }
        setupLineChart()
        setupReadingsRecyclerView()
    }

    fun populatePatientInfo(patient: Patient) {
        patientID.text = patient.id
        patientName.text = patient.name
        if (!Util.stringNullOrEmpty(patient.dob)) {
            try {
                val ageFromDob = Patient.calculateAgeFromDateString(patient.dob!!)
                val ageDisplayString: String
                ageDisplayString = if (patient.isExactDob == null || !patient.isExactDob!!) {
                    getString(
                        R.string.patient_profile_age_about_n_years_old, ageFromDob
                    )
                } else {
                    getString(
                        R.string.patient_profile_age_n_years_old, ageFromDob
                    )
                }
                patientAge.text = ageDisplayString
            } catch (ignored: ParseException) {
            }
        }
        patientSex.text = patient.sex.toString()
        if (!Util.stringNullOrEmpty(patient.villageNumber)) {
            villageNo.text = patient.villageNumber
        }
        if (!Util.stringNullOrEmpty(patient.householdNumber)) {
            householdNo.text = patient.householdNumber
        }
        if (!Util.stringNullOrEmpty(patient.zone)) {
            patientZone.text = patient.zone
        }
        if (patient.isPregnant) {
            pregnant.setText(R.string.yes)
            setupGestationalInfo(patient)
        } else {
            pregnant.setText(R.string.no)
            pregnancyInfoLayout.visibility = View.GONE
        }
        if (patient.drugHistory.isNotEmpty()) {
            val drugHistory = findViewById<TextView>(R.id.drugHistroyTxt)
            drugHistory.text = patient.drugHistory
        }
        if (patient.medicalHistory.isNotEmpty()) {
            val medHistory = findViewById<TextView>(R.id.medHistoryText)
            medHistory.text = patient.medicalHistory
        }
    }

    /**
     * This function converts either weeks or months into months and weeks
     * example: gestational age = 6 weeks ,converts to 1 month and 1.5 weeks(roughly)
     *
     * @param patient current patient
     */
    fun setupGestationalInfo(patient: Patient) {
        val radioGroup = findViewById<RadioGroup>(R.id.gestationradioGroup)
        radioGroup.setOnCheckedChangeListener{radioGroup1: RadioGroup?, index: Int ->
            var `val`: Double? = null
            if (index == R.id.monthradiobutton) {
                `val` = patient.gestationalAge?.age?.asMonths()
            } else {
                `val` = patient.gestationalAge?.age?.asWeeks()
            }
            if (`val`!! < 0) {
                gestationalAge.setText(R.string.not_available_n_slash_a)
            } else {
                gestationalAge.text = "%.2f".format(`val`)
            }
        }
        radioGroup.check(R.id.monthradiobutton)
    }

    fun setupLineChart() {
        val lineChart = findViewById<LineChart>(R.id.patientLineChart)
        val lineChartCard = findViewById<CardView>(R.id.patientLineChartCard)
        lineChartCard.visibility = View.VISIBLE
        val sBPs: ArrayList<Entry> = ArrayList()
        val dBPs: ArrayList<Entry> = ArrayList()
        val bPMs: ArrayList<Entry> = ArrayList()

        //put data sets in chronological order
        var index = patientReadings.size
        for (reading in patientReadings) {
            sBPs.add(0, Entry(index.toFloat(), reading.bloodPressure.systolic.toFloat()))
            dBPs.add(0, Entry(index.toFloat(), reading.bloodPressure.diastolic.toFloat()))
            bPMs.add(0, Entry(index.toFloat(), reading.bloodPressure.heartRate.toFloat()))
            index--
        }
        val sBPDataSet = LineDataSet(
            sBPs,
            getString(R.string.activity_patient_profile_chart_systolic_label)
        )
        val dBPDataSet = LineDataSet(
            dBPs,
            getString(R.string.activity_patient_profile_chart_diastolic_label)
        )
        val bPMDataSet = LineDataSet(
            bPMs,
            getString(R.string.activity_patient_profile_chart_heart_rate_label)
        )

        sBPDataSet.color = R.color.purple
        sBPDataSet.setCircleColor(R.color.purple)

        dBPDataSet.color = R.color.colorAccent
        dBPDataSet.setCircleColor(R.color.colorAccent)

        bPMDataSet.color = R.color.orange
        bPMDataSet.setCircleColor(R.color.orange)

        bPMDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dBPDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        sBPDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        lineChart.setDrawBorders(false)
        lineChart.setDrawGridBackground(false)
        lineChart.axisRight.setDrawLabels(false)
        lineChart.axisRight.setDrawGridLines(false)
        lineChart.xAxis.setDrawGridLines(true)
        lineChart.axisLeft.setDrawGridLines(true)

        val lineData = LineData(sBPDataSet, dBPDataSet, bPMDataSet)

        lineData.isHighlightEnabled = false

        lineChart.xAxis.setDrawAxisLine(true)
        lineChart.data = lineData
        lineChart.xAxis.isEnabled = false
        lineChart.description.text = getString(
            R.string.activity_patient_profile_line_chart_description,
            patientReadings.size
        )
        lineChart.invalidate()
    }

    private fun getThisPatientsReadings(): List<Reading> {
        val readings: List<Reading> =
           runBlocking{ readingManager.getReadingsByPatientId(currPatient!!.id) }
        val comparator: Comparator<Reading> = Reading.DescendingDateComparator
        return readings.sortedWith(comparator)
    }

    private fun setupCreatePatientReadingButton() {
        val createButton =
            findViewById<Button>(R.id.newPatientReadingButton)
        createButton.visibility = View.VISIBLE
        createButton.setOnClickListener { v: View? ->
            val intent = makeIntentForNewReadingExistingPatient(
                this@PatientProfileActivity, currPatient!!.id
            )
            startActivityForResult(intent, READING_ACTIVITY_DONE)
        }
    }

    open fun setupReadingsRecyclerView() {
        patientReadings = getThisPatientsReadings()

        // use linear layout
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        readingRecyclerview.layoutManager = layoutManager
        readingRecyclerview.isNestedScrollingEnabled = false
        val listAdapter: ReadingRecyclerViewAdapter

        // set adapter
        listAdapter = ReadingRecyclerViewAdapter(patientReadings)
        listAdapter.setOnClickElementListener(object : ReadingRecyclerViewAdapter.OnClickElement {
            override fun onClick(readingId: String?) {
                val intent =
                    makeIntentForEditReading(this@PatientProfileActivity, readingId)
                startActivityForResult(intent, READING_ACTIVITY_DONE)
            }

            override fun onLongClick(readingId: String?): Boolean {
                runBlocking { askToDeleteReading(readingId) }
                return true
            }

            override fun onClickRecheckReading(readingId: String?) {
                val intent =
                    makeIntentForRecheck(this@PatientProfileActivity, readingId)
                startActivityForResult(intent, READING_ACTIVITY_DONE)
            }
        })
        readingRecyclerview.adapter = listAdapter
    }

    /**
     * shows a dialog to confirm deleting a reading
     *
     * @param readingId id of the reading to delete
     */
    private suspend fun askToDeleteReading(readingId: String?) {
        val dialog: AlertDialog.Builder = AlertDialog.Builder(this)
            .setMessage(R.string.activity_patient_profile_delete_reading_dialog_title)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(
                R.string.activity_patient_profile_delete_reading_dialog_delete_button
            ) { dialog1, whichButton ->
                runBlocking { readingManager.deleteReadingById(readingId!!) }
                updateUi()
            }
            .setNegativeButton(android.R.string.cancel, null)
        dialog.show()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        @Nullable data: Intent?
    ) {
        if (requestCode == READING_ACTIVITY_DONE) {
            updateUi()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateUi() {
        // setupEmptyState()
        setupReadingsRecyclerView()
        setupLineChart()
    }


}