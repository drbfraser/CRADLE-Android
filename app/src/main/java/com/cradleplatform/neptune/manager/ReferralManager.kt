package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.database.daos.ReferralDao
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.http.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for interfacing with referrals stored in the database.
 *
 * This service is used to abstract away the raw database schema which prefers
 * to store data as JSON instead of using actual database constructs.
 *
 * When interacting with this service from Kotlin, use of the `suspend` methods
 * is preferred. For Java interop, one can use either the convenience `Async`
 * or `Blocking` variants. For call-and-forget methods like [addReferral], use
 * the `Async` variant. For methods which return a value, the `Blocking`
 * variants may be used but remember that those will block the current thread.
 *
 */
@Singleton
class ReferralManager @Inject constructor(
    private val database: CradleDatabase,
    private val referralDao: ReferralDao,
    private val restApi: RestApi
) {

    /**
     * Adds a new referral to the database.
     * @param referral the referral to insert
     */
    suspend fun addReferral(referral: Referral, isReferralFromServer: Boolean) {
        if (isReferralFromServer) referral.isUploadedToServer = true
        referralDao.updateOrInsertIfNotExists(referral)
    }

    /**
     * Updates an existing referral in the database.
     * of this class and make this a [suspend] function
     * @param referral the referral to update
     */
    suspend fun updateReferral(referral: Referral) = referralDao.update(referral)

    /**
     * Returns the referral (and its associated patient) with a given [id] from
     * the database. Returns `null` if unable to find such a referral.
     */
    suspend fun getReferralById(id: Int): Referral? = referralDao.getReferralById(id)

    // /**
    //  * Get all the referrals that have been created or edited offline
    //  */
    // suspend fun getReferralsToUpload(): List<Referral> = referralDao.referralsToUpload()

    /**
     * Returns all referrals associated with a specific patient [id].
     */
    suspend fun getReferralsByPatientId(id: String): List<Referral>? =
        referralDao.getAllReferralByPatientId(id)

    // /**
    //  * Returns all referrals which have not been uploaded to the server yet.
    //  */
    // suspend fun getUnUploadedReferrals(): List<Referral> = referralDao.getAllUnUploadedReferrals()

    /**
     * Get the newest referral of a patient
     */
    suspend fun getReferralByPatientId(id: String): List<Referral>? =
        referralDao.getAllReferralByPatientId(id)

    /**
     * Get the number of referrals that have been created or edited offline
     */
    suspend fun getNumberOfReferralsToUpload(): Int = referralDao.countReferralsToUpload()

    /**
     * Uploads an edited referrals to the server.
     *
     * @param referral the referral to upload
     * @return whether the upload succeeded or not
     */
    suspend fun updateReferralOnServerAndSave(referral: Referral): NetworkResult<Unit> {
        val result = restApi.postReferral(referral)
        if (result is NetworkResult.Success) {
            referral.lastServerUpdate = referral.lastEdited
        }
        addReferral(referral, true)
        return result.map { }
    }

    /**
     * Get all the referrals that have been created or edited offline
     */
    suspend fun getReferralsToUpload(): List<Referral> = referralDao.referralsToUpload()

//
    // suspend fun markAllReferralsAsUploaded() = referralDao.markAllAsUploadedToServer()
    //
    // /**
    //  * get unUploaded referrals for patients who already exists in the server
    //  */
    // suspend fun getUnUploadedReferralsForServerPatients(): List<Referral> =
    //     referralDao.getAllUnUploadedReferralsForTrackedPatients()
    //

    //
}
