package com.cradle.neptune.view;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class StatsActivity extends AppCompatActivity {

    @Inject
    ReadingManager readingManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        ((MyApp) getApplication()).getAppComponent().inject(this);

        setupLineChar();
        setupBarChart();
    }

    private void setupBarChart() {

        List<Reading> readings = readingManager.getReadings(this);
        BarChart barChart = findViewById(R.id.bargraph);
        List<BarEntry> entries = new ArrayList<>();
        List<BarEntry> entries1 = new ArrayList<>();
        List<BarEntry> entries2 = new ArrayList<>();

        entries.add(new BarEntry(3,22));
        entries1.add(new BarEntry(1,18));
        entries2.add(new BarEntry(2,8));

        BarDataSet dataSet = new BarDataSet(entries,"HeartRate1");
        BarDataSet dataSet1 = new BarDataSet(entries1,"HeartRate2");
        BarDataSet dataSet2 = new BarDataSet(entries2,"HeartRate3");

        dataSet.setColor(Color.GREEN);
        dataSet1.setColor(Color.RED);
        dataSet2.setColor(Color.YELLOW);


        barChart.setDrawBorders(false);
        barChart.setDrawGridBackground(false);
        barChart.getAxisRight().setDrawLabels(false);
        barChart.getXAxis().setDrawAxisLine(false);
        barChart.getAxisRight().setDrawGridLines(false);
        barChart.getXAxis().setDrawGridLines(false);
        barChart.getAxisLeft().setDrawGridLines(false);
        barChart.getXAxis().setDrawLabels(false);
        barChart.getAxisRight().setAxisMinimum(0);
        barChart.getAxisLeft().setAxisMinimum(0);

        BarData lineData = new BarData(dataSet,dataSet1,dataSet2);

        barChart.setData(lineData);
        barChart.invalidate();
    }

    private void setupLineChar() {
        LineChart lineChart = findViewById(R.id.lineChart);
        List<Entry> diastolicEntry = new ArrayList<>();
        List<Entry> systolicEntry = new ArrayList<>();
        List<Entry> heartrateEntry = new ArrayList<>();
        List<Reading> readings = readingManager.getReadings(this);
        diastolicEntry.add(new Entry(0,0));
        systolicEntry.add(new Entry(0,0));
        heartrateEntry.add(new Entry(0,0));

        for(int i =1;i<readings.size();i++){
            Reading reading = readings.get(i);
            Log.d("buggg",reading.dateTimeTaken.getDayOfMonth()+" "+ reading);
            diastolicEntry.add(new Entry(i,reading.bpDiastolic));
            systolicEntry.add(new Entry(i,reading.bpSystolic));
            heartrateEntry.add(new Entry(i,reading.heartRateBPM));


        }

        LineDataSet diastolicDataSet = new LineDataSet(diastolicEntry,"BP Diastolic");
        LineDataSet systolicDataSet = new LineDataSet(systolicEntry,"BP Systolic");
        LineDataSet heartRateDataSet = new LineDataSet(heartrateEntry,"HearRate BPM");

        diastolicDataSet.setColor(getResources().getColor(R.color.colorAccent));
        systolicDataSet.setColor(getResources().getColor(R.color.purple));
        heartRateDataSet.setColor(getResources().getColor(R.color.orange));

        diastolicDataSet.setCircleColor(getResources().getColor(R.color.colorAccent));
        systolicDataSet.setCircleColor(getResources().getColor(R.color.purple));
        heartRateDataSet.setCircleColor(getResources().getColor(R.color.orange));

        //this is to make the curve smooth
        diastolicDataSet.setDrawCircleHole(false);
        diastolicDataSet.setDrawCircles(false);
        diastolicDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        systolicDataSet.setDrawCircleHole(false);
        systolicDataSet.setDrawCircles(false);
        systolicDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        heartRateDataSet.setDrawCircleHole(false);
        heartRateDataSet.setDrawCircles(false);
        heartRateDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        //remove unneccessy background lines
        lineChart.setDrawBorders(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getAxisRight().setDrawLabels(false);
        lineChart.getAxisRight().setDrawGridLines(false);
        lineChart.getXAxis().setDrawGridLines(false);
        lineChart.getAxisLeft().setDrawGridLines(false);

        LineData lineData = new LineData(diastolicDataSet,systolicDataSet,heartRateDataSet);
        lineData.setDrawValues(false);
        lineData.setHighlightEnabled(false);
        lineChart.getXAxis().setDrawAxisLine(false);
        lineChart.setData(lineData);
        //start at zero
        lineChart.getAxisRight().setAxisMinimum(0);
        lineChart.getAxisRight().setAxisMinimum(0);
        lineChart.invalidate();
    }
}
