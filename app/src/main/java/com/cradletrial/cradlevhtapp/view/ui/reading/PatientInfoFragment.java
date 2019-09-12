package com.cradletrial.cradlevhtapp.view.ui.reading;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.model.Settings;
import com.cradletrial.cradlevhtapp.utilitiles.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

/**
 *  Gather information about the patient.
 */
public class PatientInfoFragment extends BaseFragment {
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
        et.setText(currentReading.patientId);

        // initials
        et = mView.findViewById(R.id.etPatientName);
        et.setText(currentReading.patientName);

        // age
        et = mView.findViewById(R.id.etPatientAge);
        if (currentReading.ageYears != null) {
            et.setText(Integer.toString(currentReading.ageYears));
        } else {
            et.setText("");
        }
    }
    private void updateText_ModelFromUi(View mView) {
        EditText et;
        // id
        et = mView.findViewById(R.id.etPatientId);
        currentReading.patientId = et.getText().toString();

        // initials
        et = mView.findViewById(R.id.etPatientName);
        currentReading.patientName = et.getText().toString();

        // age
        et = mView.findViewById(R.id.etPatientAge);
        String ageStr = et.getText().toString().trim();
        if (ageStr.length() > 0) {
            currentReading.ageYears = Util.stringToIntOr0(ageStr);
        }
    }


    /*
        Gestational Age
     */
    private static final int GA_UNIT_INDEX_WEEKS = 0;
    private static final int GA_UNIT_INDEX_MOTHS = 1;
    private static final int GA_UNIT_INDEX_LMP   = -9;    // TODO: not yet implemented
    private static final int GA_UNIT_INDEX_NONE  = 2;
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

    private void updateGA_UiFromModel(View v) {
        Spinner spin = v.findViewById(R.id.spinnerGestationalAgeUnits);
        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);

        int selection = 0;

        if (currentReading.gestationalAgeUnit != null) {
            switch (currentReading.gestationalAgeUnit) {
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
        etValue.setText(currentReading.gestationalAgeValue);

        updateGA_onSpinnerChange(v);
    }
    private void updateGA_ModelFromUi(View v) {
        Spinner spin = v.findViewById(R.id.spinnerGestationalAgeUnits);
        EditText etValue = v.findViewById(R.id.etGestationalAgeValue);

        switch (spin.getSelectedItemPosition()) {
            case GA_UNIT_INDEX_NONE:
                currentReading.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_NONE;
                break;
            case GA_UNIT_INDEX_WEEKS:
                currentReading.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS;
                break;
            case GA_UNIT_INDEX_MOTHS:
                currentReading.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_MONTHS;
                break;
            default:
                Util.ensure(false);
        }

        // save value
        currentReading.gestationalAgeValue = etValue.getText().toString();
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

}
