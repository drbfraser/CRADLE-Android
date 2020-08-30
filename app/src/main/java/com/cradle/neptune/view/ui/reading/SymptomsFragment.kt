package com.cradle.neptune.view.ui.reading

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ToggleButton
import com.cradle.neptune.R
import com.cradle.neptune.model.UrineTest
import java.util.ArrayList

/**
 * Gather information about the patient.
 */
class SymptomsFragment : BaseFragment() {
    //urine tests
    private lateinit var leucRadioGroup: RadioGroup
    private lateinit var nitRadioGroup: RadioGroup
    private lateinit var protienRadioGroup: RadioGroup
    private lateinit var bloodRadioGroup: RadioGroup
    private lateinit var glucoseRadioGroup: RadioGroup
    private lateinit var urineResultTakenButton: ToggleButton
    private var mView: View? = null
    private val checkBoxes: MutableList<CheckBox> =
        ArrayList()
    private var noSymptomsCheckBox: CheckBox? = null
    private var otherSymptoms: EditText? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_symptoms, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        mView = view
        setupSymptoms(view)
        setupUrineResult(view)
    }

    private fun setupUrineResult(view: View) {
        leucRadioGroup = view.findViewById(R.id.leukRadioGroup)
        nitRadioGroup = view.findViewById(R.id.nitrRadioGroup)
        protienRadioGroup = view.findViewById(R.id.protientRadioGroup)
        bloodRadioGroup = view.findViewById(R.id.bloodRadioGroup)
        glucoseRadioGroup = view.findViewById(R.id.glucRadioGroup)
        val urineTestResultView =
            view.findViewById<View>(R.id.urineTestResultLayout)
        urineResultTakenButton = view.findViewById(R.id.UrineTestToggleButton)

        urineResultTakenButton.setOnClickListener {
            if (urineResultTakenButton.isChecked) {
                urineTestResultView.visibility = View.VISIBLE
            } else {
                urineTestResultView.visibility = View.GONE
            }
        }
    }

    override fun onMyBeingDisplayed() {
        // may not have created view yet.
        if (mView == null) {
            return
        }
        hideKeyboard()
        updateSymptoms_UiFromModel()
        if (urineResultTakenButton.isChecked) {
            val urineTestResultView =
                mView!!.findViewById<View>(R.id.urineTestResultLayout)
            urineTestResultView.visibility = View.VISIBLE
        }
        updateUrineTestUIFromModel()
    }

    private fun updateUrineTestUIFromModel() {
        val (leukocytes, nitrites, protein, blood, glucose) = viewModel!!.urineTest ?: return
        setupUrineTestResultRadio(leucRadioGroup, leukocytes)
        setupUrineTestResultRadio(bloodRadioGroup, blood)
        setupUrineTestResultRadio(protienRadioGroup, protein)
        setupUrineTestResultRadio(nitRadioGroup, nitrites)
        setupUrineTestResultRadio(glucoseRadioGroup, glucose)
    }

    private fun setupUrineTestResultRadio(
        radioGroup: RadioGroup?,
        leukocytes: String
    ) {
        radioGroup!!.clearCheck()
        for (i in 0 until radioGroup.childCount) {
            val radioButton = radioGroup.getChildAt(i) as RadioButton
            if (radioButton.text.toString() == leukocytes) {
                radioButton.isChecked = true
            }
        }
    }

    override fun onMyBeingHidden(): Boolean {
        // may not have created view yet.
        if (mView == null) {
            return true
        }
        updateSymptoms_ModelFromUi()
        updateUrineTestModelFromUI()
        return true
    }

    private fun updateUrineTestModelFromUI() {
        if (!urineResultTakenButton.isChecked) {
            viewModel!!.urineTest = null
            return
        }
        val leuk =
            (leucRadioGroup.findViewById(leucRadioGroup.checkedRadioButtonId) as RadioButton).text
                .toString()

        val blood =
            (bloodRadioGroup.findViewById(bloodRadioGroup.checkedRadioButtonId) as RadioButton).text
                .toString()
        val glucose =
            (glucoseRadioGroup.findViewById(glucoseRadioGroup.checkedRadioButtonId) as RadioButton).text
                .toString()
        val nitr =
            (nitRadioGroup.findViewById(nitRadioGroup.checkedRadioButtonId) as RadioButton).text
                .toString()
        val protient =
            (protienRadioGroup.findViewById(protienRadioGroup.checkedRadioButtonId) as RadioButton).text
                .toString()
        viewModel!!.urineTest = UrineTest(leuk, nitr, protient, blood, glucose)
    }

    /*
        Symptoms
     */
    private fun setupSymptoms(v: View) {
        val res = resources
        val symptomsFromRes =
            res.getStringArray(R.array.reading_symptoms)

        // populate symptoms
        checkBoxes.clear()
        val layout = v.findViewById<LinearLayout>(R.id.linearSymptoms)
        for ((index, symptom) in symptomsFromRes.withIndex()) {
            val cb = CheckBox(context)
            cb.text = symptom
            if (index == 0) {
                cb.isChecked = true
            }
            cb.setOnClickListener {
                onSymptomCheckboxClicked(
                    index
                )
            }
            layout.addView(cb)
            checkBoxes.add(cb)
            if (index == CHECKBOX_NO_SYMPTOMS_INDEX) {
                noSymptomsCheckBox = cb
            }
        }

        // change watcher on other symptoms:
        otherSymptoms = v.findViewById(R.id.etOtherSymptoms)

        otherSymptoms?.clearFocus()
        otherSymptoms?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence,
                i: Int,
                i1: Int,
                i2: Int
            ) {
            }

            override fun onTextChanged(
                charSequence: CharSequence,
                i: Int,
                i1: Int,
                i2: Int
            ) {
                onOtherSymptomEdited(charSequence)
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        updateSymptoms_UiFromModel()
    }

    private fun onSymptomCheckboxClicked(index: Int) {
        // did we just click "no symptoms"
        if (index == CHECKBOX_NO_SYMPTOMS_INDEX) {
            // clicked "no symptoms" --> uncheck all others
            for (cb in checkBoxes) {
                if (cb !== noSymptomsCheckBox) {
                    cb.isChecked = false
                }
            }
            otherSymptoms!!.clearFocus()
            otherSymptoms!!.setText("")
            viewModel!!.symptoms = ArrayList()
            //            viewModel.setHasNoSymptoms(true);
//            currentReading.userHasSelectedNoSymptoms = true;
        } else {
            // 'real' symptom clicked; turn off "no symptoms"
            noSymptomsCheckBox!!.isChecked = false
        }
    }

    private fun onOtherSymptomEdited(
        charSequence: CharSequence
    ) {
        if (charSequence.length > 0) {
            // 'real' symptom clicked; turn off "no symptoms"
            noSymptomsCheckBox!!.isChecked = false
        }
    }

    private fun updateSymptoms_UiFromModel() {
        // add checks for patient symptoms
        val res = resources
        val symptomsFromRes =
            res.getStringArray(R.array.reading_symptoms)
        var otherSymptomsStr = ""
        // TODO: [IMPORTANT] App crashes here if you navigate away and come back: currentReading could be null
        if (viewModel!!.symptoms == null || viewModel!!.symptoms!!.size == 0) {
            // no symptoms
            if (viewModel!!.symptoms != null && viewModel!!.symptoms!!.isEmpty()) {
                noSymptomsCheckBox!!.isChecked = true
            }
        } else {
            // some symptoms
            for (patientSymptom in viewModel!!.symptoms!!) {
                // find the symptom and check UI box
                var found = false
                for (i in symptomsFromRes.indices) {
                    if (symptomsFromRes[i] == patientSymptom) {
                        checkBoxes[i].isChecked = true
                        found = true
                        break
                    }
                }

                // add it to "other symptoms" if not found
                if (!found) {
                    otherSymptomsStr = otherSymptomsStr.trim { it <= ' ' }
                    if (otherSymptomsStr.length > 0) {
                        otherSymptomsStr += ", "
                    }
                    otherSymptomsStr += patientSymptom
                }
            }
        }
        // set other symptoms
        otherSymptoms!!.setText(otherSymptomsStr)
    }

    private fun updateSymptoms_ModelFromUi() {
        if (viewModel!!.symptoms != null) {
            viewModel!!.symptoms?.clear()
        } else {
            viewModel!!.symptoms = ArrayList()
        }

        // checkboxes
        for (cb in checkBoxes) {
            if (cb.isChecked) {
                viewModel!!.symptoms?.add(cb.text.toString())
            }
        }

        // other
        val otherSymptomsStr = otherSymptoms!!.text.toString().trim { it <= ' ' }
        if (otherSymptomsStr.isNotEmpty()) {
            viewModel!!.symptoms?.add(otherSymptomsStr)
        }
    }

    companion object {
        private const val CHECKBOX_NO_SYMPTOMS_INDEX = 0
        fun newInstance(): SymptomsFragment {
            return SymptomsFragment()
        }
    }

    init {
        // Required empty public constructor
        TAG = SymptomsFragment::class.java.name
    }
}