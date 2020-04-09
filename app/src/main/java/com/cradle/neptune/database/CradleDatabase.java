package com.cradle.neptune.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {ReadingEntity.class, HealthFacilityEntity.class}, version = 1, exportSchema = false)
public abstract class CradleDatabase extends RoomDatabase {
    public abstract ReadingDaoAccess readingDaoAccess();

    public abstract HealthFacilityDaoAccess healthFacilityDaoAccess();
}
