package uk.co.appoly.droid.data.repo

import android.content.Context
import kotlinx.coroutines.flow.Flow
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints
import uk.co.appoly.droid.s3upload.multipart.config.UploadNetworkType
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadResult
import uk.co.appoly.droid.s3upload.multipart.worker.S3UploadWorkManager
import java.io.File

/**
 * Result data for a successful multipart upload.
 *
 * @property sessionId The library's internal session ID
 * @property filePath The S3 file path (key) where the file was stored
 * @property location The full S3 URL, if provided by the server
 */
data class MultipartUploadSuccess(
	val sessionId: String,
	val filePath: String,
	val location: String?,
)

/**
 * Converts a [kotlin.Result] of [Unit] to [APIResult].
 */
private fun Result<Unit>.toUnitAPIResult(defaultErrorMessage: String): APIResult<Unit> {
	return if (isSuccess) {
		APIResult.Success(Unit)
	} else {
		val error = exceptionOrNull()
		APIResult.Error(error?.message ?: defaultErrorMessage, error)
	}
}

/**
 * Starts a multipart upload for large files with pause/resume/recovery support.
 *
 * This is an inline (suspending) upload that blocks until the upload completes.
 * For background uploads that survive app restarts, use [scheduleMultipartUploadWork] instead.
 *
 * The upload state is persisted to allow recovery after app crashes or device restarts.
 * Progress can be observed via [observeMultipartUploadProgress] using the session ID
 * from [MultipartUploadSuccess.sessionId].
 *
 * Example usage:
 * ```
 * val result = startMultipartUpload(
 *     context = applicationContext,
 *     file = File("/path/to/large-video.mp4"),
 *     apiUrls = MultipartApiUrls.fromBaseUrl("https://api.example.com/api/s3/multipart")
 * )
 * when (result) {
 *     is APIResult.Success -> {
 *         val s3Path = result.data.location ?: result.data.filePath
 *         // Register with backend using s3Path
 *     }
 *     is APIResult.Error -> println("Error: ${result.message}")
 * }
 * ```
 *
 * @param context Application context (needed for database and WorkManager)
 * @param file The file to upload to S3
 * @param apiUrls API endpoints for multipart operations
 * @param config Optional upload configuration (chunk size, concurrency, retries)
 * @return [APIResult.Success] with [MultipartUploadSuccess] containing the S3 path and location,
 *         or [APIResult.Error] if the upload failed or was cancelled
 */
suspend fun GenericBaseRepo.startMultipartUpload(
	context: Context,
	file: File,
	apiUrls: MultipartApiUrls,
	config: MultipartUploadConfig = MultipartUploadConfig.DEFAULT
): APIResult<MultipartUploadSuccess> {
	val manager = MultipartUploadManager.getInstance(context, config)
	return when (val result = manager.startUpload(file, apiUrls)) {
		is MultipartUploadResult.Success -> APIResult.Success(
			MultipartUploadSuccess(
				sessionId = result.sessionId,
				filePath = result.filePath,
				location = result.location,
			)
		)
		is MultipartUploadResult.Error -> APIResult.Error(result.message, result.throwable)
		is MultipartUploadResult.Paused -> APIResult.Error(
			"Upload paused: ${result.uploadedParts}/${result.totalParts} parts uploaded"
		)
		is MultipartUploadResult.Cancelled -> APIResult.Error("Upload was cancelled")
	}
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
	return manager.pauseUpload(sessionId).toUnitAPIResult("Failed to pause upload")
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
	return manager.resumeUpload(sessionId).toUnitAPIResult("Failed to resume upload")
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
	return manager.cancelUpload(sessionId).toUnitAPIResult("Failed to cancel upload")
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
 * Schedules a multipart upload using WorkManager with full constraint support.
 *
 * This is the recommended approach for production apps as it:
 * - Handles network connectivity changes
 * - Survives app restarts
 * - Runs in the background with proper system resource management
 * - Supports WiFi-only, battery, and charging constraints
 *
 * Example usage:
 * ```
 * // With default constraints (any network)
 * val workName = scheduleMultipartUploadWork(
 *     context = applicationContext,
 *     file = File("/path/to/large-video.mp4"),
 *     apiUrls = MultipartApiUrls.fromBaseUrl("https://api.example.com/api/s3/multipart")
 * )
 *
 * // With WiFi-only constraint
 * val workName = scheduleMultipartUploadWork(
 *     context = applicationContext,
 *     file = file,
 *     apiUrls = apiUrls,
 *     constraints = UploadConstraints.wifiOnly()
 * )
 * ```
 *
 * @param context Application context
 * @param file The file to upload
 * @param apiUrls API endpoints for multipart operations
 * @param constraints Upload constraints (network type, charging, battery, storage).
 *                    If null, uses default constraints from [MultipartUploadManager.config].
 * @return Work name that can be used to track or cancel the upload
 */
fun GenericBaseRepo.scheduleMultipartUploadWork(
	context: Context,
	file: File,
	apiUrls: MultipartApiUrls,
	constraints: UploadConstraints? = null
): String {
	return S3UploadWorkManager.scheduleUpload(
		context = context,
		file = file,
		apiUrls = apiUrls,
		constraints = constraints
	)
}

/**
 * Schedules a multipart upload using WorkManager with simple network/charging options.
 *
 * This is a convenience overload for common use cases. For full constraint support
 * including WiFi-only, battery, and storage constraints, use the overload that
 * accepts [UploadConstraints].
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
	val constraints = UploadConstraints(
		networkType = if (requiresNetwork) UploadNetworkType.CONNECTED else UploadNetworkType.NOT_REQUIRED,
		requiresCharging = requiresCharging
	)
	return S3UploadWorkManager.scheduleUpload(
		context = context,
		file = file,
		apiUrls = apiUrls,
		constraints = constraints
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
 * @return [APIResult.Success] with [MultipartUploadSuccess] for successful uploads,
 *         or [APIResult.Error] for failures/cancellations/paused
 */
fun MultipartUploadResult.toAPIResult(): APIResult<MultipartUploadSuccess> {
	return when (this) {
		is MultipartUploadResult.Success -> APIResult.Success(
			MultipartUploadSuccess(
				sessionId = sessionId,
				filePath = filePath,
				location = location,
			)
		)
		is MultipartUploadResult.Error -> APIResult.Error(message, throwable)
		is MultipartUploadResult.Paused -> APIResult.Error("Upload paused: $uploadedParts/$totalParts parts uploaded")
		is MultipartUploadResult.Cancelled -> APIResult.Error("Upload cancelled")
	}
}
