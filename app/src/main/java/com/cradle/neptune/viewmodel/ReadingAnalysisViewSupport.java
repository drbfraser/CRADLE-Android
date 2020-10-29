package com.cradle.neptune.viewmodel;

import android.graphics.Color;

import androidx.annotation.DrawableRes;

import com.cradle.neptune.R;
import com.cradle.neptune.model.ReadingAnalysis;
import com.cradle.neptune.utilitiles.Util;

/**
 * Get UI related info about an analysis
 */
public class ReadingAnalysisViewSupport {

    public static @DrawableRes int getColorCircleImageId(ReadingAnalysis analysis) {
        switch (analysis) {
            case NONE:
                return 0;
            case GREEN:
                return R.drawable.status_green;
            case YELLOW_DOWN:
            case YELLOW_UP:
                return R.drawable.status_yellow;
            case RED_DOWN:
            case RED_UP:
                return R.drawable.status_red;
            default:
                Util.ensure(false);
                return R.drawable.status_red;

        }
    }

    public static @DrawableRes int getArrowImageId(ReadingAnalysis analysis) {
        if (analysis.isUp()) {
            return R.drawable.arrow_up;
        }
        if (analysis.isDown()) {
            return R.drawable.arrow_down;
        }
        return R.drawable.arrow_blank;
    }
}
