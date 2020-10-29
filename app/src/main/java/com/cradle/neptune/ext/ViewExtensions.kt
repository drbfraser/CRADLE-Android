package com.cradle.neptune.ext

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return
    imm.hideSoftInputFromWindow(windowToken, 0)
}
