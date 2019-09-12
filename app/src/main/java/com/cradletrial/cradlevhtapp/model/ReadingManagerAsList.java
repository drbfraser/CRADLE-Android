package com.cradletrial.cradlevhtapp.model;


import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class ReadingManagerAsList implements ReadingManager {
    private List<Reading> readings = new ArrayList<>();

    @Override
    public void addNewReading(Context context, Reading reading) {
        readings.add(0, reading);
    }

    @Override
    public void updateReading(Context context, Reading reading) {
//        db.saveExistingReading(context, newR);
        for (int i = 0; i < readings.size(); i++) {
            if (readings.get(i).readingId == reading.readingId) {
                readings.remove(i);
                readings.add(i, reading);
                return;
            }
        }
        throw new UnsupportedOperationException("Unable to find requested reading to replace.");
    }

    @Override
    public List<Reading> getReadings(Context context) {
        return readings;
    }

    @Override
    public Reading getReadingById(Context context, long id) {
        for (int i = 0; i < readings.size(); i++) {
            if (readings.get(i).readingId == id) {
                readings.remove(i);
            }
        }
        throw new UnsupportedOperationException("Unable to find requested reading to delete.");
    }


    @Override
    public void deleteReadingById(Context context, long readingID) {
        for (int i = 0; i < readings.size(); i++) {
            if (readings.get(i).readingId == readingID) {
                readings.remove(i);
            }
        }
        throw new UnsupportedOperationException("Unable to find requested reading to delete.");
    }

    @Override
    public void deleteAllData(Context context) {
        readings.clear();
    }
}
