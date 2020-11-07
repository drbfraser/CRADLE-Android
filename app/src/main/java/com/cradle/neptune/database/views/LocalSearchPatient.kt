package com.cradle.neptune.database.views

import androidx.room.DatabaseView
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.ReadingAnalysis
import com.cradle.neptune.model.Referral

/**
 * Represents a patient as seen in the patient's list, using a minimal amount
 * of information to save memory. We left join on the Reading table to cover
 * the case where there is a patient without a reading.
 */
@DatabaseView(
    value = """
SELECT
  p.name,
  p.id,
  p.villageNumber,
  r.bloodPressure as latestBloodPressure,
  MAX(r.dateTimeTaken) as latestReadingDate,
  r.referral
FROM
  Patient as p
  LEFT JOIN Reading AS r ON p.id = r.patientId
GROUP BY 
  r.patientId
"""
)
data class LocalSearchPatient(
    val name: String,
    val id: String,
    val villageNumber: String?,
    val latestBloodPressure: BloodPressure?,
    val latestReadingDate: Long?,
    val referral: Referral?
) {
    fun getLatestRetestAnalysis(): ReadingAnalysis? = latestBloodPressure?.analysis
}
