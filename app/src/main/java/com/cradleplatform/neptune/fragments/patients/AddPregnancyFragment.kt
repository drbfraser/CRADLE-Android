package com.cradleplatform.neptune.fragments.patients

import android.os.Bundle
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
import com.cradleplatform.neptune.databinding.FragmentAddPregnancyBinding
import com.cradleplatform.neptune.ext.hideKeyboard
import com.cradleplatform.neptune.viewmodel.patients.EditPregnancyViewModel
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

private const val TAG = "PatientInfoFragment"

private const val FRAGMENT_TAG_DATE_PICKER = "DatePicker"

class AddPregnancyFragment : Fragment() {

    private val viewModel: EditPregnancyViewModel by activityViewModels()

    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

    private var binding: FragmentAddPregnancyBinding? = null

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
            R.layout.fragment_add_pregnancy,
            container,
            false,
            dataBindingComponent
        )
        binding?.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = this@AddPregnancyFragment.viewModel
            executePendingBindings()
        }
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) {}

        lifecycleScope.apply {
            launch { setupGestationalAge(view) }
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
}
