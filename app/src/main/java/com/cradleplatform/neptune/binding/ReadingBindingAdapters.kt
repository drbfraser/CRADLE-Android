package com.cradleplatform.neptune.binding

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.BindingAdapter
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.ReadingAnalysis
import com.cradleplatform.neptune.viewmodel.ReadingAnalysisViewSupport
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

private const val TAG = "ReadingBindingAdapter"

/**
 * Contains the BindingAdapters for Fragments in the Reading creation flow.
 */
@Suppress("LargeClass")
class ReadingBindingAdapters {
    @BindingAdapter("makeTextEmptyWhen")
    fun makeTextEmptyWhen(view: TextView, condition: Boolean) {
        if (condition && view.text.isNotEmpty()) {
            view.text = ""
        }
    }

    /**
     * To use this properly, make sure there is another focusable view in the layout.
     */
    @BindingAdapter("loseFocuswhen")
    fun loseFocusWhen(view: TextView, condition: Boolean) {
        if (condition && view.hasFocus()) {
            view.clearFocus()
        }
    }

    /**
     * Adds a mandatory star to the hint text / label when true.
     */
    @BindingAdapter("addMandatoryStarToLabelWhen")
    fun addMandatoryStarToLabelWhen(view: TextInputLayout, condition: Boolean) {
        val currentHint = view.hint as? String ?: return
        if (!condition && currentHint.endsWith('*') && currentHint.isNotEmpty()) {
            view.hint = currentHint.subSequence(0, currentHint.length - 1)
        } else if (condition && !currentHint.endsWith('*') && currentHint.isNotEmpty()) {
            view.hint = "$currentHint*"
        }
    }

    @BindingAdapter("errorMessage")
    fun setError(textInputLayout: TextInputLayout, errorMessage: String?) {
        if (textInputLayout.error != errorMessage) {
            textInputLayout.error = errorMessage
        }
    }

    @BindingAdapter("isUsingDateOfBirth")
    fun onAgeStateChanged(
        textInputLayout: TextInputLayout,
        oldIsUsingDateOfBirth: Boolean?,
        newIsUsingDateOfBirth: Boolean?
    ) {
        Log.d(TAG, "onAgeStateChanged(): old,new = $oldIsUsingDateOfBirth, $newIsUsingDateOfBirth")
        if (oldIsUsingDateOfBirth == newIsUsingDateOfBirth ||
            newIsUsingDateOfBirth == null
        ) {
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
                    resources,
                    R.drawable.ic_baseline_clear_24,
                    context.theme
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
                    resources,
                    R.drawable.ic_baseline_calendar_today_24,
                    context.theme
                )
            }
            textInputLayout.editText?.apply {
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            Log.d(TAG, "onAgeStateChange: done setting up age")
        }
    }

    @BindingAdapter("uncheckWhen")
    fun uncheckWhen(
        checkBox: CheckBox,
        shouldUncheck: Boolean?
    ) {
        checkBox.apply {
            if (shouldUncheck == true && isChecked) {
                isChecked = false
            }
        }
    }

    @BindingAdapter("gestationalAgeUnits")
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

    @BindingAdapter("setTrafficLightDrawable")
    fun setTrafficLightDrawable(view: ImageView, analysis: ReadingAnalysis?) {
        if (analysis == null) {
            view.visibility = View.INVISIBLE
            return
        }
        setImageViewFromId(view, ReadingAnalysisViewSupport.getColorCircleImageId(analysis))
    }

    @BindingAdapter("setArrowDrawable")
    fun setArrowDrawable(view: ImageView, analysis: ReadingAnalysis?) {
        if (analysis == null) {
            view.visibility = View.INVISIBLE
            return
        }
        setImageViewFromId(view, ReadingAnalysisViewSupport.getArrowImageId(analysis))
    }

    @BindingAdapter("setSrcCompatBitmap")
    fun setSrcCompatBitmap(imageView: ImageView, drawable: Drawable) {
        imageView.setImageDrawable(drawable)
    }

    private fun setImageViewFromId(imageView: ImageView, @DrawableRes idRes: Int) {
        imageView.apply {
            if (idRes == 0) {
                visibility = View.INVISIBLE
            } else {
                visibility = View.VISIBLE
                val drawable = ResourcesCompat.getDrawable(resources, idRes, context.theme)
                setImageDrawable(drawable)
            }
        }
    }

    @BindingAdapter("addRecommendedToEndWhen")
    fun addRecommendedToEndWhen(
        radioButton: RadioButton,
        oldCondition: Boolean?,
        newCondition: Boolean?
    ) {
        val context = radioButton.context
        val recommendedSuffix = " " + context.getString(R.string.recommended_radio_button_suffix)
        if (oldCondition == newCondition) return
        if (newCondition != true) {
            if (radioButton.text.endsWith(recommendedSuffix)) {
                radioButton.text = radioButton.text.dropLast(recommendedSuffix.length)
            }
        } else {
            if (!radioButton.text.endsWith(recommendedSuffix)) {
                @SuppressLint("SetTextI18n")
                radioButton.text = radioButton.text.toString() + recommendedSuffix
            }
        }
    }
}
