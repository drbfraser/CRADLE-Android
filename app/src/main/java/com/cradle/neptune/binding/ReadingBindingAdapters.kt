package com.cradle.neptune.binding

import android.util.Log
import android.widget.CheckBox
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import com.cradle.neptune.model.Sex
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

private const val TAG = "ReadingBindingAdapter"

class ReadingBindingAdapters constructor(val fragment: Fragment) {
    @BindingAdapter("errorMessage")
    fun setError(textInputLayout: TextInputLayout, errorMessage: String?) {
        textInputLayout.error = errorMessage
    }

    @BindingAdapter("patientAge", "patientDob", requireAll = true)
    fun onAgeStateChanged(
        textInputLayout: TextInputLayout,
        age: Int?,
        dob: String?
    ) {
        Log.d(TAG, "DEBUG: onAgeStateChanged for input layout, dob: $dob, age: $age")
    }

    @BindingAdapter("patientAge", "patientDob", requireAll = true)
    fun onAgeStateChanged(
        textInputEditText: TextInputEditText,
        age: Int?,
        dob: String?
    ) {
        Log.d(TAG, "DEBUG: onAgeStateChanged for TextInputEditText, dob is $dob, age is $age")
    }

    @BindingAdapter("bind:sex")
    fun onGenderChanged(
        checkBox: CheckBox,
        sex: Sex?
    ) {
        when (sex) {
            Sex.MALE, null -> checkBox.isEnabled = false
            else -> checkBox.isEnabled = true
        }
    }
}
