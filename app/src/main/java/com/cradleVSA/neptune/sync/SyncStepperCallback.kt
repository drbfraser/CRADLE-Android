package com.cradleVSA.neptune.sync

/**
 * A simple interface to let the caller know whats going on with the sync process
 * All the functions should have [Synchronized] since multiple threads might be calling them.
 *
 * TODO: remove this
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
