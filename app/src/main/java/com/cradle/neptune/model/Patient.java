package com.cradle.neptune.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Patient implements Serializable {
    // TODO: 22/09/19 encapsulate these class members
    // patient info
    public String patientId;
    public String patientName;
    public String dob;
    public List<String> symptoms = new ArrayList<>();
    public Reading.GestationalAgeUnit gestationalAgeUnit;
    public String gestationalAgeValue;
    public PATIENTSEX patientSex;
    public boolean isPregnant;
    public String zone;
    public String villageNumber;
    public List<String> drugHistoryList;
    public List<String> medicalHistoryList;

    public Patient() {
    }

    public Patient(String mPatientId, String mPatientName, String dob, Reading.GestationalAgeUnit mGestationalAgeUnit,
                   String mGestationalAgeValue, String mVillageNumber, PATIENTSEX mSex,
                   String zone, boolean isPregnant) {
        patientId = mPatientId;
        patientName = mPatientName;
        this.dob = dob;
        gestationalAgeUnit = mGestationalAgeUnit;
        gestationalAgeValue = mGestationalAgeValue;
        villageNumber = mVillageNumber;
        patientSex = mSex;
        this.zone = zone;
        this.isPregnant = isPregnant;
        drugHistoryList = new ArrayList<>();
        medicalHistoryList = new ArrayList<>();

    }

    public String genSymptomString() {
        String symptomsString = "None";
        if (symptoms.size() > 0) {
            symptomsString = symptoms.get(0);
            if (symptoms.size() > 1) {
                for (int i = 1; i < symptoms.size(); i++) {
                    symptomsString += ",";
                    symptomsString += symptoms.get(i);
                }
            }
        }
        return symptomsString;
    }

    public JSONObject getPatientInfoJSon() {
        try {
            JSONObject patientInfoObject = new JSONObject();
            patientInfoObject.put("patientId", patientId);
            patientInfoObject.put("patientName", patientName);
            patientInfoObject.put("dob", dob);
            patientInfoObject.put("gestationalAgeUnit", gestationalAgeUnit.toString());
            patientInfoObject.put("gestationalAgeValue", gestationalAgeValue);
            patientInfoObject.put("villageNumber", villageNumber);
            patientInfoObject.put("patientSex", patientSex.toString());
            patientInfoObject.put("patientAge", -1);
            String isPregnantString = "false";
            if (isPregnant) {
                isPregnantString = "true";
            }
            patientInfoObject.put("isPregnant", isPregnantString);
            return patientInfoObject;
//
//            String symptomsString = genSymptomString();
//            patientInfoObject.put("symptoms", symptomsString);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public enum PATIENTSEX {MALE, FEMALE, OTHERS}


}
