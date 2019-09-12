package com.cradletrial.cradlevhtapp.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.cradletrial.cradlevhtapp.utilitiles.GsonUtil;
import com.cradletrial.cradlevhtapp.utilitiles.Util;

import org.threeten.bp.ZonedDateTime;

import java.util.ArrayList;
import java.util.List;

public class ReadingDB {
    public void addNewReading(Context context, Reading reading) {
        reading.dateLastSaved = ZonedDateTime.now();
        SQLiteDatabase database = new ReadingSQLiteDBHelper(context).getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_PATIENT_ID, reading.patientId);
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_JSON, GsonUtil.getJson(reading));

        long newRowId = database.insert(ReadingSQLiteDBHelper.READING_TABLE_NAME, null, values);
        reading.readingId = newRowId;
    }

    public void updateReading(Context context, Reading reading) {
        reading.dateLastSaved = ZonedDateTime.now();
        SQLiteDatabase database = new ReadingSQLiteDBHelper(context).getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_DBID, reading.readingId);
        values.put(ReadingSQLiteDBHelper.READING_COLUMN_PATIENT_ID, reading.patientId);
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


    public Reading getReadingById(Context context, long id) {
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
            r.readingId = rowId;

            readings.add(r);

            // Double check that the data we are reading is consistent
            Util.ensure(patientId == r.patientId || patientId.equals(r.patientId));
        }
        return readings;
    }

    public void deleteReadingById(Context context, long readingID) {
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
