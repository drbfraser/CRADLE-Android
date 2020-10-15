package com.cradle.neptune.binding

import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.BindingAdapter
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
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
@Suppress("LargeClass")
class ReadingBindingAdapters {
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
     * To use this properly, make sure there is another focusable view in the layout.
     */
    @BindingAdapter("bind:loseFocuswhen")
    fun loseFocusWhen(view: TextView, condition: Boolean) {
        if (condition && view.hasFocus()) {
            view.clearFocus()
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

                startIconDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ResourcesCompat.getDrawable(
                        resources, R.drawable.ic_baseline_clear_24, context.theme
                    )
                } else {
                    VectorDrawableCompat.create(
                        resources, R.drawable.ic_baseline_clear_24, context.theme
                    )
                }
            }
            Log.d(TAG, "onAgeStateChange: done setting up dob")
        } else {
            Log.d(TAG, "onAgeStateChange: setting up age")
            textInputLayout.apply {
                val currentError = error
                helperText = context.getString(R.string.fragment_patient_info_dob_helper)
                error = currentError

                suffixText = context.getString(R.string.age_input_suffix_approximate_years_old)
                startIconDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ResourcesCompat.getDrawable(
                        resources, R.drawable.ic_baseline_calendar_today_24, context.theme
                    )
                } else {
                    VectorDrawableCompat.create(
                        resources, R.drawable.ic_baseline_calendar_today_24, context.theme
                    )
                }
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

    @BindingAdapter("bind:setMaterialSpinnerItemsWithArray")
    fun onSpinnerItemsChanged(
        view: AutoCompleteTextView,
        oldArray: Array<String>?,
        newArray: Array<String>?
    ) {
        if (oldArray === newArray) return
        if (newArray == null) return
        if (oldArray?.contentEquals(newArray) == true) return

        val adapter = MaterialSpinnerArrayAdapter(
            view.context,
            R.layout.list_dropdown_menu_item,
            newArray
        )
        view.setAdapter(adapter)
    }
}
