package com.cradle.neptune.viewmodel;

import android.content.Context;
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
import com.cradle.neptune.model.Assessment;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingAnalysis;
import com.cradle.neptune.model.UrineTest;
import com.cradle.neptune.utilitiles.DateUtil;
import com.google.android.material.snackbar.Snackbar;

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
            myViewHolder.urineTest.setText(
                    getUrineTestFormattedTxt(currReading.getUrineTest())
            );
        }
        if (!currReading.getSymptoms().isEmpty()) {
            StringBuilder symptomsStringBuilder = new StringBuilder();
            // TODO: make it so that the symptoms that are sent via api are forced to be in English
            final List<String> symptoms = currReading.getSymptoms();
            for (int j = 0; j < symptoms.size(); j++) {
                symptomsStringBuilder.append(symptoms.get(j));
                if (j < symptoms.size() - 1) {
                    symptomsStringBuilder.append(", ");
                }
            }
            myViewHolder.symptomTxt.setText(symptomsStringBuilder.toString());
        }
        myViewHolder.trafficLight.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));
        myViewHolder.arrow.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

        View v = myViewHolder.view;

        myViewHolder.cardView.setOnClickListener(view -> {
            // if the reading is uploaded to the server, we dont want to change it locally.
            if (currReading.isUploadedToServer()) {
                Snackbar.make(
                        v,
                        R.string.patient_profile_reading_already_uploaded_snackbar,
                        Snackbar.LENGTH_LONG
                ).show();
            } else {
                onClickElementListener.onClick(currReading.getId());
            }
        });
        myViewHolder.cardView.setOnLongClickListener(view -> {
            // if the reading is uploaded to the server, we don't want to delete it locally.
            if (currReading.isUploadedToServer()) {
                Snackbar.make(
                        v,
                        R.string.patient_profile_reading_already_uploaded_cannot_delete_snackbar,
                        Snackbar.LENGTH_LONG
                ).show();
            } else {
                onClickElementListener.onLongClick(currReading.getId());
            }
            return false;
        });

        if (myViewHolder.getItemViewType() == NO_ASSESSMENT_TYPE) {
            if (currReading.isVitalRecheckRequired()) {
                myViewHolder.retakeVitalButton.setVisibility(View.VISIBLE);
                myViewHolder.retakeVitalButton.setOnClickListener(view -> onClickElementListener.onClickRecheckReading(currReading.getId()));
            }

            if (currReading.isReferredToHealthFacility()) {
                myViewHolder.isreferedTxt.setText(R.string.patient_profile_reading_referral_pending);
            }
            myViewHolder.trafficLight.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));
            myViewHolder.arrow.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

            //upload button
            setVisibilityForImageAndText(v, R.id.imgNotUploaded, R.id.tvNotUploaded, !currReading.isUploadedToServer());

            //referral
            setVisibilityForImageAndText(v, R.id.imgReferred, R.id.txtReferred, currReading.isReferredToHealthFacility());
            if (currReading.isReferredToHealthFacility()) {
                final String message;
                if (currReading.getReferral() != null
                        && currReading.getReferral().getHealthFacilityName().length() > 0) {
                    message = v.getContext().getString(R.string.reading_referred_to_health_facility, currReading.getReferral().getHealthFacilityName());
                } else {
                    message = v.getContext().getString(R.string.reading_referred_to_health_facility_unknown);
                }

                TextView tv = v.findViewById(R.id.txtReferred);
                tv.setText(message);
            }


            // populate: follow-up
            setVisibilityForImageAndText(v, R.id.imgFollowUp, R.id.txtFollowUp, currReading.isFlaggedForFollowUp());

            // populate: recheck vitals
            setVisibilityForImageAndText(v, R.id.imgRecheckVitals, R.id.txtRecheckVitals, currReading.isVitalRecheckRequired());
            if (currReading.isVitalRecheckRequired()) {
                final String message;
                if (currReading.isVitalRecheckRequiredNow()) {
                    message = v.getContext().getString(R.string.reading_recheck_vitals_now);
                } else {
                    long minutes = currReading.getMinutesUntilVitalRecheck();
                    if (minutes <= 1) {
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
            myViewHolder.hcName.setText(Integer.toString(readingFollowUp.getHealthCareWorkerId()));
            myViewHolder.referredBy.setText(R.string.patient_profile_reading_unknown_referrer); // FIXME: no longer have referred by field
            myViewHolder.assessedBy.setText(Integer.toString(readingFollowUp.getHealthCareWorkerId()));
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
        final Context context = recyclerView.getContext();
        return context.getString(R.string.urine_test_layout_leukocytes) + ":"
                        + urineTestResult.getLeukocytes() + ", "
                + context.getString(R.string.urine_test_layout_nitrites) + ":"
                        + urineTestResult.getNitrites() + ",\n"
                + context.getString(R.string.urine_test_layout_protein) + ":"
                        + urineTestResult.getProtein() + ", "
                + context.getString(R.string.urine_test_layout_blood) + ":"
                        + urineTestResult.getBlood() + ",\n"
                + context.getString(R.string.urine_test_layout_glucose) + ":"
                        + urineTestResult.getGlucose();

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
