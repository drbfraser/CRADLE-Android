package com.cradle.neptune.view;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.cradle.neptune.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class StatsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        //setupLineChar();
        setupBarChart();
    }

    private void setupBarChart() {
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
        List<Entry> entries = new ArrayList<>();
        List<Entry> entries1 = new ArrayList<>();
        List<Entry> entries2 = new ArrayList<>();

        for(int i =0;i<15;i++){
            entries.add(new Entry(i*3,i*2));
            entries1.add(new Entry(i*5,i*3));
            entries2.add(new Entry(i*4,i/2));

        }

        LineDataSet dataSet = new LineDataSet(entries,"HeartRate1");
        LineDataSet dataSet1 = new LineDataSet(entries1,"HeartRate2");
        LineDataSet dataSet2 = new LineDataSet(entries2,"HeartRate3");

        dataSet.setColor(getResources().getColor(R.color.colorAccent));
        dataSet1.setColor(getResources().getColor(R.color.purple));
        dataSet2.setColor(getResources().getColor(R.color.orange));
        dataSet.setCircleColor(getResources().getColor(R.color.colorAccent));
        dataSet1.setCircleColor(getResources().getColor(R.color.purple));
        dataSet2.setCircleColor(getResources().getColor(R.color.orange));

        dataSet.setCircleHoleRadius(0);
        lineChart.setDrawBorders(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getAxisRight().setDrawLabels(false);
        lineChart.getAxisRight().setDrawGridLines(false);
        lineChart.getXAxis().setDrawGridLines(false);
        lineChart.getAxisLeft().setDrawGridLines(false);

        LineData lineData = new LineData(dataSet,dataSet1,dataSet2);

        lineChart.getXAxis().setDrawAxisLine(false);
        lineChart.setData(lineData);
        lineChart.invalidate();
    }
}
