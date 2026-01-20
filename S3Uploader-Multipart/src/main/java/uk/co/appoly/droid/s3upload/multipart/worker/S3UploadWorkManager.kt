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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints
import uk.co.appoly.droid.s3upload.multipart.config.UploadNetworkType
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

	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	/**
	 * Schedules a new multipart upload with full constraint support.
	 *
	 * @param context Application context
	 * @param file The file to upload
	 * @param apiUrls API endpoints for multipart operations
	 * @param constraints Upload constraints. If null, uses default constraints from
	 *                    [MultipartUploadManager.config]. Pass [UploadConstraints.DEFAULT]
	 *                    for any-network uploads or [UploadConstraints.wifiOnly] for WiFi-only.
	 * @return Unique work name that can be used to track the upload
	 */
	fun scheduleUpload(
		context: Context,
		file: File,
		apiUrls: MultipartApiUrls,
		constraints: UploadConstraints? = null
	): String {
		val workName = "${UPLOAD_WORK_PREFIX}${UUID.randomUUID()}"

		// Resolve constraints: use provided, or fall back to config default
		val resolvedConstraints = constraints
			?: MultipartUploadManager.getInstance(context).config.defaultConstraints

		val workConstraints = resolvedConstraints.toWorkManagerConstraints()

		// Serialize constraints for storage in the session
		val constraintsJson = json.encodeToString(resolvedConstraints)

		val workRequest = OneTimeWorkRequestBuilder<MultipartUploadWorker>()
			.setInputData(MultipartUploadWorker.createInputData(file, apiUrls, constraintsJson))
			.setConstraints(workConstraints)
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
	 * Schedules resumption of an existing upload with full constraint support.
	 *
	 * @param context Application context
	 * @param sessionId The session ID to resume
	 * @param constraints Upload constraints. If null, uses default constraints from
	 *                    [MultipartUploadManager.config].
	 * @param initialDelayMs Optional delay before starting the resume (useful for auto-resume
	 *                       after constraint violations to avoid rapid pause/resume cycles)
	 * @return Unique work name
	 */
	fun scheduleResume(
		context: Context,
		sessionId: String,
		constraints: UploadConstraints? = null,
		initialDelayMs: Long = 0
	): String {
		val workName = "${UPLOAD_WORK_PREFIX}resume_$sessionId"

		// Resolve constraints: use provided, or fall back to config default
		val resolvedConstraints = constraints
			?: MultipartUploadManager.getInstance(context).config.defaultConstraints

		val workConstraints = resolvedConstraints.toWorkManagerConstraints()

		val workRequestBuilder = OneTimeWorkRequestBuilder<MultipartUploadWorker>()
			.setInputData(MultipartUploadWorker.createResumeInputData(sessionId))
			.setConstraints(workConstraints)
			.setBackoffCriteria(
				BackoffPolicy.EXPONENTIAL,
				MIN_BACKOFF_MILLIS,
				TimeUnit.MILLISECONDS
			)
			.addTag(TAG_MULTIPART_UPLOAD)
			.addTag("session_$sessionId")

		if (initialDelayMs > 0) {
			workRequestBuilder.setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
		}

		val workRequest = workRequestBuilder.build()

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

	// ==================== Constraint Conversion ====================

	/**
	 * Converts [UploadConstraints] to WorkManager [Constraints].
	 */
	private fun UploadConstraints.toWorkManagerConstraints(): Constraints {
		return Constraints.Builder()
			.setRequiredNetworkType(networkType.toWorkManagerNetworkType())
			.setRequiresCharging(requiresCharging)
			.setRequiresBatteryNotLow(requiresBatteryNotLow)
			.setRequiresStorageNotLow(requiresStorageNotLow)
			.build()
	}

	/**
	 * Converts [UploadNetworkType] to WorkManager [NetworkType].
	 */
	private fun UploadNetworkType.toWorkManagerNetworkType(): NetworkType {
		return when (this) {
			UploadNetworkType.NOT_REQUIRED -> NetworkType.NOT_REQUIRED
			UploadNetworkType.CONNECTED -> NetworkType.CONNECTED
			UploadNetworkType.UNMETERED -> NetworkType.UNMETERED
			UploadNetworkType.NOT_ROAMING -> NetworkType.NOT_ROAMING
			UploadNetworkType.METERED -> NetworkType.METERED
		}
	}
}
