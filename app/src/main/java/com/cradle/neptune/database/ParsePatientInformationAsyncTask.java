package com.cradle.neptune.database;

import android.content.Context;
import android.os.AsyncTask;

import androidx.core.app.NotificationManagerCompat;

import com.cradle.neptune.R;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.model.UrineTestResult;
import com.cradle.neptune.utilitiles.DateUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.ZonedDateTime;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static com.cradle.neptune.utilitiles.NotificationUtils.PatientDownloadingNotificationID;
import static com.cradle.neptune.utilitiles.NotificationUtils.buildNotification;

/**
 * this class takes the response received from the api /paitient/allinfo
 * and parse it and saves it on the database.
 */
public class ParsePatientInformationAsyncTask extends AsyncTask<Void, Void, Void> {

    private WeakReference<Context> context;
    private ReadingManager readingManager;
    private JSONArray response;

    public ParsePatientInformationAsyncTask(JSONArray response, Context context, ReadingManager readingManager) {
        this.context = new WeakReference<>(context);
        this.response = response;
        this.readingManager = readingManager;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            List<Reading> readings = new ArrayList<>();
            for (int i = 0; i < response.length(); i++) {
                //get the main json object
                JSONObject jsonObject = response.getJSONObject(i);
                //build patient
                Patient patient = new Patient();
                patient.dob = jsonObject.getString("dob");
                patient.patientName = jsonObject.getString("patientName");
                patient.zone = jsonObject.getString("zone");
                patient.gestationalAgeUnit = Reading.GestationalAgeUnit.valueOf((String) jsonObject.get("gestationalAgeUnit"));
                patient.gestationalAgeValue = jsonObject.getString("gestationalAgeValue");
                patient.patientId = jsonObject.getString("patientId");
                patient.villageNumber = jsonObject.getString("villageNumber");
                patient.patientSex = Patient.PATIENTSEX.valueOf((String) jsonObject.get("patientSex"));
                patient.age = jsonObject.optInt("patientAge", -1);
                patient.isPregnant = jsonObject.optBoolean("isPregnant", false);
                patient.needAssessment = jsonObject.optBoolean("needsAssessment", false);

                patient.drugHistoryList = new ArrayList<>();
                if (!jsonObject.getString("drugHistory").toLowerCase().equals("null")) {
                    patient.drugHistoryList.add(jsonObject.getString("drugHistory"));
                }
                patient.medicalHistoryList = new ArrayList<>();
                if (!jsonObject.getString("medicalHistory").toLowerCase().equals("null")) {
                    patient.medicalHistoryList.add(jsonObject.getString("medicalHistory"));
                }
                JSONArray readingArray = jsonObject.getJSONArray("readings");
                if (readingArray.length() <= 0) {
                    //should never be the case but just for safety
                    //there shouldnt be a case where there is a patient without reeading
                    return null;
                }
                //get all the readings
                for (int j = 0; j < readingArray.length(); j++) {
                    JSONObject readingJson = readingArray.getJSONObject(j);
                    Reading reading = getReadingFromJSONObject(patient, readingJson);
                    //adding the reading to db
                    readings.add(reading);
                }
            }
            if (context.get() != null) {
                readingManager.addAllReadings(context.get(), readings);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (context != null && context.get() != null) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context.get());
            notificationManager.cancel(PatientDownloadingNotificationID);
            buildNotification(context.get().getString(R.string.app_name),
                    "Patients profiles successfully downloaded", PatientDownloadingNotificationID, context.get());
        }

    }

    private Reading getReadingFromJSONObject(Patient patient, JSONObject readingJson) throws JSONException {
        Reading reading = new Reading();
        reading.bpDiastolic = readingJson.optInt("bpDiastolic", -1);
        reading.bpSystolic = readingJson.optInt("bpSystolic", -1);
        reading.userHasSelectedNoSymptoms = readingJson.optBoolean("userHasSelectedNoSymptoms", false);
        reading.heartRateBPM = readingJson.optInt("heartRateBPM", -1);
        reading.readingId = readingJson.getString("readingId");
        //
        UrineTestResult urineTestResult = null;
        if (readingJson.has("urineTests") && !readingJson.get("urineTests").toString().toLowerCase().equals("null")) {
            JSONObject urineTest = readingJson.getJSONObject("urineTests");
            //checking for nulls, if one is null, all should be
            if (!urineTest.getString("urineTestPro").toLowerCase().equals("null")) {
                urineTestResult = new UrineTestResult();
                urineTestResult.setProtein(urineTest.getString("urineTestPro"));
                urineTestResult.setBlood(urineTest.getString("urineTestBlood"));
                urineTestResult.setLeukocytes(urineTest.getString("urineTestLeuc"));
                urineTestResult.setGlucose(urineTest.getString("urineTestGlu"));
                urineTestResult.setNitrites(urineTest.getString("urineTestNit"));
            }
        }
        reading.urineTestResult = urineTestResult;
        //reading.retestOfPreviousReadingIds= (List<String>) readingJson.get("retestOfPreviousReadingIds");

        reading.dateUploadedToServer = DateUtil.getZoneTimeFromString(readingJson.optString("dateUploadedToServer", ZonedDateTime.now().toString()));
        reading.dateTimeTaken = DateUtil.getZoneTimeFromString(readingJson.getString("dateTimeTaken"));
        reading.dateRecheckVitalsNeeded = DateUtil.getZoneTimeFromString(readingJson.getString("dateRecheckVitalsNeeded"));
        if (reading.dateUploadedToServer == null) {
            // cannot be null since we check this in order to upload reading to server
            reading.dateUploadedToServer = ZonedDateTime.now();
        }
        if (!readingJson.optString("dateLastSaved").equals("null")) {
            reading.dateLastSaved = ZonedDateTime.parse(readingJson.optString("dateLastSaved", ZonedDateTime.now().toString()));
        } else {
            reading.dateLastSaved = reading.dateUploadedToServer;
        }
        reading.setAManualChangeOcrResultsFlags(readingJson.optInt("manuallyChangeOcrResults", -1));
        reading.totalOcrSeconds = readingJson.optInt("totalOcrSeconds", -1);
        reading.referralComment = readingJson.optString("referral");
        reading.symptoms.add(0, readingJson.getString("symptoms"));
        reading.setFlaggedForFollowup(readingJson.optBoolean("isFlaggedForFollowup", false));
        reading.readingId = readingJson.getString("readingId");
        // because we decided to put patient inside reading --___--
        reading.patient = patient;
        return reading;
    }
}
