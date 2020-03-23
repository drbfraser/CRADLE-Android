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

public class RoomReadingManager implements ReadingManager {
    private ReadingEntitiesDatabase readingEntitiesDatabase;

    public RoomReadingManager(ReadingEntitiesDatabase readingEntitiesDatabase) {
        this.readingEntitiesDatabase = readingEntitiesDatabase;
    }

    @Override
    public void addNewReading(Context context, Reading reading) {
        // add the new reading into the DB.
        reading.dateLastSaved = ZonedDateTime.now();
        if (reading.readingId == null || reading.readingId.equals("") ||
                reading.readingId.toLowerCase().equals("null")) {
            reading.readingId = UUID.randomUUID().toString();
        }

        ReadingEntity readingEntity = new ReadingEntity();
        readingEntity.setReadingId(reading.readingId);
        readingEntity.setPatientId(reading.patient.patientId);
        readingEntity.setReadDataJsonString(GsonUtil.getJson(reading));
        readingEntitiesDatabase.daoAccess().insertReading(readingEntity);

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
        ReadingEntity readingEntity = new ReadingEntity();
        readingEntity.setReadingId(reading.readingId);
        readingEntity.setPatientId(reading.patient.patientId);
        readingEntity.setReadDataJsonString(GsonUtil.getJson(reading));

        readingEntitiesDatabase.daoAccess().update(readingEntity);
    }

    @Override
    public List<Reading> getReadings(Context context) {
        return new GetAllReadingsAsyncTask(readingEntitiesDatabase,false,"").doInBackground();
    }

    @Override
    public Reading getReadingById(Context context, String id)
    {
        ReadingEntity readingEntity = readingEntitiesDatabase.daoAccess().getReadingById(id);
        if (readingEntity==null){
            return null;
        }
        Reading r = GsonUtil.makeObjectFromJson(readingEntity.getReadDataJsonString(),Reading.class);
        String patientId  = r.patient.patientId;
        Util.ensure(readingEntity.getPatientId() == patientId ||
                patientId.equals(r.patient.patientId));
        return r;

    }

    @Override
    public void deleteReadingById(Context context, String readingID) {
        ReadingEntity readingEntity = readingEntitiesDatabase.daoAccess().getReadingById(readingID);
        if (readingEntity!=null){
            readingEntitiesDatabase.daoAccess().delete(readingEntity);
        }

    }

    @Override
    public void deleteAllData(Context context) {
        readingEntitiesDatabase.daoAccess().deleteAllReading();
    }

    @Override
    public List<Reading> getReadingByPatientID(Context context, String patientID) {
        return new GetAllReadingsAsyncTask(readingEntitiesDatabase,true,patientID).doInBackground();
    }

    @Override
    public void addAllReadings(Context context, List<Reading> readings) {
        List<ReadingEntity> readingEntities = new ArrayList<>();
        for (Reading reading: readings){
            ReadingEntity readingEntity = new ReadingEntity();
            readingEntity.setReadingId(reading.readingId);
            readingEntity.setPatientId(reading.patient.patientId);
            readingEntity.setReadDataJsonString(GsonUtil.getJson(reading));
            readingEntities.add(readingEntity);
        }
        readingEntitiesDatabase.daoAccess().insertAll(readingEntities);
    }

    /**
     * since we dont want to block the main UI thread, we have to create a seperate thread for large queries.
     */
    private class GetAllReadingsAsyncTask extends AsyncTask<Void, Void,List<Reading>>
    {
        WeakReference<ReadingEntitiesDatabase> readingEntitiesDatabaseWeakReference;
        private boolean readingByPatientId = false;
        private String patientId = "";

        GetAllReadingsAsyncTask(ReadingEntitiesDatabase readingEntitiesDatabase, boolean readingByPatientId, String patientId){
            this.readingEntitiesDatabaseWeakReference = new WeakReference<>(readingEntitiesDatabase);
            this.readingByPatientId = readingByPatientId;
            this.patientId = patientId;
        }

        @Override
        protected List<Reading> doInBackground(Void... url) {
            List<Reading> readings = new ArrayList<>();
            List<ReadingEntity> readingEntities;
            if (readingByPatientId){
                readingEntities = readingEntitiesDatabaseWeakReference.get().daoAccess().getAllReadingByPatientId(patientId);
            } else {
                readingEntities = readingEntitiesDatabaseWeakReference.get().daoAccess().getAllReadingEntities();
            }
            for (ReadingEntity readingEntity:readingEntities){
                Reading r = GsonUtil.makeObjectFromJson(readingEntity.getReadDataJsonString(),Reading.class);
                readings.add(r);
                String patientId  = r.patient.patientId;
                Util.ensure(readingEntity.getPatientId() == patientId ||
                        patientId.equals(r.patient.patientId));
            }
            return readings;
        }
    }
}
