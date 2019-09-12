package com.cradletrial.cradlevhtapp.model;

import android.content.Context;

import java.util.List;

public interface ReadingManager {
    void addNewReading(Context context, Reading reading);

    void updateReading(Context context, Reading reading);

    List<Reading> getReadings(Context context);
    Reading getReadingById(Context context, long id);

    void deleteReadingById(Context context, long readingID);
    void deleteAllData(Context context);
}