package com.cradletrial.cradlevhtapp.viewmodel;

import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
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

import java.util.List;


public class ReadingRecyclerViewAdapter extends RecyclerView.Adapter<ReadingRecyclerViewAdapter.MyViewHolder> {

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
                .inflate(R.layout.reading_card_no_or_pending_assessment, viewGroup, false);
        return new MyViewHolder(v);

    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int i) {
        Reading currReading = readings.get(i);

        ReadingAnalysis analysis = ReadingAnalysis.analyze(currReading);

        myViewHolder.readingDate.setText(DateUtil.getConciseDateString(currReading.dateTimeTaken));
        myViewHolder.sysBP.setText(new StringBuilder().append(currReading.bpSystolic).append("").toString());
        myViewHolder.diaBP.setText(new StringBuilder().append(currReading.bpDiastolic).append("").toString());
        myViewHolder.heartRate.setText(new StringBuilder().append(currReading.heartRateBPM).append("").toString());

        if (currReading.isNeedRecheckVitals()) {
            myViewHolder.retakeVitalButton.setVisibility(View.VISIBLE);
            myViewHolder.retakeVitalButton.setOnClickListener(view -> onClickElementListener.onClickRecheckReading(currReading.readingId));
        }
        myViewHolder.trafficLight.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));
        myViewHolder.arrow.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));

        View v = myViewHolder.view;
        myViewHolder.cardView.setOnClickListener(view -> onClickElementListener.onClick(currReading.readingId));

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
        long readingId = readings.get(itemPosition).readingId;
        if (onClickElementListener != null) {
            onClickElementListener.onClick(readingId);
        }
    }

    public interface OnClickElement {
        void onClick(long readingId);

        // Return true if click handled
        boolean onLongClick(long readingId);

        void onClickRecheckReading(long readingId);
    }

    static class MyViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        TextView readingDate, assessmentDate, sysBP, diaBP, heartRate, diagnosis, treatment, other;
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
            other = v.findViewById(R.id.readingOther);
            trafficLight = v.findViewById(R.id.readingTrafficLight);
            arrow = v.findViewById(R.id.readingArrow);
            retakeVitalButton = v.findViewById(R.id.newReadingButton);
            view = v;
            cardView = v.findViewById(R.id.readingCardview);
        }
    }
}
