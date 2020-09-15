package com.cradle.neptune.view.ui.reading

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.cradle.neptune.R
import com.cradle.neptune.model.RetestGroup
import com.cradle.neptune.utilitiles.DateUtil
import com.cradle.neptune.utilitiles.Util
import com.cradle.neptune.view.ui.reading.ReferralDialogFragment.Companion.makeInstance
import com.cradle.neptune.viewmodel.ReadingAnalysisViewSupport
import org.threeten.bp.ZonedDateTime

/**
 * Display summary and advice for currentReading.
 */
@Suppress("LargeClass")
class SummaryFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_summary, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        updateUI()
    }

    override fun onMyBeingDisplayed() {
        // may not have created view yet.
        if (view == null) {
            return
        }
        hideKeyboard()
        updateUI()
    }

    override fun onMyBeingHidden(): Boolean {
        // may not have created view yet.
        return if (view == null) {
            true
        } else true
    }

    private fun updateUI() {
        // TODO: switch to Data Binding for more efficient code?
        // https://www.androidauthority.com/data-binding-in-android-709747/
        val retestGroup = viewModel!!.buildRetestGroup(readingManager!!)
        if (retestGroup != null) {
            updateUiPatientInfo(retestGroup)
            updateUiReadings(retestGroup)
            updateUiAdvice(retestGroup)
            updateUiRecheckVitals(retestGroup)
            updateUiReferral(retestGroup)
            updateUiFollowup(retestGroup)
            updateUiUploaded(retestGroup)
        }
    }

    private fun updateUiPatientInfo(retestAnalysis: RetestGroup) {
        var tv: TextView
        // name
        var name = viewModel!!.patientName
        if (Util.isNullOrEmpty(name)) {
            name = getString(R.string.patient_info_no_name)
        }

        // age
        var age = viewModel!!.patientDob
        if (Util.isNullOrEmpty(age)) {
            age = ""
        }

        // gestational age
        var ga = getString(R.string.reading_no_gestational_age)
        val gaStruct = viewModel?.patientGestationalAge?.age
        if (!viewModel!!.patientIsPregnant!!) {
            ga = getString(R.string.reading_not_pregnant)
        } else if (gaStruct != null) {
            ga = getString(
                R.string.reading_gestational_age_in_weeks_and_days,
                gaStruct.weeks,
                gaStruct.days
            )
        }

        // display "name, age @ ga"
        tv = requireView().findViewById(R.id.txtPatientHeader)
        tv.text = getString(R.string.reading_name_age_ga, name, age, ga)

        // patient id
        tv = requireView().findViewById(R.id.txtPatientId)
        if (!Util.isNullOrEmpty(viewModel!!.patientId)) {
            tv.text = getString(R.string.reading_patient_id, viewModel!!.patientId)
            tv.visibility = View.VISIBLE
        } else {
            tv.visibility = View.GONE
        }

        // symptoms
        tv = requireView().findViewById(R.id.txtSymptoms)
        if (!Util.isNullOrEmpty(viewModel!!.symptomsString)) {
            tv.text = viewModel!!.symptomsString
            tv.visibility = View.VISIBLE
        } else {
            tv.visibility = View.GONE
        }

        // error messages
        tv = requireView().findViewById(R.id.txtPatientInfoErrors)
        if (viewModel!!.isMissingAnything(requireContext())) {
            tv.visibility = View.VISIBLE
            val errorMessage = viewModel!!.missingPatientInfoDescription(requireContext())
            tv.text = errorMessage
        } else {
            tv.visibility = View.GONE
        }
    }

    private fun updateUiReadings(retestAnalysis: RetestGroup) {

        // remove any current readings
        val layoutReadings = requireView().findViewById<LinearLayout>(R.id.layoutReadings)
        layoutReadings.removeAllViews()

        // display all readings:
        for (i in 0 until retestAnalysis.size) {
            val (_, _, dateTimeTaken, bloodPressure) = retestAnalysis.readings[i]
            val analysis = retestAnalysis.analyses[i]
            var tv: TextView
            var iv: ImageView

            // create new layout for this reading
            val v =
                View.inflate(activity, R.layout.reading_vitals_with_icons, null)
            layoutReadings.addView(v)

            // date & condition summary
            val time =
                DateUtil.getDateString(DateUtil.getZoneTimeFromLong(dateTimeTaken))
            val analysisText = analysis.getAnalysisText(requireContext())
            tv = v.findViewById(R.id.txtReadingHeading)
            tv.text = getString(
                R.string.reading_time_summary,
                time, analysisText
            )

            // blood pressure
            tv = v.findViewById(R.id.txtBloodPressure)
            val sys = bloodPressure.systolic
            val dia = bloodPressure.diastolic
            if (sys != null && dia != null) {
                tv.text = getString(
                    R.string.reading_blood_pressure,
                    sys, dia
                )
                tv.visibility = View.VISIBLE
            } else {
                tv.visibility = View.GONE
            }

            // heart rate
            tv = v.findViewById(R.id.txtHeartRate)
            if (viewModel!!.bloodPressure == null) {
                tv.text = getString(
                    R.string.reading_heart_rate,
                    bloodPressure.heartRate
                )
                tv.visibility = View.VISIBLE
            } else {
                tv.visibility = View.GONE
            }

            // icons
            iv = v.findViewById(R.id.imageCircle)
            iv.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis))
            iv = v.findViewById(R.id.imageArrow)
            iv.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis))

            // error messages
            tv = v.findViewById(R.id.txtReadingErrors)
            if (viewModel!!.isMissingAnything(requireContext())) {
                tv.visibility = View.VISIBLE
                // TODO: put in strings.xml
                val errorMessage = viewModel!!.missingVitalInformationDescription(requireContext())
                tv.text = errorMessage
            } else {
                tv.visibility = View.GONE
            }
        }
    }

    private fun updateUiAdvice(retestAnalysis: RetestGroup) {
        var message = ""
        message = if (retestAnalysis.isRetestRecommendedNow) {
            getString(R.string.brief_advice_retest_now)
        } else if (retestAnalysis.isRetestRecommendedIn15Min) {
            getString(R.string.brief_advice_retest_after15)
        } else {
            val analysis = retestAnalysis.mostRecentReadingAnalysis
            analysis.getBriefAdviceText(requireContext())
        }
        val tv = requireView().findViewById<TextView>(R.id.txtAdvice)
        tv.text = message
    }

    private fun updateUiRecheckVitals(retestAnalysis: RetestGroup) {
        // recheck now
        val swNow =
            requireView().findViewById<Switch>(R.id.swRecheckVitalsNow)
        val swIn15 =
            requireView().findViewById<Switch>(R.id.swRecheckVitalsIn15)
        val needDefaultForRecheckVitals = viewModel!!.metadata.dateLastSaved == null
        if (needDefaultForRecheckVitals) {
            swNow.isChecked = retestAnalysis.isRetestRecommendedNow
        } else {
            swNow.isChecked = viewModel!!.isVitalRecheckRequiredNow
        }
        // ..setup initial state
        if (swNow.isChecked && viewModel!!.dateRecheckVitalsNeeded == null) {
            viewModel!!.dateRecheckVitalsNeeded = ZonedDateTime.now().toEpochSecond()
        }
        // ..setup button
        swNow.setOnClickListener { view: View? ->
            if (swNow.isChecked) {
                swIn15.isChecked = false
                viewModel!!.dateRecheckVitalsNeeded =
                    ZonedDateTime.now().toEpochSecond()
            }

            // remove date if no recheck selected
            if (!swNow.isChecked && !swIn15.isChecked) {
                viewModel!!.dateRecheckVitalsNeeded = null
            }
        }

        // recheck  soon
        // TODO: change text box to show how many minutes until reading needed.
        if (needDefaultForRecheckVitals) {
            swIn15.isChecked = retestAnalysis.isRetestRecommendedIn15Min
        } else {
            swIn15.isChecked =
                viewModel!!.isVitalRecheckRequired && !viewModel!!.isVitalRecheckRequiredNow
        }
        // ..setup initial state
        if (swIn15.isChecked && viewModel!!.dateRecheckVitalsNeeded == null) {
            viewModel!!.dateRecheckVitalsNeeded = ZonedDateTime.now()
                .toEpochSecond() + NUM_SECONDS_IN_15_MIN
        }
        swIn15.setOnClickListener { view: View? ->
            if (swIn15.isChecked) {
                swNow.isChecked = false
                // add 15 minutes
                viewModel!!.dateRecheckVitalsNeeded = ZonedDateTime.now()
                    .toEpochSecond() + NUM_SECONDS_IN_15_MIN
            }

            // remove date if no recheck selected
            if (!swNow.isChecked && !swIn15.isChecked) {
                viewModel!!.dateRecheckVitalsNeeded = null
            }
        }

        // recommendation message
        val tvRecommend =
            requireView().findViewById<TextView>(R.id.txtRecheckVitalsRecommended)
        if (retestAnalysis.isRetestRecommended) {
            if (retestAnalysis.isRetestRecommendedNow) {
                tvRecommend.text = getString(R.string.summary_recheck_vitals_now)
            } else if (retestAnalysis.isRetestRecommendedIn15Min) {
                tvRecommend.text = getString(R.string.summary_recheck_vitals_n_min, 15)
            } else {
                Util.ensure(false)
            }
            tvRecommend.visibility = View.VISIBLE
        } else {
            tvRecommend.visibility = View.GONE
        }

        // set border color based on recommendation
        setRectangleStrokeColor(R.id.sectionRecheckVitals, retestAnalysis.isRetestRecommended)
    }

    private fun updateUiReferral(retestAnalysis: RetestGroup) {
        val btn =
            requireView().findViewById<Button>(R.id.btnSendReferral)
        btn.setOnClickListener { view: View? -> showReferralDialog() }
        val ivReferralSent =
            requireView().findViewById<ImageView>(R.id.ivReferralSent)
        val tv = requireView().findViewById<TextView>(R.id.txtReferralSent)
        if (viewModel!!.referral == null) {
//        if (currentReading.referralMessageSendTime == null) {
            tv.text = getString(R.string.reading_referral_notsent)
            ivReferralSent.visibility = View.GONE
        } else {
            tv.text = getString(
                R.string.reading_referral_sent,
                viewModel!!.referral!!.healthFacilityName,
                DateUtil.getFullDateFromUnix(viewModel!!.referral!!.dateReferred)
            )
            ivReferralSent.visibility = View.VISIBLE
        }

        // recommend referral?
        val isReferralRecommended =
            retestAnalysis.mostRecentReadingAnalysis.isReferralRecommended
        // ..message show/hide
        val tvRecommend = requireView().findViewById<TextView>(R.id.txtReferralRecommended)
        tvRecommend.visibility = if (isReferralRecommended) View.VISIBLE else View.GONE
        // ..button color
        if (isReferralRecommended) {
            ViewCompat.setBackgroundTintList(
                btn,
                ContextCompat.getColorStateList(requireActivity(), R.color.recommended)
            )
        } else {
            btn.setBackgroundResource(android.R.drawable.btn_default)
        }
        // ..border color
        setRectangleStrokeColor(R.id.sectionReferral, isReferralRecommended)
    }

    private fun updateUiFollowup(retestAnalysis: RetestGroup) {
        val swFollowup =
            requireView().findViewById<Switch>(R.id.swFollowUpNeeded)
        val needDefaultForFollowup = viewModel!!.dateTimeTaken == null
        val followupRecommended = retestAnalysis.mostRecentReadingAnalysis.isRed
        if (needDefaultForFollowup) {
            swFollowup.isChecked = followupRecommended
        } else {
            swFollowup.isChecked = viewModel!!.isFlaggedForFollowUp!!
        }
        viewModel!!.isFlaggedForFollowUp = swFollowup.isChecked
        swFollowup.setOnClickListener { view: View? ->
            viewModel!!.isFlaggedForFollowUp = swFollowup.isChecked
        }

        // recommendation message
        val tvRecommend = requireView().findViewById<TextView>(R.id.txtFollowupRecommended)
        tvRecommend.visibility = if (followupRecommended) View.VISIBLE else View.GONE
        setRectangleStrokeColor(R.id.sectionFollowUp, followupRecommended)
    }

    private fun updateUiUploaded(retestAnalysis: RetestGroup) {
        // uploaded
        val tv = requireView().findViewById<TextView>(R.id.txtUploadedMessage)
        if (viewModel!!.metadata.dateUploadedToServer == null) {
            tv.text = getString(R.string.reading_not_uploaded_to_server)
        } else {
            tv.text = getString(
                R.string.reading_uploaded_to_server,
                DateUtil.getFullDateFromUnix(viewModel!!.metadata.dateUploadedToServer)
            )
        }
    }

    private fun setRectangleStrokeColor(
        viewId: Int,
        showAsRecommended: Boolean
    ) {
        val sectionView = requireView().findViewById<View>(viewId)
        val drawable = sectionView.background as GradientDrawable
        val colorId = if (showAsRecommended) R.color.recommended else R.color.black
        val width =
            if (showAsRecommended) STROKE_WIDTH_RECOMMENDED else STROKE_WIDTH_NORMAL
        val color = ContextCompat.getColor(requireActivity(), colorId)
        drawable.setStroke(width, color)
    }

    /**
     * Dialogs
     */
    private fun showReferralDialog() {
        // don't refer (and hence save!) if missing data
        if (viewModel!!.isMissingAnything(requireContext())) {
            displayMissingDataDialog()
            return
        }
        val newFragment = makeInstance(viewModel!!)
        newFragment.onSuccessfulUpload = {

            // Close the activity upon successful upload of the referral as
            // this implies that the reading and patient have already been
            // saved.
            activityCallbackListener!!.finishActivity()
        }
        newFragment.show(requireFragmentManager(), "Referral")
    }

    private fun displayMissingDataDialog() {
        // TODO: Put strings in xml
        val dialog =
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_missing_information_title))
                .setMessage(getString(R.string.dialog_missing_information_message))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, null)
        dialog.show()
    }

    companion object {
        const val STROKE_WIDTH_RECOMMENDED = 6
        const val STROKE_WIDTH_NORMAL = 3
        var NUM_SECONDS_IN_15_MIN: Long = 900
        fun newInstance(): SummaryFragment {
            return SummaryFragment()
        }
    }
}
