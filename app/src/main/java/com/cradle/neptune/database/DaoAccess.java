package com.cradle.neptune.database;

import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

public interface DaoAccess  {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertReading(ReadingEntity readingEntity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ReadingEntity> readingEntities);

    @Delete
    void delete(ReadingEntity readingEntity);

    @Query("SELECT * FROM ReadingEntity")
    List<ReadingEntity> getAllReadingEntities();



}
