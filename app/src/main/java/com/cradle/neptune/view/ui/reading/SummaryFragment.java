package com.cradle.neptune.view.ui.reading;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;

import com.cradle.neptune.R;
import com.cradle.neptune.model.*;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.viewmodel.ReadingAnalysisViewSupport;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.temporal.ChronoUnit;

import static com.cradle.neptune.utilitiles.Util.mapNullable;

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

        RetestGroup retestGroup = viewModel.buildRetestGroup(readingManager);
//        ReadingRetestAnalysis readingRetestAnalysis
//                = new ReadingRetestAnalysis(viewModel, readingManager, getContext());

        if (retestGroup != null) {
            updateUI_PatientInfo(retestGroup);
            updateUI_Readings(retestGroup);
            updateUI_Advice(retestGroup);
            updateUI_RecheckVitals(retestGroup);
            updateUI_Referral(retestGroup);
            updateUI_Followup(retestGroup);
            updateUI_Uploaded(retestGroup);
        }
    }

    private void updateUI_PatientInfo(RetestGroup retestAnalysis) {
        TextView tv;
//        String errorMessage = "";

        // name
        String name = viewModel.getPatientName();
//        String name = currentReading.getPatient().getName();
        if (Util.isNullOrEmpty(name)) {
            name = "No name";
//            errorMessage += "- patient initials" + "\n";
        }

        // age
        String age = viewModel.getPatientDob();
//        String age = currentReading.getPatient().getDob();
        if (Util.isNullOrEmpty(age)) {
            age = "";
//            errorMessage += "- patient age" + "\n";
        }

        // gestational age
        String ga = "No gestational age";
        WeeksAndDays gaStruct = mapNullable(viewModel.getPatientGestationalAge(), GestationalAge::getAge);
        if (!viewModel.getPatientIsPregnant()) {
            ga = "Not pregnant";
        } else if (gaStruct != null) {
            ga = getString(R.string.reading_gestational_age_in_weeks_and_days, gaStruct.getWeeks(), gaStruct.getDays());
        }
//        WeeksAndDays gaStruct = currentReading.getPatient().getGestationalAgeInWeeksAndDays();
//        if (currentReading.getPatient().getGestationalAgeUnit() == GestationalAgeUnit.NONE) {
//            ga = getString(R.string.reading_gestational_age_not_pregnant);
//        } else if (gaStruct == null) {
//            ga = "No gestational age";
//            errorMessage += "- gestational age" + "\n";
//        } else {
//            ga = getString(R.string.reading_gestational_age_in_weeks_and_days, gaStruct.getWeeks(), gaStruct.getDays());
//        }

        // display "name, age @ ga"
        tv = getView().findViewById(R.id.txtPatientHeader);
        tv.setText(getString(R.string.reading_name_age_ga, name, age, ga));

        // patient id
        tv = getView().findViewById(R.id.txtPatientId);
        if (!Util.isNullOrEmpty(viewModel.getPatientId())) {
//        if (!Util.isNullOrEmpty(currentReading.getPatient().getId())) {
            tv.setText(getString(R.string.reading_patient_id, viewModel.getPatientId()));
//            tv.setText(getString(R.string.reading_patient_id, currentReading.getPatient().getId()));
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
//            errorMessage += "- patient ID" + "\n";
        }

        // symptoms
        tv = getView().findViewById(R.id.txtSymptoms);
        if (!Util.isNullOrEmpty(viewModel.getSymptomsString())) {
//        if (!Util.isNullOrEmpty(currentReading.getSymptomsString())) {
            tv.setText(viewModel.getSymptomsString());
//            tv.setText(currentReading.getSymptomsString());
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
//        if (currentReading.isMissingRequiredSymptoms()) {
//            errorMessage += "- symptoms" + "\n";
//        }

        // error messages
        tv = getView().findViewById(R.id.txtPatientInfoErrors);
        if (viewModel.isMissingAnything()) {
//        if (errorMessage.length() > 0) {
            tv.setVisibility(View.VISIBLE);
            // TODO: put in strings.xml
            String errorMessage = viewModel.missingPatientInfoDescription();
//            errorMessage = "Missing required patient information:" + "\n" + errorMessage;
            tv.setText(errorMessage);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void updateUI_Readings(RetestGroup retestAnalysis) {

        // remove any current readings
        LinearLayout layoutReadings = getView().findViewById(R.id.layoutReadings);
        layoutReadings.removeAllViews();

        // display all readings:
        for (int i = 0; i < retestAnalysis.getSize(); i++) {
            Reading reading = retestAnalysis.getReadings().get(i);
            ReadingAnalysis analysis = retestAnalysis.getAnalyses().get(i);

            TextView tv;
            ImageView iv;
//            String errorMessage = "";

            // create new layout for this reading
            View v = View.inflate(getActivity(), R.layout.reading_vitals_with_icons, null);
            layoutReadings.addView(v);

            // date & condition summary
            String time = DateUtil.getDateString(reading.getDateTimeTaken());
            String analysisText = analysis.getAnalysisText(getContext());
            tv = v.findViewById(R.id.txtReadingHeading);
            tv.setText(getString(R.string.reading_time_summary,
                    time, analysisText));
//
//            if (time.length() == 0) {
//                errorMessage += "- date/time of reading" + "\n";
//            }

            // blood pressure
            tv = v.findViewById(R.id.txtBloodPressure);
            Integer sys = reading.getBloodPressure().getSystolic();
            Integer dia = reading.getBloodPressure().getDiastolic();
            if (sys != null && dia != null) {
                tv.setText(getString(R.string.reading_blood_pressure,
                        sys, dia));
                tv.setVisibility(View.VISIBLE);
            } else {
//                errorMessage += "- blood pressure (systolic and/or diastolic)" + "\n";
                tv.setVisibility(View.GONE);
            }

            // heart rate
            tv = v.findViewById(R.id.txtHeartRate);
            if (viewModel.getBloodPressure() == null) {
//            if (reading.getBloodPressure().getHeartRate() != null) {
                tv.setText(getString(R.string.reading_heart_rate,
                            reading.getBloodPressure().getHeartRate()));
                tv.setVisibility(View.VISIBLE);
            } else {
//                errorMessage += "- heart rate" + "\n";
                tv.setVisibility(View.GONE);
            }

            // icons
            iv = v.findViewById(R.id.imageCircle);
            iv.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));

            iv = v.findViewById(R.id.imageArrow);
            iv.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

            // error messages
            tv = v.findViewById(R.id.txtReadingErrors);
            if (viewModel.isMissingAnything()) {
//            if (errorMessage.length() > 0) {
                tv.setVisibility(View.VISIBLE);
                // TODO: put in strings.xml
                String errorMessage = viewModel.missingVitalInformationDescription();
//                errorMessage = "Missing required patient vitals:" + "\n" + errorMessage;
                tv.setText(errorMessage);
            } else {
                tv.setVisibility(View.GONE);
            }
        }
    }

    private void updateUI_Advice(RetestGroup retestAnalysis) {
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

    private void updateUI_RecheckVitals(RetestGroup retestAnalysis) {
        // recheck now
        Switch swNow = getView().findViewById(R.id.swRecheckVitalsNow);
        Switch swIn15 = getView().findViewById(R.id.swRecheckVitalsIn15);

        boolean needDefaultForRecheckVitals = viewModel.getMetadata().getDateLastSaved() == null;
//        boolean needDefaultForRecheckVitals =
//                (!currentReading.isATemporaryFlagSet(MASK_USER_HAS_CHANGED_RECHECK_OPTION))
//                        && currentReading.dateLastSaved == null;
        if (needDefaultForRecheckVitals) {
            swNow.setChecked(retestAnalysis.isRetestRecommendedNow());
        } else {
            swNow.setChecked(viewModel.isVitalRecheckRequiredNow());
//            swNow.setChecked(currentReading.isNeedRecheckVitalsNow());
        }
        // ..setup initial state
        if (swNow.isChecked() && viewModel.getDateRecheckVitalsNeeded() == null) {
            viewModel.setDateRecheckVitalsNeeded(ZonedDateTime.now());
        }
//        if (swNow.isChecked() && currentReading.dateRecheckVitalsNeeded == null) {
//            currentReading.dateRecheckVitalsNeeded = ZonedDateTime.now();
//        }
        // ..setup button
        swNow.setOnClickListener(view -> {
//            currentReading.setATemporaryFlag(MASK_USER_HAS_CHANGED_RECHECK_OPTION);
            if (swNow.isChecked()) {
                swIn15.setChecked(false);
                viewModel.setDateRecheckVitalsNeeded(ZonedDateTime.now());
//                currentReading.dateRecheckVitalsNeeded = ZonedDateTime.now();
            }

            // remove date if no recheck selected
            if (!swNow.isChecked() && !swIn15.isChecked()) {
                viewModel.setDateRecheckVitalsNeeded(null);
//                currentReading.dateRecheckVitalsNeeded = null;
            }
        });

        // recheck  soon
        // TODO: change text box to show how many minutes until reading needed.
        if (needDefaultForRecheckVitals) {
            swIn15.setChecked(retestAnalysis.isRetestRecommendedIn15Min());
        } else {
            swIn15.setChecked(viewModel.isVitalRecheckRequired() && !viewModel.isVitalRecheckRequiredNow());
//            swIn15.setChecked(currentReading.isNeedRecheckVitals() && !currentReading.isNeedRecheckVitalsNow());
        }
        // ..setup initial state
        if (swIn15.isChecked() && viewModel.getDateRecheckVitalsNeeded() == null) {
            viewModel.setDateRecheckVitalsNeeded(ZonedDateTime.now().plus(15, ChronoUnit.MINUTES));
        }
//        if (swIn15.isChecked() && currentReading.dateRecheckVitalsNeeded == null) {
//            currentReading.dateRecheckVitalsNeeded =
//                    ZonedDateTime.now().plus(15, ChronoUnit.MINUTES);
//        }
        swIn15.setOnClickListener(view -> {
//            currentReading.setATemporaryFlag(MASK_USER_HAS_CHANGED_RECHECK_OPTION);
            if (swIn15.isChecked()) {
                swNow.setChecked(false);
                viewModel.setDateRecheckVitalsNeeded(ZonedDateTime.now().plus(15, ChronoUnit.MINUTES));
//                currentReading.dateRecheckVitalsNeeded =
//                        ZonedDateTime.now().plus(15, ChronoUnit.MINUTES);
            }

            // remove date if no recheck selected
            if (!swNow.isChecked() && !swIn15.isChecked()) {
                viewModel.setDateRecheckVitalsNeeded(null);
//                currentReading.dateRecheckVitalsNeeded = null;
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

    private void updateUI_Referral(RetestGroup retestAnalysis) {
        Button btn = getView().findViewById(R.id.btnSendReferral);
        btn.setOnClickListener(view -> showReferralDialog());

        ImageView ivReferralSent = getView().findViewById(R.id.ivReferralSent);
        TextView tv = getView().findViewById(R.id.txtReferralSent);
        if (viewModel.getReferral() == null) {
//        if (currentReading.referralMessageSendTime == null) {
            tv.setText(getString(R.string.reading_referral_notsent));
            ivReferralSent.setVisibility(View.GONE);
        } else {
            tv.setText(getString(R.string.reading_referral_sent,
                    viewModel.getReferral().getHealthCentre(),
                    DateUtil.getFullDateFromMilliSeconds(viewModel.getReferral().getMessageSendTimeInMS())));
//            tv.setText(getString(R.string.reading_referral_sent,
//                    currentReading.referralHealthCentre,
//                    DateUtil.getFullDateString(currentReading.referralMessageSendTime)
//            ));
            ivReferralSent.setVisibility(View.VISIBLE);
        }

        // recommend referral?
        boolean isReferralRecommended = retestAnalysis.getMostRecentReadingAnalysis().isReferralRecommended();
//        boolean isReferralRecommended = retestAnalysis.getMostRecentReadingAnalysis().isReferralToHealthCentreRecommended();
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


    private void updateUI_Followup(RetestGroup retestAnalysis) {
        Switch swFollowup = getView().findViewById(R.id.swFollowUpNeeded);

        boolean needDefaultForFollowup = viewModel.getDateTimeTaken() == null;
//        boolean needDefaultForFollowup =
//                (!currentReading.isATemporaryFlagSet(MASK_USER_HAS_CHANGED_FOLLOW_UP))
//                        && currentReading.dateLastSaved == null;
        boolean followupRecommended = retestAnalysis.getMostRecentReadingAnalysis().isRed();
        if (needDefaultForFollowup) {
            swFollowup.setChecked(followupRecommended);
        } else {
            swFollowup.setChecked(viewModel.isFlaggedForFollowUp());
//            swFollowup.setChecked(currentReading.isFlaggedForFollowup());
        }
        viewModel.setFlaggedForFollowUp(swFollowup.isChecked());
//        currentReading.setFlaggedForFollowup(swFollowup.isChecked());
        swFollowup.setOnClickListener(view -> {
            viewModel.setFlaggedForFollowUp(swFollowup.isChecked());
//            currentReading.setATemporaryFlag(MASK_USER_HAS_CHANGED_FOLLOW_UP);
//            currentReading.setFlaggedForFollowup(swFollowup.isChecked());
        });

        // recommendation message
        TextView tvRecommend = getView().findViewById(R.id.txtFollowupRecommended);
        tvRecommend.setVisibility(followupRecommended ? View.VISIBLE : View.GONE);
        setRectangleStrokeColor(R.id.sectionFollowUp, followupRecommended);
    }

    private void updateUI_Uploaded(RetestGroup retestAnalysis) {
        // uploaded
        TextView tv = getView().findViewById(R.id.txtUploadedMessage);
        if (viewModel.getMetadata().getDateUploadedToServer() == null) {
//        if (currentReading.dateUploadedToServer == null) {
            tv.setText(getString(R.string.reading_not_uploaded_to_server));
        } else {
            tv.setText(getString(R.string.reading_uploaded_to_server,
                    DateUtil.getFullDateFromMilliSeconds(viewModel.getMetadata().getDateUploadedToServer()*Referral.MS_IN_SECOND)));
//            tv.setText(getString(R.string.reading_uploaded_to_server,
//                    DateUtil.getFullDateString(currentReading.dateUploadedToServer)));
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
        if (viewModel.isMissingAnything()) {
//        if (currentReading.isMissingRequiredData()) {
            displayMissingDataDialog();
            return;
        }

        DialogFragment newFragment = ReferralDialogFragment.makeInstance(viewModel,
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
