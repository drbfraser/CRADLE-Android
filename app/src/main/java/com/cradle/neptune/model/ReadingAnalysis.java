package com.cradle.neptune.model;

import android.content.Context;

import com.cradle.neptune.R;

/**
 * Analyzes a single reading and generates advice for this reading.
 * If analyzing a sequence of readings (retests), use ReadingRetestAnalysis
 */
public enum ReadingAnalysis {
    // Enum Types
    NONE(R.string.analysis_none, R.string.brief_advice_none),
    GREEN(R.string.analysis_green, R.string.brief_advice_green),
    YELLOW_UP(R.string.analysis_yellow_up, R.string.brief_advice_yellow_up),
    YELLOW_DOWN(R.string.analysis_yellow_down, R.string.brief_advice_yellow_down),
    RED_UP(R.string.analysis_red_up, R.string.brief_advice_red_up),
    RED_DOWN(R.string.analysis_red_down, R.string.brief_advice_red_down);

    public static final int MAX_SYSTOLIC = 300;
    public static final int MIN_SYSTOLIC = 10;
    public static final int MAX_DIASTOLIC = 300;
    public static final int MIN_DIASTOLIC = 10;
    public static final int MAX_HEART_RATE = 200;
    public static final int MIN_HEART_RATE = 40;
    // Break points for determining Green/Yellow/Red Up/Down
    // source: CRADLE VSA Manual (extracted spring 2019)
    static final int RED_SYSTOLIC = 160;
    static final int RED_DIASTOLIC = 110;
    static final int YELLOW_SYSTOLIC = 140;
    static final int YELLOW_DIASTOLIC = 90;
    private static final double SHOCK_HIGH = 1.7;
    private static final double SHOCK_MEDIUM = 0.9;
    // Fields
    private final int analysisTextId;
    private final int briefAdviceTextId;

    ReadingAnalysis(int analysisTextId, int briefAdviceTextId) {
        this.analysisTextId = analysisTextId;
        this.briefAdviceTextId = briefAdviceTextId;
    }

    // Analysis Functions
    public static ReadingAnalysis analyze(Reading r) {
        // Guard no currentReading:
        if (r == null || r.getBpSystolic() == null || r.getBpDiastolic() == null || r.getHeartRateBPM() == null) {
            return NONE;
        }

        double shockIndex = getShockIndex(r);

        boolean isBpVeryHigh = (r.getBpSystolic() >= RED_SYSTOLIC) || (r.getBpDiastolic() >= RED_DIASTOLIC);
        boolean isBpHigh = (r.getBpSystolic() >= YELLOW_SYSTOLIC) || (r.getBpDiastolic() >= YELLOW_DIASTOLIC);
        boolean isSevereShock = (shockIndex >= SHOCK_HIGH);
        boolean isShock = (shockIndex >= SHOCK_MEDIUM);

        // Return analysis based on priority:
        ReadingAnalysis analysis;
        if (isSevereShock) {
            analysis = RED_DOWN;
        } else if (isBpVeryHigh) {
            analysis = RED_UP;
        } else if (isShock) {
            analysis = YELLOW_DOWN;
        } else if (isBpHigh) {
            analysis = YELLOW_UP;
        } else {
            analysis = GREEN;
        }
        return analysis;
    }

    private static double getShockIndex(Reading r) {
        // Div-zero guard:
        if (r.getBpSystolic() == null || r.getBpSystolic() == 0) {
            return 0;
        }
        return (double) r.getHeartRateBPM() / (double) r.getBpSystolic();
    }

    // Get Text
    public String getAnalysisText(Context context) {
        return context.getString(analysisTextId);
    }

    public String getBriefAdviceText(Context context) {
        return context.getString(briefAdviceTextId);
    }

    public boolean isUp() {
        return this == YELLOW_UP || this == RED_UP;
    }

    public boolean isDown() {
        return this == YELLOW_DOWN || this == RED_DOWN;
    }

    public boolean isGreen() {
        return this == GREEN;
    }

    public boolean isYellow() {
        return this == YELLOW_UP || this == YELLOW_DOWN;
    }

    public boolean isRed() {
        return this == RED_UP || this == RED_DOWN;
    }

    public boolean isReferralToHealthCentreRecommended() {
        return (this == YELLOW_UP)
                || (this == RED_UP)
                || (this == RED_DOWN);
    }

}
