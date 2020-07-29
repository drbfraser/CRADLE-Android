package com.cradle.neptune.view.sync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.model.Reading
import com.cradle.neptune.utilitiles.DateUtil
import com.cradle.neptune.view.sync.SyncReadingRecyclerview.SyncReadingViewHolder
import com.cradle.neptune.viewmodel.ReadingAnalysisViewSupport

class SyncReadingRecyclerview(private val readingList:List<Reading>): RecyclerView.Adapter<SyncReadingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyncReadingViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.upload_reading_card, parent, false)
        return SyncReadingViewHolder(v)
    }

    override fun getItemCount(): Int {
        return readingList.size
    }

    override fun onBindViewHolder(holder: SyncReadingViewHolder, position: Int) {
        val currReading = readingList[position]
        holder.diasText.text = currReading.bloodPressure.diastolic.toString()
        holder.sysText.text = currReading.bloodPressure.systolic.toString()
        holder.hrText.text = currReading.bloodPressure.heartRate.toString()

        val readingAnalysis = currReading.bloodPressure.analysis
        holder.readingLightImageView.setImageResource(ReadingAnalysisViewSupport.getColorCircleImageId(readingAnalysis))
        holder.readingArrowImageView.setImageResource(ReadingAnalysisViewSupport.getArrowImageId(readingAnalysis))

        holder.date.text = DateUtil.getConciseDateString(currReading.dateTimeTaken)
    }

     class SyncReadingViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
         val sysText:TextView = itemView.findViewById(R.id.systolicBpTextView)
         val diasText:TextView = itemView.findViewById(R.id.diastolicBpTextView)
         val hrText:TextView = itemView.findViewById(R.id.heartRateTextView)
         val readingLightImageView:ImageView = itemView.findViewById(R.id.readingLightImgView)
         val readingArrowImageView:ImageView = itemView.findViewById(R.id.readingArrowImageview)
         val date :TextView = itemView.findViewById(R.id.readingDate)
     }
}