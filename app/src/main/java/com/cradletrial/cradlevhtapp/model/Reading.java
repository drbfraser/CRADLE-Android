package com.cradletrial.cradlevhtapp.model;

import android.os.Build;

import com.cradletrial.cradlevhtapp.BuildConfig;
import com.cradletrial.cradlevhtapp.utilitiles.Util;
import com.google.gson.Gson;

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
    public String patientId;
    public String patientName;
    public Integer ageYears;
    public List<String> symptoms = new ArrayList<>();
    public GestationalAgeUnit gestationalAgeUnit;
    public String gestationalAgeValue;

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
        r.patientId = source.patientId;
        r.patientName = source.patientName;
        r.ageYears = source.ageYears;
        r.symptoms = new ArrayList<>();
        r.symptoms.addAll(source.symptoms);
        r.gestationalAgeUnit = source.gestationalAgeUnit;
        r.gestationalAgeValue = source.gestationalAgeValue;

        // don't require user to re-check the 'no symptoms' box
        if (r.symptoms.isEmpty()) {
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
        if (gestationalAgeUnit == null || gestationalAgeValue == null
            || gestationalAgeValue.trim().length() == 0)
        {
            return null;
        }

        switch (gestationalAgeUnit) {
            case GESTATIONAL_AGE_UNITS_MONTHS:
                int months = Util.stringToIntOr0(gestationalAgeValue);
                int days = DAYS_PER_MONTH * months;
                return new WeeksAndDays(
                        days / DAYS_PER_WEEK,
                        days % DAYS_PER_WEEK);
            case GESTATIONAL_AGE_UNITS_WEEKS:
                int weeks = Util.stringToIntOr0(gestationalAgeValue);
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
        for (String symptom : symptoms) {
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
        missing |= patientId == null;
        missing |= patientName == null;
        missing |= ageYears == null;
        missing |= gestationalAgeUnit == null;
        missing |= (gestationalAgeValue == null
                    && gestationalAgeUnit != GestationalAgeUnit.GESTATIONAL_AGE_UNITS_NONE);
        missing |= heartRateBPM == null;
        missing |= bpDiastolic == null;
        missing |= bpSystolic == null;
        missing |= isMissingRequiredSymptoms();
        return missing;
    }

    public boolean isMissingRequiredSymptoms() {
        return symptoms.isEmpty() && !userHasSelectedNoSymptoms && dateLastSaved == null;
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
