package com.cradleplatform.neptune.viewmodel

import android.content.Context
import androidx.annotation.DrawableRes
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.ReadingAnalysis

/**
 * Get UI related info about an analysis
 */
object ReadingAnalysisViewSupport {
    @JvmStatic
    fun getColorCircleContentDescription(context: Context, analysis: ReadingAnalysis?): String =
        context.run {
            return when (analysis) {
                ReadingAnalysis.NONE -> getString(R.string.status_none)
                ReadingAnalysis.GREEN -> getString(R.string.status_green)
                ReadingAnalysis.YELLOW_DOWN, ReadingAnalysis.YELLOW_UP -> {
                    getString(R.string.status_yellow)
                }
                ReadingAnalysis.RED_DOWN, ReadingAnalysis.RED_UP -> getString(R.string.status_red)
                else -> error("unreachable")
            }
        }

    @JvmStatic
    fun getArrowContentDescription(context: Context, analysis: ReadingAnalysis): String =
        context.run {
            return@run when {
                analysis.isUp -> getString(R.string.arrow_up)
                analysis.isDown -> getString(R.string.arrow_down)
                else -> getString(R.string.arrow_blank)
            }
        }

    @DrawableRes
    @JvmStatic
    fun getColorCircleImageId(analysis: ReadingAnalysis?): Int {
        return when (analysis) {
            ReadingAnalysis.NONE -> 0
            ReadingAnalysis.GREEN -> R.drawable.status_green
            ReadingAnalysis.YELLOW_DOWN, ReadingAnalysis.YELLOW_UP -> R.drawable.status_yellow
            ReadingAnalysis.RED_DOWN, ReadingAnalysis.RED_UP -> R.drawable.status_red
            else -> error("unreachable")
        }
    }

    @DrawableRes
    @JvmStatic
    fun getArrowImageId(analysis: ReadingAnalysis): Int {
        return when {
            analysis.isUp -> R.drawable.arrow_up
            analysis.isDown -> R.drawable.arrow_down
            else -> R.drawable.arrow_blank
        }
    }
}
