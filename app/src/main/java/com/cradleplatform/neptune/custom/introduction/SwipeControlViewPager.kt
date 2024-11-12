package com.cradleplatform.neptune.custom.introduction

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class SwipeControlViewPager : ViewPager {
    var isSwipeEnabled = true

    constructor(context: Context) : super(context)
    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : super(context, attrs)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return isSwipeEnabled && super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return isSwipeEnabled && super.onInterceptTouchEvent(event)
    }
}
