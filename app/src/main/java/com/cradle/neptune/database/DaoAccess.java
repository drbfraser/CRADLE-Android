package com.cradle.neptune.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DaoAccess  {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertReading(ReadingEntity readingEntity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(ReadingEntity... readingEntities);

    @Delete
    void delete(ReadingEntity readingEntity);

    @Query("SELECT * FROM ReadingEntity")
    List<ReadingEntity> getAllReadingEntities();

    @Query("SELECT * FROM readingentity WHERE readingId LIKE :readinId LIMIT 1")
    ReadingEntity findByName(String readinId);




}
