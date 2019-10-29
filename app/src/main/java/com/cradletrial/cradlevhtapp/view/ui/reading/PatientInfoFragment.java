package com.cradletrial.cradlevhtapp.view.ui.reading;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;
import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.utilitiles.Util;

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
        setupSexSpinner(view);
    }

    @Override
    public void onMyBeingDisplayed() {
        // may not have created view yet.
        if (mView == null) {
            return;
        }
        hideKeyboard();

        updateText_UiFromModel(mView);
        updateGA_UiFromModel(mView);
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
        et.setText(currentReading.patient.patientId);

        // initials
        et = mView.findViewById(R.id.etPatientName);
        et.setText(currentReading.patient.patientName);

        // age
        et = mView.findViewById(R.id.etPatientAge);
        if (currentReading.patient.ageYears != null) {
            et.setText(Integer.toString(currentReading.patient.ageYears));
        } else {
            et.setText("");
        }
    }

    private void updateText_ModelFromUi(View mView) {
        EditText et;
        // id
        et = mView.findViewById(R.id.etPatientId);
        currentReading.patient.patientId = et.getText().toString();

        // initials
        et = mView.findViewById(R.id.etPatientName);
        currentReading.patient.patientName = et.getText().toString();

        // age
        et = mView.findViewById(R.id.etPatientAge);
        String ageStr = et.getText().toString().trim();
        if (ageStr.length() > 0) {
            currentReading.patient.ageYears = Util.stringToIntOr0(ageStr);
        }

        // village number
        et = mView.findViewById(R.id.etVillageNumber);
        currentReading.patient.villageNumber = et.getText().toString();

        // zone no
        et = mView.findViewById(R.id.etZone);
        currentReading.patient.zone = et.getText().toString();

        // tank no
        et = mView.findViewById(R.id.etTankNumber);
        currentReading.patient.tankNo = et.getText().toString();

        // house number
        et = mView.findViewById(R.id.etHouseNumber);
        currentReading.patient.houseNumber = et.getText().toString();
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

    private void updateGA_UiFromModel(View v) {
        Spinner spin = v.findViewById(R.id.spinnerGestationalAgeUnits);
        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);

        int selection = 0;

        if (currentReading.patient.gestationalAgeUnit != null) {
            switch (currentReading.patient.gestationalAgeUnit) {
                case GESTATIONAL_AGE_UNITS_NONE:
                    selection = GA_UNIT_INDEX_NONE;
                    break;
                case GESTATIONAL_AGE_UNITS_WEEKS:
                    selection = GA_UNIT_INDEX_WEEKS;
                    break;
                case GESTATIONAL_AGE_UNITS_MONTHS:
                    selection = GA_UNIT_INDEX_MOTHS;
                    break;
                default:
                    Util.ensure(false);
            }
        }

        // Set UI state
        spin.setSelection(selection);
        etValue.setText(currentReading.patient.gestationalAgeValue);

        updateGA_onSpinnerChange(v);
    }

    private void updateGA_ModelFromUi(View v) {
        Spinner spin = v.findViewById(R.id.spinnerGestationalAgeUnits);
        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);

        switch (spin.getSelectedItemPosition()) {
            case GA_UNIT_INDEX_NONE:
                currentReading.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_NONE;
                break;
            case GA_UNIT_INDEX_WEEKS:
                currentReading.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS;
                break;
            case GA_UNIT_INDEX_MOTHS:
                currentReading.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_MONTHS;
                break;
            default:
                Util.ensure(false);
        }

        // save value
        currentReading.patient.gestationalAgeValue = etValue.getText().toString();
    }

    private void updateGA_onSpinnerChange(View v) {
        Spinner spin = v.findViewById(R.id.spinnerGestationalAgeUnits);
        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);

        String value = etValue.getText().toString();
        int valueInputType = InputType.TYPE_CLASS_NUMBER;
        boolean valueEnabled = true;
        String notApplicableString = v.getContext().getString(R.string.reading_not_applicable);

        switch (spin.getSelectedItemPosition()) {
            case GA_UNIT_INDEX_NONE:
                value = notApplicableString;
                valueEnabled = false;
                break;
            case GA_UNIT_INDEX_WEEKS:
                valueInputType = InputType.TYPE_CLASS_NUMBER;
                break;
            case GA_UNIT_INDEX_MOTHS:
                valueInputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
                break;
            default:
                Util.ensure(false);
        }
        if (spin.getSelectedItemPosition() != GA_UNIT_INDEX_NONE
                && etValue.getText().toString().equals(notApplicableString)) {
            value = "";
        }

        // Set UI state
        etValue.setEnabled(valueEnabled);
        etValue.setInputType(valueInputType);
        etValue.setText(value);
    }

    private void setupSexSpinner(View v) {
        Spinner spin = v.findViewById(R.id.spinnerPatientSex);

        // set options
        Resources res = getResources();
        String[] optionsArray = res.getStringArray(R.array.sex);
        List<String> options = Arrays.asList(optionsArray);
        ArrayAdapter<String> dataAdapter =
                new ArrayAdapter<>(v.getContext(), android.R.layout.simple_spinner_item, options);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(dataAdapter);

        spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                // updateGA_onSpinnerChange(mView);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }

    private void updateSex_ModelFromUI(View v) {
        Spinner spin = v.findViewById(R.id.spinnerPatientSex);
        Switch isPregnant = v.findViewById(R.id.pregnantSwitch);
        switch (spin.getSelectedItemPosition()) {
            case PATIENT_SEX_MALE:
                currentReading.patient.patientSex = Patient.PATIENTSEX.MALE;
                isPregnant.setChecked(false);
                currentReading.patient.isPregnant = false;
                break;
            case PATIENT_SEX_FEMALE:
                currentReading.patient.patientSex = Patient.PATIENTSEX.FEMALE;
                currentReading.patient.isPregnant = isPregnant.isChecked();
                break;
            case PATIENT_SEX_INTERSEX:
                currentReading.patient.patientSex = Patient.PATIENTSEX.OTHERS;
                currentReading.patient.isPregnant = isPregnant.isChecked();
                break;
            default:
                Util.ensure(false);
        }

    }


}
