package com.cradleVSA.neptune.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.Referral

/**
 * Data Access Object (DAO) for [Reading] entities.
 *
 * Provides methods for adding, updating, and removing entities from a database
 * along with a series of query methods.
 */
@Dao
interface ReadingDao {
    @Transaction
    suspend fun updateOrInsertIfNotExists(reading: Reading) {
        if (update(reading) <= 0) {
            insert(reading)
        }
    }

    /**
     * Inserts a new reading into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param reading The entity to insert into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: Reading)

    /**
     * Inserts each reading in the supplied list into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param readingEntities A list of entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readingEntities: List<Reading>)

    /**
     * Updates an existing reading in the database.
     *
     * @param reading An entity containing updated data.
     * @return The number of rows updated (0 means [reading] wasn't in the database, 1 means it
     * was and it was updated).
     */
    @Update
    suspend fun update(reading: Reading): Int

    @Query("UPDATE Reading SET referral = :referral WHERE readingId = :readingId")
    suspend fun updateReferral(readingId: String, referral: Referral): Int

    /**
     * Removes an entity from the database.
     *
     * @param reading The entity to remove.
     */
    @Delete
    suspend fun delete(reading: Reading?)

    /**
     * All of the readings in the database.
     * Do not use this. This should only be used for (instrumented) unit tests.
     * Calculating statistics should be done as a query or done via a raw cursor; a Reading object
     * contains too much unneeded info.
     */
    @Deprecated("Do not use this. This is not memory efficient")
    @Query("SELECT * FROM Reading")
    suspend fun getAllReadingEntities(): List<Reading>

    /**
     * Returns the first reading whose reading ID is equal to [id].
     *
     * @param id The reading id to search for.
     */
    @Query("SELECT * FROM Reading WHERE readingId = :id")
    suspend fun getReadingById(id: String): Reading?

    /**
     * Returns a List of Readings whose readingIds correspond to the given [ids]
     */
    @Query("SELECT * FROM Reading WHERE readingId IN (:ids)")
    suspend fun getReadingsByIds(ids: List<String>): List<Reading>

    /**
     * Returns all of the readings associated with a specified patient.
     *
     * @param id The id of the patient to find readings for.
     */
    @Query("SELECT * FROM Reading WHERE patientId = :id")
    suspend fun getAllReadingByPatientId(id: String): List<Reading>

    /**
     * All readings which have not yet been uploaded to the server.
     */
    @Query("SELECT * FROM Reading WHERE isUploadedToServer = 0")
    suspend fun getAllUnUploadedReadings(): List<Reading>

    /**
     * Returns number of readings that were marked as uploaded
     */
    @Query("UPDATE Reading SET isUploadedToServer = 1 WHERE isUploadedToServer = 0")
    suspend fun markAllAsUploadedToServer(): Int

    /**
     * Returns all un-uploaded readings for patients who have previously been
     * synced with the server.
     */
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * 
        FROM Reading r 
        JOIN Patient p ON r.patientId = p.id
        WHERE p.base IS NOT NULL AND r.isUploadedToServer = 0
    """
    )
    suspend fun getAllUnUploadedReadingsForTrackedPatients(): List<Reading>

    /**
     * get the newest reading of a particular patient
     */
    @Query("SELECT * FROM Reading WHERE patientId = :id ORDER BY dateTimeTaken LIMIT 1")
    suspend fun getNewestReadingByPatientId(id: String): Reading?

    /**
     * Deletes all readings from the database.
     */
    @Query("DELETE FROM Reading")
    suspend fun deleteAllReading()
}