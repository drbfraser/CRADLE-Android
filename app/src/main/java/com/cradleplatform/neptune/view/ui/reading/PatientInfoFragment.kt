package com.cradleplatform.neptune.view.ui.reading

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.FragmentPatientInfoBinding
import com.cradleplatform.neptune.ext.hideKeyboard
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.view.ReadingActivity
import com.cradleplatform.neptune.viewmodel.PatientReadingViewModel
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "PatientInfoFragment"

private const val FRAGMENT_TAG_DATE_PICKER = "DatePicker"

/**
 * Logic for the UI fragment which collects patient information when creating
 * or updating a reading.
 */
class PatientInfoFragment : Fragment() {
    /**
     * ViewModel is scoped to the [ReadingActivity] that this Fragment is attached to; therefore,
     * this is shared by all Fragments.
     */
    private val viewModel: PatientReadingViewModel by activityViewModels()

    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

    private var binding: FragmentPatientInfoBinding? = null

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_patient_info,
            container,
            false,
            dataBindingComponent
        )
        binding?.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = this@PatientInfoFragment.viewModel
            executePendingBindings()
        }
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) {}

        lifecycleScope.apply {
            launch { setupAndObserveAgeInfo(view) }
            launch { setupAndObserveGenderList(view) }
            launch { setupGestationalAge(view) }
        }
    }

    private fun setupAndObserveAgeInfo(view: View) {
        val ageInputLayout = view.findViewById<TextInputLayout>(R.id.age_input_layout)
        val ageEditText = view.findViewById<TextInputEditText>(R.id.age_input_text)

        // https://github.com/material-components/material-components-android/blob/master/catalog/
        // java/io/material/catalog/datepicker/DatePickerMainDemoFragment.java
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.age_date_picker_title)
            .setCalendarConstraints(setupCalendarConstraints())
            .build()
        datePicker.apply {
            addOnPositiveButtonClickListener {
                viewModel.setUsingDateOfBirth(useDateOfBirth = true)
                viewModel.patientDob.value =
                    with(SimpleDateFormat(Patient.DOB_FORMAT_SIMPLEDATETIME, Locale.getDefault())) {
                        timeZone = TimeZone.getTimeZone(DATE_PICKER_TIME_ZONE)
                        format(Date(it))
                    }
            }
        }
        ageInputLayout.apply {
            setStartIconOnClickListener {
                if (viewModel.patientIsExactDob.value == true) {
                    // Clear icon is shown: handle clear icon actions
                    viewModel.setUsingDateOfBirth(useDateOfBirth = false)
                } else {
                    // Calendar icon is shown: handle calendar icon actions
                    if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DATE_PICKER) == null) {
                        datePicker.show(childFragmentManager, FRAGMENT_TAG_DATE_PICKER)
                    }
                }
            }
        }

        viewModel.patientDob.observe(viewLifecycleOwner) {
            if (viewModel.patientIsExactDob.value != true) {
                Log.d(TAG, "DEBUG: patientDob observe(): exiting since isUsingDateOfBirth false")
                return@observe
            }

            ageInputLayout.apply {
                if (it != null) {
                    Log.d(TAG, "DEBUG: patient dob: setting patient age")
                    viewModel.patientAge.value = try {
                        Patient.calculateAgeFromDateString(it).also { age ->
                            // We have to set the text manually; Data Binding doesn't seem to work
                            // if the field is not editable.
                            ageEditText.setText(age.toString())
                        }
                    } catch (_: ParseException) {
                        Log.d(TAG, "DEBUG: patient dob: setting patient age got exception")
                        null
                    }
                    suffixText = ageEditText.context.getString(
                        R.string.years_old_with_date_of_birth_in_parens,
                        it
                    )
                }
            }
        }
    }

    private fun setupAndObserveGenderList(view: View) {
        val genderTextLayout = view.findViewById<TextInputLayout>(R.id.gender_input_layout)
        (genderTextLayout.editText as? AutoCompleteTextView?)?.apply {
            setOnClickListener { it.hideKeyboard() }
        }
    }

    private fun setupGestationalAge(view: View) {
        val gestAgeUnitsTextLayout = view.findViewById<TextInputLayout>(
            R.id.gestational_age_units_layout
        )
        (gestAgeUnitsTextLayout.editText as? AutoCompleteTextView?)?.apply {
            setOnClickListener { it.hideKeyboard() }
        }
    }

    private fun setupCalendarConstraints(): CalendarConstraints {
        val datePickerTimeZone = TimeZone.getTimeZone(DATE_PICKER_TIME_ZONE)
        val lowerBoundMillis = Calendar.getInstance(datePickerTimeZone).run {
            set(Calendar.YEAR, DATE_PICKER_YEAR_LOWER_BOUND)
            timeInMillis
        }
        val upperBoundMillis = MaterialDatePicker.todayInUtcMilliseconds()
        val defaultDateInMillis = Calendar.getInstance(datePickerTimeZone).run {
            set(Calendar.YEAR, DATE_PICKER_DEFAULT_YEAR)
            timeInMillis
        }

        check(lowerBoundMillis < defaultDateInMillis)
        check(defaultDateInMillis < upperBoundMillis)

        return CalendarConstraints.Builder()
            .setStart(lowerBoundMillis)
            .setEnd(upperBoundMillis)
            .setOpenAt(defaultDateInMillis)
            .build()
    }

    companion object {
        private const val DATE_PICKER_DEFAULT_YEAR = 2000
        private const val DATE_PICKER_YEAR_LOWER_BOUND = 1900
        private const val DATE_PICKER_TIME_ZONE = "UTC"
    }
}
