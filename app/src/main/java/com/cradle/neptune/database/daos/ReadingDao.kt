package com.cradle.neptune.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import com.cradle.neptune.model.Reading

/**
 * Data Access Object (DAO) for [Reading] entities.
 *
 * Provides methods for adding, updating, and removing entities from a database
 * along with a series of query methods.
 */
@Dao
interface ReadingDao {
    /**
     * Inserts a new reading into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param reading The entity to insert into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(reading: Reading)

    /**
     * Inserts each reading in the supplied list into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param readingEntities A list of entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(readingEntities: List<Reading>)

    /**
     * Updates an existing reading in the database.
     *
     * @param reading An entity containing updated data.
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(reading: Reading)

    /**
     * Removes an entity from the database.
     *
     * @param reading The entity to remove.
     */
    @Delete
    fun delete(reading: Reading?)

    /**
     * All of the readings in the database.
     * Do not use this. This should only be used for (instrumented) unit tests.
     * Calculating statistics should be done as a query or done via a raw cursor; a Reading object
     * contains too much unneeded info.
     */
    @Deprecated("Do not use this. This is not memory efficient")
    @Query("SELECT * FROM Reading")
    fun getAllReadingEntities(): List<Reading>

    /**
     * Returns the first reading who's id matches a given pattern.
     *
     * Note that this method does not perform an exact match on the reading id
     * and instead performs an SQL `LIKE` operation limiting the result to 1.
     *
     * @param id The reading id to search for.
     */
    @Query("SELECT * FROM Reading WHERE readingId LIKE :id LIMIT 1")
    fun getReadingById(id: String): Reading?

    /**
     * Returns all of the readings associated with a specified patient.
     *
     * @param id The id of the patient to find readings for.
     */
    @Query("SELECT * FROM Reading WHERE patientId LIKE :id")
    fun getAllReadingByPatientId(id: String): List<Reading>

    /**
     * All readings which have not yet been uploaded to the server.
     */
    @Query("SELECT * FROM Reading WHERE isUploadedToServer = 0")
    fun getAllUnUploadedReadings(): List<Reading>

    /**
     * Returns all un-uploaded readings for patients who have previously been
     * synced with the server.
     */
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * 
        FROM Reading r 
        JOIN Patient p ON r.patientId like p.id
        WHERE p.base is NOT null AND r.isUploadedToServer = 0
    """
    )
    fun getAllUnUploadedReadingsForTrackedPatients(): List<Reading>

    /**
     * get the newest reading of a particular patient
     */
    @Query("SELECT * FROM READING WHERE patientId LIKE :id ORDER BY dateTimeTaken LIMIT 1 ")
    fun getNewestReadingByPatientId(id: String): Reading?

    /**
     * Deletes all readings from the database.
     */
    @Query("DELETE FROM Reading")
    fun deleteAllReading()
}
