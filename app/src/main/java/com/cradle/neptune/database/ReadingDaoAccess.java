package com.cradle.neptune.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ReadingDaoAccess {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertReading(ReadingEntity readingEntity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ReadingEntity> readingEntities);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(ReadingEntity readingEntity);

    @Delete
    void delete(ReadingEntity readingEntity);

    @Query("SELECT * FROM ReadingEntity")
    List<ReadingEntity> getAllReadingEntities();

    @Query("SELECT * FROM ReadingEntity WHERE readingId LIKE :readingId LIMIT 1")
    ReadingEntity getReadingById(String readingId);

    @Query("SELECT * FROM ReadingEntity WHERE patientId LIKE :patientId")
    List<ReadingEntity> getAllReadingByPatientId(String patientId);

    // room maps bolean to zero and one. zero = false
    @Query("SELECT * FROM ReadingEntity WHERE isUploadedToServer =0")
    List<ReadingEntity> getAllUnUploadedReading();

    @Query("DELETE FROM ReadingEntity")
    void deleteAllReading();


}