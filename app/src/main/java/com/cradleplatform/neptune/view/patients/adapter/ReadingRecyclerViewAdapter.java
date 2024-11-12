package com.cradleplatform.neptune.view.patients.adapter;

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

import com.cradleplatform.neptune.R;
import com.cradleplatform.neptune.model.Assessment;
import com.cradleplatform.neptune.model.Reading;
import com.cradleplatform.neptune.model.ReadingAnalysis;
import com.cradleplatform.neptune.model.Referral;
import com.cradleplatform.neptune.model.SymptomsState;
import com.cradleplatform.neptune.model.UrineTest;
import com.cradleplatform.neptune.utilities.DateUtil;
import com.cradleplatform.neptune.viewmodel.ReadingAnalysisViewSupport;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;


public class ReadingRecyclerViewAdapter extends RecyclerView.Adapter<ReadingRecyclerViewAdapter.MyViewHolder> {

    private final static int READING_VIEW = 3;
    private final static int REFERRAL_PENDING = 4;
    private final static int REFERRAL_CANCELLED = 5;
    private final static int REFERRAL_ASSESSED = 6;
    private final static int ASSESSMENT_VIEW = 7;
    private List<?> combinedList;
    private RecyclerView recyclerView;
    private OnClickElement onClickElementListener;

    public ReadingRecyclerViewAdapter(List<?> combinedList) {
        this.combinedList = combinedList;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v;
        switch(i){
            case ASSESSMENT_VIEW:
                v = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.assessment_card, viewGroup, false);
                break;
            case REFERRAL_PENDING:
                v = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.referral_pending_card, viewGroup, false);
                break;
            case REFERRAL_CANCELLED:
                v = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.referral_cancelled_card, viewGroup, false);
                break;
            case REFERRAL_ASSESSED:
                v = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.referral_assessed_card, viewGroup, false);
                break;
            default:
                v = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.reading_card, viewGroup, false);
        }
        return new MyViewHolder(v);
    }

    @Override
    public int getItemViewType(int position) {
        String viewType = combinedList.get(position).getClass().getName();
        //assign view
        switch (viewType) {
            case "com.cradleplatform.neptune.model.Assessment":
                return ASSESSMENT_VIEW;
            case "com.cradleplatform.neptune.model.Referral":
                Referral currReferral = (Referral) combinedList.get(position);
                if (currReferral.isAssessed())
                    return REFERRAL_ASSESSED;
                else if (currReferral.isCancelled())
                    return REFERRAL_CANCELLED;
                else
                    return REFERRAL_PENDING;
            default:
                return READING_VIEW;
        }
    }


    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int i) {
        switch(myViewHolder.getItemViewType()){
            case ASSESSMENT_VIEW:
                Assessment currAssessment = (Assessment) combinedList.get(i);
                myViewHolder.assessmentDate.setText(DateUtil.getConciseDateString(currAssessment.getDateAssessed(), false));
                myViewHolder.investigateAndResults.setText(currAssessment.getSpecialInvestigations());
                myViewHolder.finalDiagnosis.setText(currAssessment.getDiagnosis());
                myViewHolder.treatmentOp.setText(currAssessment.getTreatment());
                myViewHolder.medication.setText(currAssessment.getMedicationPrescribed());
                myViewHolder.followUp.setText(currAssessment.getFollowupInstructions());
                break;
            case REFERRAL_ASSESSED:
                Referral currReferral = (Referral) combinedList.get(i);
                myViewHolder.referralDate.setText(DateUtil.getConciseDateString(currReferral.getDateReferred(), false));
                myViewHolder.refAssessmentDate.setText("N/A");
                myViewHolder.referralLocation.setText(currReferral.getReferralHealthFacilityName());
                myViewHolder.referralComments.setText(currReferral.getComment());
                break;
            case REFERRAL_CANCELLED:
                currReferral = (Referral) combinedList.get(i);
                myViewHolder.referralDate.setText(DateUtil.getConciseDateString(currReferral.getDateReferred(), false));
                myViewHolder.refAssessmentDate.setText("N/A");
                myViewHolder.referralLocation.setText(currReferral.getReferralHealthFacilityName());
                myViewHolder.referralComments.setText(currReferral.getComment());
                myViewHolder.cancellationReason.setText(currReferral.getCancelReason());
                break;
            case REFERRAL_PENDING:
                currReferral = (Referral) combinedList.get(i);
//                myViewHolder.readingDate.setText(DateUtil.getConciseDateString(currReferral.getDateReferred(), false));
                myViewHolder.referralDate.setText(DateUtil.getConciseDateString(currReferral.getDateReferred(), false));
                myViewHolder.referralLocation.setText(currReferral.getReferralHealthFacilityName());
                myViewHolder.referralComments.setText(currReferral.getComment());
                break;
            case READING_VIEW:
                Reading currReading = (Reading) combinedList.get(i);
                ReadingAnalysis analysis = currReading.getBloodPressure().getAnalysis();

                myViewHolder.readingDate.setText(DateUtil.getConciseDateString(currReading.getDateTimeTaken(), false));
                myViewHolder.sysBP.setText(new StringBuilder().append(currReading.getBloodPressure().getSystolic()).append("").toString());
                myViewHolder.diaBP.setText(new StringBuilder().append(currReading.getBloodPressure().getDiastolic()).append("").toString());
                myViewHolder.heartRate.setText(new StringBuilder().append(currReading.getBloodPressure().getHeartRate()).append("").toString());

                if (currReading.getUrineTest() != null) {
                    myViewHolder.urineTest.setText(
                            getUrineTestFormattedTxt(currReading.getUrineTest())
                    );
                }
                SymptomsState symptomsState = new SymptomsState(currReading.getSymptoms(), myViewHolder.itemView.getContext());

                StringBuilder symptomsStringBuilder = new StringBuilder();
                String[] defaultSymptoms = myViewHolder.itemView.getResources()
                        .getStringArray(R.array.reading_symptoms);

                final List<String> symptoms = symptomsState.buildSymptomsList(defaultSymptoms, true);
                for (int j = 0; j < symptoms.size(); j++) {
                    symptomsStringBuilder.append(symptoms.get(j));
                    if (j < symptoms.size() - 1) {
                        symptomsStringBuilder.append(", ");
                    }
                }

                myViewHolder.symptomTxt.setText(symptomsStringBuilder.toString());

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

                //No assessment type for reading card
                if (currReading.getFollowUp() == null) {
                    if (currReading.isVitalRecheckRequired()) {
                        myViewHolder.retakeVitalButton.setVisibility(View.VISIBLE);
                        myViewHolder.retakeVitalButton.setOnClickListener(view -> onClickElementListener.onClickRecheckReading(currReading.getId()));
                    }

                    myViewHolder.trafficLight.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));
                    myViewHolder.arrow.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

                    //upload button
                    setVisibilityForImageAndText(v, R.id.imgNotUploaded, R.id.tvNotUploaded, !currReading.isUploadedToServer());


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
        return combinedList.size();
    }

    public void setOnClickElementListener(OnClickElement obs) {
        onClickElementListener = obs;
    }

    private void onClick(View view) {
        int itemPosition = recyclerView.getChildLayoutPosition(view);
        if (getItemViewType(itemPosition) == READING_VIEW) {
            Reading currReading = (Reading) combinedList.get(itemPosition);
            String readingId = currReading.getId();
            if (onClickElementListener != null) {
                onClickElementListener.onClick(readingId);
            }
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
        TextView readingDate, sysBP, diaBP, heartRate,
                urineTest, symptomTxt, referralDate, refAssessmentDate,
                referralLocation, referralComments, cancellationReason,
                assessmentDate, assessedBy, investigateAndResults,
                finalDiagnosis, treatmentOp, medication,
                followUp;
        ImageView trafficLight, arrow;
        Button retakeVitalButton;
        View view;
        CardView cardView;

        MyViewHolder(View v) {
            super(v);
            readingDate = v.findViewById(R.id.readingDate);
            sysBP = v.findViewById(R.id.sysBP);
            diaBP = v.findViewById(R.id.diaBP);
            heartRate = v.findViewById(R.id.readingHeartRate);
            trafficLight = v.findViewById(R.id.readingTrafficLight);
            arrow = v.findViewById(R.id.readingArrow);
            retakeVitalButton = v.findViewById(R.id.newReadingButton);
            view = v;
            cardView = v.findViewById(R.id.readingCardview);
            urineTest = v.findViewById(R.id.urineResultTxt);
            symptomTxt = v.findViewById(R.id.symptomtxt);
            referralDate = v.findViewById(R.id.referralDate);
            refAssessmentDate = v.findViewById(R.id.referralAssessmentDate);
            referralLocation = v.findViewById(R.id.referralLocation);
            referralComments = v.findViewById(R.id.referralComments);
            cancellationReason = v.findViewById(R.id.cancellationReason);
            assessmentDate = v.findViewById(R.id.assessmentCardDateTxt);
            investigateAndResults = v.findViewById(R.id.specialInvestigationsAndResultsTxt);
            finalDiagnosis = v.findViewById(R.id.finalDiagnosisTxt);
            treatmentOp = v.findViewById(R.id.treatmentOperationTxt);
            medication = v.findViewById(R.id.medicationPrescribedTxt);
            followUp = v.findViewById(R.id.assessmentFollowUpTxt);
        }
    }
}
