package com.cradle.neptune.database;

import android.content.Context;

import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;

import java.util.List;

public class RoomReadingManager implements ReadingManager {
    private ReadingEntitiesDatabase readingEntitiesDatabase;

    public RoomReadingManager(ReadingEntitiesDatabase readingEntitiesDatabase) {
        this.readingEntitiesDatabase = readingEntitiesDatabase;
    }

    @Override
    public void addNewReading(Context context, Reading reading) {

    }

    @Override
    public void updateReading(Context context, Reading reading) {

    }

    @Override
    public List<Reading> getReadings(Context context) {
        return null;
    }

    @Override
    public Reading getReadingById(Context context, String id) {
        return null;
    }

    @Override
    public void deleteReadingById(Context context, String readingID) {

    }

    @Override
    public void deleteAllData(Context context) {

    }
}
