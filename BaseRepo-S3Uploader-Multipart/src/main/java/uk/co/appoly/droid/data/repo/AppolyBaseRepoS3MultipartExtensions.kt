package uk.co.appoly.droid.data.repo

import android.content.Context
import kotlinx.coroutines.flow.Flow
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadResult
import uk.co.appoly.droid.s3upload.multipart.worker.S3UploadWorkManager
import java.io.File

/**
 * Starts a multipart upload for large files with pause/resume/recovery support.
 *
 * This extension function provides a resumable upload mechanism using AWS S3 Multipart Upload.
 * The upload state is persisted to allow recovery after app crashes or device restarts.
 *
 * Example usage:
 * ```
 * val result = startMultipartUpload(
 *     context = applicationContext,
 *     file = File("/path/to/large-video.mp4"),
 *     apiUrls = MultipartApiUrls.fromBaseUrl("https://api.example.com/api/s3/multipart")
 * )
 * when (result) {
 *     is APIResult.Success -> println("Session ID: ${result.data}")
 *     is APIResult.Error -> println("Error: ${result.message}")
 * }
 * ```
 *
 * @param context Application context (needed for database and WorkManager)
 * @param file The file to upload to S3
 * @param apiUrls API endpoints for multipart operations
 * @param config Optional upload configuration (chunk size, concurrency, retries)
 * @return [APIResult.Success] with session ID if upload started successfully, or [APIResult.Error] if failed
 */
suspend fun GenericBaseRepo.startMultipartUpload(
    context: Context,
    file: File,
    apiUrls: MultipartApiUrls,
    config: MultipartUploadConfig = MultipartUploadConfig.DEFAULT
): APIResult<String> {
    val manager = MultipartUploadManager.getInstance(context, config)
    val result = manager.startUpload(file, apiUrls)

    return result.fold(
        onSuccess = { sessionId -> APIResult.Success(sessionId) },
        onFailure = { error -> APIResult.Error(error.message ?: "Failed to start upload", error) }
    )
}

/**
 * Pauses an active multipart upload.
 *
 * The upload can be resumed later using [resumeMultipartUpload].
 * Progress is persisted, so the upload will continue from where it left off.
 *
 * @param context Application context
 * @param sessionId The session ID returned from [startMultipartUpload]
 * @return [APIResult.Success] if paused successfully, or [APIResult.Error] if failed
 */
suspend fun GenericBaseRepo.pauseMultipartUpload(
    context: Context,
    sessionId: String
): APIResult<Unit> {
    val manager = MultipartUploadManager.getInstance(context)
    val result = manager.pauseUpload(sessionId)

    return result.fold(
        onSuccess = { APIResult.Success(Unit) },
        onFailure = { error -> APIResult.Error(error.message ?: "Failed to pause upload", error) }
    )
}

/**
 * Resumes a paused or recovered multipart upload.
 *
 * @param context Application context
 * @param sessionId The session ID to resume
 * @return [APIResult.Success] if resumed successfully, or [APIResult.Error] if failed
 */
suspend fun GenericBaseRepo.resumeMultipartUpload(
    context: Context,
    sessionId: String
): APIResult<Unit> {
    val manager = MultipartUploadManager.getInstance(context)
    val result = manager.resumeUpload(sessionId)

    return result.fold(
        onSuccess = { APIResult.Success(Unit) },
        onFailure = { error -> APIResult.Error(error.message ?: "Failed to resume upload", error) }
    )
}

/**
 * Cancels and aborts a multipart upload.
 *
 * This will abort the upload with S3 and clean up any uploaded parts.
 * The upload cannot be resumed after cancellation.
 *
 * @param context Application context
 * @param sessionId The session ID to cancel
 * @return [APIResult.Success] if cancelled successfully, or [APIResult.Error] if failed
 */
suspend fun GenericBaseRepo.cancelMultipartUpload(
    context: Context,
    sessionId: String
): APIResult<Unit> {
    val manager = MultipartUploadManager.getInstance(context)
    val result = manager.cancelUpload(sessionId)

    return result.fold(
        onSuccess = { APIResult.Success(Unit) },
        onFailure = { error -> APIResult.Error(error.message ?: "Failed to cancel upload", error) }
    )
}

/**
 * Observes the progress of a multipart upload.
 *
 * @param context Application context
 * @param sessionId The session ID to observe
 * @return Flow of [MultipartUploadProgress] updates
 */
fun GenericBaseRepo.observeMultipartUploadProgress(
    context: Context,
    sessionId: String
): Flow<MultipartUploadProgress?> {
    val manager = MultipartUploadManager.getInstance(context)
    return manager.observeProgress(sessionId)
}

/**
 * Observes all active multipart uploads.
 *
 * @param context Application context
 * @return Flow of all active upload progress
 */
fun GenericBaseRepo.observeAllMultipartUploads(
    context: Context
): Flow<List<MultipartUploadProgress>> {
    val manager = MultipartUploadManager.getInstance(context)
    return manager.observeAllUploads()
}

/**
 * Schedules a multipart upload using WorkManager.
 *
 * This is the recommended approach for production apps as it:
 * - Handles network connectivity changes
 * - Survives app restarts
 * - Runs in the background with proper system resource management
 *
 * Example usage:
 * ```
 * val workName = scheduleMultipartUploadWork(
 *     context = applicationContext,
 *     file = File("/path/to/large-video.mp4"),
 *     apiUrls = MultipartApiUrls.fromBaseUrl("https://api.example.com/api/s3/multipart")
 * )
 * // Observe work progress using WorkManager APIs
 * ```
 *
 * @param context Application context
 * @param file The file to upload
 * @param apiUrls API endpoints for multipart operations
 * @param requiresNetwork Whether to require network connectivity (default true)
 * @param requiresCharging Whether to require device charging (default false)
 * @return Work name that can be used to track or cancel the upload
 */
fun GenericBaseRepo.scheduleMultipartUploadWork(
    context: Context,
    file: File,
    apiUrls: MultipartApiUrls,
    requiresNetwork: Boolean = true,
    requiresCharging: Boolean = false
): String {
    return S3UploadWorkManager.scheduleUpload(
        context = context,
        file = file,
        apiUrls = apiUrls,
        requiresNetwork = requiresNetwork,
        requiresCharging = requiresCharging
    )
}

/**
 * Enables automatic recovery of interrupted uploads.
 *
 * This schedules a periodic worker that checks for interrupted uploads
 * and automatically resumes them when network is available.
 *
 * @param context Application context
 */
fun GenericBaseRepo.enableMultipartUploadAutoRecovery(context: Context) {
    S3UploadWorkManager.enableAutoRecovery(context)
}

/**
 * Converts a [MultipartUploadResult] to [APIResult].
 *
 * @return [APIResult.Success] with file path for successful uploads,
 *         or [APIResult.Error] for failures/cancellations
 */
fun MultipartUploadResult.toAPIResult(): APIResult<String> {
    return when (this) {
        is MultipartUploadResult.Success -> APIResult.Success(filePath)
        is MultipartUploadResult.Error -> APIResult.Error(message, throwable)
        is MultipartUploadResult.Paused -> APIResult.Error("Upload paused: $uploadedParts/$totalParts parts uploaded")
        is MultipartUploadResult.Cancelled -> APIResult.Error("Upload cancelled")
    }
}
