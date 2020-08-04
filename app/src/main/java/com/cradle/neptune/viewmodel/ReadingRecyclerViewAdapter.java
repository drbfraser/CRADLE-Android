package com.cradle.neptune.viewmodel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.model.*;
import com.cradle.neptune.utilitiles.DateUtil;
import com.google.android.material.snackbar.Snackbar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


public class ReadingRecyclerViewAdapter extends RecyclerView.Adapter<ReadingRecyclerViewAdapter.MyViewHolder> {

    private final static int NO_ASSESSMENT_TYPE = 1;
    private final static int ASSESSMENT_TYPE = 2;
    private List<Reading> readings;
    private RecyclerView recyclerView;
    private OnClickElement onClickElementListener;

    public ReadingRecyclerViewAdapter(List<Reading> readings) {
        this.readings = readings;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {

        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.reading_card_assesment, viewGroup, false);

        if (i == NO_ASSESSMENT_TYPE) {
            v = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.reading_card_no_or_pending_assessment, viewGroup, false);
        }

        return new MyViewHolder(v);

    }

    @Override
    public int getItemViewType(int position) {
        Assessment followUpAction = readings.get(position).getFollowUp();
        if (followUpAction == null) {
            return NO_ASSESSMENT_TYPE;
        }
        return ASSESSMENT_TYPE;
    }


    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int i) {
        Reading currReading = readings.get(i);

        ReadingAnalysis analysis = currReading.getBloodPressure().getAnalysis();

        myViewHolder.readingDate.setText(DateUtil.getConciseDateString(currReading.getDateTimeTaken()));
        myViewHolder.sysBP.setText(new StringBuilder().append(currReading.getBloodPressure().getSystolic()).append("").toString());
        myViewHolder.diaBP.setText(new StringBuilder().append(currReading.getBloodPressure().getDiastolic()).append("").toString());
        myViewHolder.heartRate.setText(new StringBuilder().append(currReading.getBloodPressure().getHeartRate()).append("").toString());

        if (currReading.getUrineTest() != null) {
            myViewHolder.urineTest.setText(getUrineTestFormattedTxt(currReading.getUrineTest()));
        }
        if (!currReading.getSymptoms().isEmpty()){
            StringBuilder symptoms = new StringBuilder();
            for (String s:currReading.getSymptoms()){
                symptoms.append(s).append(", ");
            }
            myViewHolder.symptomTxt.setText(symptoms.toString());
        }
        myViewHolder.trafficLight.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));
        myViewHolder.arrow.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

        View v = myViewHolder.view;

        myViewHolder.cardView.setOnClickListener(view -> {
            // if the reading is uploaded to the server, we dont want to change it locally.
            if (currReading.isUploadedToServer()){
                Snackbar.make(v,"This reading is already uploaded to the server, " +
                        "unable to make changes to it.",Snackbar.LENGTH_LONG).show();
            }else {
                onClickElementListener.onClick(currReading.getId());
            }
        });
        myViewHolder.cardView.setOnLongClickListener(view -> {
            // if the reading is uploaded to the server, we dont want to delete it locally.
            if (currReading.isUploadedToServer()) {
                Snackbar.make(v,"This reading is already uploaded to the server, " +
                        "Cannot delete the reading.",Snackbar.LENGTH_LONG).show();
            }else {
                onClickElementListener.onLongClick(currReading.getId());
            }
            return false;
        });

        if (myViewHolder.getItemViewType() == NO_ASSESSMENT_TYPE) {

            if (currReading.isVitalRecheckRequired()) {
                myViewHolder.retakeVitalButton.setVisibility(View.VISIBLE);
                myViewHolder.retakeVitalButton.setOnClickListener(view -> onClickElementListener.onClickRecheckReading(currReading.getId()));
            }

            if (currReading.isReferredToHealthCentre()) {
                myViewHolder.isreferedTxt.setText("Referral Pending");
            }
            myViewHolder.trafficLight.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));
            myViewHolder.arrow.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

            //upload button
            setVisibilityForImageAndText(v, R.id.imgNotUploaded, R.id.tvNotUploaded, !currReading.getMetadata().isUploaded());

            //referral
            setVisibilityForImageAndText(v, R.id.imgReferred, R.id.txtReferred, currReading.isReferredToHealthCentre());
            if (currReading.isReferredToHealthCentre()) {
                String message;
                if (currReading.getReferral() != null && currReading.getReferral().getHealthFacilityName().length() > 0) {
                    message = v.getContext().getString(R.string.reading_referred_to_health_centre, currReading.getReferral().getHealthFacilityName());
                } else {
                    message = v.getContext().getString(R.string.reading_referred_to_health_centre_unknown);
                }

                TextView tv = v.findViewById(R.id.txtReferred);
                tv.setText(message);
            }


            // populate: follow-up
            setVisibilityForImageAndText(v, R.id.imgFollowUp, R.id.txtFollowUp, currReading.isFlaggedForFollowUp());

            // populate: recheck vitals
            setVisibilityForImageAndText(v, R.id.imgRecheckVitals, R.id.txtRecheckVitals, currReading.isVitalRecheckRequired());
            if (currReading.isVitalRecheckRequired()) {
                String message;
                if (currReading.isVitalRecheckRequiredNow()) {
                    message = v.getContext().getString(R.string.reading_recheck_vitals_now);
                } else {
                    long minutes = currReading.getMinutesUtilVitalRecheck();
                    if (minutes == 1) {
                        message = v.getContext().getString(R.string.reading_recheck_vitals_in_one_minute);
                    } else {
                        message = v.getContext().getString(R.string.reading_recheck_vitals_in_minutes, minutes);
                    }
                }

                TextView tvRecheckVitals = v.findViewById(R.id.txtRecheckVitals);
                tvRecheckVitals.setText(message);
            }

        } else if (myViewHolder.getItemViewType() == ASSESSMENT_TYPE) {
            Assessment readingFollowUp = currReading.getFollowUp();
            myViewHolder.diagnosis.setText(readingFollowUp.getDiagnosis());
            myViewHolder.treatment.setText(readingFollowUp.getTreatment());
            myViewHolder.hcName.setText(readingFollowUp.getHealthCareWorkerId());
            myViewHolder.referredBy.setText("Unknown"); // FIXME: no longer have referred by field
            myViewHolder.assessedBy.setText(readingFollowUp.getHealthCareWorkerId());
            myViewHolder.assessmentDate.setText(DateUtil.getFullDateFromUnix(readingFollowUp.getDateAssessed()));
            TextView specialInvestigation = v.findViewById(R.id.specialInvestigationTxt);
            TextView medPrescribed = v.findViewById(R.id.medPrescibedTxt);
            specialInvestigation.setText(readingFollowUp.getSpecialInvestigations());
            medPrescribed.setText(readingFollowUp.getMedicationPrescribed());
            if (readingFollowUp.getFollowupNeeded()) {
                myViewHolder.followUp.setText(readingFollowUp.getFollowupInstructions());
                TextView frequencyTxt = v.findViewById(R.id.followupFrequencyTxt);
                frequencyTxt.setVisibility(View.VISIBLE);
//                String txt = "Every " + readingFollowUp.getFollowUpFrequencyValue() + " " + readingFollowUp.getFollowUpFrequencyUnit().toLowerCase()
//                        + " till: " + readingFollowUp.getFollowUpNeededTill();
//                frequencyTxt.setText(txt);
                // FIXME: no longer have frequency fields
            } else {
                TextView frequencyTxt = v.findViewById(R.id.followupFrequencyTxt);
                frequencyTxt.setVisibility(View.GONE);
            }
        }

    }

    private String getUrineTestFormattedTxt(UrineTest urineTestResult) {
        return "Leukocytes: " + urineTestResult.getLeukocytes() + " , " +
                "Nitrites: " + urineTestResult.getNitrites() + " , " +
                "Protein: " + urineTestResult.getProtein() + " \n " +
                "Blood: " + urineTestResult.getBlood() + " , " +
                "Glucose: " + urineTestResult.getGlucose();
    }

    private void setVisibilityForImageAndText(View v, int imageViewId, int textViewId, boolean show) {
        ImageView iv = v.findViewById(imageViewId);
        iv.setVisibility(show ? View.VISIBLE : View.GONE);

        TextView tv = v.findViewById(textViewId);
        tv.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // Store ref to recycler
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public int getItemCount() {
        return readings.size();
    }

    public void setOnClickElementListener(OnClickElement obs) {
        onClickElementListener = obs;
    }

    private void onClick(View view) {
        int itemPosition = recyclerView.getChildLayoutPosition(view);
        String readingId = readings.get(itemPosition).getId();
        if (onClickElementListener != null) {
            onClickElementListener.onClick(readingId);
        }
    }

    public interface OnClickElement {
        void onClick(String readingId);

        // Return true if click handled
        boolean onLongClick(String readingId);

        void onClickRecheckReading(String readingId);
    }

    static class MyViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        TextView readingDate, assessmentDate, sysBP, diaBP, heartRate, diagnosis,
                treatment, followUp, assessedBy, isreferedTxt, referredBy,
                hcName, urineTest, symptomTxt;
        ImageView trafficLight, arrow;
        Button retakeVitalButton;
        View view;
        CardView cardView;

        MyViewHolder(View v) {
            super(v);
            readingDate = v.findViewById(R.id.readingDate);
            assessmentDate = v.findViewById(R.id.assessmentDate);
            sysBP = v.findViewById(R.id.sysBP);
            diaBP = v.findViewById(R.id.diaBP);
            heartRate = v.findViewById(R.id.readingHeartRate);
            diagnosis = v.findViewById(R.id.readingdiagnosis);
            treatment = v.findViewById(R.id.readingTreatment);
            assessedBy = v.findViewById(R.id.assessedBy);
            trafficLight = v.findViewById(R.id.readingTrafficLight);
            arrow = v.findViewById(R.id.readingArrow);
            retakeVitalButton = v.findViewById(R.id.newReadingButton);
            followUp = v.findViewById(R.id.followupTreatment);
            view = v;
            cardView = v.findViewById(R.id.readingCardview);
            isreferedTxt = v.findViewById(R.id.isReferrerdText);
            referredBy = v.findViewById(R.id.treatmentReferedBy);
            hcName = v.findViewById(R.id.hcReferred);
            urineTest = v.findViewById(R.id.urineResultTxt);
            symptomTxt = v.findViewById(R.id.symptomtxt);
        }
    }
}
