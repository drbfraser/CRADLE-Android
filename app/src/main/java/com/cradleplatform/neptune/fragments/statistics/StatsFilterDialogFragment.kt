package com.cradleplatform.neptune.fragments.statistics

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.UserRole
import com.cradleplatform.neptune.activities.statistics.StatisticsFilterOptions
import com.cradleplatform.neptune.viewmodel.statistics.StatsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StatsFilterDialogFragment : DialogFragment() {
    private val viewModel: StatsViewModel by activityViewModels()

    private var selectedFilterOption: StatisticsFilterOptions? = null
    private var selectedHealthFacility: HealthFacility? = null
    private var healthFacilityPickerHasSelection = false

    interface FilterSelectionListener {
        fun onFilterSelected(
            filterOption: StatisticsFilterOptions,
            healthFacility: HealthFacility?
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore state
        savedInstanceState?.let {
            selectedFilterOption = it.getSerializable(KEY_FILTER_OPTION) as? StatisticsFilterOptions
            selectedHealthFacility = it.getParcelable(KEY_HEALTH_FACILITY)
            healthFacilityPickerHasSelection = it.getBoolean(KEY_HAS_SELECTION, false)
        }

        // Initialize from ViewModel if not restored
        if (selectedFilterOption == null) {
            selectedFilterOption = viewModel.savedFilterOption
            selectedHealthFacility = viewModel.savedHealthFacility
            healthFacilityPickerHasSelection = viewModel.savedHealthFacility != null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.stats_activity_filter_header))

        val dialogView = layoutInflater.inflate(R.layout.dialog_stats_filter_picker, null)
        builder.setView(dialogView)

        val healthFacilityPicker = dialogView.findViewById<AutoCompleteTextView>(
            R.id.health_facility_auto_complete_text
        )
        val healthFacilityLayout = dialogView.findViewById<TextInputLayout>(R.id.health_facility_input_layout)
        val healthTextView = dialogView.findViewById<TextView>(R.id.filterPickerTextView)

        builder.setNegativeButton(getString(android.R.string.cancel), null)
        builder.setPositiveButton(getString(android.R.string.ok)) { _, _ ->
            selectedFilterOption?.let { filterOption ->
                (activity as? FilterSelectionListener)?.onFilterSelected(
                    filterOption,
                    selectedHealthFacility
                )
            }
        }

        val dialog = builder.create()

        // Setup views after dialog is created
        dialog.setOnShowListener {
            setupDialogViews(dialogView, healthFacilityPicker, healthFacilityLayout, healthTextView, dialog)
        }

        return dialog
    }

    private fun setupDialogViews(
        dialogView: View,
        healthFacilityPicker: AutoCompleteTextView,
        healthFacilityLayout: TextInputLayout,
        healthTextView: TextView,
        dialog: Dialog
    ) {
        val alertDialog = dialog as? androidx.appcompat.app.AlertDialog ?: return

        val allStatsButton = dialogView.findViewById<RadioButton>(R.id.statFilterDialog_showAllButton)
        val facilityButton = dialogView.findViewById<RadioButton>(R.id.statFilterDialog_healthFacilityButton)

        // Button enable/disable based on role
        val roleString = requireActivity().getSharedPreferences(
            getString(R.string.key_shared_pref_name),
            android.content.Context.MODE_PRIVATE
        ).getString(getString(R.string.key_role), UserRole.VHT.toString())

        roleString?.let {
            when (UserRole.safeValueOf(it)) {
                UserRole.VHT -> {
                    allStatsButton.isEnabled = false
                    facilityButton.isEnabled = false
                }
                UserRole.CHO -> {
                    allStatsButton.isEnabled = false
                }
                UserRole.HCW -> {
                    allStatsButton.isEnabled = false
                }
                UserRole.UNKNOWN -> {
                    healthTextView.text = getString(R.string.stats_activity_unknown_role)
                    healthTextView.setTextColor(Color.RED)
                    healthTextView.visibility = View.VISIBLE
                }
                else -> {
                    // Leave them all open for ADMIN
                }
            }
        }

        healthFacilityLayout.visibility = View.GONE
        healthTextView.visibility = View.GONE

        // Setup health facility picker
        val facilityStringArray = viewModel.healthFacilityArray.map { it.name }.toTypedArray()
        healthFacilityPicker.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.support_simple_spinner_dropdown_item,
                facilityStringArray
            )
        )

        healthFacilityPicker.setOnItemClickListener { _, _, position: Int, _ ->
            selectedHealthFacility = viewModel.healthFacilityArray[position]
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = true
            healthFacilityPickerHasSelection = true
        }

        // If we have already set a health facility, set it as selected
        selectedHealthFacility?.let {
            healthFacilityPicker.setText(it.name, false)
            healthFacilityPickerHasSelection = true
        }

        // Setup radio group
        val buttonGroup = dialogView.findViewById<RadioGroup>(R.id.statFilterDialog_radioGroup)
        when (selectedFilterOption) {
            StatisticsFilterOptions.ALL -> buttonGroup.check(R.id.statFilterDialog_showAllButton)
            StatisticsFilterOptions.JUSTME -> buttonGroup.check(R.id.statFilterDialog_userIDButton)
            StatisticsFilterOptions.BYFACILITY -> {
                buttonGroup.check(R.id.statFilterDialog_healthFacilityButton)
                healthFacilityLayout.visibility = View.VISIBLE
                healthTextView.visibility = View.VISIBLE
            }
            null -> {}
        }

        buttonGroup.setOnCheckedChangeListener { radioGroup: RadioGroup, _ ->
            when (radioGroup.checkedRadioButtonId) {
                R.id.statFilterDialog_healthFacilityButton -> {
                    if (!healthFacilityPickerHasSelection) {
                        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
                    }
                    healthFacilityLayout.visibility = View.VISIBLE
                    healthTextView.visibility = View.VISIBLE
                    selectedFilterOption = StatisticsFilterOptions.BYFACILITY
                }
                R.id.statFilterDialog_showAllButton -> {
                    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = true
                    healthFacilityLayout.visibility = View.GONE
                    healthTextView.visibility = View.GONE
                    selectedFilterOption = StatisticsFilterOptions.ALL
                }
                R.id.statFilterDialog_userIDButton -> {
                    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = true
                    healthFacilityLayout.visibility = View.GONE
                    healthTextView.visibility = View.GONE
                    selectedFilterOption = StatisticsFilterOptions.JUSTME
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(KEY_FILTER_OPTION, selectedFilterOption)
        outState.putParcelable(KEY_HEALTH_FACILITY, selectedHealthFacility)
        outState.putBoolean(KEY_HAS_SELECTION, healthFacilityPickerHasSelection)
    }

    companion object {
        const val TAG = "StatsFilterDialogFragment"
        private const val KEY_FILTER_OPTION = "filter_option"
        private const val KEY_HEALTH_FACILITY = "health_facility"
        private const val KEY_HAS_SELECTION = "has_selection"

        fun newInstance(): StatsFilterDialogFragment {
            return StatsFilterDialogFragment()
        }
    }
}

