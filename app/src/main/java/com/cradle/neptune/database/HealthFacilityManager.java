package com.cradle.neptune.database;

import java.util.List;

public interface HealthFacilityManager {

    void insert(HealthFacilityEntity healthFacilityEntity);

    void removeFacilityById(String id);

    void insertAll(List<HealthFacilityEntity> healthCareFacilityEntities);

    List<HealthFacilityEntity> getAllFacilities();

    HealthFacilityEntity getFacilityById(String id);

    List<HealthFacilityEntity> getUserSelectedFacilities();
}
