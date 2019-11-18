package com.cradle.neptune.view;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingAnalysis;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.utilitiles.BarGraphValueFormatter;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
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

    List<Reading> readings;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        ((MyApp) getApplication()).getAppComponent().inject(this);
        readings = readingManager.getReadings(this);
        setupBasicStats();
        setupLineChar();
        setupBarChart();
    }

    private void setupBasicStats() {
        int totalReadings = readings.size();
        int totalRef =0;
        int totalassessments=0;

        for (int i=0;i<totalReadings;i++){
            if(readings.get(i).isReferredToHealthCentre()) {
                totalRef++;
            }
        }
        TextView readingTV = findViewById(R.id.readingTvStats);
        readingTV.setText(totalReadings+"");
        TextView refTV = findViewById(R.id.refTvStats);
        refTV.setText(totalRef+"");
        //todo do the same for the referrals


    }

    private void setupBarChart() {

        BarChart barChart = findViewById(R.id.bargraph);
        List<BarEntry> greenEntry = new ArrayList<>();
        List<BarEntry> yellowUpEntry = new ArrayList<>();
        List<BarEntry> yellowDownEntry = new ArrayList<>();
        List<BarEntry> redUpEntry = new ArrayList<>();
        List<BarEntry> redDownEntry = new ArrayList<>();
        int green =0;
        int yellowup=0;
        int yellowDown = 0;
        int redDown =0;
        int redUp =0;

        for(int i =0;i<readings.size();i++){
            ReadingAnalysis analysis = ReadingAnalysis.analyze(readings.get(i));
            if(analysis == ReadingAnalysis.RED_DOWN){
                redDown++;
            } else if(analysis==ReadingAnalysis.GREEN){
                green++;
            } else if (analysis==ReadingAnalysis.RED_UP){
                redDown++;
            } else if (analysis == ReadingAnalysis.YELLOW_UP){
                yellowup++;
            } else if (analysis==ReadingAnalysis.YELLOW_DOWN){
                yellowDown++;
            }
        }
        greenEntry.add(new BarEntry(1,green));
        yellowUpEntry.add(new BarEntry(2,yellowup));
        yellowDownEntry.add(new BarEntry(3,yellowDown));
        redUpEntry.add(new BarEntry(4,redUp));
        redDownEntry.add(new BarEntry(5,redDown));

        BarDataSet greenDataSet = new BarDataSet(greenEntry,"GREEN");
        BarDataSet yellowUpDataSet = new BarDataSet(yellowUpEntry,"YELLOW UP");
        BarDataSet yellowDownDataSet = new BarDataSet(yellowDownEntry,"YELLOW DOWN");
        BarDataSet redUpDataSet = new BarDataSet(redUpEntry,"RED UP");
        BarDataSet redDownDataSet = new BarDataSet(redDownEntry,"RED DOWN");

        greenDataSet.setValueFormatter(new BarGraphValueFormatter("Green"));
        yellowDownDataSet.setValueFormatter(new BarGraphValueFormatter("Yellow Down"));
        yellowUpDataSet.setValueFormatter(new BarGraphValueFormatter("Yellow Up"));
        redDownDataSet.setValueFormatter(new BarGraphValueFormatter("Red Down"));
        redUpDataSet.setValueFormatter(new BarGraphValueFormatter("Red Up"));

        greenDataSet.setColor(Color.GREEN);
        yellowUpDataSet.setColor(Color.YELLOW);
        yellowDownDataSet.setColor(Color.YELLOW);
        redDownDataSet.setColor(Color.RED);
        redUpDataSet.setColor(Color.RED);


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

        BarData lineData = new BarData(greenDataSet,yellowUpDataSet,yellowDownDataSet,redUpDataSet,redDownDataSet);
        barChart.getDescription().setText("");


        barChart.setData(lineData);
        barChart.getLegend().setEnabled(false);
        barChart.setHighlightPerTapEnabled(false);
        barChart.invalidate();
    }

    private void setupLineChar() {
        LineChart lineChart = findViewById(R.id.lineChart);
        List<Entry> diastolicEntry = new ArrayList<>();
        List<Entry> systolicEntry = new ArrayList<>();
        List<Entry> heartrateEntry = new ArrayList<>();
        //start at 0

        for(int i =0;i<readings.size();i++){
            Reading reading = readings.get(i);
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
        //lineData.setDrawValues(false);

        lineData.setHighlightEnabled(false);

        lineChart.getXAxis().setDrawAxisLine(true);
        lineChart.setData(lineData);
        //start at zero
        lineChart.getAxisRight().setAxisMinimum(0);
        lineChart.getAxisRight().setAxisMinimum(0);
        lineChart.getXAxis().setEnabled(false);
        lineChart.getDescription().setText("Cardiovascular Data from last "+ readings.size()+ " readings");
        lineChart.invalidate();
    }
}
