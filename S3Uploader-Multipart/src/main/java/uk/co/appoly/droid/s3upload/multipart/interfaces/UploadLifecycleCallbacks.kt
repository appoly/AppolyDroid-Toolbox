package uk.co.appoly.droid.s3upload.multipart.interfaces

import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadResult

/**
 * Callbacks for upload lifecycle events.
 *
 * Implement this interface to hook into various stages of the upload process.
 * All callbacks are invoked from the upload worker and run on a background thread.
 *
 * ## Common Use Cases
 *
 * - **Pre-upload validation**: Call your backend API to register the upload before it starts
 * - **Post-upload confirmation**: Notify your backend when an upload completes
 * - **Cleanup**: Delete temporary files after successful uploads
 * - **Analytics**: Track upload events and metrics
 *
 * ## Example Usage
 *
 * ```kotlin
 * val config = MultipartUploadConfig(
 *     lifecycleCallbacks = object : UploadLifecycleCallbacks {
 *         override suspend fun onBeforeUpload(filePath: String): BeforeUploadResult {
 *             // Validate or register with backend before upload starts
 *             return try {
 *                 backendApi.validateUpload(filePath)
 *                 BeforeUploadResult.Continue
 *             } catch (e: Exception) {
 *                 BeforeUploadResult.Abort("Validation failed: ${e.message}")
 *             }
 *         }
 *
 *         override suspend fun onUploadComplete(
 *             sessionId: String,
 *             result: MultipartUploadResult
 *         ) {
 *             when (result) {
 *                 is MultipartUploadResult.Success -> {
 *                     // Confirm upload with backend
 *                     backendApi.confirmUpload(sessionId, result.location)
 *                     // Clean up temp file
 *                     File(result.filePath).delete()
 *                 }
 *                 is MultipartUploadResult.Error -> {
 *                     analytics.trackUploadError(result.message)
 *                 }
 *                 else -> { /* Handle other states */ }
 *             }
 *         }
 *     }
 * )
 * ```
 *
 * ## Thread Safety
 *
 * All callbacks are `suspend` functions and are called from a coroutine context.
 * You can safely perform network calls, database operations, or other async work.
 *
 * @see BeforeUploadResult
 */
interface UploadLifecycleCallbacks {

	/**
	 * Called before the upload begins, before any S3 interaction.
	 *
	 * Use this to perform pre-upload validation, check user permissions/quotas,
	 * or abort the upload if conditions aren't met.
	 *
	 * **Note:** This is called before the multipart upload is initiated with S3.
	 * If you return [BeforeUploadResult.Abort], no S3 calls will be made and no
	 * session will be created.
	 *
	 * @param filePath The local file path being uploaded
	 * @return [BeforeUploadResult.Continue] to proceed, or [BeforeUploadResult.Abort] to cancel
	 */
	suspend fun onBeforeUpload(
		filePath: String
	): BeforeUploadResult = BeforeUploadResult.Continue

	/**
	 * Called when the upload completes (success, error, or cancellation).
	 *
	 * Use this to perform post-upload actions like confirming with your backend,
	 * cleaning up temporary files, or logging analytics.
	 *
	 * @param sessionId The upload session ID
	 * @param result The final upload result
	 */
	suspend fun onUploadComplete(
		sessionId: String,
		result: MultipartUploadResult
	) {
		// Default: no-op
	}

	/**
	 * Called when an upload is paused.
	 *
	 * This can happen due to:
	 * - User-initiated pause
	 * - Constraint violation (network type changed, battery low, etc.)
	 *
	 * @param sessionId The upload session ID
	 * @param reason Human-readable reason for the pause
	 * @param isConstraintViolation True if paused due to a WorkManager constraint violation
	 */
	suspend fun onUploadPaused(
		sessionId: String,
		reason: String,
		isConstraintViolation: Boolean
	) {
		// Default: no-op
	}

	/**
	 * Called when an upload is resumed.
	 *
	 * @param sessionId The upload session ID
	 */
	suspend fun onUploadResumed(sessionId: String) {
		// Default: no-op
	}

	/**
	 * Called periodically as upload progress updates.
	 *
	 * **Note:** This may be called frequently during active uploads.
	 * Avoid heavy operations in this callback, or implement your own throttling.
	 *
	 * The default implementation is a no-op. Only override if you need
	 * progress tracking beyond what the notification provides.
	 *
	 * @param sessionId The upload session ID
	 * @param progress Current upload progress
	 */
	suspend fun onProgressUpdate(
		sessionId: String,
		progress: MultipartUploadProgress
	) {
		// Default: no-op
	}
}

/**
 * Result returned from [UploadLifecycleCallbacks.onBeforeUpload] to control
 * whether the upload should proceed.
 */
sealed class BeforeUploadResult {

	/**
	 * Continue with the upload.
	 */
	data object Continue : BeforeUploadResult()

	/**
	 * Abort the upload.
	 *
	 * The upload will not proceed and no S3 session will be created.
	 * The worker will return a failure result with the provided reason.
	 *
	 * @property reason Human-readable reason for aborting (returned as error message)
	 */
	data class Abort(val reason: String) : BeforeUploadResult()
}
