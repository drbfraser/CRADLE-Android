package com.cradleplatform.neptune.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data for the statistics between two dates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Statistics(
    @JsonProperty("patients_referred")
    val patientsReferred: Int,
    @JsonProperty("sent_referrals")
    val sentReferrals: Int,
    @JsonProperty("unique_patient_readings")
    val uniquePatientReadings: Int,
    @JsonProperty("total_readings")
    val totalReadings: Int,
    @JsonProperty("days_with_readings")
    val daysWithReadings: Int,
    @JsonProperty("color_readings")
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
