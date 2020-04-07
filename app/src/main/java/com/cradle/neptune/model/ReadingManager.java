package com.cradle.neptune.model;

import android.content.Context;

import com.cradle.neptune.database.HealthFacilityEntity;

import java.util.List;

public interface ReadingManager {
    void addNewReading(Context context, Reading reading);

    void updateReading(Context context, Reading reading);

    List<Reading> getReadings(Context context);

    Reading getReadingById(Context context, String id);

    void deleteReadingById(Context context, String readingID);

    void deleteAllData(Context context);

    List<Reading> getReadingByPatientID(Context context, String patientID);

    void addAllReadings(Context context, List<Reading> readings);

    List<Reading> getUnuploadedReadings();


    void insert(HealthFacilityEntity healthFacilityEntity);

    void removeFacilityById(String id);

    void insertAll(List<HealthFacilityEntity> healthCareFacilityEntities);

    List<HealthFacilityEntity> getAllFacilities();

    HealthFacilityEntity getFacilityById(String id);

    List<HealthFacilityEntity> getUserSelectedFacilities();

    void updateFacility(HealthFacilityEntity healthFacilityEntity);
}