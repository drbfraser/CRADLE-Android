package com.cradleplatform.neptune.utilities

import android.app.Activity
import android.content.Context
import android.view.View
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

class SnackbarHelper {
    companion object {
        fun showSnackbar(activity: Activity, message: String) {
            Snackbar
                .make(
                    activity.findViewById(android.R.id.content),
                    message,
                    Snackbar.LENGTH_INDEFINITE)
                .setAction(activity.getString(android.R.string.ok), null)
                .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
                // .setActionTextColor(android.graphics.Color.W)
                .show()
        }
    }
}