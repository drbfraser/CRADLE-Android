//package com.cradle.neptune.model;
//
//import android.content.Context;
//import android.content.SharedPreferences;
//import android.os.Build;
//import android.util.Log;
//
//import com.cradle.neptune.BuildConfig;
//import com.cradle.neptune.utilitiles.Util;
//
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//import org.threeten.bp.ZonedDateTime;
//import org.threeten.bp.temporal.ChronoUnit;
//
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.List;
//import java.util.UUID;
//
//import static com.cradle.neptune.view.LoginActivity.AUTH_PREF;
//import static com.cradle.neptune.view.LoginActivity.USER_ID;
//
///**
// * Basic data about a currentReading.
// * <p>
// * For format and processing ideas, see: https://www.hl7.org/fhir/overview-dev.html
// */
//public class Reading {
//
//    public static final int MANUAL_USER_ENTRY_SYSTOLIC = 1;
//    public static final int MANUAL_USER_ENTRY_DIASTOLIC = 2;
//    public static final int MANUAL_USER_ENTRY_HEARTRATE = 4;
//    /**
//     * Constants
//     */
//    private static final int DAYS_PER_MONTH = 30;
//    private static final int DAYS_PER_WEEK = 7;
//    /**
//     * Stored Values
//     */
//    // offline db
//    public String readingId;
//    public ZonedDateTime dateLastSaved;
//    //todo later change the reading Id to be same for offline and online
//    // patient info
////    public String patientId;
////    public String patientName;
////    public Integer ageYears;
////    public List<String> symptoms = new ArrayList<>();
////    public GestationalAgeUnit gestationalAgeUnit;
////    public String gestationalAgeValue;
//    public Patient patient = new Patient();
//    // reading
//    public String pathToPhoto;
//    public boolean isImageUploaded = false;
//    public Integer bpSystolic;  // first number (top)
//    public Integer bpDiastolic; // second number (bottom)
//    public Integer heartRateBPM;
//    public ZonedDateTime dateTimeTaken;
//    public String gpsLocationOfReading;
//    public ZonedDateTime dateUploadedToServer;
//    // retest & follow-up
//    public List<String> retestOfPreviousReadingIds;   // oldest first
//    public ZonedDateTime dateRecheckVitalsNeeded;
//    // assessment
//    public ReadingFollowUp readingFollowUp = null;
//    public List<String> symptoms = new ArrayList<>();
//
//    // referrals
//    public ZonedDateTime referralMessageSendTime;
//    public String referralHealthCentre;
//    public String referralComment;
//    // app metrics
//    public String appVersion;
//    public String deviceInfo;
//    public float totalOcrSeconds;
//    transient public boolean userHasSelectedNoSymptoms;
//    public UrineTest urineTestResult;
//    private Boolean isFlaggedForFollowup;
//    private int manuallyChangeOcrResults; // constants above
//    // temporary values
//    transient private long temporaryFlags = 0;
//
//    /**
//     * Constructors & Factories
//     */
//    public Reading() {
//        // for JSON only
//
//
//    }
//
//    /**
//     * This functions puts Reading into a json object which we can use to send to the server
//     *
//     * @param reading reading to put into json object.
//     * @return a json string
//     */
//    public static String getJsonObj(Reading reading, String userId) throws JSONException {
//
//        JSONObject patientVal = reading.patient.marshal();
//        JSONObject readingVal = getJsonReadingObject(reading, userId);
//        JSONObject mainObj = new JSONObject();
//
//        mainObj.put("patient", patientVal);
//        mainObj.put("reading", readingVal);
//        Log.d("bugg", mainObj.toString());
//
//        return mainObj.toString();
//    }
//
//    public static JSONObject getJsonReadingObject(Reading reading, String userId) throws JSONException {
//        JSONObject readingVal = new JSONObject();
//        JSONObject urineTest = null;
//        if (reading.urineTestResult != null) {
//            urineTest = new JSONObject();
//            urineTest.put("urineTestBlood", reading.urineTestResult.getBlood());
//            urineTest.put("urineTestPro", reading.urineTestResult.getProtein());
//            urineTest.put("urineTestLeuc", reading.urineTestResult.getLeukocytes());
//            urineTest.put("urineTestGlu", reading.urineTestResult.getGlucose());
//            urineTest.put("urineTestNit", reading.urineTestResult.getNitrites());
//        }
//        //incase we are sending the referral before saving the reading
//        if (Util.stringNullOrEmpty(reading.readingId)){
//            reading.readingId = UUID.randomUUID().toString();
//        }
//        readingVal.put("readingId", reading.readingId);
//        long dateLastSavedLong = reading.dateLastSaved.toInstant().getEpochSecond();
//        readingVal.put("dateLastSaved", dateLastSavedLong);
//        long datetakenLong = reading.dateTimeTaken.toInstant().getEpochSecond();
//        readingVal.put("dateTimeTaken", datetakenLong);
//        readingVal.put("bpSystolic", reading.bpSystolic);
//        readingVal.put("urineTests", urineTest);
//        readingVal.put(USER_ID, userId);
//        readingVal.put("bpDiastolic", reading.bpDiastolic);
//        readingVal.put("heartRateBPM", reading.heartRateBPM);
//        long dateRechackVitalNeededLong = -1;
//        if (reading.dateRecheckVitalsNeeded !=null){
//            dateRechackVitalNeededLong = reading.dateRecheckVitalsNeeded.toInstant().getEpochSecond();
//        }
//        readingVal.put("dateRecheckVitalsNeeded", dateRechackVitalNeededLong);
//        readingVal.put("isFlaggedForFollowup", reading.isFlaggedForFollowup);
//        readingVal.put("symptoms", reading.getSymptomsString());
//        return readingVal;
//    }
//
//    public static Reading makeNewReading(ZonedDateTime now) {
//        // setup basic info
//        Reading r = new Reading();
//        r.dateTimeTaken = now;
//        r.appVersion = String.format("%s = %s", BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
//        r.deviceInfo = Build.MANUFACTURER + ", " + Build.MODEL;
//        return r;
//    }
//
//    public static Reading makeToConfirmReading(Reading source, ZonedDateTime now) {
//        // copy fields
//        Reading r = Reading.makeNewReading(now);
////        r.patientId = source.patientId;
////        r.patientName = source.patientName;
////        r.ageYears = source.ageYears;
////        r.symptoms = new ArrayList<>();
////        r.symptoms.addAll(source.symptoms);
////        r.gestationalAgeUnit = source.gestationalAgeUnit;
////        r.gestationalAgeValue = source.gestationalAgeValue;
//        r.patient = source.patient;
//        // don't require user to re-check the 'no symptoms' box
//        if (r.symptoms.isEmpty()) {
//            r.userHasSelectedNoSymptoms = true;
//        }
//
//        // record it's a retest
//        r.addIdToRetestOfPreviousReadings(source.retestOfPreviousReadingIds, source.readingId);
//        return r;
//    }
//
//    public static Reading makeNewExistingPatientReading(Reading source, ZonedDateTime now) {
//
//        Reading r = Reading.makeNewReading(now);
//        if (source!=null) {
//            r.patient = source.patient;
//        }
//        r.symptoms.clear();
//        // don't require user to re-check the 'no symptoms' box
//        if (r.symptoms.isEmpty()) {
//            r.userHasSelectedNoSymptoms = true;
//        }
//
//        return r;
//    }
//
//    @Override
//    public String toString() {
//        return "Reading{" +
//                "readingId=" + readingId +
//                ", dateLastSaved=" + dateLastSaved +
//                ", patient=" + patient +
//                ", pathToPhoto='" + pathToPhoto + '\'' +
//                ", bpSystolic=" + bpSystolic +
//                ", bpDiastolic=" + bpDiastolic +
//                ", heartRateBPM=" + heartRateBPM +
//                ", dateTimeTaken=" + dateTimeTaken +
//                ", gpsLocationOfReading='" + gpsLocationOfReading + '\'' +
//                ", dateUploadedToServer=" + dateUploadedToServer +
//                ", retestOfPreviousReadingIds=" + retestOfPreviousReadingIds +
//                ", dateRecheckVitalsNeeded=" + dateRecheckVitalsNeeded +
//                ", isFlaggedForFollowup=" + isFlaggedForFollowup +
//                ", referralMessageSendTime=" + referralMessageSendTime +
//                ", referralHealthCentre='" + referralHealthCentre + '\'' +
//                ", referralComment='" + referralComment + '\'' +
//                ", appVersion='" + appVersion + '\'' +
//                ", deviceInfo='" + deviceInfo + '\'' +
//                ", totalOcrSeconds=" + totalOcrSeconds +
//                ", manuallyChangeOcrResults=" + manuallyChangeOcrResults +
//                ", temporaryFlags=" + temporaryFlags +
//                ", userHasSelectedNoSymptoms=" + userHasSelectedNoSymptoms +
//                '}';
//    }
//
////    public String getReferralString() {
////         String message = "New Referral for " + referralHealthCentre + ":\n" +
////                 "\nPatient ID = " + patient.patientId +
////                 "\nPatient Name = " + patient.patientName +
////                 "\nPatient Sex = " + patient.patientSex +
////                 "\nPatient Age = " + patient.ageYears;
////
////         if(patient.isPregnant)
////         {
////             message += "Patient Pregnancy Information:\n" +
////                     "\nGestational Age = " + patient.gestationalAgeValue + " " + patient.gestationalAgeUnit;
////         }
////
////         DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss z");
////         message += "\nDate = " + dateTimeTaken.format(formatter) +
////                 "\n\nBlood Pressure Systolic = " + bpSystolic +
////                 "\nBlood Pressure Diastolic = " + bpDiastolic +
////                 "\nHeart Rate BPM = " + heartRateBPM +
////                 "\nSymptoms = " + getSymptomsString();
////
////         return message;
////    }
//
//
//
//    /**
//     * Accessors
//     */
//    public WeeksAndDays getGestationalAgeInWeeksAndDays() {
//        // Handle not set:
//        if (patient.gestationalAgeUnit == null || patient.gestationalAgeValue == null
//                || patient.gestationalAgeValue.trim().length() == 0) {
//            return null;
//        }
//
//        switch (patient.gestationalAgeUnit) {
//            case GESTATIONAL_AGE_UNITS_MONTHS:
//                int months = Util.stringToIntOr0(patient.gestationalAgeValue);
//                int days = DAYS_PER_MONTH * months;
//                return new WeeksAndDays(
//                        days / DAYS_PER_WEEK,
//                        days % DAYS_PER_WEEK);
//            case GESTATIONAL_AGE_UNITS_WEEKS:
//                int weeks = Util.stringToIntOr0(patient.gestationalAgeValue);
//                return new WeeksAndDays(weeks, 0);
//            case GESTATIONAL_AGE_UNITS_NONE:
//                return null;
//            default:
//                Util.ensure(false);
//                return null;
//        }
//    }
//
//    // referred
//    public boolean isReferredToHealthCentre() {
//        return referralMessageSendTime != null;
//    }
//
//    public void setReferredToHealthCentre(String healthCentre, ZonedDateTime time) {
//        referralHealthCentre = healthCentre;
//        referralMessageSendTime = time;
//    }
//
//    // follow-up
//    public boolean isFlaggedForFollowup() {
//        return Util.isTrue(isFlaggedForFollowup);
//    }
//
//    public void setFlaggedForFollowup(Boolean flaggedForFollowup) {
//        isFlaggedForFollowup = flaggedForFollowup;
//    }
//
//    // upload
//    public boolean isUploaded() {
//        return dateUploadedToServer != null;
//    }
//
//    // recheck vitals
//    public boolean isRetestOfPreviousReading() {
//        return retestOfPreviousReadingIds != null && retestOfPreviousReadingIds.size() > 0;
//    }
//
//    public void addIdToRetestOfPreviousReadings(List<String> retestOfPreviousReadingIds, String readingId) {
//        if (this.retestOfPreviousReadingIds == null) {
//            this.retestOfPreviousReadingIds = new ArrayList<>();
//        }
//
//        // add history
//        if (retestOfPreviousReadingIds != null) {
//            this.retestOfPreviousReadingIds.addAll(retestOfPreviousReadingIds);
//        }
//
//        // dd most recent
//        this.retestOfPreviousReadingIds.add(readingId);
//    }
//
//    public boolean isNeedRecheckVitals() {
//        return dateRecheckVitalsNeeded != null;
//    }
//
//    public boolean isNeedRecheckVitalsNow() {
//        return isNeedRecheckVitals()
//                && dateRecheckVitalsNeeded.isBefore(ZonedDateTime.now());
//    }
//
//    public long getMinutesUntilNeedRecheckVitals() {
//        if (!isNeedRecheckVitals()) {
//            throw new UnsupportedOperationException("No number of minutes for no recheck");
//        }
//
//        if (isNeedRecheckVitalsNow()) {
//            return 0;
//        } else {
//            long seconds = ChronoUnit.SECONDS.between(ZonedDateTime.now(), dateRecheckVitalsNeeded);
//            return (seconds + 59) / 60;
//        }
//    }
//
//    // symptoms
//    public String getSymptomsString() {
//        String description = "";
//        for (String symptom : symptoms) {
//            // clean up
//            symptom = symptom.trim();
//            if (symptom.length() == 0) {
//                continue;
//            }
//
//            // append
//            if (description.length() != 0) {
//                description += ", ";
//            }
//            description += symptom;
//        }
//        return description;
//    }
//
//    // manual vitals edits
//    public void clearManualChangeOcrResultsFlags() {
//        manuallyChangeOcrResults = 0;
//    }
//
//    public void setAManualChangeOcrResultsFlags(int flagMask) {
//        manuallyChangeOcrResults |= flagMask;
//    }
//
//    public int getManualChangeOcrResults() {
//        return manuallyChangeOcrResults;
//    }
//
//    // check for valid data
//    // if hasInvalidData is true the dialog will be displayed
//    public boolean hasInvalidData() {
//        //todo add the age checker
//        if ((heartRateBPM < 30 || heartRateBPM > 300)
//                || (bpDiastolic < 10 || bpDiastolic > 300)
//                || (bpSystolic < 10 || bpSystolic > 300)) {
//            return true;
//        }
//
//        if (patient.isPregnant) {
//            return (patient.gestationalAgeUnit == GestationalAgeUnit.GESTATIONAL_AGE_UNITS_MONTHS
//                    && Integer.valueOf(patient.gestationalAgeValue) > 15)
//                    || (patient.gestationalAgeUnit == GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS
//                    && Integer.valueOf(patient.gestationalAgeValue) > 66);
//        }
//        return false;
//    }
//
//    // check for required data
//    public boolean isMissingRequiredData() {
//        boolean missing = false;
//        missing |= patient == null;
//        missing |= patient.patientId == null;
//        missing |= patient.patientName == null;
//        missing |= patient.gestationalAgeUnit == null;
//        missing |= (patient.gestationalAgeValue == null
//                && patient.gestationalAgeUnit != GestationalAgeUnit.GESTATIONAL_AGE_UNITS_NONE);
//        missing |= heartRateBPM == null;
//        missing |= bpDiastolic == null;
//        missing |= bpSystolic == null;
//        missing |= isMissingRequiredSymptoms();
//        return missing;
//    }
//
//    public boolean isMissingRequiredSymptoms() {
//        return symptoms.isEmpty() && !userHasSelectedNoSymptoms && dateLastSaved == null;
//    }
//
//    /**
//     * Temporary Flags
//     */
//    public void setATemporaryFlag(long flagMask) {
//        temporaryFlags |= flagMask;
//    }
//
//    public void clearATemporaryFlag(long flagMask) {
//        temporaryFlags &= ~flagMask;
//    }
//
//    public boolean isATemporaryFlagSet(long flagMask) {
//        return (temporaryFlags & flagMask) != 0;
//    }
//
//    public void clearAllTemporaryFlags() {
//        temporaryFlags = 0;
//    }
//
//    public Integer getBpDiastolic() {
//        return bpDiastolic;
//    }
//
//    public Integer getBpSystolic() {
//        return bpSystolic;
//    }
//
//    public Integer getHeartRateBPM() {
//        return heartRateBPM;
//    }
//
//    /**
//     * Types
//     */
//    public enum GestationalAgeUnit {
//        GESTATIONAL_AGE_UNITS_NONE,
//        GESTATIONAL_AGE_UNITS_WEEKS,
//        GESTATIONAL_AGE_UNITS_MONTHS,
////        GESTATIONAL_AGE_UNITS_LASTMENSTRUALPERIOD,
//    }
//
//    public static class ComparatorByDateReverse implements Comparator<Reading> {
//        @Override
//        public int compare(Reading r1, Reading r2) {
//            return r2.dateTimeTaken.compareTo(r1.dateTimeTaken);
//        }
//    }
//
//    public static class ComparatorByDate implements Comparator<Reading> {
//        @Override
//        public int compare(Reading r1, Reading r2) {
//            return r1.dateTimeTaken.compareTo(r2.dateTimeTaken);
//        }
//    }
//
//    public class WeeksAndDays {
//        public final int weeks;
//        public final int days;
//
//        public WeeksAndDays(int weeks, int days) {
//            this.weeks = weeks;
//            this.days = days;
//        }
//    }
//}
