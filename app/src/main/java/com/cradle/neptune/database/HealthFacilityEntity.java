package com.cradle.neptune.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class HealthFacilityEntity {

    @PrimaryKey
    @NonNull
    private String id;
    @ColumnInfo
    private String name;
    @ColumnInfo
    private String location;
    @ColumnInfo
    private String phoneNumber;
    @ColumnInfo
    private String about;
    @ColumnInfo
    private String type;


    // if user wants this in the dropdown menu
    @ColumnInfo
    private boolean isUserSelected;

    public HealthFacilityEntity(@NonNull String id, String name, String location, String phoneNumber, String about, String type) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.phoneNumber = phoneNumber;
        this.type = type;
        this.about = about;
    }

    public String getAbout() {
        return about;
    }

    public String getType() {
        return type;
    }

    public boolean isUserSelected() {
        return isUserSelected;
    }

    public void setUserSelected(boolean userSelected) {
        isUserSelected = userSelected;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
}
