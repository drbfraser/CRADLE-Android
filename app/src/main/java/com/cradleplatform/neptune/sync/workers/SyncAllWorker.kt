package com.cradleplatform.neptune.sync.workers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.manager.AssessmentManager
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.http_sms_service.http.AssessmentSyncResult
import com.cradleplatform.neptune.http_sms_service.http.FormSyncResult
import com.cradleplatform.neptune.http_sms_service.http.HealthFacilitySyncResult
import com.cradleplatform.neptune.http_sms_service.http.PatientSyncResult
import com.cradleplatform.neptune.http_sms_service.http.ReadingSyncResult
import com.cradleplatform.neptune.http_sms_service.http.ReferralSyncResult
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.http.SyncException
import com.cradleplatform.neptune.utilities.RateLimitRunner
import com.cradleplatform.neptune.utilities.UnixTimestamp
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

/**
 * A Worker that syncs the local [Patient]s and [Reading]s (and [Referral]s sent by SMS) with
 * the CRADLE server. The data that is on the phone but not on the server is first uploaded, then
 * new data from the server is downloaded. Syncing is done using a timestamp passed as a parameter
 * when accessing the API.
 *
 * TODO: Make sure that patients or readings can't be edited or created while syncing is in
 *  progress.
 * TODO: Use SyncWorker to perform periodic sync. (refer to issue #32)
 */
@HiltWorker
class SyncAllWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val restApi: RestApi,
    private val patientManager: PatientManager,
    private val readingManager: ReadingManager,
    private val referralManager: ReferralManager,
    private val assessmentManager: AssessmentManager,
    private val healthFacilityManager: HealthFacilityManager,
    private val formManager: FormManager,
    private val sharedPreferences: SharedPreferences,
    private val database: CradleDatabase
) : CoroutineWorker(context, params) {

    enum class State {
        AFK,
        STARTING,
        /** Checking the server for patients by uploading an empty list */
        CHECKING_SERVER_PATIENTS,
        UPLOADING_PATIENTS,
        DOWNLOADING_PATIENTS,
        /**
         * Downloading the full health facility list from server
         */
        DOWNLOADING_HEALTH_FACILITIES,
        /**
         * Checking the server for new readings, referrals, assessments by uploading an empty list
         */
        CHECKING_SERVER_READINGS,
        UPLOADING_READINGS,
        DOWNLOADING_READINGS,
        /**
         * Checking the server for new referrals by uploading an empty list
         */
        CHECKING_SERVER_REFERRALS,
        UPLOADING_REFERRALS,
        DOWNLOADING_REFERRALS,
        /**
         * Checking the server for new assessments by uploading an empty list
         */
        CHECKING_SERVER_ASSESSMENTS,
        UPLOADING_ASSESSMENTS,
        DOWNLOADING_ASSESSMENTS,
        /**
         * Downloading Form Tempalates from server
         */
        DOWNLOADING_FORM_TEMPLATES,
    }

    companion object {
        private const val TAG = "SyncWorker"

        /** SharedPreferences key for last time patients were synced */
        const val LAST_PATIENT_SYNC = "lastSyncTime"
        /** SharedPreferences key for last time readings were synced */
        const val LAST_READING_SYNC = "lastSyncTimeReadings"
        /** SharedPreferences key for last time referrals were synced */
        const val LAST_REFERRAL_SYNC = "lastSyncTimeReferrals"
        /** SharedPreferences key for last time assessments were synced */
        const val LAST_ASSESSMENT_SYNC = "lastSyncTimeAssessments"
        /** SharedPreferences key for last time assessments were synced */
        const val LAST_HEALTH_FACILITIES_SYNC = "lastSyncTimeHealthFacilities"
        /** Default last sync timestamp. Note that using 0 will result in server rejecting param */
        const val LAST_SYNC_DEFAULT = "1"

        /** The key for current syncing state in the [WorkInfo] progress */
        private const val PROGRESS_CURRENT_STATE = "currentState"
        /** The key for total number to download in the [WorkInfo] progress */
        private const val PROGRESS_TOTAL_NUMBER = "total"
        /** The key for number to downloaded so far in the [WorkInfo] progress */
        private const val PROGRESS_NUMBER_SO_FAR = "number_so_far"
        /** The key for result of the syncing stored in the finished[WorkInfo] */
        private const val RESULT_MESSAGE = "result_message"

        /**
         * Given a [WorkInfo] instance from WorkManager's getWorkInfo* methods for observing
         * intermediate progress, it gets the current syncing state.
         */
        fun getState(workInfo: WorkInfo) = workInfo
            .progress
            .getString(PROGRESS_CURRENT_STATE)?.let { State.valueOf(it) } ?: State.AFK

        /**
         * Get the progress for the current state in the [workInfo]'s progress.
         *
         * The returned [Pair] contains the progress for the current state (e.g., for downloading,
         * it will be the number of patients or readings downloaded out of the total number that are
         * downloaded). The [Pair] will be null if and only if there is no progress with the current
         * state (e.g., there might be nothing to download).
         *
         * For now, there is no uploading progress; only downloading has progress associated with
         * it.
         */
        fun getProgress(workInfo: WorkInfo): Pair<Int, Int>? {
            val progress = workInfo.progress.getInt(PROGRESS_NUMBER_SO_FAR, -1)
            val total = workInfo.progress.getInt(PROGRESS_TOTAL_NUMBER, -1)
            @Suppress("ComplexCondition")
            return if (
                (progress == -1 && total == -1) ||
                // Don't bother showing progress if there's nothing to download.
                (progress == 0 && total == 0)
            ) {
                null
            } else {
                progress to total
            }
        }

        /**
         * Given a *finished* [WorkInfo] instance from WorkManager's getWorkInfo* method, it gets
         * the message of the sync result.
         */
        fun getSyncResultMessage(workInfo: WorkInfo): String? = workInfo.outputData.getString(
            RESULT_MESSAGE
        )
    }

    /**
     * To rate limit download updates.
     * This is needed in order to make the progress reporting work, because the parsing is way
     * too fast for the updates.
     */
    private val rateLimitRunner = RateLimitRunner(seconds = 0.075)

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork()")
        setProgress(workDataOf(PROGRESS_CURRENT_STATE to State.STARTING.name))

        val lastPatientSyncTime = BigInteger(
            sharedPreferences.getString(
                LAST_PATIENT_SYNC,
                LAST_SYNC_DEFAULT
            )!!
        )
        // We only use the timestamp right before the internet call is made.
        // We do not use the timestamp after syncing is done; there could be a case where someone
        // edits a patient or adds a reading etc. during the sync; those changes will never the
        // phone if we only use a timestamp after the sync.
        val syncTimestampToSave: BigInteger = UnixTimestamp.now

        val patientsToUpload: List<Patient> = patientManager.getPatientsToUpload()
        val patientResult = syncPatients(patientsToUpload, lastPatientSyncTime)
        val patientsLeftToUpload = patientManager.getNumberOfPatientsToUpload()
        if (patientsLeftToUpload > 0) {
            patientResult.totalPatientsUploaded -= patientsLeftToUpload
        }

        if (patientResult.networkResult is NetworkResult.Success) {
            sharedPreferences.edit(commit = true) {
                putString(LAST_PATIENT_SYNC, syncTimestampToSave.toString())
            }
            Log.d(TAG, "Patient sync is a success, moving on to syncing readings")
        } else {
            return Result.failure(
                workDataOf(RESULT_MESSAGE to getResultErrorMessage(patientResult.networkResult))
            )
        }

        // Downloading for HealthFacilities
        //
        // HealthFacilities needs to be downloaded before referrals due to HealthFacility.name being
        // a foreign key in referral in the schema
        val lastHealthFacilitiesDownloadTime = BigInteger(
            sharedPreferences.getString(
                LAST_HEALTH_FACILITIES_SYNC,
                LAST_SYNC_DEFAULT
            )!!
        )
        val healthFacilitiesResult = syncHealthFacilities(
            healthFacilityManager.getAllFacilities(),
            lastHealthFacilitiesDownloadTime
        )

        val lastReadingSyncTime = BigInteger(
            sharedPreferences.getString(
                LAST_READING_SYNC,
                LAST_SYNC_DEFAULT
            )!!
        )
        val readingsToUpload = readingManager.getUnUploadedReadings()
        val readingResult = syncReadings(readingsToUpload, lastReadingSyncTime)
        val readingsLeftToUpload = readingManager.getUnUploadedReadings().size
        if (readingsLeftToUpload > 0) {
            readingResult.totalReadingsUploaded -= readingsLeftToUpload

            Log.wtf(
                TAG,
                "There are $readingsLeftToUpload readings left to upload"
            )
            if (readingResult.networkResult is NetworkResult.Success) {
                Log.wtf(TAG, "successful reading sync but still readings left unsynced")
                // https://csil-git1.cs.surrey.sfu.ca/415-cradle/cradle-platform/-/blob/master/server/api/resources/sync.py#L97-103
                //  The only reasons why a reading might still not be uploaded but the response from
                //  server is still successful is that:
                //  The server will skip readings that are for non-existent patients.
                //  although we should note this usually never happens, because it requires
                //  the Android client to somehow upload a because patients on Android are
                //  synced first before reading sync. Also, any patients that are sent
                //  through SMS are still treated as unuploaded in case the SMS did not
                //  actually reach the server.
            }
        }

        if (readingResult.networkResult is NetworkResult.Success) {
            sharedPreferences.edit(commit = true) {
                putString(LAST_READING_SYNC, syncTimestampToSave.toString())
            }
        } else {
            return Result.failure(
                workDataOf(RESULT_MESSAGE to getResultErrorMessage(readingResult.networkResult))
            )
        }

        // for referrals
        val lastReferralSyncTime = BigInteger(
            sharedPreferences.getString(
                LAST_REFERRAL_SYNC,
                LAST_SYNC_DEFAULT
            )!!
        )

        val referralsToUpload = referralManager.getReferralsToUpload()
        val referralResult = syncReferrals(referralsToUpload, lastReferralSyncTime)
        val referralsLeftToUpload = referralManager.getReferralsToUpload().size
        if (referralsLeftToUpload > 0) {
            referralResult.totalReferralsUploaded -= referralsLeftToUpload

            Log.wtf(
                TAG,
                "There are $referralsLeftToUpload referrals left to upload"
            )
        }

        if (referralResult.networkResult is NetworkResult.Success) {
            sharedPreferences.edit(commit = true) {
                putString(LAST_REFERRAL_SYNC, syncTimestampToSave.toString())
            }
        } else {
            return Result.failure(
                workDataOf(RESULT_MESSAGE to getResultErrorMessage(referralResult.networkResult))
            )
        }

        // for assessments
        val lastAssessmentSyncTime = BigInteger(
            sharedPreferences.getString(
                LAST_ASSESSMENT_SYNC,
                LAST_SYNC_DEFAULT
            )!!
        )
        val assessmentsToUpload = assessmentManager.getAssessmentsToUpload()
        val assessmentResult = syncAssessments(assessmentsToUpload, lastAssessmentSyncTime)
        val assessmentsLeftToUpload = assessmentManager.getAssessmentsToUpload().size
        if (assessmentsLeftToUpload > 0) {
            assessmentResult.totalAssessmentsUploaded -= assessmentsLeftToUpload

            Log.wtf(
                TAG,
                "There are $assessmentsLeftToUpload assessments left to upload"
            )
        }

        if (assessmentResult.networkResult is NetworkResult.Success) {
            sharedPreferences.edit(commit = true) {
                putString(LAST_ASSESSMENT_SYNC, syncTimestampToSave.toString())
            }
        } else {
            return Result.failure(
                workDataOf(RESULT_MESSAGE to getResultErrorMessage(assessmentResult.networkResult))
            )
        }

        val formTemplateResult = syncFormTemplates()
        if (formTemplateResult.networkResult !is NetworkResult.Success) {
            return Result.failure(
                workDataOf(RESULT_MESSAGE to getResultErrorMessage(formTemplateResult.networkResult))
            )
        }

        return Result.success(
            workDataOf(
                RESULT_MESSAGE to
                    getResultSuccessMessage(
                        patientResult,
                        healthFacilitiesResult,
                        readingResult,
                        referralResult,
                        assessmentResult,
                        formTemplateResult
                    )
            )
        )
    }

    private suspend fun syncPatients(
        patientsToUpload: List<Patient>,
        lastSyncTime: BigInteger
    ): PatientSyncResult = withContext(Dispatchers.Default) {
        setProgress(
            if (patientsToUpload.isEmpty()) {
                workDataOf(PROGRESS_CURRENT_STATE to State.CHECKING_SERVER_PATIENTS.name)
            } else {
                workDataOf(PROGRESS_CURRENT_STATE to State.UPLOADING_PATIENTS.name)
            }
        )
        Log.d(TAG, "preparing to upload ${patientsToUpload.size} patients")
        val channel = Channel<Patient>()
        launch {
            try {
                database.withTransaction {
                    for (patient in channel) {
                        patientManager.add(patient)
                    }
                }
            } catch (e: SyncException) {
                // Need to switch context, since Dispatchers.Default doesn't do logging
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "patients sync failed", e)
                }
            }
            withContext(Dispatchers.Main) { Log.d(TAG, "patients job done") }
        }

        restApi.syncPatients(
            patientsToUpload,
            lastSyncTimestamp = lastSyncTime,
            patientChannel = channel
        ) { current, total ->
            reportProgress(
                state = State.DOWNLOADING_PATIENTS,
                progress = current,
                total = total,
            )
        }
    }

    private suspend fun syncReadings(
        readingsToUpload: List<Reading>,
        lastSyncTime: BigInteger
    ): ReadingSyncResult = withContext(Dispatchers.Default) {
        Log.d(TAG, "preparing to upload ${readingsToUpload.size} readings")
        setProgress(
            if (readingsToUpload.isEmpty()) {
                workDataOf(PROGRESS_CURRENT_STATE to State.CHECKING_SERVER_READINGS.name)
            } else {
                workDataOf(PROGRESS_CURRENT_STATE to State.UPLOADING_READINGS.name)
            }
        )

        val readingChannel = Channel<Reading>()

        launch {
            try {
                database.withTransaction {
                    for (reading in readingChannel) {
                        readingManager.addReading(reading, isReadingFromServer = true)
                    }
                }
            } catch (e: SyncException) {
                // Need to switch context, since Dispatchers.Default doesn't do logging
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "reading sync failed", e)
                }
            }
        }

        restApi.syncReadings(
            readingsToUpload,
            lastSyncTimestamp = lastSyncTime,
            readingChannel
        ) { current, total ->
            reportProgress(
                State.DOWNLOADING_READINGS,
                progress = current,
                total = total,
            )
        }
    }

    private suspend fun syncReferrals(
        referralsToUpload: List<Referral>,
        lastSyncTime: BigInteger
    ): ReferralSyncResult = withContext(Dispatchers.Default) {
        setProgress(
            if (referralsToUpload.isEmpty()) {
                workDataOf(PROGRESS_CURRENT_STATE to State.CHECKING_SERVER_REFERRALS.name)
            } else {
                workDataOf(PROGRESS_CURRENT_STATE to State.UPLOADING_REFERRALS.name)
            }
        )
        Log.d(TAG, "preparing to upload ${referralsToUpload.size} referrals")
        val channel = Channel<Referral>()
        launch {
            try {
                database.withTransaction {
                    for (referral in channel) {
                        referralManager.addReferral(referral, true)
                    }
                }
            } catch (e: SyncException) {
                // Need to switch context, since Dispatchers.Default doesn't do logging
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "referrals sync failed", e)
                }
            }
            withContext(Dispatchers.Main) { Log.d(TAG, "referrals job done") }
        }

        restApi.syncReferrals(
            referralsToUpload,
            lastSyncTimestamp = lastSyncTime,
            referralChannel = channel
        ) { current, total ->
            reportProgress(
                state = State.DOWNLOADING_REFERRALS,
                progress = current,
                total = total,
            )
        }
    }

    private suspend fun syncAssessments(
        assessmentsToUpload: List<Assessment>,
        lastSyncTime: BigInteger
    ): AssessmentSyncResult = withContext(Dispatchers.Default) {
        setProgress(
            if (assessmentsToUpload.isEmpty()) {
                workDataOf(PROGRESS_CURRENT_STATE to State.CHECKING_SERVER_ASSESSMENTS.name)
            } else {
                workDataOf(PROGRESS_CURRENT_STATE to State.UPLOADING_ASSESSMENTS.name)
            }
        )
        Log.d(TAG, "preparing to upload ${assessmentsToUpload.size} assessments")
        val channel = Channel<Assessment>()
        launch {
            try {
                database.withTransaction {
                    for (assessment in channel) {
                        assessmentManager.addAssessment(assessment, true)
                    }
                }
            } catch (e: SyncException) {
                // Need to switch context, since Dispatchers.Default doesn't do logging
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "assessments sync failed", e)
                }
            }
            withContext(Dispatchers.Main) { Log.d(TAG, "assessments job done") }
        }

        restApi.syncAssessments(
            assessmentsToUpload,
            lastSyncTimestamp = lastSyncTime,
            assessmentChannel = channel
        ) { current, total ->
            reportProgress(
                state = State.DOWNLOADING_ASSESSMENTS,
                progress = current,
                total = total,
            )
        }
    }

    private suspend fun syncHealthFacilities(
        currentHealthFacilities: List<HealthFacility>,
        lastSyncTime: BigInteger
    ): HealthFacilitySyncResult = withContext(Dispatchers.Default) {

        val currentHealthFacilitiesNames = currentHealthFacilities.map { it.name }
        val channel = Channel<HealthFacility>()
        launch {
            try {
                database.withTransaction {
                    for (healthFacility in channel) {

                        if (!currentHealthFacilitiesNames.contains(healthFacility.name)) {
                            // new facility to be added, selects by default
                            healthFacility.isUserSelected = true
                            healthFacilityManager.add(healthFacility)
                        } else {
                            // facility already exists in local database
                        }
                    }
                }
            } catch (e: SyncException) {
                Log.e(TAG, "Failed to add health facility during Sync, with error:\n $e")
            }
            withContext(Dispatchers.Main) { Log.d(TAG, "health facilities sync job is done") }
        }

        restApi.syncHealthFacilities(
            channel,
            lastSyncTime
        ) { current, total ->
            reportProgress(
                state = State.DOWNLOADING_HEALTH_FACILITIES,
                progress = current,
                total = total,
            )
        }
    }

    private suspend fun syncFormTemplates(): FormSyncResult =
        withContext(Dispatchers.Default) {
            val channel = Channel<FormClassification>()
            launch {
                try {
                    database.withTransaction {
                        for (formClassification in channel) {
                            formManager.addFormByClassification(formClassification)
                        }
                    }
                } catch (e: SyncException) {
                    Log.e(TAG, "Failed to add form template during Sync, with error:\n $e")
                }
                withContext(Dispatchers.Main) { Log.d(TAG, "form template sync job is done") }
            }

            restApi.getAllFormTemplates(channel) { current, total ->
                reportProgress(
                    state = State.DOWNLOADING_HEALTH_FACILITIES,
                    progress = current,
                    total = total,
                )
            }
        }

    private suspend fun reportProgress(
        state: State,
        progress: Int,
        total: Int,
        bypassRateLimit: Boolean = false
    ) {
        if (bypassRateLimit) {
            setProgress(
                workDataOf(
                    PROGRESS_CURRENT_STATE to state.name,
                    PROGRESS_NUMBER_SO_FAR to progress,
                    PROGRESS_TOTAL_NUMBER to total
                )
            )
        } else {
            rateLimitRunner.runSuspend {
                setProgress(
                    workDataOf(
                        PROGRESS_CURRENT_STATE to state.name,
                        PROGRESS_NUMBER_SO_FAR to progress,
                        PROGRESS_TOTAL_NUMBER to total
                    )
                )
            }
        }
    }

    private fun getResultErrorMessage(networkResult: NetworkResult<Unit>): String {
        return when (networkResult) {
            is NetworkResult.Failure -> applicationContext.getString(
                R.string.sync_worker_failure_server_sent_error_code_d__s,
                networkResult.statusCode,
                networkResult.getStatusMessage(applicationContext)
            )
            is NetworkResult.NetworkException -> applicationContext.getString(
                R.string.sync_worker_failure_exception_during_sync_s__s,
                networkResult.cause::class.java.simpleName,
                networkResult.cause.message
            )
            is NetworkResult.Success -> applicationContext.getString(R.string.sync_worker_success)
        }
    }

    private fun getResultSuccessMessage(
        patientSyncResult: PatientSyncResult,
        healthFacilitySyncResult: HealthFacilitySyncResult,
        readingSyncResult: ReadingSyncResult,
        referralSyncResult: ReferralSyncResult,
        assessmentSyncResult: AssessmentSyncResult,
        formTemplateSyncResult: FormSyncResult
    ): String {
        val success = patientSyncResult.networkResult.getStatusMessage(applicationContext)
        val totalPatientsUploaded = applicationContext.getString(
            R.string.sync_total_patients_uploaded_s, patientSyncResult.totalPatientsUploaded
        )
        val totalPatientsDownloaded = applicationContext.getString(
            R.string.sync_total_patients_downloaded_s, patientSyncResult.totalPatientsDownloaded
        )
        val totalHealthFacilitiesDownloaded = applicationContext.getString(
            R.string.sync_total_HealthFacilities_downloaded_s,
            healthFacilitySyncResult.totalHealthFacilitiesDownloaded
        )
        val totalReadingsUploaded = applicationContext.getString(
            R.string.sync_total_readings_uploaded_s, readingSyncResult.totalReadingsUploaded
        )
        val totalReadingsDownloaded = applicationContext.getString(
            R.string.sync_total_readings_downloaded_s, readingSyncResult.totalReadingsDownloaded
        )
        val totalReferralsUploaded = applicationContext.getString(
            R.string.sync_total_referrals_uploaded_s, referralSyncResult.totalReferralsUploaded
        )
        val totalReferralsDownloaded = applicationContext.getString(
            R.string.sync_total_referrals_downloaded_s, referralSyncResult.totalReferralsDownloaded
        )
        val totalAssessmentsUploaded = applicationContext.getString(
            R.string.sync_total_assessments_uploaded_s, assessmentSyncResult.totalAssessmentsUploaded
        )
        val totalAssessmentsDownloaded = applicationContext.getString(
            R.string.sync_total_assessments_downloaded_s, assessmentSyncResult.totalAssessmentsDownloaded
        )
        val totalFormsDownloaded = applicationContext.getString(
            R.string.sync_total_form_templates_downloaded, formTemplateSyncResult.totalFormClassDownloaded
        )

        val errors = patientSyncResult.errors.let { if (it != "[ ]") "\nErrors:\n$it" else "" }
        return "$success\n" +
            "$totalPatientsUploaded\n" +
            "$totalPatientsDownloaded\n" +
            "$totalHealthFacilitiesDownloaded\n" +
            "$totalReadingsUploaded\n" +
            "$totalReadingsDownloaded\n" +
            "$totalReferralsUploaded\n" +
            "$totalReferralsDownloaded\n" +
            "$totalAssessmentsUploaded\n" +
            "$totalAssessmentsDownloaded\n" +
            "$totalFormsDownloaded\n" +
            errors
    }
}

enum class PatientSyncField(override val text: String) : Field {
    PATIENTS("patients"),
    ERRORS("errors"),
}

enum class ReadingSyncField(override val text: String) : Field {
    READINGS("readings"),
    NEW_REFERRALS("newReferrals"),
    NEW_FOLLOW_UPS("newFollowups"),
}

enum class ReferralSyncField(override val text: String) : Field {
    REFERRALS("referrals"),
    ERRORS("errors")
}

enum class AssessmentSyncField(override val text: String) : Field {
    ASSESSMENTS("assessments"),
    ERRORS("errors")
}
