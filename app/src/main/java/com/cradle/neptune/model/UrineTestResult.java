package com.cradle.neptune.model;

import java.io.Serializable;

public class UrineTestResult implements Serializable {
    private String leukocytes = "";
    private String nitrites = "";
    private String protein = "";
    private String blood = "";
    private String glucose = "";

    public UrineTestResult() {
    }

    public UrineTestResult(String leukocytes, String nitrites,
                           String protein, String blood,
                           String glucose) {
        this.leukocytes = leukocytes;
        this.nitrites = nitrites;
        this.protein = protein;
        this.blood = blood;
        this.glucose = glucose;
    }

    public String getLeukocytes() {
        return leukocytes;
    }

    public void setLeukocytes(String leukocytes) {
        this.leukocytes = leukocytes;
    }

    public String getNitrites() {
        return nitrites;
    }

    public void setNitrites(String nitrites) {
        this.nitrites = nitrites;
    }

    public String getProtein() {
        return protein;
    }

    public void setProtein(String protein) {
        this.protein = protein;
    }

    public String getBlood() {
        return blood;
    }

    public void setBlood(String blood) {
        this.blood = blood;
    }

    public String getGlucose() {
        return glucose;
    }

    public void setGlucose(String glucose) {
        this.glucose = glucose;
    }

    @Override
    public String toString() {
        return "UrineTestResult{" +
                "leukocytes='" + leukocytes + '\'' +
                ", nitrites='" + nitrites + '\'' +
                ", protein='" + protein + '\'' +
                ", blood='" + blood + '\'' +
                ", glucose='" + glucose + '\'' +
                '}';
    }
}
