package com.cradleplatform.neptune.view.ui.editPregnancy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.FragmentClosePregnancyBinding
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.view.ReadingActivity
import com.cradleplatform.neptune.viewmodel.EditPregnancyViewModel
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val FRAGMENT_TAG_DATE_PICKER = "DatePicker"

class ClosePregnancyFragment : Fragment() {
    /**
     * ViewModel is scoped to the [ReadingActivity] that this Fragment is attached to; therefore,
     * this is shared by all Fragments.
     */
    private val viewModel: EditPregnancyViewModel by activityViewModels()

    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

    private var binding: FragmentClosePregnancyBinding? = null

    companion object {
        private const val DATE_PICKER_YEAR_LOWER_BOUND = 1900
        private const val DATE_PICKER_TIME_ZONE = "UTC"
    }

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
            R.layout.fragment_close_pregnancy,
            container,
            false,
            dataBindingComponent
        )
        binding?.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = this@ClosePregnancyFragment.viewModel
            executePendingBindings()
        }
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) {}

        viewModel.pregnancyStartTimestamp.observe(viewLifecycleOwner) {
            lifecycleScope.apply {
                launch { setupAndObserveAgeInfo(view) }
            }
        }
    }

    private fun setupAndObserveAgeInfo(view: View) {
        val endDateLayout = view.findViewById<TextInputLayout>(R.id.gestational_age_layout)
        val displayEndDate = view.findViewById<TextInputEditText>(R.id.display_end_date)

        // can you print the gestational time?

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Set pregnancy end date")
            .setCalendarConstraints(setupCalendarConstraints())
            .build()
        datePicker.apply {
            addOnPositiveButtonClickListener {

                // Time is in miliseconds so we have to convert it to seconds
                val dateTimestampSeconds = with(SimpleDateFormat(Patient.DOB_FORMAT_SIMPLEDATETIME, Locale.getDefault())) {
                    timeZone = TimeZone.getTimeZone(DATE_PICKER_TIME_ZONE)
                    Date(it).time
                } / 1000

                viewModel.pregnancyEndTimestamp = dateTimestampSeconds
                viewModel.gestationalAgeFromEndDate(dateTimestampSeconds.toBigInteger())
                viewModel.checkEndDateErrors(dateTimestampSeconds)

                displayEndDate.setText(
                    with(SimpleDateFormat(Patient.DOB_FORMAT_SIMPLEDATETIME, Locale.getDefault())) {
                        timeZone = TimeZone.getTimeZone(DATE_PICKER_TIME_ZONE)
                        format(Date(it))
                    }
                )
            }
        }
        endDateLayout.apply {
            setStartIconOnClickListener {
                // Calendar icon is shown: handle calendar icon actions
                if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DATE_PICKER) == null) {
                    datePicker.show(childFragmentManager, FRAGMENT_TAG_DATE_PICKER)
                }
            }
        }
    }

    private fun setupCalendarConstraints(): CalendarConstraints {
        val datePickerTimeZone = TimeZone.getTimeZone(DATE_PICKER_TIME_ZONE)
        // make this value the value of gestationalAge

        var lowerBoundMillis = (viewModel.pregnancyStartTimestamp.value?.toLong())?.times(
            1000
        )

        if (lowerBoundMillis == null) {
            lowerBoundMillis = Calendar.getInstance(datePickerTimeZone).run {
                set(Calendar.YEAR, DATE_PICKER_YEAR_LOWER_BOUND)
                timeInMillis
            }
        }

        val upperBoundMillis = MaterialDatePicker.todayInUtcMilliseconds()
        val defaultDateInMillis = MaterialDatePicker.todayInUtcMilliseconds()

        check(lowerBoundMillis < defaultDateInMillis)
        check(defaultDateInMillis <= upperBoundMillis)

        return CalendarConstraints.Builder()
            .setStart(lowerBoundMillis)
            .setEnd(upperBoundMillis)
            .setOpenAt(defaultDateInMillis)
            .build()
    }
}
