package com.cradle.neptune.database;

import android.content.Context;
import android.os.AsyncTask;

import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.utilitiles.GsonUtil;
import com.cradle.neptune.utilitiles.Util;

import org.threeten.bp.ZonedDateTime;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RoomDatabaseManager implements ReadingManager, HealthFacilityManager {
    private CradleDatabase cradleDatabase;

    public RoomDatabaseManager(CradleDatabase cradleDatabase) {
        this.cradleDatabase = cradleDatabase;
    }

    @Override
    public void addNewReading(Context context, Reading reading) {
        // add the new reading into the DB.
        reading.dateLastSaved = ZonedDateTime.now();
        if (reading.readingId == null || reading.readingId.equals("") ||
                reading.readingId.toLowerCase().equals("null")) {
            reading.readingId = UUID.randomUUID().toString();
        }

        ReadingEntity readingEntity = new ReadingEntity(reading.readingId,
                reading.patient.patientId, GsonUtil.getJson(reading), reading.isUploaded());
        cradleDatabase.readingDaoAccess().insertReading(readingEntity);

        // update all the other patient's records .. because thats the way it is...
        List<Reading> readings = getReadings(context);
        for (Reading r : readings) {
            if (r.patient.patientId.equals(reading.patient.patientId) && r.readingId != reading.readingId) {
                if (r.isNeedRecheckVitals()) {
                    r.dateRecheckVitalsNeeded = null;
                    updateReading(context, r);
                }
            }
        }

    }

    @Override
    public void updateReading(Context context, Reading reading) {
        reading.dateLastSaved = ZonedDateTime.now();
        ReadingEntity readingEntity = new ReadingEntity(reading.readingId,
                reading.patient.patientId, GsonUtil.getJson(reading), reading.isUploaded());
        cradleDatabase.readingDaoAccess().update(readingEntity);
    }

    @Override
    public List<Reading> getReadings(Context context) {
        return new GetAllReadingsAsyncTask(cradleDatabase, false, "").doInBackground();
    }

    @Override
    public Reading getReadingById(Context context, String id) {
        ReadingEntity readingEntity = cradleDatabase.readingDaoAccess().getReadingById(id);
        if (readingEntity == null) {
            return null;
        }
        Reading r = GsonUtil.makeObjectFromJson(readingEntity.getReadDataJsonString(), Reading.class);
        String patientId = r.patient.patientId;
        Util.ensure(readingEntity.getPatientId() == patientId ||
                patientId.equals(r.patient.patientId));
        return r;

    }

    @Override
    public void deleteReadingById(Context context, String readingID) {
        ReadingEntity readingEntity = cradleDatabase.readingDaoAccess().getReadingById(readingID);
        if (readingEntity != null) {
            cradleDatabase.readingDaoAccess().delete(readingEntity);
        }

    }

    @Override
    public void deleteAllData(Context context) {
        cradleDatabase.readingDaoAccess().deleteAllReading();
    }

    @Override
    public List<Reading> getReadingByPatientID(Context context, String patientId) {
        return new GetAllReadingsAsyncTask(cradleDatabase, true, patientId).doInBackground();
    }

    @Override
    public void addAllReadings(Context context, List<Reading> readings) {
        List<ReadingEntity> readingEntities = new ArrayList<>();
        for (Reading reading : readings) {
            ReadingEntity readingEntity = new ReadingEntity(reading.readingId,
                    reading.patient.patientId, GsonUtil.getJson(reading), reading.isUploaded());
            readingEntities.add(readingEntity);
        }
        cradleDatabase.readingDaoAccess().insertAll(readingEntities);
    }

    @Override
    public List<Reading> getUnuploadedReadings() {
        List<ReadingEntity> readingEntities = cradleDatabase.readingDaoAccess().getAllUnUploadedReading();
        List<Reading> readings = new ArrayList<>();
        for (ReadingEntity readingEntity : readingEntities) {
            Reading r = GsonUtil.makeObjectFromJson(readingEntity.getReadDataJsonString(), Reading.class);
            readings.add(r);
        }
        return readings;
    }

    /**
     * since we dont want to block the main UI thread, we have to create a seperate thread for large queries.
     */
    private class GetAllReadingsAsyncTask extends AsyncTask<Void, Void, List<Reading>> {
        WeakReference<CradleDatabase> readingEntitiesDatabaseWeakReference;
        private boolean readingByPatientId = false;
        private String patientId = "";

        GetAllReadingsAsyncTask(CradleDatabase cradleDatabase, boolean readingByPatientId, String patientId) {
            this.readingEntitiesDatabaseWeakReference = new WeakReference<>(cradleDatabase);
            this.readingByPatientId = readingByPatientId;
            this.patientId = patientId;
        }

        @Override
        protected List<Reading> doInBackground(Void... url) {
            List<Reading> readings = new ArrayList<>();
            List<ReadingEntity> readingEntities;
            if (readingByPatientId) {
                readingEntities = readingEntitiesDatabaseWeakReference.get().readingDaoAccess().getAllReadingByPatientId(patientId);
            } else {
                readingEntities = readingEntitiesDatabaseWeakReference.get().readingDaoAccess().getAllReadingEntities();
            }
            for (ReadingEntity readingEntity : readingEntities) {
                Reading r = GsonUtil.makeObjectFromJson(readingEntity.getReadDataJsonString(), Reading.class);
                readings.add(r);
                String patientId = r.patient.patientId;
                Util.ensure(readingEntity.getPatientId() == patientId ||
                        patientId.equals(r.patient.patientId));
            }
            return readings;
        }
    }

    /**
     * HealthFacility functions
     */

    @Override
    public void insert(HealthFacilityEntity healthFacilityEntity) {
        cradleDatabase.healthFacilityDaoAccess().insert(healthFacilityEntity);
    }

    @Override
    public void removeFacilityById(String id) {
        HealthFacilityEntity healthFacilityEntity =
                cradleDatabase.healthFacilityDaoAccess().getHealthFacilityById(id);
        cradleDatabase.healthFacilityDaoAccess().delete(healthFacilityEntity);
    }

    @Override
    public void insertAll(List<HealthFacilityEntity> healthCareFacilityEntities) {
        cradleDatabase.healthFacilityDaoAccess().insertAll(healthCareFacilityEntities);
    }

    @Override
    public List<HealthFacilityEntity> getAllFacilities() {
        return cradleDatabase.healthFacilityDaoAccess().getAllHealthFacilities();
    }

    @Override
    public HealthFacilityEntity getFacilityById(String id) {
        return cradleDatabase.healthFacilityDaoAccess().getHealthFacilityById(id);
    }

    @Override
    public List<HealthFacilityEntity> getUserSelectedFacilities() {
        return cradleDatabase.healthFacilityDaoAccess().getAllUserSelectedHealthFacilities();
    }
}
