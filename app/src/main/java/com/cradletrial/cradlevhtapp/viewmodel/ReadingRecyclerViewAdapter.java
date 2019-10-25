package com.cradletrial.cradlevhtapp.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.model.ReadingAnalysis;
import com.cradletrial.cradlevhtapp.utilitiles.DateUtil;
import com.cradletrial.cradlevhtapp.view.PatientProfileActivity;
import com.cradletrial.cradlevhtapp.view.ReadingActivity;

import java.util.List;

import static com.cradletrial.cradlevhtapp.view.ReadingsListActivity.READING_ACTIVITY_DONE;

public class ReadingRecyclerViewAdapter extends RecyclerView.Adapter<ReadingRecyclerViewAdapter.MyViewHolder> {
    private static final int VIEWTYPE_ASSESSMENT =0;
    private static final int VIEWTYPE_NO_ASSESSMENT=1;
    private List<Reading> readings;
    private Context context;

    public ReadingRecyclerViewAdapter(List<Reading> readings, Context context) {
        this.readings = readings;
        Log.d("bugg","size of listL "+readings.size());
        this.context = context;

            }



    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {

        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.reading_card_no_or_pending_assessment, viewGroup, false);
        return new MyViewHolder(v);

    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int i) {
        Reading currReading = readings.get(i);
        Log.d("bugg", "onbind referral: " + currReading.isReferredToHealthCentre() + " " + currReading.readingId + " " + i);

        ReadingAnalysis analysis = ReadingAnalysis.analyze(currReading);

        myViewHolder.readingDate.setText(DateUtil.getConciseDateString(currReading.dateTimeTaken));
        myViewHolder.sysBP.setText(currReading.bpSystolic + "");
        myViewHolder.diaBP.setText(currReading.bpDiastolic + "");
        myViewHolder.heartRate.setText(currReading.heartRateBPM + "");

        if (currReading.isNeedRecheckVitals()) {
            myViewHolder.retakeVitalButton.setVisibility(View.VISIBLE);
        }
        myViewHolder.trafficLight.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));
        myViewHolder.arrow.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

        ConstraintLayout v = myViewHolder.layout;
        //upload button
        setVisibilityForImageAndText(v, R.id.imgNotUploaded, R.id.tvNotUploaded, !currReading.isUploaded());

        //referral
        setVisibilityForImageAndText(v, R.id.imgReferred, R.id.txtReferred, currReading.isReferredToHealthCentre());
        if (currReading.isReferredToHealthCentre()) {
            String message;
            if (currReading.referralHealthCentre != null && currReading.referralHealthCentre.length() > 0) {
                message = v.getContext().getString(R.string.reading_referred_to_health_centre, currReading.referralHealthCentre);
            } else {
                message = v.getContext().getString(R.string.reading_referred_to_health_centre_unknown);
            }

            TextView tv = v.findViewById(R.id.txtReferred);
            tv.setText(message);
        }


        // populate: follow-up
        setVisibilityForImageAndText(v, R.id.imgFollowUp, R.id.txtFollowUp, currReading.isFlaggedForFollowup());

        // populate: recheck vitals
        setVisibilityForImageAndText(v, R.id.imgRecheckVitals, R.id.txtRecheckVitals, currReading.isNeedRecheckVitals());
        if (currReading.isNeedRecheckVitals()) {
            String message;
            if (currReading.isNeedRecheckVitalsNow()) {
                message = v.getContext().getString(R.string.reading_recheck_vitals_now);
            } else {
                long minutes = currReading.getMinutesUntilNeedRecheckVitals();
                if (minutes == 1) {
                    message = v.getContext().getString(R.string.reading_recheck_vitals_in_one_minute);
                } else {
                    message = v.getContext().getString(R.string.reading_recheck_vitals_in_minutes, minutes);
                }
            }

            TextView tvRecheckVitals = v.findViewById(R.id.txtRecheckVitals);
            tvRecheckVitals.setText(message);
        }
        //todo: setup on click listner for cardview and open the summary page

    }
    private void setVisibilityForImageAndText(View v, int imageViewId, int textViewId, boolean show) {
        ImageView iv = v.findViewById(imageViewId);
        iv.setVisibility( show ? View.VISIBLE : View.GONE);

        TextView tv = v.findViewById(textViewId);
        tv.setVisibility( show ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return readings.size();
    }

     static class MyViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        TextView readingDate, assessmentDate, sysBP, diaBP,heartRate,diagnosis,treatment,other;
        ImageView trafficLight, arrow;
        Button retakeVitalButton;
        CardView readingCardView;
        ConstraintLayout layout;
         MyViewHolder(View v) {
            super(v);
            readingDate = v.findViewById(R.id.readingDate);
            assessmentDate =v.findViewById(R.id.assessmentDate);
            sysBP = v.findViewById(R.id.sysBP);
            diaBP = v.findViewById(R.id.diaBP);
            heartRate = v.findViewById(R.id.readingHeartRate);
            diagnosis = v.findViewById(R.id.readingdiagnosis);
            treatment = v.findViewById(R.id.readingTreatment);
            other = v.findViewById(R.id.readingOther);
            trafficLight = v.findViewById(R.id.readingTrafficLight);
            arrow = v.findViewById(R.id.readingArrow);
            retakeVitalButton = v.findViewById(R.id.newReadingButton);
            readingCardView = v.findViewById(R.id.readingCardview);
            layout = v.findViewById(R.id.readingLayout);
        }
    }
}
