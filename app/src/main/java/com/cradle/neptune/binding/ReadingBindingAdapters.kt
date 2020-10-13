package com.cradle.neptune.binding

import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import com.cradle.neptune.R
import com.cradle.neptune.model.Sex
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

private const val TAG = "ReadingBindingAdapter"

/**
 * Contains the BindingAdapters for Fragments in the Reading creation flow.
 * We use the `bind` name space just to clarify that it is part of our custom BindingAdapter set;
 * the Data Binding library in reality just ignores custom name spaces.
 */
class ReadingBindingAdapters constructor(val fragment: Fragment) {
    @BindingAdapter("bind:enabledOnlyWhen")
    fun enabledOnlyWhen(view: View, condition: Boolean) {
        if (!condition && view.isEnabled) {
            view.isEnabled = false
        } else if (condition && !view.isEnabled) {
            view.isEnabled = true
        }
    }

    @BindingAdapter("bind:makeTextEmptyWhen")
    fun makeTextEmptyWhen(view: TextView, condition: Boolean) {
        if (condition && view.text.isNotEmpty()) {
            view.text = ""
        }
    }

    /**
     * Adds a mandatory star to the hint text / label when true.
     */
    @BindingAdapter("bind:addMandatoryStarToLabelWhen")
    fun addMandatoryStarToLabelWhen(view: TextInputLayout, condition: Boolean) {
        val currentHint = view.hint as? String ?: return
        if (!condition && currentHint.endsWith('*') && currentHint.isNotEmpty()) {
            view.hint = currentHint.subSequence(0, currentHint.length - 1)
        } else if (condition && !currentHint.endsWith('*') && currentHint.isNotEmpty()) {
            view.hint = "$currentHint*"
        }
    }

    @BindingAdapter("bind:errorMessage")
    fun setError(textInputLayout: TextInputLayout, errorMessage: String?) {
        if (textInputLayout.error != errorMessage) {
            textInputLayout.error = errorMessage
        }
    }

    @BindingAdapter("bind:isUsingDateOfBirth")
    fun onAgeStateChanged(
        textInputLayout: TextInputLayout,
        oldIsUsingDateOfBirth: Boolean?,
        newIsUsingDateOfBirth: Boolean?
    ) {
        Log.d(TAG, "onAgeStateChanged(): old,new = $oldIsUsingDateOfBirth, $newIsUsingDateOfBirth")
        if (oldIsUsingDateOfBirth == newIsUsingDateOfBirth ||
            newIsUsingDateOfBirth == null) {
            return
        }

        val context = textInputLayout.context
        if (newIsUsingDateOfBirth) {
            Log.d(TAG, "onAgeStateChange: setting up dob")
            textInputLayout.editText?.apply {
                inputType = InputType.TYPE_NULL
                Log.d(TAG, "onAgeStateChange: dob in EditText is now $text")
            }
            textInputLayout.apply {
                val currentError = error
                helperText = context.getString(R.string.dob_helper_using_age_from_date_of_birth)
                error = currentError

                startIconDrawable = ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_baseline_clear_24, context.theme
                )
            }
            Log.d(TAG, "onAgeStateChange: done setting up dob")
        } else {
            Log.d(TAG, "onAgeStateChange: setting up age")
            textInputLayout.apply {
                val currentError = error
                helperText = context.getString(R.string.fragment_patient_info_dob_helper)
                error = currentError

                suffixText = context.getString(R.string.age_input_suffix_approximate_years_old)
                startIconDrawable = ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_baseline_calendar_today_24, context.theme
                )
            }
            textInputLayout.editText?.apply {
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            Log.d(TAG, "onAgeStateChange: done setting up age")
        }
    }

    @BindingAdapter("bind:sex")
    fun onGenderChanged(
        checkBox: CheckBox,
        sex: Sex?
    ) {
        checkBox.apply {
            when (sex) {
                Sex.MALE, null -> {
                    isEnabled = false
                    isChecked = false
                }
                else -> isEnabled = true
            }
        }
    }

    @BindingAdapter("bind:gestationalAgeUnits")
    fun onGestationalAgeUnitsChanged(
        textInputLayout: TextInputLayout,
        oldUnits: String?,
        newUnits: String?
    ) {
        Log.d(TAG, "onGestationalAgeUnitsChanged: old,new $oldUnits, $newUnits")
        if (oldUnits == newUnits) {
            return
        }
        newUnits ?: return
        val editText = textInputLayout.editText ?: return
        (editText as? TextInputEditText)?.apply {
            if (newUnits == context.resources.getStringArray(R.array.reading_ga_units)[0]) {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(2))
            } else {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                @Suppress("MagicNumber")
                filters = arrayOf(InputFilter.LengthFilter(10))
            }
        }
    }

    /**
     * A BindingAdapter for setting the selected text in a dropdown menu. This code is copy and
     * pasted(and converted to Kotlin) from
     *
     * The only different is in the last line. In `setText`, we need to set filter = false to work
     * with menus in Material Design. It's copy and pasted because we only need to change one line
     * in this default provided function for things to work.
     *
     * (refer to https://material.io/develop/android/components/menu#setting-a-default-value:
     *  "In order to have a pre-selected value displayed, you can call setText(CharSequence text,
     *  boolean filter) on the AutoCompleteTextView with the filter set to false.")
     *
     * Copyright (C) 2015 The Android Open Source Project
     * Licensed under the Apache License, Version 2.0
     * https://android.googlesource.com/platform/frameworks/data-binding/+/refs/heads/
     * studio-master-dev/extensions/baseAdapters/src/main/java/androidx/databinding/adapters/
     * TextViewBindingAdapter.java#68
     */
    @BindingAdapter("android:text")
    fun setDropdownMenuText(autoCompleteTextView: AutoCompleteTextView, text: CharSequence?) {
        val oldText: CharSequence = autoCompleteTextView.text
        if (text == oldText || (text == null && oldText.isEmpty())) {
            return
        }
        if (text is Spanned) {
            if (text == oldText) {
                // No change in the spans, so don't set anything.
                return
            }
        } else if (!haveContentsChanged(text, oldText)) {
            // No content changes, so don't set anything.
            return
        }
        // Using filter = false in order to properly set the drop down menu. If we don't do this,
        // all the items in the menu will be removed.
        autoCompleteTextView.setText(text, false)
    }

    /**
     *
     * We need to copy and paste since the method is used for [setDropdownMenuText] and this
     * method is originally private.
     *
     * Copyright (C) 2015 The Android Open Source Project
     * Licensed under the Apache License, Version 2.0
     * https://android.googlesource.com/platform/frameworks/data-binding/+/refs/heads/
     * studio-master-dev/extensions/baseAdapters/src/main/java/androidx/databinding/adapters/
     * TextViewBindingAdapter.java#332
     */
    private fun haveContentsChanged(str1: CharSequence?, str2: CharSequence?): Boolean {
        if (str1 == null != (str2 == null)) {
            return true
        } else if (str1 == null) {
            return false
        }
        val length = str1.length
        if (length != str2!!.length) {
            return true
        }
        for (i in 0 until length) {
            if (str1[i] != str2[i]) {
                return true
            }
        }
        return false
    }
}
