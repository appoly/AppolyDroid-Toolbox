package uk.co.appoly.droid.s3upload.multipart.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadResult
import uk.co.appoly.droid.s3upload.multipart.utils.MultipartUploadLog
import java.io.File

/**
 * WorkManager worker for executing multipart uploads in the background.
 *
 * This worker handles:
 * - Starting new uploads
 * - Resuming existing uploads
 * - Progress reporting
 * - Foreground service notification
 */
class MultipartUploadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Sync logger config from S3Uploader before any logging
        MultipartUploadManager.syncLoggerConfig()

        val sessionId = inputData.getString(KEY_SESSION_ID)
        val filePath = inputData.getString(KEY_FILE_PATH)
        val isResume = inputData.getBoolean(KEY_IS_RESUME, false)

        MultipartUploadLog.d(this@MultipartUploadWorker, "Starting work: sessionId=$sessionId, isResume=$isResume")

        try {
            // Set foreground for long-running upload
            setForeground(createForegroundInfo(sessionId ?: "upload"))

            val manager = MultipartUploadManager.getInstance(context)

            val result = if (isResume && sessionId != null) {
                // Resume existing upload
                manager.resumeUpload(sessionId)
                if (manager.resumeUpload(sessionId).isSuccess) {
                    manager.executeUpload(sessionId)
                } else {
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR_MESSAGE to "Failed to resume upload")
                    )
                }
            } else if (filePath != null) {
                // Start new upload
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR_MESSAGE to "File not found: $filePath")
                    )
                }

                val apiUrls = MultipartApiUrls(
                    initiateUrl = inputData.getString(KEY_INITIATE_URL) ?: return@withContext Result.failure(),
                    presignPartUrl = inputData.getString(KEY_PRESIGN_URL) ?: return@withContext Result.failure(),
                    completeUrl = inputData.getString(KEY_COMPLETE_URL) ?: return@withContext Result.failure(),
                    abortUrl = inputData.getString(KEY_ABORT_URL) ?: return@withContext Result.failure()
                )

                val startResult = manager.startUpload(file, apiUrls)
                if (startResult.isFailure) {
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR_MESSAGE to (startResult.exceptionOrNull()?.message ?: "Failed to start upload"))
                    )
                }

                val newSessionId = startResult.getOrThrow()
                manager.executeUpload(newSessionId)
            } else {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "No session ID or file path provided")
                )
            }

            // Handle result
            when (result) {
                is MultipartUploadResult.Success -> {
                    MultipartUploadLog.d(this@MultipartUploadWorker, "Upload completed: ${result.filePath}")
                    Result.success(
                        workDataOf(
                            KEY_SESSION_ID to result.sessionId,
                            KEY_FILE_PATH to result.filePath,
                            KEY_LOCATION to result.location
                        )
                    )
                }

                is MultipartUploadResult.Paused -> {
                    MultipartUploadLog.d(this@MultipartUploadWorker, "Upload paused: ${result.uploadedParts}/${result.totalParts}")
                    // Return success but indicate it's paused - can be resumed later
                    Result.success(
                        workDataOf(
                            KEY_SESSION_ID to result.sessionId,
                            KEY_IS_PAUSED to true,
                            KEY_UPLOADED_PARTS to result.uploadedParts,
                            KEY_TOTAL_PARTS to result.totalParts
                        )
                    )
                }

                is MultipartUploadResult.Error -> {
                    MultipartUploadLog.e(this@MultipartUploadWorker, "Upload failed: ${result.message}")
                    if (result.isRecoverable && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                        Result.retry()
                    } else {
                        Result.failure(
                            workDataOf(
                                KEY_SESSION_ID to result.sessionId,
                                KEY_ERROR_MESSAGE to result.message
                            )
                        )
                    }
                }

                is MultipartUploadResult.Cancelled -> {
                    MultipartUploadLog.d(this@MultipartUploadWorker, "Upload cancelled: ${result.sessionId}")
                    Result.failure(
                        workDataOf(
                            KEY_SESSION_ID to result.sessionId,
                            KEY_IS_CANCELLED to true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            MultipartUploadLog.e(this@MultipartUploadWorker, "Worker failed", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error"))
                )
            }
        }
    }

    private fun createForegroundInfo(sessionId: String): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Uploading file")
            .setContentText("Upload in progress...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                sessionId.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(sessionId.hashCode(), notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "S3 Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of file uploads"
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_INITIATE_URL = "initiate_url"
        const val KEY_PRESIGN_URL = "presign_url"
        const val KEY_COMPLETE_URL = "complete_url"
        const val KEY_ABORT_URL = "abort_url"
        const val KEY_IS_RESUME = "is_resume"
        const val KEY_IS_PAUSED = "is_paused"
        const val KEY_IS_CANCELLED = "is_cancelled"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_LOCATION = "location"
        const val KEY_UPLOADED_PARTS = "uploaded_parts"
        const val KEY_TOTAL_PARTS = "total_parts"

        private const val CHANNEL_ID = "s3_upload_channel"
        private const val MAX_RETRY_ATTEMPTS = 3

        /**
         * Creates input data for starting a new upload.
         */
        fun createInputData(
            file: File,
            apiUrls: MultipartApiUrls
        ): Data = workDataOf(
            KEY_FILE_PATH to file.absolutePath,
            KEY_INITIATE_URL to apiUrls.initiateUrl,
            KEY_PRESIGN_URL to apiUrls.presignPartUrl,
            KEY_COMPLETE_URL to apiUrls.completeUrl,
            KEY_ABORT_URL to apiUrls.abortUrl,
            KEY_IS_RESUME to false
        )

        /**
         * Creates input data for resuming an existing upload.
         */
        fun createResumeInputData(sessionId: String): Data = workDataOf(
            KEY_SESSION_ID to sessionId,
            KEY_IS_RESUME to true
        )
    }
}
