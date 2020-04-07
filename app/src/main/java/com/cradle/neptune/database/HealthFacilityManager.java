package com.cradle.neptune.database;

import java.util.List;

public interface HealthFacilityManager {

    void addFacility(HealthCareFacilityEntity healthCareFacilityEntity);

    void removeFacility(String id);

    void addAllFacilities(List<HealthCareFacilityEntity> healthCareFacilityEntities);

    List<HealthCareFacilityEntity> getAllFacilities();

    HealthCareFacilityEntity getFacilityById(String id);

    List<HealthCareFacilityEntity> getUserSelectedFacilities();
}
