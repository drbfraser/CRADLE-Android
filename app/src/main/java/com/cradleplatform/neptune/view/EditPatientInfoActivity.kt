package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ActivityEditPatientInfoBinding
import com.cradleplatform.neptune.ext.hideKeyboard
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.viewmodel.EditPatientViewModel
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class EditPatientInfoActivity : AppCompatActivity() {
    private val viewModel: EditPatientViewModel by viewModels()
    private var binding: ActivityEditPatientInfoBinding? = null
    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

    companion object {
        private const val TAG = "EditPatientInfoActivity"
        private const val EXTRA_PATIENT_ID = "patientId"
        private const val DATE_PICKER_DEFAULT_YEAR = 2000
        private const val DATE_PICKER_YEAR_LOWER_BOUND = 1900
        private const val DATE_PICKER_TIME_ZONE = "UTC"

        fun makeIntentWithPatientId(context: Context, patientId: String): Intent {
            val intent = Intent(context, EditPatientInfoActivity::class.java)
            intent.putExtra(EXTRA_PATIENT_ID, patientId)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_edit_patient_info, dataBindingComponent)
        binding?.apply {
            viewModel = this@EditPatientInfoActivity.viewModel
            lifecycleOwner = this@EditPatientInfoActivity
            executePendingBindings()
        }

        setupToolBar()
        setupSaveButton()

        if (intent.hasExtra(EXTRA_PATIENT_ID)) {
            val patientId = intent.getStringExtra(EXTRA_PATIENT_ID)
                ?: error("no patient with given id")
            viewModel.initialize(patientId)
        }

        lifecycleScope.apply {
            launch { setupAndObserveAgeInfo() }
            launch { setupAndObserveGenderList() }
        }
    }

    private fun setupToolBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.edit_patient)
    }

    private fun setupSaveButton() {
        val btnSave = findViewById<Button>(R.id.btn_save)
        btnSave.setOnClickListener {
            lifecycleScope.launch {
                when (viewModel.save()) {
                    is EditPatientViewModel.SaveResult.SavedAndUploaded -> {
                        Toast.makeText(
                            it.context,
                            "Success - patient sent to server ",
                            Toast.LENGTH_LONG
                        )
                            .show()
                        finish()
                    }
                    is EditPatientViewModel.SaveResult.SavedOffline -> {
                        Toast.makeText(
                            it.context,
                            "Please sync! Patient edits weren't pushed to server",
                            Toast.LENGTH_LONG
                        )
                            .show()
                        finish()
                    }
                    else -> {
                        Toast.makeText(
                            it.context,
                            "Invalid patient - check errors",
                            Toast.LENGTH_LONG
                        )
                            .show()
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    private fun setupAndObserveGenderList() {
        val genderTextLayout = findViewById<TextInputLayout>(R.id.gender_input_layout)
        (genderTextLayout.editText as? AutoCompleteTextView?)?.apply {
            setOnClickListener { it.hideKeyboard() }
        }
    }

    private fun setupAndObserveAgeInfo() {
        val ageInputLayout = findViewById<TextInputLayout>(R.id.age_input_layout)
        val ageEditText = findViewById<TextInputEditText>(R.id.age_input_text)

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
                    datePicker.show(supportFragmentManager, MaterialDatePicker::class.java.canonicalName)
                }
            }
        }

        viewModel.patientDob.observe(this@EditPatientInfoActivity) {
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
}
