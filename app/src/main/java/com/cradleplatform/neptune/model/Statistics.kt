package com.cradleplatform.neptune.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data for the statistics between two dates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Statistics(
    @JsonProperty("patientsReferred")
    val patientsReferred: Int,
    @JsonProperty("sentReferrals")
    val sentReferrals: Int,
    @JsonProperty("uniquePatientReadings")
    val uniquePatientReadings: Int,
    @JsonProperty("totalReadings")
    val totalReadings: Int,
    @JsonProperty("daysWithReadings")
    val daysWithReadings: Int,
    @JsonProperty("colorReadings")
    val colorReadings: ColorReadings
)

/**
 * Stoplight/color reading statistics.
 */
data class ColorReadings(
    @JsonProperty("GREEN")
    val greenReadings: Int,
    @JsonProperty("RED_DOWN")
    val redDownReadings: Int,
    @JsonProperty("RED_UP")
    val redUpReadings: Int,
    @JsonProperty("YELLOW_DOWN")
    val yellowDownReadings: Int,
    @JsonProperty("YELLOW_UP")
    val yellowUpReadings: Int
)
