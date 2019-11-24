package com.cradle.neptune.model;

import android.support.annotation.NonNull;

public class ReadingFollowUp {

    private String readingServerId;
    private String followUpAction;
    private String treatment;
    private String diagnosis;
    private String healthcare;
    private String date;
    public ReadingFollowUp(String readingServerId, String followUpAction, String treatment, String diagnosis) {
        this.readingServerId = readingServerId;
        this.followUpAction = followUpAction;
        this.treatment = treatment;
        this.diagnosis = diagnosis;
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

    @Override
    public String toString() {
        return "ReadingFollowUp{" +
                "readingServerId='" + readingServerId + '\'' +
                ", followUpAction='" + followUpAction + '\'' +
                ", treatment='" + treatment + '\'' +
                ", diagnosis='" + diagnosis + '\'' +
                '}';
    }
}
