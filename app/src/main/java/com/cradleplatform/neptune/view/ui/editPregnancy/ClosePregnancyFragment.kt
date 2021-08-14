package com.cradleplatform.neptune.view.ui.editPregnancy

import android.os.Bundle
import android.util.Log
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
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "Edit"

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
        private const val DATE_PICKER_DEFAULT_YEAR = 2000
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

        lifecycleScope.apply {
            launch { setupAndObserveAgeInfo(view) }
        }
    }

    //CHANGE TO BE GESTATIONAL AGE VIA END DATE
    private fun setupAndObserveAgeInfo(view: View) {
        val endDateLayout = view.findViewById<TextInputLayout>(R.id.gestational_age)
        //TODO: some error checking on the input - and some bounds for the datepicker
        // also put the date in the layout (Add text to xml and set it with pregnancyEndDate)

        // can you print the gestational time?

        // https://github.com/material-components/material-components-android/blob/master/catalog/
        // java/io/material/catalog/datepicker/DatePickerMainDemoFragment.java
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.age_date_picker_title)
            .setCalendarConstraints(setupCalendarConstraints())
            .build()
        datePicker.apply {
            addOnPositiveButtonClickListener {
                var pregnancyEndDate =
                    with(SimpleDateFormat(Patient.DOB_FORMAT_SIMPLEDATETIME, Locale.getDefault())) {
                        timeZone = TimeZone.getTimeZone(DATE_PICKER_TIME_ZONE)
                        format(Date(it))
                    }
                Log.d(TAG, pregnancyEndDate)
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
