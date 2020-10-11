package com.cradle.neptune.binding

import android.text.InputType
import android.util.Log
import android.widget.CheckBox
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import com.cradle.neptune.R
import com.cradle.neptune.model.Sex
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

private const val TAG = "ReadingBindingAdapter"

class ReadingBindingAdapters constructor(val fragment: Fragment) {
    @BindingAdapter("errorMessage")
    fun setError(textInputLayout: TextInputLayout, errorMessage: String?) {
        textInputLayout.error = errorMessage
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
                helperText = context.getString(R.string.dob_helper_using_age_from_date_of_birth)
                startIconDrawable = ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_baseline_clear_24, context.theme
                )
            }
            Log.d(TAG, "onAgeStateChange: done setting up dob")
        } else {
            Log.d(TAG, "onAgeStateChange: setting up age")
            textInputLayout.apply {
                suffixText = context.getString(R.string.age_input_suffix_approximate_years_old)
                helperText = context.getString(R.string.fragment_patient_info_dob_helper)
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
            inputType = if (newUnits == context.resources.getStringArray(R.array.reading_ga_units)[0]) {
                InputType.TYPE_CLASS_NUMBER
            } else {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
    }

    /*
    @BindingAdapter("android:text", "bind:gestationalAgeUnits", requireAll = true)
    fun onGestationalAgeChanged(
        textInputLayout: TextInputLayout,
        oldText: CharSequence?,
        oldGestAgeUnits: String?,
        text: CharSequence?,
        gestAgeUnits: String?
    ) {
        val editText = textInputLayout.editText ?: return
        val oldTextFromView = editText.text ?: return
        Log.d(TAG, "onGestationalAgeChanged: old,oldTextFromView,new $oldText, $oldTextFromView, $text")
        Log.d(TAG, "onGestationalAgeChanged: old units, new units $oldGestAgeUnits, $gestAgeUnits")
        if (oldText == text || oldTextFromView == text) {
            return
        }
        if (gestAgeUnits != oldGestAgeUnits) {
            return
        }
        gestAgeUnits ?: return

        (editText as? TextInputEditText)?.apply {
            s
        }
    }
     */
}
