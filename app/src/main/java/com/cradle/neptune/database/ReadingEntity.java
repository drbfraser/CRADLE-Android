package com.cradle.neptune.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity
public class ReadingEntity implements Serializable {

    @NonNull
    @PrimaryKey
    private String readingId;

    @ColumnInfo
    private String patientId;

    @ColumnInfo
    private String readDataJsonString;

    @ColumnInfo
    private boolean isUploadedToServer;


    public String getReadingId() {
        return readingId;
    }

    public ReadingEntity(String readingId, String patientId, String readDataJsonString, boolean isUploadedToServer){
        this.readingId = readingId;
        this.readDataJsonString = readDataJsonString;
        this.patientId = patientId;
        this.isUploadedToServer = isUploadedToServer;
    }

    public void setReadingId(String readingId) {
        this.readingId = readingId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getReadDataJsonString() {
        return readDataJsonString;
    }

    public void setReadDataJsonString(String readDataJsonString) {
        this.readDataJsonString = readDataJsonString;
    }

    public boolean isUploadedToServer() {
        return isUploadedToServer;
    }

    public void setUploadedToServer(boolean uploadedToServer) {
        isUploadedToServer = uploadedToServer;
    }
}
