package com.cradletrial.cradlevhtapp.model;

import android.content.Context;

import com.cradletrial.cradlevhtapp.utilitiles.Util;

import java.util.ArrayList;
import java.util.List;

public class ReadingRetestAnalysis {

    // our initial arguments
    private Reading reading;
    private ReadingManager manager;
    // this and previous readings:
    //  ORDER: oldest first
    private List<Reading> readings;
    private List<ReadingAnalysis> analyses = new ArrayList<>();
    private RetestWhen retestAdvice;
    /**
     * Constructor
     */
    public ReadingRetestAnalysis(Reading reading, ReadingManager manager, Context context) {
        this.reading = reading;
        this.manager = manager;
        loadAllRecords(context);
        computeAnalyses();
        computeAdvice();
    }

    /**
     * Update on change
     */
    public void refresh(Context context) {
        loadAllRecords(context);
        computeAnalyses();
        computeAdvice();
    }

    /**
     * Retest Advice
     */
    public boolean isRetestRecommended() {
        return isRetestRecommendedIn15Min() || isRetestRecommendedNow();
    }

    public boolean isRetestRecommendedNow() {
        return retestAdvice == RetestWhen.RETEST_RIGHT_NOW_RECOMMENDED;
    }

    public boolean isRetestRecommendedIn15Min() {
        return retestAdvice == RetestWhen.RETEST_IN_15_RECOMMENDED;
    }

    /**
     * Access stored Readings and ReadingAnalysis objects
     */
    public List<Reading> getReadings() {
        return readings;
    }

    public List<ReadingAnalysis> getReadingAnalyses() {
        return analyses;
    }

    public ReadingAnalysis getMostRecentReadingAnalysis() {
        return analyses.get(analyses.size() - 1);
    }

    public int getNumberReadings() {
        Util.ensure(readings.size() == analyses.size());
        return readings.size();
    }

    /**
     * Access DB and figure out advice
     */
    private void loadAllRecords(Context context) {
        readings = new ArrayList<>();

        // load history
        if (reading.retestOfPreviousReadingIds != null) {
            for (Long l : reading.retestOfPreviousReadingIds) {
                Reading r = manager.getReadingById(context, l);
                readings.add(r);
            }
        }

        // add current
        readings.add(reading);
    }

    private void computeAnalyses() {
        analyses.clear();
        for (Reading r : readings) {
            analyses.add(ReadingAnalysis.analyze(r));
        }
    }

    private void computeAdvice() {
        // count green, yellow, and red of analyses
        int countGreen = 0;
        int countYellow = 0;
        int countRed = 0;
        for (ReadingAnalysis analysis : analyses) {
            countGreen += analysis.isGreen() ? 1 : 0;
            countYellow += analysis.isYellow() ? 1 : 0;
            countRed += analysis.isRed() ? 1 : 0;
        }

        /*
         *  If 1 reading:
         */
        if (analyses.size() == 1) {
            if (countGreen == 1) {
                // done if just one reading, and it's green
                retestAdvice = RetestWhen.RETEST_NOT_RECOMMENDED;
            } else if (countYellow == 1) {
                // retest in 15m if just one reading, and it's yellow
                retestAdvice = RetestWhen.RETEST_IN_15_RECOMMENDED;
            } else if (countRed == 1) {
                // retest immediately if just one reading, and it's red
                retestAdvice = RetestWhen.RETEST_RIGHT_NOW_RECOMMENDED;
            }
        }

        /*
         * If 2 readings:
         */
        else if (analyses.size() == 2) {
            if (countGreen == 2 || countYellow == 2 || countRed == 2) {
                // both readings agree
                retestAdvice = RetestWhen.RETEST_NOT_RECOMMENDED;
            } else {
                // they differ: retest immediately
                retestAdvice = RetestWhen.RETEST_RIGHT_NOW_RECOMMENDED;
            }
        }

        /*
         * If 3+ readings
         */
        else {
            // done if have 3+ readings (using the most recent reading is sufficient)
            retestAdvice = RetestWhen.RETEST_NOT_RECOMMENDED;
        }
    }

    private enum RetestWhen {
        RETEST_NOT_RECOMMENDED,
        RETEST_RIGHT_NOW_RECOMMENDED,
        RETEST_IN_15_RECOMMENDED
    }
}
