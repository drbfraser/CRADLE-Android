package com.cradle.neptune.view.ui.reading

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.cradle.neptune.R
import com.cradle.neptune.binding.FragmentDataBindingComponent
import com.cradle.neptune.databinding.FragmentPatientInfoBinding
import com.cradle.neptune.model.Patient
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlinx.android.synthetic.main.fragment_symptoms.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

private const val TAG = "PatientInfoFragment"

private const val GA_UNIT_INDEX_WEEKS = 0
private const val GA_UNIT_INDEX_MONTHS = 1

private const val PATIENT_SEX_MALE = 0
private const val PATIENT_SEX_FEMALE = 1
private const val PATIENT_SEX_OTHER = 2

/**
 * Logic for the UI fragment which collects patient information when creating
 * or updating a reading.
 */
@Suppress("LargeClass")
class PatientInfoFragment : BaseFragment() {

    val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent(this)

    var binding: FragmentPatientInfoBinding? = null

    /**
     * A Mutex to lock the changing of the helper and error texts for age input.
     * If one thread sets an error text and then another thread sets an helper text,
     * the error text will be ignored.
     */
    private val ageHelperErrorTextMutex = Mutex(locked = false)

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
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.lifecycleOwner = viewLifecycleOwner
        binding?.viewModel = viewModel
        super.onViewCreated(view, savedInstanceState)
        viewModel.patientId.observe(viewLifecycleOwner) {
            viewModel.getValidityErrorMessagePair(
                value = it, isForPatient = true, property = Patient::id
            )
        }
        viewModel.patientName.observe(viewLifecycleOwner) {
            viewModel.getValidityErrorMessagePair(
                value = it, isForPatient = true, property = Patient::name
            )
        }

        viewModel.patientZone.observe(viewLifecycleOwner) {
            viewModel.getValidityErrorMessagePair(
                value = it, isForPatient = true, property = Patient::zone
            )
        }
        viewModel.patientVillageNumber.observe(viewLifecycleOwner) {
            viewModel.getValidityErrorMessagePair(
                value = it, isForPatient = true, property = Patient::villageNumber
            )
        }

        setupAndObserveAgeInfo(view)

        val genderTextLayout = view.findViewById<TextInputLayout>(R.id.gender_input_layout)
        val genders = view.resources.getStringArray(R.array.sex)
        val genderAdapter = ArrayAdapter(view.context, R.layout.list_item, genders)
        (genderTextLayout.editText as? AutoCompleteTextView)?.setAdapter(genderAdapter)

        lifecycleScope.launch(Dispatchers.Default) {
            val autoTextView = genderTextLayout.editText as? AutoCompleteTextView?
            while (true) {
                // TODO: Remove me.
                @Suppress("MagicNumber")
                delay(6000L)
                Log.d(TAG, "DEBUG: patientAge is ${viewModel.patientAge.value}, " +
                    "dob is ${viewModel.patientDob.value}")
                Log.d(TAG, "DEBUG: gender editText has selection: ${autoTextView?.listSelection}")
                Log.d(TAG, "DEBUG: gender editText has text: ${autoTextView?.text}; gender is " +
                    "actually ${viewModel.patientSex.value}")
            }
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
            .build().apply {
                addOnPositiveButtonClickListener {
                    @SuppressLint("SimpleDateFormat")
                    viewModel.patientDob.value =
                        SimpleDateFormat(Patient.DOB_FORMAT_SIMPLEDATETIME).format(Date(it))
                    Log.d(TAG, "DEBUG: DOB received from picker: ${viewModel.patientDob.value}")
                    viewModel.setAgeState(useDateOfBirth = true)
                }
            }
        ageInputLayout.apply {
            setStartIconOnClickListener {
                if (viewModel.isUsingDateOfBirth.value == true) {
                    // Clear icon is shown: handle clear icon actions
                    viewModel.setAgeState(useDateOfBirth = false)
                } else {
                    // Calendar icon is shown: handle calendar icon actions
                    datePicker.show(childFragmentManager, datePicker.toString())
                }
            }
        }
        viewModel.patientAge.observe(viewLifecycleOwner) {
            Log.d(TAG, "DEBUG: patientAge observe()")
            if (viewModel.isUsingDateOfBirth.value == true) {
                // This may be triggered when we calculate the actual age from the date of birth.
                // So, we bail out.
                Log.d(TAG, "DEBUG: patientAge observe(): exiting since dob is not null")
                return@observe
            }
            val (isValid, errorMessage) = viewModel.getValidityErrorMessagePair(
                value = it, isForPatient = true, property = Patient::age, putInErrorMap = false
            )
            ageInputLayout.apply {
                lifecycleScope.launch {
                    ageHelperErrorTextMutex.lock()
                    error = if (isValid) null else errorMessage
                    Log.d(TAG, "DEBUG: patient age: set error message to $error")
                    ageHelperErrorTextMutex.unlock()
                }
            }
        }
        viewModel.patientDob.observe(viewLifecycleOwner) {
            val (isValid, errorMessage) = viewModel.getValidityErrorMessagePair(
                value = it, isForPatient = true, property = Patient::dob, putInErrorMap = false
            )
            Log.d(TAG, "DEBUG: patient dob observe() value: $it, valid: $isValid, " +
                "errors: $errorMessage")

            ageInputLayout.apply {
                lifecycleScope.launch {
                    ageHelperErrorTextMutex.lock()
                    error = if (isValid) null else errorMessage
                    Log.d(TAG, "DEBUG: patient dob: set error message to $error")
                    ageHelperErrorTextMutex.unlock()
                }

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
                        R.string.years_old_with_date_of_birth_in_parens, it
                    )
                }
            }
        }

        viewModel.isUsingDateOfBirth.observe(viewLifecycleOwner) {
            lifecycleScope.launch { onAgeStateChange(it, ageInputLayout, ageEditText) }
        }
    }

    private fun setupCalendarConstraints(): CalendarConstraints {
        val lowerBoundMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
            set(Calendar.YEAR, DATE_PICKER_YEAR_LOWER_BOUND)
            timeInMillis
        }
        val upperBoundMillis = MaterialDatePicker.todayInUtcMilliseconds()
        val defaultDateInMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
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

    /**
     * Switches between state where user can input approximate age, and the state where user
     * selected a date of birth.
     *
     * - Date of birth state:
     *   If date of birth is being used, user input is denied, and the other thing the user can do
     *   is clear the date of birth. Clearing the date of birth brings it into the approximate age
     *   state.
     *
     * - Age input state:
     *   If date of birth is not being used, user input is allowed for approximate age. User also has
     *   the option to input a date of birth using a date picker.
     */
    @Suppress("NestedBlockDepth")
    private suspend fun onAgeStateChange(
        isUsingDateOfBirth: Boolean,
        layout: TextInputLayout,
        editText: TextInputEditText
    ) {
        // Mutex is needed to prevent helper text changes from overriding error messages.
        // We use the mutex over this entire function, since this function indirectly modifies a
        // LiveData value. This modification triggers the observers that set error messages.
        // Those observers need to be blocked until the helper text is set.
        ageHelperErrorTextMutex.lock()
        val context = layout.context
        if (isUsingDateOfBirth) {
            Log.d(TAG, "onAgeStateChange: setting up dob")
            editText.apply {
                inputType = InputType.TYPE_NULL
                Log.d(TAG, "onAgeStateChange: dob in EditText is now $text")
            }
            layout.apply {
                helperText = context.getString(R.string.dob_helper_using_age_from_date_of_birth)
                startIconDrawable = ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_baseline_clear_24, context.theme
                )
            }
            Log.d(TAG, "onAgeStateChange: done setting up dob")
        } else {
            Log.d(TAG, "onAgeStateChange: setting up age")
            val currentAge: Int? = viewModel.patientAge.value
            editText.apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                if (currentAge != null) {
                    // Changing the input type doesn't play nice with Data Binding; we need to
                    // manually set the text to this.
                    setText(currentAge.toString())
                }
            }
            layout.apply {
                suffixText = context.getString(R.string.age_input_suffix_approximate_years_old)
                helperText = context.getString(R.string.fragment_patient_info_dob_helper)
                startIconDrawable = ResourcesCompat.getDrawable(
                    resources, R.drawable.ic_baseline_calendar_today_24, context.theme
                )
            }
            Log.d(TAG, "onAgeStateChange: done setting up age")
        }
        ageHelperErrorTextMutex.unlock()
    }

    companion object {
        private const val DATE_PICKER_DEFAULT_YEAR = 2000
        private const val DATE_PICKER_YEAR_LOWER_BOUND = 1900

        private const val EDIT_TEXT_AGE_INPUT_STATE_TAG = 0
        private const val EDIT_TEXT_DOB_STATE_TAG = 1
    }
}
