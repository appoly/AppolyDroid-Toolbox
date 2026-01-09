package uk.co.appoly.droid.s3upload.multipart.worker

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Helper object for scheduling and managing multipart upload work.
 *
 * Provides convenient methods for:
 * - Scheduling new uploads
 * - Resuming paused uploads
 * - Cancelling uploads
 * - Observing upload progress
 * - Enabling automatic recovery
 */
object S3UploadWorkManager {

    private const val UPLOAD_WORK_PREFIX = "multipart_upload_"
    private const val RECOVERY_WORK_NAME = "multipart_upload_recovery"
    private const val MIN_BACKOFF_MILLIS = 10_000L // 10 seconds

    /**
     * Schedules a new multipart upload.
     *
     * @param context Application context
     * @param file The file to upload
     * @param apiUrls API endpoints for multipart operations
     * @param requiresNetwork Whether to require network connectivity (default true)
     * @param requiresCharging Whether to require device charging (default false)
     * @return Unique work name that can be used to track the upload
     */
    fun scheduleUpload(
        context: Context,
        file: File,
        apiUrls: MultipartApiUrls,
        requiresNetwork: Boolean = true,
        requiresCharging: Boolean = false
    ): String {
        val workName = "${UPLOAD_WORK_PREFIX}${UUID.randomUUID()}"

        val constraints = Constraints.Builder().apply {
            if (requiresNetwork) {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
            setRequiresCharging(requiresCharging)
        }.build()

        val workRequest = OneTimeWorkRequestBuilder<MultipartUploadWorker>()
            .setInputData(MultipartUploadWorker.createInputData(file, apiUrls))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_MULTIPART_UPLOAD)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,
                workRequest
            )

        return workName
    }

    /**
     * Schedules resumption of an existing upload.
     *
     * @param context Application context
     * @param sessionId The session ID to resume
     * @return Unique work name
     */
    fun scheduleResume(
        context: Context,
        sessionId: String
    ): String {
        val workName = "${UPLOAD_WORK_PREFIX}resume_$sessionId"

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MultipartUploadWorker>()
            .setInputData(MultipartUploadWorker.createResumeInputData(sessionId))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_MULTIPART_UPLOAD)
            .addTag("session_$sessionId")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

        return workName
    }

    /**
     * Cancels upload work by work name.
     *
     * @param context Application context
     * @param workName The work name returned from scheduleUpload
     */
    fun cancelByWorkName(context: Context, workName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }

    /**
     * Cancels all work for a specific session.
     *
     * @param context Application context
     * @param sessionId The session ID
     */
    fun cancelBySessionId(context: Context, sessionId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("session_$sessionId")
    }

    /**
     * Cancels all multipart upload work.
     *
     * @param context Application context
     */
    fun cancelAllUploads(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_MULTIPART_UPLOAD)
    }

    /**
     * Observes the work info for a specific upload.
     *
     * @param context Application context
     * @param workName The work name returned from scheduleUpload
     * @return LiveData of work info list
     */
    fun observeWorkInfo(context: Context, workName: String): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(workName)
    }

    /**
     * Observes all multipart upload work.
     *
     * @param context Application context
     * @return LiveData of all upload work info
     */
    fun observeAllUploads(context: Context): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(TAG_MULTIPART_UPLOAD)
    }

    /**
     * Enables automatic recovery of interrupted uploads.
     *
     * This schedules a periodic worker that:
     * - Checks for interrupted uploads every 15 minutes
     * - Re-enqueues them for completion
     * - Cleans up old completed sessions
     *
     * @param context Application context
     */
    fun enableAutoRecovery(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val recoveryRequest = PeriodicWorkRequestBuilder<UploadRecoveryWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(TAG_RECOVERY)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                RECOVERY_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                recoveryRequest
            )
    }

    /**
     * Disables automatic recovery.
     *
     * @param context Application context
     */
    fun disableAutoRecovery(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(RECOVERY_WORK_NAME)
    }

    /**
     * Runs recovery check immediately.
     *
     * @param context Application context
     * @return UUID of the work request
     */
    fun runRecoveryNow(context: Context): UUID {
        val workRequest = OneTimeWorkRequestBuilder<UploadRecoveryWorker>()
            .addTag(TAG_RECOVERY)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }

    /**
     * Gets pending and running upload count.
     *
     * @param context Application context
     * @return LiveData of count
     */
    fun getActiveUploadCount(context: Context): LiveData<Int> {
        return WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(TAG_MULTIPART_UPLOAD)
            .map { workInfoList ->
                workInfoList.count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            }
    }

    // Tags for work identification
    const val TAG_MULTIPART_UPLOAD = "multipart_upload"
    const val TAG_RECOVERY = "multipart_recovery"
}
