package uk.co.appoly.droid.s3upload.multipart.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.utils.MultipartUploadLog

/**
 * Periodic worker that recovers interrupted uploads.
 *
 * This worker:
 * - Finds uploads that were interrupted (app killed, device restarted)
 * - Re-enqueues them for completion
 * - Cleans up old completed sessions
 */
class UploadRecoveryWorker(
	private val context: Context,
	params: WorkerParameters
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
		// Sync logger config from S3Uploader before any logging
		MultipartUploadManager.syncLoggerConfig()

		MultipartUploadLog.d(this@UploadRecoveryWorker, "Running upload recovery check")

		try {
			val manager = MultipartUploadManager.getInstance(context)

			// Recover interrupted uploads
			val recoveredIds = manager.recoverInterruptedUploads()
			MultipartUploadLog.d(this@UploadRecoveryWorker, "Recovered ${recoveredIds.size} uploads")

			// Clean up old sessions (older than 7 days)
			val cleanedCount = manager.cleanupOldSessions()
			if (cleanedCount > 0) {
				MultipartUploadLog.d(this@UploadRecoveryWorker, "Cleaned up $cleanedCount old sessions")
			}

			Result.success(
				workDataOf(
					KEY_RECOVERED_COUNT to recoveredIds.size,
					KEY_CLEANED_COUNT to cleanedCount
				)
			)
		} catch (e: Exception) {
			MultipartUploadLog.e(this@UploadRecoveryWorker, "Recovery worker failed", e)
			Result.failure(
				workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error"))
			)
		}
	}

	companion object {
		const val KEY_RECOVERED_COUNT = "recovered_count"
		const val KEY_CLEANED_COUNT = "cleaned_count"
		const val KEY_ERROR_MESSAGE = "error_message"
	}
}
