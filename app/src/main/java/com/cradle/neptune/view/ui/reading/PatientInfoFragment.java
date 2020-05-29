package com.cradle.neptune.view.ui.reading;

import android.app.DatePickerDialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cradle.neptune.R;
import com.cradle.neptune.model.*;
import com.cradle.neptune.utilitiles.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gather information about the patient.
 */
public class PatientInfoFragment extends BaseFragment {
    /*
        Gestational Age
     */
    private static final int GA_UNIT_INDEX_WEEKS = 0;
    private static final int GA_UNIT_INDEX_MOTHS = 1;
    private static final int GA_UNIT_INDEX_LMP = -9;    // TODO: not yet implemented
    private static final int GA_UNIT_INDEX_NONE = 2;
    private static final int PATIENT_SEX_MALE = 0;
    private static final int PATIENT_SEX_FEMALE = 1;
    private static final int PATIENT_SEX_INTERSEX = 2;
    private View mView;
    private List<CheckBox> checkBoxes = new ArrayList<>();

    public PatientInfoFragment() {
        // Required empty public constructor
        TAG = PatientInfoFragment.class.getName();
    }

    public static PatientInfoFragment newInstance() {
        PatientInfoFragment fragment = new PatientInfoFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_patient_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mView = view;

        setupGASpinner(view);
        setupSexSpinner(view, false);
        setupDOBAgeSwitch(view);

        TextView et = mView.findViewById(R.id.dobTxt);

        et.setOnClickListener(view1 -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(getActivity(), new DatePickerDialog.OnDateSetListener() {
                @Override
                public void onDateSet(DatePicker datePicker, int year, int month, int day) {
                    String date = year + "-" + month + "-" + day;
                    et.setText(date);
                    viewModel.setPatientDob(date);
//                    currentReading.patient.dob = date;
                }
            }, 2010, 1, 1);
            datePickerDialog.show();
        });

    }

    private void setupDOBAgeSwitch(View view) {

        Switch sw = view.findViewById(R.id.dobSwitch);
        EditText dobtxt = view.findViewById(R.id.dobTxt);
        EditText ageET = view.findViewById(R.id.patientAgeEditTxt);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    dobtxt.setEnabled(true);
                    dobtxt.setClickable(true);
                    ageET.setText("");
                    ageET.setEnabled(false);
                    ageET.setClickable(false);
                    viewModel.setPatientAge(null);
//                    currentReading.patient.age = null;
                } else {
                    dobtxt.setEnabled(false);
                    dobtxt.setText("");
                    dobtxt.setClickable(false);
                    dobtxt.setText("");
                    ageET.setEnabled(true);
                    ageET.setClickable(true);
                    viewModel.setPatientDob(null);
//                    currentReading.patient.dob = null;
                }
            }
        });
    }

    @Override
    public void onMyBeingDisplayed() {
        // may not have created view yet.
        if (mView == null) {
            return;
        }
        hideKeyboard();

        updateText_UiFromModel(mView);
    }

    @Override
    public boolean onMyBeingHidden() {
        // may not have created view yet.
        if (mView == null) {
            return true;
        }
        updateSex_ModelFromUI(mView);
        updateText_ModelFromUi(mView);
        updateGA_ModelFromUi(mView);
        return true;
    }

    /*
        Text fields
     */
    private void updateText_UiFromModel(View mView) {
        EditText et;
        // id
        et = mView.findViewById(R.id.etPatientId);
        et.setText(viewModel.getPatientId());
//        et.setText(currentReading.patient.patientId);

        // initials
        et = mView.findViewById(R.id.etPatientName);
        et.setText(viewModel.getPatientName());
//        et.setText(currentReading.patient.patientName);

        // age
        EditText dobET = mView.findViewById(R.id.dobTxt);
        EditText ageET = mView.findViewById(R.id.patientAgeEditTxt);
        if (viewModel.getPatientDob() != null && !viewModel.getPatientDob().isEmpty()) {
            dobET.setText(viewModel.getPatientDob());
        } else if (viewModel.getPatientAge() != null && viewModel.getPatientAge() >= -1) {
            ageET.setText(viewModel.getPatientAge());
        }
//        if (currentReading.patient.dob != null && !currentReading.patient.dob.isEmpty()) {
//            dobET.setText(currentReading.patient.dob);
//        } else if (currentReading.patient.age != null && currentReading.patient.age >= -1) {
//            ageET.setText(currentReading.patient.age + "");
//        }
        setupSexSpinner(mView, true);

    }

    private void updateText_ModelFromUi(View mView) {
        EditText et;
        // id
        et = mView.findViewById(R.id.etPatientId);
        viewModel.setPatientId(et.getText().toString());
//        currentReading.patient.patientId = et.getText().toString();

        // initials
        et = mView.findViewById(R.id.etPatientName);
        viewModel.setPatientName(et.getText().toString());
//        currentReading.patient.patientName = et.getText().toString();

        // age
        EditText dobET = mView.findViewById(R.id.dobTxt);
        EditText ageET = mView.findViewById(R.id.patientAgeEditTxt);
        String dobStr = dobET.getText().toString().trim();
        String ageStr = ageET.getText().toString().trim();
        if (!dobStr.isEmpty()) {
            viewModel.setPatientDob(dobStr);
            viewModel.setPatientAge(null);
//            currentReading.patient.dob = dobStr;
//            currentReading.patient.age = null;
        } else if (!ageStr.isEmpty()) {
            viewModel.setPatientAge(Integer.parseInt(ageStr));
            viewModel.setPatientDob(null);
//            currentReading.patient.age = Integer.parseInt(ageStr);
//            currentReading.patient.dob = null;
        }

        // village number
        et = mView.findViewById(R.id.etVillageNumber);
        viewModel.setPatientVillageNumber(et.getText().toString());
//        currentReading.patient.villageNumber = et.getText().toString();

        // zone no
        et = mView.findViewById(R.id.etZone);
        viewModel.setPatientZone(et.getText().toString());
//        currentReading.patient.zone = et.getText().toString();

    }

    private void setupGASpinner(View v) {
        Spinner spin = v.findViewById(R.id.spinnerGestationalAgeUnits);

        // set options
        Resources res = getResources();
        String[] optionsArray = res.getStringArray(R.array.reading_ga_units);
        List<String> options = Arrays.asList(optionsArray);
        ArrayAdapter<String> dataAdapter =
                new ArrayAdapter<>(v.getContext(), android.R.layout.simple_spinner_item, options);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(dataAdapter);

        spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                updateGA_onSpinnerChange(mView);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }

    /*
        Patient Sex
     */


    private void updateGA_ModelFromUi(View v) {
        Spinner spin = v.findViewById(R.id.spinnerGestationalAgeUnits);
        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);

        Integer gestationalAgeValue = null;
        if (etValue.getText() != null && !etValue.getText().toString().isEmpty() && !etValue.getText().toString().equals("N/A")) {
            gestationalAgeValue = Integer.parseInt(etValue.getText().toString());
        }
        if (gestationalAgeValue != null) {
            switch (spin.getSelectedItemPosition()) {
//            case GA_UNIT_INDEX_NONE:
//                currentReading.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_NONE;
//                break;
                case GA_UNIT_INDEX_WEEKS:
                    viewModel.setPatientGestationalAge(new GestationalAgeWeeks(gestationalAgeValue));
//                currentReading.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS;
                    break;
                case GA_UNIT_INDEX_MOTHS:
                    viewModel.setPatientGestationalAge(new GestationalAgeMonths(gestationalAgeValue));
//                    currentReading.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_MONTHS;
                    break;
                default:
                    Util.ensure(false);
            }
        }

        // save value
//        currentReading.patient.gestationalAgeValue = etValue.getText().toString();
    }

    private void updateGA_onSpinnerChange(View v) {
        Spinner spin = v.findViewById(R.id.spinnerGestationalAgeUnits);
        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);

        String value = etValue.getText().toString();
        int valueInputType = InputType.TYPE_CLASS_NUMBER;
        boolean valueEnabled = true;
        String notApplicableString = v.getContext().getString(R.string.reading_not_applicable);

        switch (spin.getSelectedItemPosition()) {
//            case GA_UNIT_INDEX_NONE:
//                value = notApplicableString;
//                valueEnabled = false;
//                break;
            case GA_UNIT_INDEX_WEEKS:
                valueInputType = InputType.TYPE_CLASS_NUMBER;
                break;
            case GA_UNIT_INDEX_MOTHS:
                valueInputType = InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER;
                break;
            default:
                Util.ensure(false);
        }
        if (spin.getSelectedItemPosition() != GA_UNIT_INDEX_NONE
                && etValue.getText().toString().equals(notApplicableString)) {
            value = "N/A";
        }

        // Set UI state
//        etValue.setEnabled(valueEnabled);
        etValue.setInputType(valueInputType);
        etValue.setText(value);
    }

    private void setupSexSpinner(View v, boolean fromOldReading) {
        Spinner spin = v.findViewById(R.id.spinnerPatientSex);
        Switch isPregnant = v.findViewById(R.id.pregnantSwitch);
        Spinner spinGA = v.findViewById(R.id.spinnerGestationalAgeUnits);
        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);

        // set options
        Resources res = getResources();
        String[] optionsArray = res.getStringArray(R.array.sex);
        List<String> options = Arrays.asList(optionsArray);
        ArrayAdapter<String> dataAdapter =
                new ArrayAdapter<>(v.getContext(), android.R.layout.simple_spinner_item, options);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(dataAdapter);


        if (fromOldReading) {
            if (viewModel.getPatientSex() == Sex.FEMALE) {
//            if (currentReading.patient.patientSex == Patient.PATIENTSEX.FEMALE) {
                isPregnant.setEnabled(true);
                spin.setSelection(1);
            } else if (viewModel.getPatientSex() == Sex.MALE) {
//            } else if (currentReading.patient.patientSex == Patient.PATIENTSEX.MALE) {
                spin.setSelection(0);
            } else {
                spin.setSelection(2);
            }
            if (viewModel.getPatientIsPregnant()) {
//            if (currentReading.patient.isPregnant) {
                isPregnant.setChecked(true);
                etValue.setEnabled(true);
                etValue.setText(Integer.toString(viewModel.getPatientGestationalAge().getValue()));
//                etValue.setText(currentReading.patient.gestationalAgeValue);
                spinGA.setEnabled(true);
                if (viewModel.getPatientGestationalAge() instanceof GestationalAgeWeeks) {
//                if (currentReading.patient.gestationalAgeUnit == Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS) {
                    spinGA.setSelection(0);
                } else {
                    spinGA.setSelection(2);
                }

            }
        }

        spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                // updateGA_onSpinnerChange(mView);
                if (i == 0) {
                    isPregnant.setChecked(false);
                    isPregnant.setEnabled(false);
                    spinGA.setEnabled(false);
                    etValue.setEnabled(false);
                    etValue.setText("N/A");
                } else {
                    isPregnant.setEnabled(true);
                    isPregnant.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                            if (!b) {
                                etValue.setText("N/A");
                                etValue.setEnabled(false);
                                spinGA.setEnabled(false);
                            } else {
                                etValue.setText("");
                                etValue.setEnabled(true);
                                spinGA.setEnabled(true);
                            }
                        }
                    });
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }

    private void updateSex_ModelFromUI(View v) {
        Spinner spin = v.findViewById(R.id.spinnerPatientSex);
        Switch isPregnant = v.findViewById(R.id.pregnantSwitch);
//
//        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);
//        etValue.setText("N/A");

        switch (spin.getSelectedItemPosition()) {
            case PATIENT_SEX_MALE:
                viewModel.setPatientSex(Sex.MALE);
//                currentReading.patient.patientSex = Patient.PATIENTSEX.MALE;
                isPregnant.setChecked(false);
                viewModel.setPatientIsPregnant(false);
//                currentReading.patient.isPregnant = false;
                break;
            case PATIENT_SEX_FEMALE:
                viewModel.setPatientSex(Sex.FEMALE);
                viewModel.setPatientIsPregnant(isPregnant.isChecked());
//                currentReading.patient.patientSex = Patient.PATIENTSEX.FEMALE;
//                currentReading.patient.isPregnant = isPregnant.isChecked();
                break;
            case PATIENT_SEX_INTERSEX:
                viewModel.setPatientSex(Sex.OTHER);
                viewModel.setPatientIsPregnant(isPregnant.isChecked());
//                currentReading.patient.patientSex = Patient.PATIENTSEX.OTHERS;
//                currentReading.patient.isPregnant = isPregnant.isChecked();
                break;
            default:
                Util.ensure(false);
        }

    }


}
