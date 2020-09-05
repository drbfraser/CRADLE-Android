package com.cradle.neptune.sync

/**
 * This is the main interface for the whole sync process
 * describes all the different steps required to do our one button sync
 * There is a google doc that explains all the steps and the order of the steps as well
 */
interface SyncStepper {

    /**
     * This is the first step: we fetch updates with /api/updates
     * we pass this api a time stamp of our last successful sync
     * the api returns arrays of ids for new patients,edited patients, new readings, new assessments
     * Once we have all the data we move to step number 2
     */
    suspend fun stepOneFetchUpdatesFromServer()

    /**
     * This is the step number two. Here we start uploading data to the server.
     * The data can include new patients, edited patients, new readings etc.
     * NOTE: we do not upload the patients edited by local user as well as the server
     */
    suspend fun stepTwoSetupUploadingPatientReadings(lastSyncTime: Long)

    /**
     * This is the third step. Here we download all the new data from the server
     * The data includes, new readings, patients, edited patients, follow ups etc.
     * NOTE: in step number 2, we avoided uploading patient info that was also edited by the server
     * in this step, we override our changes with the server since server changes are always prioritized.
     */
    suspend fun stepThreeDownloadAllInfo()

    /**
     * This is the last step of the sync process.
     * Here we update the last sync timestamp in the shared pref
     */
    suspend fun finish(success: Boolean)
}

/**
 * A simple interface to let the caller know whats going on with the sync process
 * All the functions should have [Synchronized] since multiple threads might be calling them.
 */
interface SyncStepperCallback {

    /**
     * Let the caller know we have completed fetching the data from the server
     * @param success status of fetching the data from the server
     */
    fun onFetchDataCompleted(success: Boolean)

    /**
     * called every time we get a network result for all the upload network calls
     * @param uploadStatus contains number of total requests, failed requests, success requests
     */
    fun onNewPatientAndReadingUploading(uploadStatus: TotalRequestStatus)

    /**
     * called when we finished uploading the patient and readings.
     */
    fun onNewPatientAndReadingUploadFinish(uploadStatus: TotalRequestStatus)

    /**
     * called every time we get a network result for all the download network calls
     * @param downloadStatus contains number of total requests, failed requests, success requests
     */
    fun onNewPatientAndReadingDownloading(downloadStatus: TotalRequestStatus)

    /**
     * called when we finish downloading the information from the server
     */
    fun onNewPatientAndReadingDownloadFinish(downloadStatus: TotalRequestStatus)

    /**
     * let the caller know we are done sync process
     */
    fun onFinish(errorCodes: HashMap<Int?, String?>)
}
