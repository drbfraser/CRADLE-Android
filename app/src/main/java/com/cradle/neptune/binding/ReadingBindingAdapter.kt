package com.cradle.neptune.binding

import android.util.Log
import androidx.databinding.BindingAdapter
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object ReadingBindingAdapter {
    @BindingAdapter("errorMessage")
    @JvmStatic fun setError(textInputLayout: TextInputLayout, errorMessage: String?) {
        textInputLayout.error = errorMessage
    }

    @BindingAdapter("patientAge", "patientDob", requireAll = true)
    @JvmStatic fun onAgeStateChanged(
        textInputLayout: TextInputLayout,
        age: Int?,
        dob: String?
    ) {
        // TODO: Figure out why these are not called when changed.
        Log.d("ReadingBindingAdapter", "DEBUG: onAgeStateChanged for input layout, dob: $dob, age: $age")
    }

    @BindingAdapter("patientAge", "patientDob", requireAll = true)
    @JvmStatic fun onAgeStateChanged(
        textInputEditText: TextInputEditText,
        age: Int?,
        dob: String?
    ) {
        Log.d("ReadingBindingAdapter", "DEBUG: onAgeStateChanged for TextInputEditText, dob is $dob, age is $age")
    }
}
