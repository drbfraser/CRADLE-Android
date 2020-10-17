package com.cradle.neptune.utilitiles

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun View.dismissKeyboard() {
    val manager = this.context.getSystemService(Context.INPUT_METHOD_SERVICE)
        as? InputMethodManager
    manager?.hideSoftInputFromWindow(this.windowToken, 0)
}
