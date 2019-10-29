package com.cradletrial.cradlevhtapp.model;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ReadingSQLiteDBHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "app_database";
    public static final String PATIENT_TABLE_NAME = "patient";
    public static final String PATIENT_COLUMN_ID = "_id";
    public static final String READING_TABLE_NAME = "reading";
    public static final String READING_COLUMN_DBID = "_id";
    public static final String READING_COLUMN_PATIENT_ID = "patient_id";
    public static final String READING_COLUMN_JSON = "json";
    private static final int DATABASE_VERSION = 1;

    public ReadingSQLiteDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE " + PATIENT_TABLE_NAME + " (" +
                PATIENT_COLUMN_ID + " TEXT PRIMARY KEY "
                + ")");

        sqLiteDatabase.execSQL("CREATE TABLE " + READING_TABLE_NAME + " (" +
                READING_COLUMN_DBID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                READING_COLUMN_PATIENT_ID + " TEXT, " +
                READING_COLUMN_JSON + " JSON " +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        dropAllTablesAndRecreate(sqLiteDatabase);
    }

    public void dropAllTablesAndRecreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + PATIENT_TABLE_NAME);
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + READING_TABLE_NAME);
        onCreate(sqLiteDatabase);
    }
}
