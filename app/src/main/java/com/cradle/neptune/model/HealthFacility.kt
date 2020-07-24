package com.cradle.neptune.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
data class HealthFacility(
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
