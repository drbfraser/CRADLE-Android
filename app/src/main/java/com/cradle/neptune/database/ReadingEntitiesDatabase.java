package com.cradle.neptune.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {ReadingEntity.class}, version = 1, exportSchema = false)
public abstract class ReadingEntitiesDatabase extends RoomDatabase {
    public abstract DaoAccess daoAccess();
}
