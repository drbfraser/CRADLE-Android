package com.cradleplatform.neptune.view

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.AssessmentManager
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.utilities.SnackbarHelper
import com.cradleplatform.neptune.utilities.Util
import com.cradleplatform.neptune.view.DashBoardActivity.Companion.READING_ACTIVITY_DONE
import com.cradleplatform.neptune.view.ReadingActivity.Companion.makeIntentForEditReading
import com.cradleplatform.neptune.view.ReadingActivity.Companion.makeIntentForNewReadingExistingPatient
import com.cradleplatform.neptune.view.ReadingActivity.Companion.makeIntentForRecheck
import com.cradleplatform.neptune.viewmodel.ReadingRecyclerViewAdapter
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.ParseException
import java.util.ArrayList
import java.util.Comparator
import javax.inject.Inject

/**
 * Note:
 * This class was translated from Java to Kotlin which may account for any stylistic
 * differences between it and other Kotlin-native classes.
 *
 * The usage of runBlocking is not ideal for performance.
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
    private lateinit var btnPregnancy: Button

    lateinit var readingRecyclerview: RecyclerView
    lateinit var currPatient: Patient
    lateinit var patientReadings: List<Reading>
    private var patientReferrals: List<Referral>? = null
    private var patientAssessments: List<Assessment>? = null

    // Data Model
    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var referralManager: ReferralManager

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var assessmentManager: AssessmentManager

    @Inject
    lateinit var formResponseManager: FormResponseManager

    companion object {
        private const val EXTRA_PATIENT = "patient"
        private const val EXTRA_PATIENT_ID = "patientId"

        fun makeIntentForPatient(context: Context, patient: Patient): Intent {
            val intent = Intent(context, PatientProfileActivity::class.java)
            intent.putExtra(EXTRA_PATIENT, patient)
            return intent
        }

        fun makeIntentForPatientId(context: Context, patientId: String): Intent {
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
        setupReadingsRecyclerView()
        setupCreatePatientReadingButton()
        setupCreatePatientReferralButton()
        setupCreateAndFillFormButton()
        lifecycleScope.launch {
            setupSeeSavedFormsButton()
        }
        setupUpdateRecord()
        setupLineChart()
        setupToolBar()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_patient_activity, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onResume() {
        super.onResume()
        if (!getLocalPatient()) {
            // not a local patient, might be a child class
            return
        }
        setupLineChart()
        setupReadingsRecyclerView()

        setupEditPatient(currPatient)
        setupBtnPregnancy(currPatient)
        setupCreatePatientReadingButton()
        setupCreatePatientReferralButton()
    }

    private fun changeAddReadingButtonColorIfNeeded() {
        val button: Button = findViewById(R.id.newPatientReadingButton)
        if (patientReadings.isNotEmpty() && Util.isRecheckNeededNow(patientReadings[0].dateRecheckVitalsNeeded)) {
            button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.redDown)
            button.text = getString(R.string.new_reading_is_required_now)
        } else {
            button.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.colorPrimaryLight)
            button.text = getString(R.string.create_new_reading)
        }
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
        btnPregnancy = findViewById(R.id.btn_pregnancy)
    }

    open fun getLocalPatient(): Boolean {
        val tmpPatient = if (intent.hasExtra(EXTRA_PATIENT_ID)) {
            // Assertion here should be safe due to hasExtra check
            val patientId: String = intent.getStringExtra(EXTRA_PATIENT_ID)!!
            runBlocking { patientManager.getPatientById(patientId) }
        } else {
            intent.getSerializableExtra(EXTRA_PATIENT) as Patient?
        }

        if (tmpPatient != null) {
            currPatient = tmpPatient
            populatePatientInfo(currPatient)
        }
        return tmpPatient != null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    fun populatePatientInfo(patient: Patient) {
        patientID.text = patient.id
        patientName.text = patient.name
        if (!Util.stringNullOrEmpty(patient.dob)) {
            try {
                val ageFromDob = Patient.calculateAgeFromDateString(patient.dob!!)
                val ageDisplayString = if (patient.isExactDob == true) {
                    getString(
                        R.string.patient_profile_age_n_years_old,
                        ageFromDob
                    )
                } else {
                    getString(
                        R.string.patient_profile_age_about_n_years_old,
                        ageFromDob
                    )
                }
                patientAge.text = ageDisplayString
            } catch (ignored: ParseException) {
            }
        }
        patientSex.text = patient.sex.toString()
        if (patient.sex == Sex.MALE) {
            btnPregnancy.visibility = View.GONE
        }
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
            btnPregnancy.text = getString(R.string.close_pregnancy)
            pregnancyInfoLayout.visibility = View.VISIBLE
        } else {
            pregnant.setText(R.string.no)
            pregnancyInfoLayout.visibility = View.GONE
            btnPregnancy.text = getString(R.string.add_pregnancy)
        }
        if (patient.drugHistory.isNotEmpty()) {
            val drugHistory = findViewById<TextView>(R.id.drugHistroyTxt)
            drugHistory.text = patient.drugHistory
        }
        if (patient.medicalHistory.isNotEmpty()) {
            val medHistory = findViewById<TextView>(R.id.medHistoryText)
            medHistory.text = patient.medicalHistory
        }
        if (patient.allergy.isNotEmpty()) {
            val allergies = findViewById<TextView>(R.id.allergies)
            allergies.text = patient.allergy
        }
    }

    private fun setupBtnPregnancy(patient: Patient) {
        btnPregnancy.setOnClickListener() {

            // Don't allow users to end a pregnancy when they haven't synced the start date
            if (patient.pregnancyId == null && patient.gestationalAge != null) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.unable_to_edit_pregnancy)
                    .setMessage(R.string.error_please_sync)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()

                return@setOnClickListener
            }

            val intent =
                EditPregnancyActivity.makeIntentWithPatientId(this, patient.id, patient.isPregnant)
            startActivity(intent)
        }
    }

    private fun setupEditPatient(patient: Patient) {
        val editIcon = findViewById<ImageView>(R.id.im_edit_personal_info)
        editIcon.setOnClickListener() {
            val intent = EditPatientInfoActivity.makeIntentWithPatientId(this, patient.id)
            startActivity(intent)
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
        radioGroup.setOnCheckedChangeListener { _: RadioGroup?, index: Int ->
            val ageVal: Double? = if (index == R.id.monthradiobutton) {
                patient.gestationalAge?.ageFromNow?.asMonths()
            } else {
                patient.gestationalAge?.ageFromNow?.asWeeks()
            }

            gestationalAge.text = if (ageVal == null || ageVal < 0) {
                getText(R.string.not_available_n_slash_a)
            } else {
                "%.2f".format(ageVal)
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

        // put data sets in chronological order
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

        val lineData = LineData(sBPDataSet, dBPDataSet, bPMDataSet)
        lineData.isHighlightEnabled = false

        lineChart.apply {
            setDrawBorders(false)
            setDrawGridBackground(false)
            axisRight.setDrawLabels(false)
            axisRight.setDrawGridLines(false)
            xAxis.setDrawGridLines(true)
            axisLeft.setDrawGridLines(true)
            xAxis.setDrawAxisLine(true)
            data = lineData
            xAxis.isEnabled = false
            description.text = getString(
                R.string.activity_patient_profile_line_chart_description,
                patientReadings.size
            )
            invalidate()
        }
    }

    private fun getThisPatientsReadings(): List<Reading> {
        // TODO: as per MOB-270, we should not have database retrieval operations within runBlocking.
        //  (refer to issue #37)
        val readings: List<Reading> =
            runBlocking { readingManager.getReadingsByPatientId(currPatient.id) }
        val comparator: Comparator<Reading> = Reading.DescendingDateComparator
        return readings.sortedWith(comparator)
    }

    private fun getThisPatientsReferrals(): List<Referral>? {
        val referrals: List<Referral>? =
            runBlocking { referralManager.getReferralByPatientId(currPatient.id) }
        val comparator: Comparator<Referral> = Referral.DescendingDateComparator
        return referrals?.sortedWith(comparator)
    }

    private fun getThisPatientAssessments(): List<Assessment>? {
        val assessments: List<Assessment>? =
            runBlocking { assessmentManager.getAssessmentByPatientId(currPatient.id) }
        val comparator: Comparator<Assessment> = Assessment.DescendingDateComparator
        return assessments?.sortedWith(comparator)
    }

    private fun setupCreatePatientReadingButton() {
        // TODO: this function has unclear dependency on setupReadingsRecyclerView,
        //  patientsReadings won't be loaded until it's called (refer to issue #50)
        val createButton =
            findViewById<Button>(R.id.newPatientReadingButton)
        createButton.visibility = View.VISIBLE

        if (patientReadings.isNotEmpty() && Util.isRecheckNeededNow(patientReadings[0].dateRecheckVitalsNeeded)) {
            createButton.setOnClickListener { _: View? ->
                val readingId = patientReadings[0].id
                val intent =
                    makeIntentForRecheck(this@PatientProfileActivity, readingId)
                startActivityForResult(intent, READING_ACTIVITY_DONE)
            }
        } else {
            createButton.setOnClickListener { _: View? ->
                val intent = makeIntentForNewReadingExistingPatient(
                    this@PatientProfileActivity,
                    currPatient.id
                )
                startActivityForResult(intent, READING_ACTIVITY_DONE)
            }
        }

        changeAddReadingButtonColorIfNeeded()
    }

    private fun setupCreatePatientReferralButton() {
        val createButton =
            findViewById<Button>(R.id.newPatientReferralButton)

        createButton.visibility = View.VISIBLE

        createButton.setOnClickListener { _: View? ->
            val intent = PatientReferralActivity.makeIntentForPatient(
                this@PatientProfileActivity,
                currPatient
            )
            startActivity(intent)
        }
    }

    private fun setupCreateAndFillFormButton() {
        val createFormButton = findViewById<Button>(R.id.newFormButton)

        createFormButton.visibility = View.VISIBLE
        createFormButton.setOnClickListener {
            val intent = FormSelectionActivity.makeIntentForPatientId(
                this@PatientProfileActivity,
                currPatient.id,
                currPatient
            )
            startActivity(intent)
        }
    }

    private suspend fun setupSeeSavedFormsButton() {
        val createFormButton = findViewById<Button>(R.id.seeSavedFormsButton)
        val responsesForPatient = formResponseManager.searchForFormResponseByPatientId(currPatient.id)
        createFormButton.visibility = if (!responsesForPatient.isNullOrEmpty()) View.VISIBLE else View.GONE
        createFormButton.setOnClickListener {
            val intent = SavedFormsActivity.makeIntent(
                this@PatientProfileActivity,
                currPatient.id,
                currPatient
            )
            startActivity(intent)
        }
    }

    private fun onUpdateButtonClicked(isDrugRecord: Boolean) {
        val intent = PatientUpdateDrugMedicalActivity.makeIntent(this, isDrugRecord, currPatient)
        startActivity(intent)
    }

    private fun setupUpdateRecord() {
        findViewById<Button>(R.id.medicalHistoryUpdateButton).setOnClickListener {
            onUpdateButtonClicked(false)
        }
        findViewById<Button>(R.id.drugHistoryUpdateButton).setOnClickListener {
            onUpdateButtonClicked(true)
        }
    }

    open fun setupReadingsRecyclerView() {
        patientReadings = getThisPatientsReadings()
        patientReferrals = getThisPatientsReferrals()
        patientAssessments = getThisPatientAssessments()
        val combinedList: MutableList<Any> = patientReadings.map { i -> i }.toMutableList()
        if (patientReferrals != null)
            combinedList.addAll(patientReferrals!!.map { i -> i })
        if (patientAssessments != null)
            combinedList.addAll(patientAssessments!!.map { i -> i })
        combinedList.sortWith(
            compareByDescending {
                when (it) {
                    is Reading -> it.dateTimeTaken
                    is Referral -> it.dateReferred
                    is Assessment -> it.dateAssessed
                    else -> Integer.MAX_VALUE
                }
            }
        )
        // use linear layout
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        readingRecyclerview.layoutManager = layoutManager
        readingRecyclerview.isNestedScrollingEnabled = false

        val listAdapter = ReadingRecyclerViewAdapter(combinedList)
        listAdapter.setOnClickElementListener(
            object : ReadingRecyclerViewAdapter.OnClickElement {
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
            }
        )
        readingRecyclerview.adapter = listAdapter
    }

    /**
     * shows a dialog to confirm deleting a reading
     *
     * @param readingId id of the reading to delete
     */
    private fun askToDeleteReading(readingId: String?) {
        val dialog: AlertDialog.Builder = AlertDialog.Builder(this)
            .setMessage(R.string.activity_patient_profile_delete_reading_dialog_title)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(
                R.string.activity_patient_profile_delete_reading_dialog_delete_button
            ) { _, _ ->
                runBlocking { readingManager.deleteReadingById(readingId!!) }
                updateUi()
            }
            .setNegativeButton(android.R.string.cancel, null)
        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        @Nullable data: Intent?
    ) {
        if (requestCode == READING_ACTIVITY_DONE) {
            updateUi()

            if (resultCode == RESULT_OK) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    data?.getStringExtra(ReadingActivity.EXTRA_SNACKBAR_MSG)?.let {
                        SnackbarHelper.showSnackbarWithOK(
                            this,
                            it
                        )
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateUi() {
        // setupEmptyState()
        setupReadingsRecyclerView()
        setupLineChart()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()

        var intent = PatientsActivity.makeIntent(this@PatientProfileActivity)
        startActivity(intent)
    }
}
