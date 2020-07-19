package com.cradle.neptune.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.Sex
import java.io.Serializable

/**
 * A reading database entity.
 *
 * @property readingId Identifier for this reading; the entity's primary key.
 * @property patientId Identifier for the patient this reading is associated with.
 * @property readDataJsonString A JSON string containing data about the reading.
 * @property isUploadedToServer Whether this reading has been uploaded to the
 * server or not.
 */
@Entity
data class ReadingEntity(
    @PrimaryKey var readingId: String,
    @ColumnInfo var patientId: String?,
    @ColumnInfo var readDataJsonString: String?,
    @ColumnInfo var isUploadedToServer: Boolean
) : Serializable

@Entity
data class PatientEntity(
    @PrimaryKey var id: String,
    @ColumnInfo var name: String,
    @ColumnInfo var dob: String?,
    @ColumnInfo var age: Int?,
    @ColumnInfo var gestationalAge: GestationalAge?,
    @ColumnInfo var sex: Sex,
    @ColumnInfo var isPregnant: Boolean,
    @ColumnInfo var zone: String?,
    @ColumnInfo var villageNumber: String?,
    @ColumnInfo var drugHistory: List<String>,
    @ColumnInfo var medicalHistory: List<String>,
    @ColumnInfo var lastEdited: Long?
) : Serializable
/**
 * A health facility database entity.
 *
 * @property id Identifier for this health facility; the entity's primary key.
 * @property name The name of the health facility.
 * @property location The location of the health facility.
 * @property phoneNumber The phone number associated with this health facility.
 * @property about A description of the health facility.
 * @property type The type of the health facility.
 * @property isUserSelected Whether the user wishes to see this health facility
 * in their drop down menu.
 */
@Entity
data class HealthFacilityEntity(
    @PrimaryKey var id: String,
    @ColumnInfo var name: String?,
    @ColumnInfo var location: String?,
    @ColumnInfo var phoneNumber: String?,
    @ColumnInfo var about: String?,
    @ColumnInfo var type: String?,
    @ColumnInfo var isUserSelected: Boolean
) {
    /**
     * Constructs a health facility entity setting [isUserSelected] to `false`.
     */
    constructor(
        id: String,
        name: String?,
        location: String?,
        phoneNumber: String?,
        about: String?,
        type: String?
    ) : this(id, name, location, phoneNumber, about, type, false)
}
