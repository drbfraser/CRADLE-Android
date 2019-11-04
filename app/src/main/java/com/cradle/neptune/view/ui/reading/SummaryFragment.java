package com.cradle.neptune.view.ui.reading;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.cradle.neptune.R;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingAnalysis;
import com.cradle.neptune.model.ReadingRetestAnalysis;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.viewmodel.ReadingAnalysisViewSupport;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.temporal.ChronoUnit;

/**
 * Display summary and advice for currentReading.
 */
public class SummaryFragment extends BaseFragment {

    public static final int STROKE_WIDTH_RECOMMENDED = 6;
    public static final int STROKE_WIDTH_NORMAL = 3;

    public SummaryFragment() {
        // Required empty public constructor
    }

    public static SummaryFragment newInstance() {
        SummaryFragment fragment = new SummaryFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_summary, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateUI();
    }

    @Override
    public void onMyBeingDisplayed() {
        // may not have created view yet.
        if (getView() == null) {
            return;
        }
        hideKeyboard();
        updateUI();
    }


    @Override
    public boolean onMyBeingHidden() {
        // may not have created view yet.
        if (getView() == null) {
            return true;
        }
        return true;
    }


    private void updateUI() {
        // TODO: switch to Data Binding for more efficient code?
        // https://www.androidauthority.com/data-binding-in-android-709747/

        ReadingRetestAnalysis readingRetestAnalysis
                = new ReadingRetestAnalysis(currentReading, readingManager, getContext());

        updateUI_PatientInfo(readingRetestAnalysis);
        updateUI_Readings(readingRetestAnalysis);
        updateUI_Advice(readingRetestAnalysis);
        updateUI_RecheckVitals(readingRetestAnalysis);
        updateUI_Referral(readingRetestAnalysis);
        updateUI_Followup(readingRetestAnalysis);
        updateUI_Uploaded(readingRetestAnalysis);
    }

    private void updateUI_PatientInfo(ReadingRetestAnalysis retestAnalysis) {
        TextView tv;
        String errorMessage = "";

        // name
        String name = currentReading.patient.patientName;
        if (Util.isNullOrEmpty(name)) {
            name = "No name";
            errorMessage += "- patient initials" + "\n";
        }

        // age
        Integer age = currentReading.patient.ageYears;
        if (Util.isNullOrZero(age)) {
            age = 0;
            errorMessage += "- patient age" + "\n";
        }

        // gestational age
        String ga;
        Reading.WeeksAndDays gaStruct = currentReading.getGestationalAgeInWeeksAndDays();
        if (currentReading.patient.gestationalAgeUnit == Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_NONE) {
            ga = getString(R.string.reading_gestational_age_not_pregnant);
        } else if (gaStruct == null) {
            ga = "No gestational age";
            errorMessage += "- gestational age" + "\n";
        } else {
            ga = getString(R.string.reading_gestational_age_in_weeks_and_days, gaStruct.weeks, gaStruct.days);
        }

        // display "name, age @ ga"
        tv = getView().findViewById(R.id.txtPatientHeader);
        tv.setText(getString(R.string.reading_name_age_ga, name, age, ga));

        // patient id
        tv = getView().findViewById(R.id.txtPatientId);
        if (!Util.isNullOrEmpty(currentReading.patient.patientId)) {
            tv.setText(getString(R.string.reading_patient_id, currentReading.patient.patientId));
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
            errorMessage += "- patient ID" + "\n";
        }

        // symptoms
        tv = getView().findViewById(R.id.txtSymptoms);
        if (!Util.isNullOrEmpty(currentReading.getSymptomsString())) {
            tv.setText(currentReading.getSymptomsString());
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
        if (currentReading.isMissingRequiredSymptoms()) {
            errorMessage += "- symptoms" + "\n";
        }

        // error messages
        tv = getView().findViewById(R.id.txtPatientInfoErrors);
        if (errorMessage.length() > 0) {
            tv.setVisibility(View.VISIBLE);
            // TODO: put in strings.xml
            errorMessage = "Missing required patient information:" + "\n" + errorMessage;
            tv.setText(errorMessage);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void updateUI_Readings(ReadingRetestAnalysis retestAnalysis) {

        // remove any current readings
        LinearLayout layoutReadings = getView().findViewById(R.id.layoutReadings);
        layoutReadings.removeAllViews();

        // display all readings:
        for (int i = 0; i < retestAnalysis.getNumberReadings(); i++) {
            Reading reading = retestAnalysis.getReadings().get(i);
            ReadingAnalysis analysis = retestAnalysis.getReadingAnalyses().get(i);

            TextView tv;
            ImageView iv;
            String errorMessage = "";

            // create new layout for this reading
            View v = View.inflate(getActivity(), R.layout.reading_vitals_with_icons, null);
            layoutReadings.addView(v);

            // date & condition summary
            String time = DateUtil.getDateString(reading.dateTimeTaken);
            String analysisText = analysis.getAnalysisText(getContext());
            tv = v.findViewById(R.id.txtReadingHeading);
            tv.setText(getString(R.string.reading_time_summary,
                    time, analysisText));
            if (time.length() == 0) {
                errorMessage += "- date/time of reading" + "\n";
            }

            // blood pressure
            tv = v.findViewById(R.id.txtBloodPressure);
            Integer sys = reading.bpSystolic;
            Integer dia = reading.bpDiastolic;
            if (sys != null && dia != null) {
                tv.setText(getString(R.string.reading_blood_pressure,
                        sys, dia));
                tv.setVisibility(View.VISIBLE);
            } else {
                errorMessage += "- blood pressure (systolic and/or diastolic)" + "\n";
                tv.setVisibility(View.GONE);
            }

            // heart rate
            tv = v.findViewById(R.id.txtHeartRate);
            if (reading.heartRateBPM != null) {
                tv.setText(getString(R.string.reading_heart_rate,
                        reading.heartRateBPM));
                tv.setVisibility(View.VISIBLE);
            } else {
                errorMessage += "- heart rate" + "\n";
                tv.setVisibility(View.GONE);
            }

            // icons
            iv = v.findViewById(R.id.imageCircle);
            iv.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));

            iv = v.findViewById(R.id.imageArrow);
            iv.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

            // error messages
            tv = v.findViewById(R.id.txtReadingErrors);
            if (errorMessage.length() > 0) {
                tv.setVisibility(View.VISIBLE);
                // TODO: put in strings.xml
                errorMessage = "Missing required patient vitals:" + "\n" + errorMessage;
                tv.setText(errorMessage);
            } else {
                tv.setVisibility(View.GONE);
            }
        }
    }

    private void updateUI_Advice(ReadingRetestAnalysis retestAnalysis) {
        String message = "";
        if (retestAnalysis.isRetestRecommendedNow()) {
            message = getString(R.string.brief_advice_retest_now);
        } else if (retestAnalysis.isRetestRecommendedIn15Min()) {
            message = getString(R.string.brief_advice_retest_after15);
        } else {
            ReadingAnalysis analysis = retestAnalysis.getMostRecentReadingAnalysis();
            message = analysis.getBriefAdviceText(getContext());
        }

        TextView tv = getView().findViewById(R.id.txtAdvice);
        tv.setText(message);
    }

    private void updateUI_RecheckVitals(ReadingRetestAnalysis retestAnalysis) {
        // recheck now
        Switch swNow = getView().findViewById(R.id.swRecheckVitalsNow);
        Switch swIn15 = getView().findViewById(R.id.swRecheckVitalsIn15);

        boolean needDefaultForRecheckVitals =
                (!currentReading.isATemporaryFlagSet(MASK_USER_HAS_CHANGED_RECHECK_OPTION))
                        && currentReading.dateLastSaved == null;
        if (needDefaultForRecheckVitals) {
            swNow.setChecked(retestAnalysis.isRetestRecommendedNow());
        } else {
            swNow.setChecked(currentReading.isNeedRecheckVitalsNow());
        }
        // ..setup initial state
        if (swNow.isChecked() && currentReading.dateRecheckVitalsNeeded == null) {
            currentReading.dateRecheckVitalsNeeded = ZonedDateTime.now();
        }
        // ..setup button
        swNow.setOnClickListener(view -> {
            currentReading.setATemporaryFlag(MASK_USER_HAS_CHANGED_RECHECK_OPTION);
            if (swNow.isChecked()) {
                swIn15.setChecked(false);
                currentReading.dateRecheckVitalsNeeded = ZonedDateTime.now();
            }

            // remove date if no recheck selected
            if (!swNow.isChecked() && !swIn15.isChecked()) {
                currentReading.dateRecheckVitalsNeeded = null;
            }
        });

        // recheck  soon
        // TODO: change text box to show how many minutes until reading needed.
        if (needDefaultForRecheckVitals) {
            swIn15.setChecked(retestAnalysis.isRetestRecommendedIn15Min());
        } else {
            swIn15.setChecked(currentReading.isNeedRecheckVitals() && !currentReading.isNeedRecheckVitalsNow());
        }
        // ..setup initial state
        if (swIn15.isChecked() && currentReading.dateRecheckVitalsNeeded == null) {
            currentReading.dateRecheckVitalsNeeded =
                    ZonedDateTime.now().plus(15, ChronoUnit.MINUTES);
        }
        swIn15.setOnClickListener(view -> {
            currentReading.setATemporaryFlag(MASK_USER_HAS_CHANGED_RECHECK_OPTION);
            if (swIn15.isChecked()) {
                swNow.setChecked(false);
                currentReading.dateRecheckVitalsNeeded =
                        ZonedDateTime.now().plus(15, ChronoUnit.MINUTES);
            }

            // remove date if no recheck selected
            if (!swNow.isChecked() && !swIn15.isChecked()) {
                currentReading.dateRecheckVitalsNeeded = null;
            }
        });

        // recommendation message
        TextView tvRecommend = getView().findViewById(R.id.txtRecheckVitalsRecommended);
        if (retestAnalysis.isRetestRecommended()) {
            if (retestAnalysis.isRetestRecommendedNow()) {
                tvRecommend.setText("Recheck vitals now is recommended");
            } else if (retestAnalysis.isRetestRecommendedIn15Min()) {
                tvRecommend.setText("Recheck vitals in 15 minutes is recommended");
            } else {
                Util.ensure(false);
            }
            tvRecommend.setVisibility(View.VISIBLE);
        } else {
            tvRecommend.setVisibility(View.GONE);
        }

        // set border color based on recommendation
        setRectangleStrokeColor(R.id.sectionRecheckVitals, retestAnalysis.isRetestRecommended());
    }

    private void updateUI_Referral(ReadingRetestAnalysis retestAnalysis) {
        Button btn = getView().findViewById(R.id.btnSendReferral);
        btn.setOnClickListener(view -> showReferralDialog());

        ImageView ivReferralSent = getView().findViewById(R.id.ivReferralSent);
        TextView tv = getView().findViewById(R.id.txtReferralSent);
        if (currentReading.referralMessageSendTime == null) {
            tv.setText(getString(R.string.reading_referral_notsent));
            ivReferralSent.setVisibility(View.GONE);
        } else {
            tv.setText(getString(R.string.reading_referral_sent,
                    currentReading.referralHealthCentre,
                    DateUtil.getFullDateString(currentReading.referralMessageSendTime)
            ));
            ivReferralSent.setVisibility(View.VISIBLE);
        }

        // recommend referral?
        boolean isReferralRecommended = retestAnalysis.getMostRecentReadingAnalysis().isReferralToHealthCentreRecommended();
        // ..message show/hide
        TextView tvRecommend = getView().findViewById(R.id.txtReferralRecommended);
        tvRecommend.setVisibility(isReferralRecommended ? View.VISIBLE : View.GONE);
        // ..button color
        if (isReferralRecommended) {
            ViewCompat.setBackgroundTintList(btn, ContextCompat.getColorStateList(getActivity(), R.color.recommended));
        } else {
            btn.setBackgroundResource(android.R.drawable.btn_default);
        }
        // ..border color
        setRectangleStrokeColor(R.id.sectionReferral, isReferralRecommended);

    }


    private void updateUI_Followup(ReadingRetestAnalysis retestAnalysis) {
        Switch swFollowup = getView().findViewById(R.id.swFollowUpNeeded);

        boolean needDefaultForFollowup =
                (!currentReading.isATemporaryFlagSet(MASK_USER_HAS_CHANGED_FOLLOW_UP))
                        && currentReading.dateLastSaved == null;
        boolean followupRecommended = retestAnalysis.getMostRecentReadingAnalysis().isRed();
        if (needDefaultForFollowup) {
            swFollowup.setChecked(followupRecommended);
        } else {
            swFollowup.setChecked(currentReading.isFlaggedForFollowup());
        }
        currentReading.setFlaggedForFollowup(swFollowup.isChecked());
        swFollowup.setOnClickListener(view -> {
            currentReading.setATemporaryFlag(MASK_USER_HAS_CHANGED_FOLLOW_UP);
            currentReading.setFlaggedForFollowup(swFollowup.isChecked());
        });

        // recommendation message
        TextView tvRecommend = getView().findViewById(R.id.txtFollowupRecommended);
        tvRecommend.setVisibility(followupRecommended ? View.VISIBLE : View.GONE);
        setRectangleStrokeColor(R.id.sectionFollowUp, followupRecommended);
    }

    private void updateUI_Uploaded(ReadingRetestAnalysis retestAnalysis) {
        // uploaded
        TextView tv = getView().findViewById(R.id.txtUploadedMessage);
        if (currentReading.dateUploadedToServer == null) {
            tv.setText(getString(R.string.reading_not_uploaded_to_server));
        } else {
            tv.setText(getString(R.string.reading_uploaded_to_server,
                    DateUtil.getFullDateString(currentReading.dateUploadedToServer)));
        }
    }


    private void setRectangleStrokeColor(int viewId, boolean showAsRecommended) {
        View sectionView = getView().findViewById(viewId);
        GradientDrawable drawable = (GradientDrawable) sectionView.getBackground();

        int colorId = showAsRecommended ? R.color.recommended : R.color.black;
        int width = showAsRecommended ? STROKE_WIDTH_RECOMMENDED : STROKE_WIDTH_NORMAL;
        int color = ContextCompat.getColor(getActivity(), colorId);

        drawable.setStroke(width, color);
    }


    /**
     * Dialogs
     */
    private void showReferralDialog() {
        // don't refer (and hence save!) if missing data
        if (currentReading.isMissingRequiredData()) {
            displayMissingDataDialog();
            return;
        }

        DialogFragment newFragment = ReferralDialogFragment.makeInstance(currentReading,
                message -> {
                    activityCallbackListener.saveCurrentReading();
                    updateUI();
                });
        newFragment.show(getFragmentManager(), "Referral");
    }

    private void displayMissingDataDialog() {
        // TODO: Put strings in xml
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext())
                .setTitle("Missing Information")
                .setMessage("Some required fields from PATIENT or CONFIRM VITALS tabs are missing. Please enter this data before referring patient.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, null);
        dialog.show();
    }


}
