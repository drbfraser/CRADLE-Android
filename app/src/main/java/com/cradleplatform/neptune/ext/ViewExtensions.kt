package com.cradleplatform.neptune.ext

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

fun View.hideKeyboard() {
    val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java) ?: return
    imm.hideSoftInputFromWindow(windowToken, 0)
}
