package com.cradleplatform.neptune.testutils

import android.view.View
import androidx.test.espresso.IdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior

class BottomSheetIdlingResource(
    private val bottomSheetBehavior: BottomSheetBehavior<*>
) : IdlingResource {

    private var callback: IdlingResource.ResourceCallback? = null

    override fun getName() = "BottomSheetIdlingResource"

    override fun isIdleNow(): Boolean {
        val idle = bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED ||
            bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED
        if (idle) callback?.onTransitionToIdle()
        return idle
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED ||
                    newState == BottomSheetBehavior.STATE_COLLAPSED
                ) {
                    this@BottomSheetIdlingResource.callback?.onTransitionToIdle()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }
}
