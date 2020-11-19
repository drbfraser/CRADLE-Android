package com.cradleVSA.neptune.view

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

abstract class TabActivityBase(private val myTabButtonId: Int) : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // changing the color for all the activity status bar.
        val window = window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.BLACK
    }

    companion object {
        const val TAB_ACTIVITY_BASE_SETTINGS_DONE = 948
    }
}
