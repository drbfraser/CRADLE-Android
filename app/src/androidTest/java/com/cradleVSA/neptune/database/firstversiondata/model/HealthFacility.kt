package com.cradleVSA.neptune.database.firstversiondata.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cradleVSA.neptune.ext.Field
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A health facility database entity.
 *
 * @property name The name of the health facility.
 * @property location The location of the health facility.
 * @property phoneNumber The phone number associated with this health facility.
 * @property about A description of the health facility.
 * @property type The type of the health facility.
 * @property isUserSelected Whether the user wishes to see this health facility
 * in their drop down menu.
 */
@Entity
internal data class HealthFacility(
    @PrimaryKey @ColumnInfo @JsonProperty("healthFacilityName")
    val name: String,
    @ColumnInfo
    val location: String = "",
    @ColumnInfo @JsonProperty("healthFacilityPhoneNumber")
    val phoneNumber: String = "",
    @ColumnInfo
    val about: String = "",
    @ColumnInfo @JsonProperty("facilityType")
    val type: String = "",
    @ColumnInfo @JsonIgnore
    val isUserSelected: Boolean = false
)

/**
 * The collection of JSON fields which make up a [HealthFacility] object.
 *
 * These fields are defined here to ensure that the marshal and unmarshal
 * methods use the same field names.
 */
private enum class HealthFacilityField(override val text: String) : Field {
    TYPE("facilityType"),
    LOCATION("location"),
    ABOUT("about"),
    PHONE_NUMBER("healthFacilityPhoneNumber"),
    NAME("healthFacilityName"),
    ID("id");
}
