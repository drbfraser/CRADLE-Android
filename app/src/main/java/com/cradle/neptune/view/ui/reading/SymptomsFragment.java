package com.cradle.neptune.view.ui.reading;

import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cradle.neptune.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Gather information about the patient.
 */
public class SymptomsFragment extends BaseFragment {
    private static final int CHECKBOX_NO_SYMPTOMS_INDEX = 0;


    private View mView;
    private List<CheckBox> checkBoxes = new ArrayList<>();
    private CheckBox noSymptomsCheckBox;
    private EditText otherSymptoms;

    public SymptomsFragment() {
        // Required empty public constructor
        TAG = SymptomsFragment.class.getName();
    }

    public static SymptomsFragment newInstance() {
        return new SymptomsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_symptoms, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mView = view;

        setupSymptoms(view);
        setupUrineResult(view);
    }

    private void setupUrineResult(View view) {

        Switch urineresultSwitch = view.findViewById(R.id.urineResultSwitch);
        Spinner urineResultSpinner= view.findViewById(R.id.urineTestResultSpinner);

        Resources res = getResources();
        String[] urineResults = res.getStringArray(R.array.urine_test_result);
        urineResultSpinner.setEnabled(false);


        urineresultSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (urineresultSwitch.isChecked()){
                    urineResultSpinner.setEnabled(true);
                } else {
                    urineResultSpinner.setEnabled(false);
                    currentReading.urineTestResult= "";
                }
            }
        });
        urineResultSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

                currentReading.urineTestResult = urineResults[i];
                Log.d("bugg","urine: "+ urineResults[i]);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                currentReading.urineTestResult = "";
                Log.d("bugg","nothing: "+ currentReading.urineTestResult);
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

        updateSymptoms_UiFromModel(mView);
    }


    @Override
    public boolean onMyBeingHidden() {
        // may not have created view yet.
        if (mView == null) {
            return true;
        }
        updateSymptoms_ModelFromUi(mView);
        return true;
    }


    /*
        Symptoms
     */
    private void setupSymptoms(View v) {
        Resources res = getResources();
        String[] symptomsFromRes = res.getStringArray(R.array.reading_symptoms);

        // populate symptoms
        checkBoxes.clear();
        LinearLayout layout = v.findViewById(R.id.linearSymptoms);
        int index = 0;
        for (String symptom : symptomsFromRes) {
            CheckBox cb = new CheckBox(getContext());
            cb.setText(symptom);
            if (index == 0) {
                cb.setChecked(true);
            }

            final int indexCopy = index;
            cb.setOnClickListener(view -> onSymptomCheckboxClicked(indexCopy));

            layout.addView(cb);
            checkBoxes.add(cb);
            if (index == CHECKBOX_NO_SYMPTOMS_INDEX) {
                noSymptomsCheckBox = cb;
            }
            index++;
        }

        // change watcher on other symptoms:
        otherSymptoms = v.findViewById(R.id.etOtherSymptoms);
        otherSymptoms.clearFocus();
        otherSymptoms.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                onOtherSymptomEdited(v, charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        updateSymptoms_UiFromModel(v);
    }

    private void onSymptomCheckboxClicked(int index) {
        // did we just click "no symptoms"
        if (index == CHECKBOX_NO_SYMPTOMS_INDEX) {
            // clicked "no symptoms" --> uncheck all others
            for (CheckBox cb : checkBoxes) {
                if (cb != noSymptomsCheckBox) {
                    cb.setChecked(false);
                }
            }

            otherSymptoms.clearFocus();
            otherSymptoms.setText("");

            currentReading.userHasSelectedNoSymptoms = true;
        } else {
            // 'real' symptom clicked; turn off "no symptoms"
            noSymptomsCheckBox.setChecked(false);
        }
    }

    private void onOtherSymptomEdited(View v, CharSequence charSequence) {
        if (charSequence.length() > 0) {
            // 'real' symptom clicked; turn off "no symptoms"
            noSymptomsCheckBox.setChecked(false);
        }
    }


    private void updateSymptoms_UiFromModel(View v) {
        // add checks for patient symptoms
        Resources res = getResources();
        String[] symptomsFromRes = res.getStringArray(R.array.reading_symptoms);

        String otherSymptomsStr = "";
        // TODO: [IMPORTANT] App crashes here if you navigate away and come back: currentReading could be null
        if (currentReading.patient.symptoms.size() == 0) {
            // no symptoms
            if (currentReading.dateLastSaved != null || currentReading.userHasSelectedNoSymptoms) {
                noSymptomsCheckBox.setChecked(true);
            }
        } else {
            // some symptoms
            for (String patientSymptom : currentReading.patient.symptoms) {
                // find the symptom and check UI box
                boolean found = false;
                for (int i = 0; i < symptomsFromRes.length; i++) {
                    if (symptomsFromRes[i].equals(patientSymptom)) {
                        checkBoxes.get(i).setChecked(true);
                        found = true;
                        break;
                    }
                }

                // add it to "other symptoms" if not found
                if (!found) {
                    otherSymptomsStr = otherSymptomsStr.trim();
                    if (otherSymptomsStr.length() > 0) {
                        otherSymptomsStr += ", ";
                    }
                    otherSymptomsStr += patientSymptom;
                }
            }
        }
        // set other symptoms
        otherSymptoms.setText(otherSymptomsStr);
    }

    private void updateSymptoms_ModelFromUi(View v) {
        currentReading.patient.symptoms.clear();

        // checkboxes
        for (CheckBox cb : checkBoxes) {
            if (cb.isChecked()) {
                currentReading.patient.symptoms.add(cb.getText().toString());
            }
        }

        // other
        String otherSymptomsStr = otherSymptoms.getText().toString().trim();
        if (otherSymptomsStr.length() > 0) {
            currentReading.patient.symptoms.add(otherSymptomsStr);
        }
    }
}
