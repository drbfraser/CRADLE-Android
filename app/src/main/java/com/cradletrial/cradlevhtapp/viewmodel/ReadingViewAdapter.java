package com.cradletrial.cradlevhtapp.viewmodel;

import android.support.annotation.NonNull;
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

/**
 * Show a single Reading in the list
 * source: https://developer.android.com/guide/topics/ui/layout/recyclerview#java
 */
public class ReadingViewAdapter extends RecyclerView.Adapter<ReadingViewAdapter.MyViewHolder> {
    private List<Reading> readings;
    private RecyclerView recyclerView;

    public interface OnClickElement {
        void onClick(long readingId);
        // Return true if click handled
        boolean onLongClick(long readingId);
        void onClickRecheckReading(long readingId);
    }
    private OnClickElement onClickElementListener;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class MyViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public View itemView;
        public MyViewHolder(View v) {
            super(v);
            itemView = v;
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public ReadingViewAdapter(List<Reading> readings) {
        this.readings = readings;
    }

    // Store ref to recycler
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ReadingViewAdapter.MyViewHolder onCreateViewHolder(
            ViewGroup parent, int viewType)
    {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.listelement_reading, parent, false);

        // on click
        v.setOnClickListener( (view)-> onClick(view));
        v.setOnLongClickListener( (view) -> onLongClick(view));

        MyViewHolder vh = new MyViewHolder(v);
        return vh;
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
    private boolean onLongClick(View view) {
        int itemPosition = recyclerView.getChildLayoutPosition(view);
        long readingId = readings.get(itemPosition).readingId;
        if (onClickElementListener != null) {
            return onClickElementListener.onLongClick(readingId);
        }
        return false;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        View v = holder.itemView;
        Reading r = readings.get(position);
        ReadingAnalysis analysis = ReadingAnalysis.analyze(r);

        // analysis circle & arrow
        ImageView ivCircle = v.findViewById(R.id.imageCircle);
        ivCircle.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(analysis));

        ImageView ivArrow = v.findViewById(R.id.imageArrow);
        ivArrow.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(analysis));


        // populate: name & date
        TextView tvName = v.findViewById(R.id.tvName);
        String nameAndId = v.getContext().getString(R.string.reading_name_and_id, r.patient.patientName, r.patient.patientId);
        tvName.setText(nameAndId);

        TextView tvDate = v.findViewById(R.id.date);
        tvDate.setText(DateUtil.getConciseDateString(r.dateTimeTaken));

        // populate: uploaded
        setVisibilityForImageAndText(v, R.id.imgNotUploaded, R.id.tvNotUploaded, !r.isUploaded());

        // populate: referred
        setVisibilityForImageAndText(v, R.id.imgReferred, R.id.txtReferred, r.isReferredToHealthCentre());
        if (r.isReferredToHealthCentre()) {
            String message;
            if (r.referralHealthCentre != null && r.referralHealthCentre.length() > 0) {
                message = v.getContext().getString(R.string.reading_referred_to_health_centre, r.referralHealthCentre);
            } else {
                message = v.getContext().getString(R.string.reading_referred_to_health_centre_unknown);
            }

            TextView tv = v.findViewById(R.id.txtReferred);
            tv.setText(message);
        }

        // populate: follow-up
        setVisibilityForImageAndText(v, R.id.imgFollowUp, R.id.txtFollowUp, r.isFlaggedForFollowup());

        // populate: recheck vitals
        setVisibilityForImageAndText(v, R.id.imgRecheckVitals, R.id.txtRecheckVitals, r.isNeedRecheckVitals());
        if (r.isNeedRecheckVitals()) {
            String message;
            if (r.isNeedRecheckVitalsNow()) {
                message = v.getContext().getString(R.string.reading_recheck_vitals_now);
            } else {
                long minutes = r.getMinutesUntilNeedRecheckVitals();
                if (minutes == 1) {
                    message = v.getContext().getString(R.string.reading_recheck_vitals_in_one_minute);
                } else {
                    message = v.getContext().getString(R.string.reading_recheck_vitals_in_minutes, minutes);
                }
            }

            TextView tvRecheckVitals = v.findViewById(R.id.txtRecheckVitals);
            tvRecheckVitals.setText(message);
        }

        // recheck-vitals button
        Button btn = v.findViewById(R.id.btnRecheckVitalsNow);
        btn.setVisibility(r.isNeedRecheckVitals() ? View.VISIBLE : View.GONE);
        if (r.isNeedRecheckVitals()) {
            btn.setOnClickListener(view -> onClickElementListener.onClickRecheckReading(r.readingId));
        }
    }


    private void setVisibilityForImageAndText(View v, int imageViewId, int textViewId, boolean show) {
        ImageView iv = v.findViewById(imageViewId);
        iv.setVisibility( show ? View.VISIBLE : View.GONE);

        TextView tv = v.findViewById(textViewId);
        tv.setVisibility( show ? View.VISIBLE : View.GONE);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return readings.size();
    }
}