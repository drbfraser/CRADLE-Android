package com.cradle.neptune.view.ui.reading

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import com.cradle.neptune.R
import com.cradle.neptune.model.GestationalAgeMonths
import com.cradle.neptune.model.GestationalAgeWeeks
import com.cradle.neptune.model.Sex
import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Weeks
import com.cradle.neptune.utilitiles.nullIfEmpty
import com.cradle.neptune.utilitiles.unreachable
import kotlin.math.roundToInt
import org.threeten.bp.ZonedDateTime

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

    private lateinit var mView: View
    private lateinit var gestationalAgeSpinner: Spinner
    private lateinit var sexSpinner: Spinner

    private val gestationalAgeEditText
        get() = mView.findViewById<EditText>(R.id.etGestationalAgeValue)

    private val dobEditText
        get() = mView.findViewById<EditText>(R.id.dobTxt)

    private val ageEditText
        get() = mView.findViewById<EditText>(R.id.patientAgeEditTxt)

    private val pregnantSwitch
        get() = mView.findViewById<Switch>(R.id.pregnantSwitch)

    private val dobSwitch
        get() = mView.findViewById<Switch>(R.id.dobSwitch)

    init {
        // Overwrite base class's TAG field for logging purposes.
        TAG = this::class.java.name
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment.
        return inflater.inflate(R.layout.fragment_patient_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mView = view

        // Register listener with the pregnancy switch.
        pregnantSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Enable gestational age elements if the patient is pregnant.
                gestationalAgeEditText.setText("")
                gestationalAgeEditText.isEnabled = true
                gestationalAgeSpinner.isEnabled = true
            } else {
                // Disable them otherwise.
                gestationalAgeEditText.setText(getString(R.string.not_available_n_slash_a))
                gestationalAgeEditText.isEnabled = false
                gestationalAgeSpinner.isEnabled = false
            }
        }

        // Setup the gestational age spinner menu.
        gestationalAgeSpinner =
            setupSpinner(
                view,
                R.id.spinnerGestationalAgeUnits,
                R.array.reading_ga_units
            ) { spinner ->
                // Update the input type of the "Gestational Age" input box depending on
                // whether we are entering a value in weeks or months.
                gestationalAgeEditText.inputType = when (spinner.selectedItemPosition) {
                    // For weeks we only want whole numbers.
                    GA_UNIT_INDEX_WEEKS -> InputType.TYPE_CLASS_NUMBER
                    // For months we allow the user to enter fractional values with a
                    // decimal point.
                    GA_UNIT_INDEX_MONTHS -> InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_CLASS_NUMBER
                    else -> unreachable("illegal gestational age spinner state")
                }
            }

        // Setup the patient sex spinner menu.
        sexSpinner = setupSpinner(view, R.id.spinnerPatientSex, R.array.sex) { spinner ->
            if (spinner.selectedItemPosition == PATIENT_SEX_MALE) {
                // Disable pregnancy elements if the patient's sex is male.
                pregnantSwitch.isChecked = false
                pregnantSwitch.isEnabled = false
                gestationalAgeSpinner.isEnabled = false
                gestationalAgeEditText.isEnabled = false
                gestationalAgeEditText.setText(getString(R.string.not_available_n_slash_a))
            } else {
                // Enable pregnancy elements if the patient's sex is not male.
                pregnantSwitch.isEnabled = true
            }
        }

        // Setup date of birth switch.
        dobSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                dobEditText.isEnabled = true
                dobEditText.isClickable = true

                ageEditText.isEnabled = false
                ageEditText.isClickable = false
                ageEditText.setText("")

                viewModel?.patientAge = null
            } else {
                dobEditText.isEnabled = false
                dobEditText.isClickable = false
                dobEditText.setText("")

                ageEditText.isEnabled = true
                ageEditText.isClickable = true

                viewModel?.patientDob = null
            }
        }

        // Setup date picker for date of birth edit text.
        dobEditText.setOnClickListener {
            Log.d(this::class.java.name, "Showing Date picker dialog")
            val listener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                // Month ordinals are zero indexed.
                val date = "$year-${month + 1}-$dayOfMonth"
                dobEditText.setText(date)
                viewModel?.patientDob = date
            }

            val dialog = DatePickerDialog(requireActivity(), listener, DatePickerDefaultYear, 0, 1)
            dialog.show()
        }
    }

    /**
     * Called when this fragment is displayed.
     *
     * Pulls information from the view model and populates the required UI elements.
     */
    override fun onMyBeingDisplayed() {
        // This method may be called before the fragment has been initialized.
        // In such cases we simply do nothing.
        // TODO: This method should only be called after the fragment has been
        //  initialized, this is a hack solution and needs a proper fix.
        if (!this::mView.isInitialized) {
            return
        }
        hideKeyboard()

        // Pull data from view model and update UI elements.
        mView.findViewById<EditText>(R.id.etPatientId).setText(viewModel?.patientId)
        mView.findViewById<EditText>(R.id.etPatientName).setText(viewModel?.patientName)
        mView.findViewById<EditText>(R.id.dobTxt).setText(viewModel?.patientDob)
        mView.findViewById<EditText>(R.id.patientAgeEditTxt)
            .setText(viewModel?.patientAge?.toString())
        // Check the dob switch if we have a dob value or we don't have either value.
        dobSwitch.isChecked = viewModel?.patientDob != null || viewModel?.patientAge == null
        mView.findViewById<EditText>(R.id.etVillageNumber).setText(viewModel?.patientVillageNumber)
        mView.findViewById<EditText>(R.id.etZone).setText(viewModel?.patientZone)

        // If for some reason, both date of birth and patient age are present
        // in the view model, prefer date of birth.
        if (viewModel?.patientDob != null && viewModel?.patientAge != null) {
            mView.findViewById<EditText>(R.id.patientAgeEditTxt).text = null
        }

        // Pull pregnancy and sex data fro the view model and update the UI accordingly.
        pregnantSwitch.isEnabled = viewModel?.patientSex != Sex.MALE
        sexSpinner.setSelection(viewModel?.patientSex?.let { sexIndex(it) } ?: PATIENT_SEX_MALE)
        if (viewModel?.patientIsPregnant == true) {
            pregnantSwitch.isChecked = true
            gestationalAgeEditText.isEnabled = true
            gestationalAgeSpinner.isEnabled = true
            when (val gestationalAge = viewModel?.patientGestationalAge) {
                is GestationalAgeWeeks -> {
                    gestationalAgeSpinner.setSelection(GA_UNIT_INDEX_WEEKS)
                    gestationalAgeEditText.setText(
                        gestationalAge.age.asWeeks().roundToInt().toString()
                    )
                }
                is GestationalAgeMonths -> {
                    gestationalAgeSpinner.setSelection(GA_UNIT_INDEX_MONTHS)
                    gestationalAgeEditText.setText(
                        gestationalAge.age.asMonths().toString()
                    )
                }
                else -> {
                    gestationalAgeSpinner.setSelection(GA_UNIT_INDEX_WEEKS)
                    gestationalAgeEditText.setText("")
                }
            }
        }
    }

    /**
     * Called with this fragment is hidden.
     *
     * Pushes values from the UI elements into the view model to persist them.
     */
    @Suppress("NestedBlockDepth")
    override fun onMyBeingHidden(): Boolean = with(viewModel) {
        if (!this@PatientInfoFragment::mView.isInitialized) {
            return true
        }

        this?.patientId = mView.editText(R.id.etPatientId).nullIfEmpty()
        this?.patientName = mView.editText(R.id.etPatientName).nullIfEmpty()

        this?.patientDob = mView.editText(R.id.dobTxt).nullIfEmpty()
        this?.patientAge = mView.editText(R.id.patientAgeEditTxt).toIntOrNull()
        // If for some reason both age and dob have been populated, prefer dob.
        if (!(this?.patientDob?.equals(null) ?: (true)) && this?.patientAge != null) {
            patientAge = null
        }

        this?.patientVillageNumber = mView.editText(R.id.etVillageNumber).nullIfEmpty()
        this?.patientZone = mView.editText(R.id.etZone).nullIfEmpty()

        this?.patientSex = sexFromIndex(sexSpinner.selectedItemPosition)
        this?.patientIsPregnant = pregnantSwitch.isChecked

        this?.patientGestationalAge = run {
            if (!this?.patientIsPregnant!!) {
                return@run null
            }

            when (gestationalAgeSpinner.selectedItemPosition) {
                GA_UNIT_INDEX_WEEKS -> {
                    val value = mView.editText(R.id.etGestationalAgeValue).toLongOrNull()
                    return@run if (value == null) null else GestationalAgeWeeks(Weeks(value))
                }
                GA_UNIT_INDEX_MONTHS -> {
                    val value = mView.editText(R.id.etGestationalAgeValue).toDoubleOrNull()
                    return@run if (value == null) null else GestationalAgeMonths(Months(value))
                }
                else -> unreachable("illegal gestational age spinner state")
            }
        }

        this?.patientLastEdited = ZonedDateTime.now().toEpochSecond()
        return true
    }

    /**
     * Sets up a generic UI spinner.
     *
     * @param v the view that the spinner is located in
     * @param id the identifier for the spinner UI element
     * @param optionsId the identifier for the array of strings used to populate the spinner
     * @param onItemSelectedCallback a callback called whenever an item is selected
     */
    private fun setupSpinner(
        v: View,
        id: Int,
        optionsId: Int,
        onItemSelectedCallback: (Spinner) -> Unit
    ): Spinner {
        val spinner = v.findViewById<Spinner>(id)
        val options = resources.getStringArray(optionsId).toList()
        val adapter = ArrayAdapter(v.context, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Register callback as a listener within the spinner.
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                onItemSelectedCallback(spinner)
            }
        }
        return spinner
    }

    companion object {
        private val DatePickerDefaultYear = 2000
    }
}

/**
 * Converts [sex] into an index.
 *
 * We use this function instead of [Sex.ordinal] because, in the event that
 * someone re-orders or adds a new [Sex] variant, the UI would silently break.
 */
private fun sexIndex(sex: Sex): Int = when (sex) {
    Sex.MALE -> PATIENT_SEX_MALE
    Sex.FEMALE -> PATIENT_SEX_FEMALE
    Sex.OTHER -> PATIENT_SEX_OTHER
}

/**
 * Converts an index into a [Sex] variant.
 */
private fun sexFromIndex(index: Int): Sex = when (index) {
    PATIENT_SEX_MALE -> Sex.MALE
    PATIENT_SEX_FEMALE -> Sex.FEMALE
    PATIENT_SEX_OTHER -> Sex.OTHER
    else -> unreachable("illegal sex index")
}

private fun View.editText(id: Int): String = this
    .findViewById<EditText>(id)
    .text
    .toString()
    .trim()
