package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.database.daos.AssessmentDao
import com.cradleplatform.neptune.database.daos.ReferralDao
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.net.RestApi
import com.cradleplatform.neptune.net.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for interfacing with assessments stored in the database.
 *
 * This service is used to abstract away the raw database schema which prefers
 * to store data as JSON instead of using actual database constructs.
 *
 * When interacting with this service from Kotlin, use of the `suspend` methods
 * is preferred. For Java interop, one can use either the convenience `Async`
 * or `Blocking` variants. For call-and-forget methods like [addAssessment], use
 * the `Async` variant. For methods which return a value, the `Blocking`
 * variants may be used but remember that those will block the current thread.
 *
 */
@Singleton
class AssessmentManager @Inject constructor(
    private val database: CradleDatabase,
    private val assessmentDao: AssessmentDao,
    private val restApi: RestApi
) {

    /**
     * Adds a new assessment to the database.
     * @param assessment the assessment to insert
     */
    suspend fun addAssessment(assessment: Assessment) {
        assessmentDao.updateOrInsertIfNotExists(assessment)
    }

    /**
     * Updates an existing assessment in the database.
     * of this class and make this a [suspend] function
     * @param assessment the assessment to update
     */
    suspend fun updateAssessment(assessment: Assessment) = assessmentDao.update(assessment)

    /**
     * Returns the assessment (and its associated patient) with a given [id] from
     * the database. Returns `null` if unable to find such a assessment.
     */
    suspend fun getAssessmentById(id: Int): Assessment? = assessmentDao.getAssessmentById(id)

    // /**
    //  * Get all the assessments that have been created or edited offline
    //  */
    // suspend fun getAssessmentsToUpload(): List<Assessment> = assessmentDao.assessmentsToUpload()

    /**
     * Returns all assessments associated with a specific patient [id].
     */
    suspend fun getAssessmentsByPatientId(id: String): List<Assessment>? =
        assessmentDao.getAllAssessmentByPatientId(id)

    // /**
    //  * Returns all assessments which have not been uploaded to the server yet.
    //  */
    // suspend fun getUnUploadedAssessments(): List<Assessment> = assessmentDao.getAllUnUploadedAssessments()

    /**
     * Get the newest assessment of a patient
     */
    suspend fun getAssessmentByPatientId(id: String): List<Assessment>? =
        assessmentDao.getAllAssessmentByPatientId(id)

    /**
     * Uploads an edited assessments to the server.
     *
     * @param assessment the assessment to upload
     * @return whether the upload succeeded or not
     */
    suspend fun updateAssessmentOnServerAndSave(assessment: Assessment): NetworkResult<Unit> {
        val result = restApi.postAssessment(assessment)
        if (result is NetworkResult.Success) {
            assessment.lastServerUpdate = assessment.lastEdited
        }
        addAssessment(assessment)
        return result.map { }
    }

    /**
     * Get all the assessments that have been created or edited offline
     */
    suspend fun getAssessmentsToUpload(): List<Assessment> = assessmentDao.assessmentsToUpload()

}
