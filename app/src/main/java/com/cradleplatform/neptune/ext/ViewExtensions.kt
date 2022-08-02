package com.cradleplatform.neptune.ext

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

fun View.hideKeyboard() {
    val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java) ?: return
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager =
        getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}
