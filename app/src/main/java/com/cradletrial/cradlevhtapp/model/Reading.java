package com.cradletrial.cradlevhtapp.model;

import android.os.Build;

import com.cradletrial.cradlevhtapp.BuildConfig;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;
import com.cradletrial.cradlevhtapp.utilitiles.Util;

import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Basic data about a currentReading.
 *
 * For format and processing ideas, see: https://www.hl7.org/fhir/overview-dev.html
 */
public class Reading {

    /**
     * Constants
     */
    private static final int DAYS_PER_MONTH = 30;
    private static final int DAYS_PER_WEEK = 7;

    public static final int MANUAL_USER_ENTRY_SYSTOLIC = 1;
    public static final int MANUAL_USER_ENTRY_DIASTOLIC = 2;
    public static final int MANUAL_USER_ENTRY_HEARTRATE = 4;



    /**
     * Types
     */
    public enum GestationalAgeUnit {
        GESTATIONAL_AGE_UNITS_NONE,
        GESTATIONAL_AGE_UNITS_WEEKS,
        GESTATIONAL_AGE_UNITS_MONTHS,
//        GESTATIONAL_AGE_UNITS_LASTMENSTRUALPERIOD,
    }
    public class WeeksAndDays {
        public final int weeks;
        public final int days;

        public WeeksAndDays(int weeks, int days) {
            this.weeks = weeks;
            this.days = days;
        }
    }

    /**
     * Stored Values
     */
    // db
    public Long readingId;
    public ZonedDateTime dateLastSaved;

    // patient info
//    public String patientId;
//    public String patientName;
//    public Integer ageYears;
//    public List<String> symptoms = new ArrayList<>();
//    public GestationalAgeUnit gestationalAgeUnit;
//    public String gestationalAgeValue;
    public Patient patient = new Patient();


    // reading
    public String pathToPhoto;
    public Integer bpSystolic;  // first number (top)
    public Integer bpDiastolic; // second number (bottom)
    public Integer heartRateBPM;

    public ZonedDateTime dateTimeTaken;
    public String gpsLocationOfReading;
    public ZonedDateTime dateUploadedToServer;

    // retest & follow-up
    public List<Long> retestOfPreviousReadingIds;   // oldest first
    public ZonedDateTime dateRecheckVitalsNeeded;
    private Boolean isFlaggedForFollowup;

    // referrals
    public ZonedDateTime referralMessageSendTime;
    public String referralHealthCentre;
    public String referralComment;

    // app metrics
    public String appVersion;
    public String deviceInfo;
    public float totalOcrSeconds;
    private int manuallyChangeOcrResults; // constants above

    // temporary values
    transient private long temporaryFlags = 0;
    transient public boolean userHasSelectedNoSymptoms;


    /**
     * Constructors & Factories
     */
    public Reading() {
        // for JSON only


    }


    /**
     * This functions puts Reading into a json object which we can use to send to the server
     * @param reading reading to put into json object.
     * @return a json string
     */
    public static String getJsonObj(Reading reading){
        JSONObject patientVal = new JSONObject();
        Patient patient = reading.patient;
        try {

            patientVal.put("patientId", patient.patientId);
            patientVal.put("patientName", patient.patientName);
            patientVal.put("patientAge", patient.ageYears);
            patientVal.put("gestationalAgeUnit", patient.gestationalAgeUnit);
            patientVal.put("gestationalAgeValue", patient.gestationalAgeValue);
            patientVal.put("villageNumber", patient.villageNumber);
            patientVal.put("patientSex", patient.patientSex);
            patientVal.put("isPregnant", "false");
        } catch (JSONException e) {
            e.printStackTrace();
        }
         JSONObject readingVal = new JSONObject();
        try {

            readingVal.put("dateLastSaved", reading.dateLastSaved);
            readingVal.put("bpSystolic", reading.bpSystolic);
            readingVal.put("bpDiastolic", reading.bpDiastolic);
            readingVal.put("heartRateBPM", reading.heartRateBPM);
            readingVal.put("dateRecheckVitalsNeeded", reading.dateRecheckVitalsNeeded);
            readingVal.put("isFlaggedForFollowup", reading.isFlaggedForFollowup);
            readingVal.put("symptoms", reading.getSymptomsString());
        } catch (JSONException e) {
            e.printStackTrace();
        }


        JSONObject mainObj = new JSONObject();
        try {

            mainObj.put("patient", patientVal);
            mainObj.put("reading", readingVal);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return mainObj.toString();
    }

    @Override
    public String toString() {
        return "Reading{" +
                "readingId=" + readingId +
                ", dateLastSaved=" + dateLastSaved +
                ", patient=" + patient +
                ", pathToPhoto='" + pathToPhoto + '\'' +
                ", bpSystolic=" + bpSystolic +
                ", bpDiastolic=" + bpDiastolic +
                ", heartRateBPM=" + heartRateBPM +
                ", dateTimeTaken=" + dateTimeTaken +
                ", gpsLocationOfReading='" + gpsLocationOfReading + '\'' +
                ", dateUploadedToServer=" + dateUploadedToServer +
                ", retestOfPreviousReadingIds=" + retestOfPreviousReadingIds +
                ", dateRecheckVitalsNeeded=" + dateRecheckVitalsNeeded +
                ", isFlaggedForFollowup=" + isFlaggedForFollowup +
                ", referralMessageSendTime=" + referralMessageSendTime +
                ", referralHealthCentre='" + referralHealthCentre + '\'' +
                ", referralComment='" + referralComment + '\'' +
                ", appVersion='" + appVersion + '\'' +
                ", deviceInfo='" + deviceInfo + '\'' +
                ", totalOcrSeconds=" + totalOcrSeconds +
                ", manuallyChangeOcrResults=" + manuallyChangeOcrResults +
                ", temporaryFlags=" + temporaryFlags +
                ", userHasSelectedNoSymptoms=" + userHasSelectedNoSymptoms +
                '}';
    }

    public static Reading makeNewReading(ZonedDateTime now) {
        // setup basic info
        Reading r = new Reading();
        r.dateTimeTaken = now;
        r.appVersion = String.format("%s = %s", BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
        r.deviceInfo = Build.MANUFACTURER + ", " + Build.MODEL;
        return r;
    }

    public static Reading makeToConfirmReading(Reading source, ZonedDateTime now) {
        // copy fields
        Reading r = Reading.makeNewReading(now);
//        r.patientId = source.patientId;
//        r.patientName = source.patientName;
//        r.ageYears = source.ageYears;
//        r.symptoms = new ArrayList<>();
//        r.symptoms.addAll(source.symptoms);
//        r.gestationalAgeUnit = source.gestationalAgeUnit;
//        r.gestationalAgeValue = source.gestationalAgeValue;
        r.patient = source.patient;
        // don't require user to re-check the 'no symptoms' box
        if (r.patient.symptoms.isEmpty()) {
            r.userHasSelectedNoSymptoms = true;
        }

        // record it's a retest
        r.addIdToRetestOfPreviousReadings(source.retestOfPreviousReadingIds, source.readingId);
        return r;
    }

    /**
     * Accessors
     */
    public WeeksAndDays getGestationalAgeInWeeksAndDays() {
        // Handle not set:
        if (patient.gestationalAgeUnit == null || patient.gestationalAgeValue == null
            || patient.gestationalAgeValue.trim().length() == 0)
        {
            return null;
        }

        switch (patient.gestationalAgeUnit) {
            case GESTATIONAL_AGE_UNITS_MONTHS:
                int months = Util.stringToIntOr0(patient.gestationalAgeValue);
                int days = DAYS_PER_MONTH * months;
                return new WeeksAndDays(
                        days / DAYS_PER_WEEK,
                        days % DAYS_PER_WEEK);
            case GESTATIONAL_AGE_UNITS_WEEKS:
                int weeks = Util.stringToIntOr0(patient.gestationalAgeValue);
                return new WeeksAndDays(weeks,0);
            case GESTATIONAL_AGE_UNITS_NONE:
                return null;
            default:
                Util.ensure(false);
                return null;
        }
    }


    // referred
    public boolean isReferredToHealthCentre() {
        return referralMessageSendTime != null;
    }
    public void setReferredToHealthCentre(String healthCentre, ZonedDateTime time) {
        referralHealthCentre = healthCentre;
        referralMessageSendTime = time;
    }

    // follow-up
    public boolean isFlaggedForFollowup() {
        return Util.isTrue(isFlaggedForFollowup);
    }
    public void setFlaggedForFollowup(Boolean flaggedForFollowup) {
        isFlaggedForFollowup = flaggedForFollowup;
    }

    // upload
    public boolean isUploaded() {
        return dateUploadedToServer != null;
    }

    // recheck vitals
    public boolean isRetestOfPreviousReading() {
        return retestOfPreviousReadingIds != null && retestOfPreviousReadingIds.size() > 0;
    }
    public void addIdToRetestOfPreviousReadings(List<Long> retestOfPreviousReadingIds, Long readingId) {
        if (this.retestOfPreviousReadingIds == null) {
            this.retestOfPreviousReadingIds = new ArrayList<>();
        }

        // add history
        if (retestOfPreviousReadingIds != null) {
            this.retestOfPreviousReadingIds.addAll(retestOfPreviousReadingIds);
        }

        // dd most recent
        this.retestOfPreviousReadingIds.add(readingId);
    }
    public boolean isNeedRecheckVitals() {
        return dateRecheckVitalsNeeded != null;
    }
    public boolean isNeedRecheckVitalsNow() {
        return isNeedRecheckVitals()
                && dateRecheckVitalsNeeded.isBefore(ZonedDateTime.now());
    }
    public long getMinutesUntilNeedRecheckVitals() {
        if (!isNeedRecheckVitals()) {
            throw new UnsupportedOperationException("No number of minutes for no recheck");
        }

        if (isNeedRecheckVitalsNow()) {
            return 0;
        } else {
            long seconds = ChronoUnit.SECONDS.between(ZonedDateTime.now(), dateRecheckVitalsNeeded);
            return (seconds + 59) / 60;
        }
    }

    // symptoms
    public String getSymptomsString() {
        String description = "";
        for (String symptom : patient.symptoms) {
            // clean up
            symptom = symptom.trim();
            if (symptom.length() == 0) {
                continue;
            }

            // append
            if (description.length() != 0) {
                description += ", ";
            }
            description += symptom;
        }
        return description;
    }

    // manual vitals edits
    public void clearManualChangeOcrResultsFlags() {
        manuallyChangeOcrResults = 0;
    }
    public void setAManualChangeOcrResultsFlags(int flagMask) {
        manuallyChangeOcrResults |= flagMask;
    }
    public int getManualChangeOcrResults() {
        return manuallyChangeOcrResults;
    }

    // check for required data
    public boolean isMissingRequiredData() {
        boolean missing = false;
        missing |= patient == null;
        missing |= patient.patientId == null;
        missing |= patient.patientName == null;
        missing |= patient.ageYears == null;
        missing |= patient.gestationalAgeUnit == null;
        missing |= (patient.gestationalAgeValue == null
                    && patient.gestationalAgeUnit != GestationalAgeUnit.GESTATIONAL_AGE_UNITS_NONE);
        missing |= heartRateBPM == null;
        missing |= bpDiastolic == null;
        missing |= bpSystolic == null;
        missing |= isMissingRequiredSymptoms();
        return missing;
    }

    public boolean isMissingRequiredSymptoms() {
        return patient.symptoms.isEmpty() && !userHasSelectedNoSymptoms && dateLastSaved == null;
    }

    public static class ComparatorByDateReverse implements Comparator <Reading>{
        @Override
        public int compare(Reading r1, Reading r2) {
            return r2.dateTimeTaken.compareTo(r1.dateTimeTaken);
        }
    }

    /**
     * Temporary Flags
     */
    public void setATemporaryFlag(long flagMask) {
        temporaryFlags |= flagMask;
    }
    public void clearATemporaryFlag(long flagMask) {
        temporaryFlags &= ~flagMask;
    }
    public boolean isATemporaryFlagSet(long flagMask) {
        return (temporaryFlags & flagMask) != 0;
    }
    public void clearAllTemporaryFlags() {
        temporaryFlags = 0;
    }
}
