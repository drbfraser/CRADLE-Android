package com.cradleplatform.neptune.utilities

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.cradleplatform.neptune.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

class SnackbarHelper {
    companion object {
        @RequiresApi(Build.VERSION_CODES.M)
        fun showSnackbarWithOK(activity: Activity, message: String) {
            Snackbar
                .make(
                    activity.findViewById(android.R.id.content),
                    message,
                    Snackbar.LENGTH_INDEFINITE
                )
                .setAction(activity.getString(android.R.string.ok), View.OnClickListener { })
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                .setActionTextColor(activity.getColor(R.color.white))
                .show()
        }

//        @RequiresApi(Build.VERSION_CODES.M)
//        fun showSnackbarWithError(activity: Activity, message: String) {
//            Snackbar
//                .make(
//                    activity.findViewById(android.R.id.content),
//                    message,
//                    Snackbar.LENGTH_INDEFINITE
//                )
//                .setAction(activity.getString(android.R.string.ok), View.OnClickListener { })
//                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
//                .setActionTextColor(activity.getColor(R.color.redDown))
//                .show()
//        }
    }
}

fun makeSuccessSnackbar(view: View, text: CharSequence): Snackbar {
    val green = Color.parseColor("#4CAF50")
    return Snackbar.make(
        view,
        text,
        Snackbar.LENGTH_LONG
    ).setBackgroundTint(green)
}

fun makeWarningSnackbar(view: View, text: CharSequence): Snackbar {
    val orange = Color.parseColor("#ec942c")
    return Snackbar.make(
        view,
        text,
        Snackbar.LENGTH_INDEFINITE
    )
        .setBackgroundTint(orange)
        .setActionTextColor(Color.WHITE)
        .setAction("Dismiss") {}
}

fun makeErrorSnackbar(view: View, text: CharSequence): Snackbar {
    return Snackbar.make(
        view,
        text,
        Snackbar.LENGTH_INDEFINITE
    )
        .setBackgroundTint(Color.RED)
        .setActionTextColor(Color.WHITE)
        .setAction("Dismiss") {}
}
