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
    // age in case there is no DOB
    public Integer age;
    public Reading.GestationalAgeUnit gestationalAgeUnit;
    public String gestationalAgeValue;
    public PATIENTSEX patientSex;
    public boolean isPregnant;
    public boolean needAssessment;
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


    public JSONObject getPatientInfoJSon() {
        try {
            JSONObject patientInfoObject = new JSONObject();
            patientInfoObject.put("patientId", patientId);
            patientInfoObject.put("patientName", patientName);
            patientInfoObject.put("dob", dob);
            if (age==null){
                patientInfoObject.put("patientAge",JSONObject.NULL);
            }else {
                patientInfoObject.put("patientAge", age);
            }
            patientInfoObject.put("gestationalAgeUnit", gestationalAgeUnit.toString());
            patientInfoObject.put("gestationalAgeValue", gestationalAgeValue);
            patientInfoObject.put("villageNumber", villageNumber);
            patientInfoObject.put("patientSex", patientSex.toString());
            patientInfoObject.put("zone",zone);
            String isPregnantString = "false";
            if (isPregnant) {
                isPregnantString = "true";
            }
            patientInfoObject.put("isPregnant", isPregnantString);
            return patientInfoObject;

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public enum PATIENTSEX {MALE, FEMALE, OTHERS}

    @Override
    public String toString() {
        return "Patient{" +
                "patientId='" + patientId + '\'' +
                ", patientName='" + patientName + '\'' +
                ", dob='" + dob + '\'' +
                ", gestationalAgeUnit=" + gestationalAgeUnit +
                ", gestationalAgeValue='" + gestationalAgeValue + '\'' +
                ", patientSex=" + patientSex +
                ", isPregnant=" + isPregnant +
                ", zone='" + zone + '\'' +
                ", villageNumber='" + villageNumber + '\'' +
                ", drugHistoryList=" + drugHistoryList +
                ", medicalHistoryList=" + medicalHistoryList +
                '}';
    }
}
