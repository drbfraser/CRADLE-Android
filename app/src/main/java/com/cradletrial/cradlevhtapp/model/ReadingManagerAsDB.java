package com.cradletrial.cradlevhtapp.model;

import android.content.Context;
import java.util.List;


public class ReadingManagerAsDB implements ReadingManager {
    private ReadingDB readingDb = new ReadingDB();

    @Override
    public void addNewReading(Context context, Reading reading) {
        readingDb.addNewReading(context, reading);

        // update other records for this patient: done rechecking vitals
        List<Reading> readings = getReadings(context);
        for (Reading r : readings) {
            if (r.patientId.equals(reading.patientId) && r.readingId != reading.readingId) {
                if (r.isNeedRecheckVitals()) {
                    r.dateRecheckVitalsNeeded = null;
                    updateReading(context, r);
                }
            }
        }

        // todo: delete unneeded photos!
    }

    @Override
    public void updateReading(Context context, Reading reading) {
        readingDb.updateReading(context, reading);
    }

    @Override
    public List<Reading> getReadings(Context context) {
        return readingDb.getReadings(context);
    }

    @Override
    public Reading getReadingById(Context context, long readingId) {
        return readingDb.getReadingById(context, readingId);
    }

    @Override
    public void deleteReadingById(Context context, long readingId) {
        readingDb.deleteReadingById(context, readingId);
    }

    @Override
    public void deleteAllData(Context context) {
        readingDb.deleteAllData(context);
    }
}
