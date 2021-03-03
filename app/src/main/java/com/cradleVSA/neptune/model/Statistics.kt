package com.cradleVSA.neptune.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data for the statistics between two dates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Statistics (
    @JsonProperty("patients_referred")
    val patients_referred: Int?,
    @JsonProperty("sent_referrals")
    val sent_referrals: Int?,
    @JsonProperty("unique_patient_readings")
    val unique_patient_readings: Int,
    @JsonProperty("total_readings")
    val total_readings: Int,
    @JsonProperty("color_readings")
    val color_readings: ColorReadings
)

/**
 * Stoplight/color reading statistics.
 */
data class ColorReadings (
    @JsonProperty("GREEN")
    val green_readings: Int?,
    @JsonProperty("RED_DOWN")
    val red_down_readings: Int?,
    @JsonProperty("RED_UP")
    val red_up_readings: Int?,
    @JsonProperty("YELLOW_DOWN")
    val yellow_down_readings: Int?,
    @JsonProperty("YELLOW_UP")
    val yellow_up_readings: Int?
)
