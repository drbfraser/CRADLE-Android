package com.cradle.neptune.model;

public class ReadingFollowUp {

    private String readingServerId;
    private String followUpAction;
    private String treatment;
    private String diagnosis;
    private String healthcare;
    private String date;
    private String assessedBy;
    private String referredBy;
    private String patientMedInfoUpdate;
    private String patientDrugInfoUpdate;
    private String patientId;
    private boolean followUpNeeded;
    private String followupNeededTill;
    private String medicationPrescribed;
    private String followupFrequencyUnit ;
    private int followupFrequencyValue;


    public boolean isFollowUpNeeded() {
        return followUpNeeded;
    }

    public void setFollowUpNeeded(boolean followUpNeeded) {
        this.followUpNeeded = followUpNeeded;
    }

    public String getFollowupNeededTill() {
        return followupNeededTill;
    }

    public void setFollowupNeededTill(String followupNeededTill) {
        this.followupNeededTill = followupNeededTill;
    }

    public String getMedicationPrescribed() {
        return medicationPrescribed;
    }

    public void setMedicationPrescribed(String medicationPrescribed) {
        this.medicationPrescribed = medicationPrescribed;
    }

    public String getFollowupFrequencyUnit() {
        return followupFrequencyUnit;
    }

    public void setFollowupFrequencyUnit(String followupFrequencyUnit) {
        this.followupFrequencyUnit = followupFrequencyUnit;
    }

    public int getFollowupFrequencyValue() {
        return followupFrequencyValue;
    }

    public void setFollowupFrequencyValue(int followupFrequencyValue) {
        this.followupFrequencyValue = followupFrequencyValue;
    }

    public ReadingFollowUp(String readingServerId, String followUpAction, String treatment,
                           String diagnosis, String healthcare, String date, String assessedBy, String referredBy) {
        this.readingServerId = readingServerId;
        this.followUpAction = followUpAction;
        this.treatment = treatment;
        this.diagnosis = diagnosis;
        this.date = date;
        this.healthcare = healthcare;
        this.assessedBy = assessedBy;
        this.referredBy = referredBy;
        this.patientId = null;
        this.patientMedInfoUpdate = null;
        this.patientDrugInfoUpdate = null;
    }


    public String getReadingServerId() {
        return readingServerId;
    }

    public void setReadingServerId(String readingServerId) {
        this.readingServerId = readingServerId;
    }

    public String getFollowUpAction() {
        return followUpAction;
    }

    public void setFollowUpAction(String followUpAction) {
        this.followUpAction = followUpAction;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "ReadingFollowUp{" +
                "readingServerId='" + readingServerId + '\'' +
                ", followUpAction='" + followUpAction + '\'' +
                ", treatment='" + treatment + '\'' +
                ", diagnosis='" + diagnosis + '\'' +
                ", healthcare='" + healthcare + '\'' +
                ", date='" + date + '\'' +
                '}';
    }

    public String getAssessedBy() {
        return assessedBy;
    }

    public void setAssessedBy(String assessedBy) {
        this.assessedBy = assessedBy;
    }

    public String getReferredBy() {
        return referredBy;
    }

    public void setReferredBy(String referredBy) {
        this.referredBy = referredBy;
    }

    public String getHealthcare() {
        return healthcare;
    }

    public void setHealthcare(String healthcare) {
        this.healthcare = healthcare;
    }

    public String getPatientDrugInfoUpdate() {
        return patientDrugInfoUpdate;
    }

    public void setPatientDrugInfoUpdate(String patientDrugInfoUpdate) {
        this.patientDrugInfoUpdate = patientDrugInfoUpdate;
    }

    public String getPatientMedInfoUpdate() {
        return patientMedInfoUpdate;
    }

    public void setPatientMedInfoUpdate(String patientMedInfoUpdate) {
        this.patientMedInfoUpdate = patientMedInfoUpdate;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
}
