package com.cradle.neptune.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
interface HealthFacilityDaoAccess {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HealthFacilityEntity healthFacilityEntity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<HealthFacilityEntity> healthFacilityEntities);

    @Update
    void update(HealthFacilityEntity healthFacilityEntity);

    @Delete
    void delete(HealthFacilityEntity healthFacilityEntity);

    @Query("SELECT * FROM HealthFacilityEntity")
    List<HealthFacilityEntity> getAllHealthFacilities();

    @Query("SELECT * FROM HealthFacilityEntity WHERE id LIKE :id LIMIT 1")
    HealthFacilityEntity getHealthFacilityById(String id);

    @Query("SELECT * FROM HealthFacilityEntity WHERE isUserSelected =1")
    List<HealthFacilityEntity> getAllUserSelectedHealthFacilities();






}
