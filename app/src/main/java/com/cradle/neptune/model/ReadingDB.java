package com.cradle.neptune.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cradle.neptune.utilitiles.GsonUtil;
import com.cradle.neptune.utilitiles.Util;

import org.threeten.bp.ZonedDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReadingDB {
    public void addNewOrUpdateReading(Context context, Reading reading) {
        reading.dateLastSaved = ZonedDateTime.now();
        SQLiteDatabase database = new ReadingSQLiteDBHelper(context).getWritableDatabase();

        ContentValues values = new ContentValues();
        //incase we are adding the reading from server on first login and readingId already exists.
        if (reading.readingId == null || reading.readingId.equals("") ||
                reading.readingId.toLowerCase().equals("null")) {
            reading.readingId = UUID.randomUUID().toString();
        }
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_DBID, reading.readingId);
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_PATIENT_ID, reading.patient.patientId);
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_JSON, GsonUtil.getJson(reading));
        // using replace is helpful since we might have duplicated entries whose values needs to be updated.
        database.replace(ReadingSQLiteDBHelper.READING_TABLE_NAME, null, values);
        database.close();

    }

    public void updateReading(Context context, Reading reading) {
        reading.dateLastSaved = ZonedDateTime.now();
        SQLiteDatabase database = new ReadingSQLiteDBHelper(context).getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_DBID, reading.readingId);
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_PATIENT_ID, reading.patient.patientId);
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_JSON, GsonUtil.getJson(reading));

        String whereClause = ReadingSQLiteDBHelper.READING_COLUMN_DBID + " = ?";
        String[] whereArgs = {String.valueOf(reading.readingId)};

        int numUpdates = database.update(
                ReadingSQLiteDBHelper.READING_TABLE_NAME,
                values,
                whereClause,
                whereArgs
        );
        Util.ensure(numUpdates == 1);
        database.close();
    }

    public List<Reading> getReadings(Context context) {
        SQLiteDatabase database = new ReadingSQLiteDBHelper(context).getReadableDatabase();

        String[] projection = {
                ReadingSQLiteDBHelper.READING_COLUMN_DBID,
                ReadingSQLiteDBHelper.READING_COLUMN_PATIENT_ID,
                ReadingSQLiteDBHelper.READING_COLUMN_JSON
        };

        String selection = null;
//                ReadingSQLiteDBHelper.PERSON_COLUMN_NAME + " like ? and " +
//                        ReadingSQLiteDBHelper.PERSON_COLUMN_AGE + " > ? and " +
//                        ReadingSQLiteDBHelper.PERSON_COLUMN_GENDER + " like ?";

        String[] selectionArgs = null;
//                {"%" + name + "%", age, "%" + gender + "%"};

        Cursor cursor = database.query(
                ReadingSQLiteDBHelper.READING_TABLE_NAME,   // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                null                                      // don't sort
        );

        return cursorToArrayList(cursor);
    }


    public Reading getReadingById(Context context, String id) {
        SQLiteDatabase database = new ReadingSQLiteDBHelper(context).getReadableDatabase();

        String[] projection = {
                ReadingSQLiteDBHelper.READING_COLUMN_DBID,
                ReadingSQLiteDBHelper.READING_COLUMN_PATIENT_ID,
                ReadingSQLiteDBHelper.READING_COLUMN_JSON
        };

        String selection = ReadingSQLiteDBHelper.READING_COLUMN_DBID + " = ? ";

        String[] selectionArgs = {String.valueOf(id)};

        Cursor cursor = database.query(
                ReadingSQLiteDBHelper.READING_TABLE_NAME,   // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                null                                      // don't sort
        );

        List<Reading> list = cursorToArrayList(cursor);
        cursor.close();
        database.close();
        Util.ensure(list.size() <= 1);
        return list.size() > 0 ? list.get(0) : null;
    }

    private List<Reading> cursorToArrayList(Cursor cursor) {
        List<Reading> readings = new ArrayList<>();
        int readingIdColumnIndex = cursor.getColumnIndex(ReadingSQLiteDBHelper.READING_COLUMN_DBID);
        int patientIdColumnIndex = cursor.getColumnIndex(ReadingSQLiteDBHelper.READING_COLUMN_PATIENT_ID);
        int jsonColumnIndex = cursor.getColumnIndex(ReadingSQLiteDBHelper.READING_COLUMN_JSON);
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            // get column data
            long rowId = cursor.getLong(readingIdColumnIndex);
            String patientId = cursor.getString(patientIdColumnIndex);
            String json = cursor.getString(jsonColumnIndex);

            // create reading & fill ID (not in JSON)
            Reading r = GsonUtil.makeObjectFromJson(json, Reading.class);
            readings.add(r);

            // Double check that the data we are reading is consistent
            Util.ensure(patientId == r.patient.patientId || patientId.equals(r.patient.patientId));
        }
        cursor.close();
        return readings;
    }

    public void deleteReadingById(Context context, String readingID) {
        SQLiteDatabase database = new ReadingSQLiteDBHelper(context).getWritableDatabase();

        String whereClause = ReadingSQLiteDBHelper.READING_COLUMN_DBID + " = ?";
        String[] whereArgs = {String.valueOf(readingID)};

        int numDeleted = database.delete(
                ReadingSQLiteDBHelper.READING_TABLE_NAME,   // The table to query
                whereClause,
                whereArgs
        );
    }

    public void deleteAllData(Context context) {
        ReadingSQLiteDBHelper helper = new ReadingSQLiteDBHelper(context);
        SQLiteDatabase database = helper.getWritableDatabase();

        helper.dropAllTablesAndRecreate(database);
    }

}
