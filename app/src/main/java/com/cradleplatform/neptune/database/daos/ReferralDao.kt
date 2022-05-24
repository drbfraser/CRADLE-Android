package com.cradleplatform.neptune.database.daos

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Referral

/**
 * Data Access Object (DAO) for [Referral] entities.
 *
 * Provides methods for adding, updating, and removing entities from a database
 * along with a series of query methods.
 */
@Dao
interface ReferralDao {
    @Transaction
    suspend fun updateOrInsertIfNotExists(referral: Referral) {
        if (update(referral) <= 0) {
            insert(referral)
        }
    }

    /**
     * Inserts a new referral into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param referral The entity to insert into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(referral: Referral)

    /**
     * Inserts each referral in the supplied list into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param referral A list of entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(referralEntities: List<Referral>)

    /**
     * Updates an existing referral in the database.
     *
     * @param referral An entity containing updated data.
     * @return the number of rows updated (i.e., 0 means the given referral wasn't in the database,
     * and 1 means the [referral] was updated)
     */
    @Update
    suspend fun update(referral: Referral): Int

    /**
     * Removes an entity from the database.
     *
     * @param referral The entity to remove.
     */
    @Delete
    suspend fun delete(referral: Referral?)

    /**
     * Returns the first referral whose referral ID is equal to [id].
     *
     * @param id The referral id to search for.
     */
    @Query("SELECT * FROM Referral WHERE id = :id")
    suspend fun getReferralById(id: Int): Referral?

    /**
     * Returns all of the referrals associated with a specified patient.
     *
     * @param id The id of the patient to find referrals for.
     */
    @Query("SELECT * FROM Referral WHERE patientId = :id")
    suspend fun getAllReferralByPatientId(id: String): List<Referral>?

    /**
     * Query the database for all the referrals that have been created or edited offline
     */
    @Query("SELECT * FROM Referral WHERE isUploadedToServer = 0")
    suspend fun referralsToUpload(): List<Referral>

    /**
     * Query the database for the number of referrals that have been created or edited offline
     */
    @Query("SELECT COUNT(id) FROM Referral WHERE isUploadedToServer = 0")
    suspend fun countReferralsToUpload(): Int

    /**
     * Returns a live list of referrals
     */
    @Query("SELECT * FROM Referral")
    fun getAllReferralsLiveData(): LiveData<List<Referral>>
}
