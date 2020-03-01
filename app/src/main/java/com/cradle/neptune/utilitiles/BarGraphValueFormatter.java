package com.cradle.neptune.utilitiles;

import com.github.mikephil.charting.formatter.ValueFormatter;

public class BarGraphValueFormatter extends ValueFormatter {
    private String label = "";

    public BarGraphValueFormatter(String label) {
        this.label = label;
    }

    @Override
    public String getFormattedValue(float value) {
        return super.getFormattedValue(value) + " " + label;
    }
}
