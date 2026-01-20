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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints
import uk.co.appoly.droid.s3upload.multipart.interfaces.BeforeUploadResult
import uk.co.appoly.droid.s3upload.multipart.interfaces.DefaultUploadNotificationProvider
import uk.co.appoly.droid.s3upload.multipart.interfaces.UploadLifecycleCallbacks
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
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
 * - Lifecycle callback invocation
 *
 * ## Customization Options
 *
 * There are two approaches to customize upload behavior:
 *
 * ### Option 1: Provider Interfaces (Recommended)
 *
 * For most use cases, use the provider interfaces via [MultipartUploadConfig][uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig]:
 * - [UploadNotificationProvider][uk.co.appoly.droid.s3upload.multipart.interfaces.UploadNotificationProvider] - Custom notifications
 * - [UploadLifecycleCallbacks][uk.co.appoly.droid.s3upload.multipart.interfaces.UploadLifecycleCallbacks] - Pre/post upload hooks
 *
 * ```kotlin
 * val config = MultipartUploadConfig(
 *     notificationProvider = DefaultUploadNotificationProvider(
 *         channelId = "my_uploads",
 *         smallIconResId = R.drawable.ic_upload
 *     ),
 *     lifecycleCallbacks = object : UploadLifecycleCallbacks {
 *         override suspend fun onUploadComplete(sessionId: String, result: MultipartUploadResult) {
 *             // Handle completion
 *         }
 *     }
 * )
 * ```
 *
 * ### Option 2: Custom Worker Subclass (Advanced)
 *
 * For full control, subclass this worker and register via [WorkerFactory][androidx.work.WorkerFactory].
 * This approach is compatible with [S3UploadWorkManager][uk.co.appoly.droid.s3upload.multipart.worker.S3UploadWorkManager] - WorkManager will use your factory
 * to create worker instances.
 *
 * **Step 1: Create your custom worker**
 * ```kotlin
 * class MyUploadWorker(
 *     appContext: Context,
 *     params: WorkerParameters
 * ) : MultipartUploadWorker(appContext, params) {
 *
 *     override fun createForegroundInfo(
 *         sessionId: String,
 *         progress: MultipartUploadProgress?
 *     ): ForegroundInfo {
 *         // Your custom notification logic
 *         createNotificationChannel()
 *         val notification = NotificationCompat.Builder(context, "my_channel")
 *             .setContentTitle("My App Upload")
 *             .setSmallIcon(R.drawable.ic_upload)
 *             .build()
 *         return ForegroundInfo(sessionId.hashCode(), notification)
 *     }
 *
 *     override fun createNotificationChannel() {
 *         // Your custom channel creation
 *     }
 *
 *     // Override lifecycle hooks directly instead of using UploadLifecycleCallbacks
 *     override suspend fun onBeforeUpload(filePath: String): BeforeUploadResult {
 *         // Validate or register with backend before upload starts
 *         return BeforeUploadResult.Continue
 *     }
 *
 *     override suspend fun onUploadComplete(sessionId: String, result: MultipartUploadResult) {
 *         if (result is MultipartUploadResult.Success) {
 *             // Confirm with backend, cleanup temp files
 *         }
 *     }
 * }
 * ```
 *
 * **Step 2: Create a WorkerFactory**
 * ```kotlin
 * class MyWorkerFactory : WorkerFactory() {
 *     override fun createWorker(
 *         appContext: Context,
 *         workerClassName: String,
 *         workerParameters: WorkerParameters
 *     ): ListenableWorker? {
 *         return when (workerClassName) {
 *             MultipartUploadWorker::class.java.name -> {
 *                 // Return your custom worker instead
 *                 MyUploadWorker(appContext, workerParameters)
 *             }
 *             else -> null // Let default factory handle other workers
 *         }
 *     }
 * }
 * ```
 *
 * **Step 3: Register the factory with WorkManager**
 *
 * In your `Application` class, disable default WorkManager initialization and provide your own:
 *
 * ```xml
 * <!-- In AndroidManifest.xml -->
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="androidx.work.WorkManagerInitializer"
 *         android:value="androidx.startup"
 *         tools:node="remove" />
 * </provider>
 * ```
 *
 * ```kotlin
 * // In Application.onCreate()
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         val config = Configuration.Builder()
 *             .setWorkerFactory(MyWorkerFactory())
 *             .build()
 *         WorkManager.initialize(this, config)
 *     }
 * }
 * ```
 *
 * **Using with S3UploadWorkManager:**
 *
 * Once your factory is registered, [S3UploadWorkManager.scheduleUpload][uk.co.appoly.droid.s3upload.multipart.worker.S3UploadWorkManager.scheduleUpload] works normally.
 * WorkManager intercepts the request for `MultipartUploadWorker` and your factory
 * returns `MyUploadWorker` instead.
 *
 * ```kotlin
 * // This will use MyUploadWorker via your factory
 * S3UploadWorkManager.scheduleUpload(context, file, apiUrls)
 * ```
 *
 * ## Interface Implementation
 *
 * This worker implements [UploadLifecycleCallbacks] to ensure compile-time parity
 * between the worker's lifecycle methods and the callback interface. Subclasses can
 * override these methods directly instead of configuring callbacks separately.
 *
 * ## Protected/Override Methods for Subclassing
 *
 * **Notification:**
 * - [createForegroundInfo] - Override to fully customize foreground service behavior
 * - [createNotificationChannel] - Override to customize the notification channel
 *
 * **Lifecycle Hooks:** (from [UploadLifecycleCallbacks], called at appropriate points during upload)
 * - [onBeforeUpload] - Called before upload starts, can abort
 * - [onUploadResumed] - Called when a paused upload resumes
 * - [onUploadComplete] - Called when upload finishes (success, error, or cancel)
 * - [onUploadPaused] - Called when upload pauses (user or constraint violation)
 * - [onProgressUpdate] - Called periodically with progress updates
 *
 * **Properties:**
 * - [context] - Protected access to application context
 * - [defaultNotificationProvider] - Lazy default provider, available for fallback
 *
 * @param appContext The application context
 * @param params Worker parameters from WorkManager
 *
 * @see uk.co.appoly.droid.s3upload.multipart.worker.S3UploadWorkManager
 * @see uk.co.appoly.droid.s3upload.multipart.interfaces.UploadNotificationProvider
 * @see uk.co.appoly.droid.s3upload.multipart.interfaces.UploadLifecycleCallbacks
 */
open class MultipartUploadWorker(
	appContext: Context,
	params: WorkerParameters
) : CoroutineWorker(appContext, params), UploadLifecycleCallbacks {

	/** Application context, available to subclasses */
	protected val context: Context = appContext

	/**
	 * Tracks the current session ID being processed by this worker.
	 * Used in constraint violation handling.
	 */
	@Volatile
	private var currentSessionId: String? = null

	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	final override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
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
				// Track the session ID for constraint violation handling
				currentSessionId = sessionId
				// Resume existing upload - prepares session for execution
				val resumeResult = manager.resumeUpload(sessionId)
				if (resumeResult.isSuccess) {
					// Invoke onUploadResumed lifecycle hook
					onUploadResumed(sessionId)
					manager.executeUpload(sessionId)
				} else {
					return@withContext Result.failure(
						workDataOf(KEY_ERROR_MESSAGE to (resumeResult.exceptionOrNull()?.message ?: "Failed to resume upload"))
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

				// Invoke onBeforeUpload lifecycle hook BEFORE any S3 interaction
				val beforeResult = onBeforeUpload(filePath)
				if (beforeResult is BeforeUploadResult.Abort) {
					MultipartUploadLog.d(this@MultipartUploadWorker, "Upload aborted by onBeforeUpload: ${beforeResult.reason}")
					return@withContext Result.failure(
						workDataOf(KEY_ERROR_MESSAGE to "Upload aborted: ${beforeResult.reason}")
					)
				}

				val apiUrls = MultipartApiUrls(
					initiateUrl = inputData.getString(KEY_INITIATE_URL) ?: return@withContext Result.failure(),
					presignPartUrl = inputData.getString(KEY_PRESIGN_URL) ?: return@withContext Result.failure(),
					completeUrl = inputData.getString(KEY_COMPLETE_URL) ?: return@withContext Result.failure(),
					abortUrl = inputData.getString(KEY_ABORT_URL) ?: return@withContext Result.failure()
				)

				// Parse constraints from input data, or use manager's default
				val constraints = inputData.getString(KEY_CONSTRAINTS_JSON)?.let { constraintsJson ->
					try {
						json.decodeFromString<UploadConstraints>(constraintsJson)
					} catch (e: Exception) {
						MultipartUploadLog.w(this@MultipartUploadWorker, "Failed to parse constraints, using defaults", e)
						null
					}
				} ?: manager.config.defaultConstraints

				// Initialize the upload session (does not execute)
				val initResult = manager.initializeUpload(file, apiUrls, constraints)
				if (initResult.isFailure) {
					return@withContext Result.failure(
						workDataOf(KEY_ERROR_MESSAGE to (initResult.exceptionOrNull()?.message ?: "Failed to initialize upload"))
					)
				}

				val newSessionId = initResult.getOrThrow()
				// Track the session ID for constraint violation handling
				currentSessionId = newSessionId

				// Execute the upload (worker manages execution separately from initialization)
				manager.executeUpload(newSessionId)
			} else {
				return@withContext Result.failure(
					workDataOf(KEY_ERROR_MESSAGE to "No session ID or file path provided")
				)
			}

			// Get session ID from result for callbacks
			val resultSessionId = when (result) {
				is MultipartUploadResult.Success -> result.sessionId
				is MultipartUploadResult.Error -> result.sessionId
				is MultipartUploadResult.Paused -> result.sessionId
				is MultipartUploadResult.Cancelled -> result.sessionId
			}

			// Invoke onUploadComplete lifecycle hook
			onUploadComplete(resultSessionId, result)

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
					// Invoke onUploadPaused lifecycle hook (user-initiated pause)
					onUploadPaused(result.sessionId, "User requested pause", isConstraintViolation = false)
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
		} catch (e: CancellationException) {
			// Coroutine was cancelled - this happens when WorkManager stops us
			// (constraint violation, explicit cancellation, etc.)
			MultipartUploadLog.d(this@MultipartUploadWorker, "Worker cancelled, isStopped=$isStopped")

			// Handle constraint violation if that's why we were stopped
			handleWorkerStoppedIfNeeded()

			// Clear session ID before rethrowing
			currentSessionId = null

			// Rethrow to properly propagate cancellation
			// WorkManager handles cancelled workers appropriately
			throw e
		} catch (e: Exception) {
			MultipartUploadLog.e(this@MultipartUploadWorker, "Worker failed", e)
			if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
				Result.retry()
			} else {
				Result.failure(
					workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error"))
				)
			}
		} finally {
			// Clear session ID tracking when work completes normally
			// (CancellationException case already handles this before rethrowing)
			currentSessionId = null
		}
	}

	/**
	 * Handles the case when the worker was stopped by the system (e.g., constraint violation).
	 *
	 * Called in the finally block to ensure we handle constraint violations gracefully
	 * and potentially schedule auto-resume.
	 */
	private suspend fun handleWorkerStoppedIfNeeded() {
		if (!isStopped) return

		val sessionId = currentSessionId ?: return

		val stopReasonCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			stopReason
		} else {
			STOP_REASON_UNKNOWN
		}

		val reason = mapStopReasonToMessage(stopReasonCode)
		MultipartUploadLog.d(this, "Worker stopped for session $sessionId: $reason (code=$stopReasonCode)")

		try {
			val manager = MultipartUploadManager.getInstance(context)
			manager.pauseUploadForConstraintViolation(sessionId, reason, stopReasonCode)

			// Invoke onUploadPaused lifecycle hook for constraint violation
			onUploadPaused(sessionId, reason, isConstraintViolation = true)
		} catch (e: Exception) {
			MultipartUploadLog.e(this, "Error handling stop for session $sessionId", e)
		}
	}

	/**
	 * Maps WorkManager stop reason codes to human-readable messages.
	 */
	private fun mapStopReasonToMessage(stopReasonCode: Int): String {
		return when (stopReasonCode) {
			STOP_REASON_CONSTRAINT_CONNECTIVITY -> "Network constraint violated"
			STOP_REASON_CONSTRAINT_CHARGING -> "Charging constraint violated"
			STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "Battery too low"
			STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "Storage too low"
			STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "Device idle constraint violated"
			STOP_REASON_APP_STANDBY -> "App in standby"
			STOP_REASON_QUOTA -> "Work quota exceeded"
			STOP_REASON_BACKGROUND_RESTRICTION -> "Background restriction"
			STOP_REASON_CANCELLED_BY_APP -> "Cancelled by app"
			STOP_REASON_PREEMPT -> "Preempted by system"
			STOP_REASON_TIMEOUT -> "Work timed out"
			STOP_REASON_DEVICE_STATE -> "Device state changed"
			STOP_REASON_USER -> "Stopped by user"
			STOP_REASON_SYSTEM_PROCESSING -> "System processing"
			STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "App launch time changed"
			STOP_REASON_FOREGROUND_SERVICE_TIMEOUT -> "Foreground service timeout"
			else -> "System stopped worker"
		}
	}

	/**
	 * Creates foreground info for the upload notification.
	 *
	 * Override this method to fully customize the foreground service behavior.
	 * For simpler notification customization, consider using [UploadNotificationProvider][uk.co.appoly.droid.s3upload.multipart.interfaces.UploadNotificationProvider]
	 * via [MultipartUploadConfig][uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig] instead.
	 *
	 * @param sessionId The upload session ID
	 * @param progress Current upload progress, or null if not yet available
	 * @return ForegroundInfo for the worker
	 */
	protected open fun createForegroundInfo(
		sessionId: String,
		progress: MultipartUploadProgress? = null
	): ForegroundInfo {
		val manager = MultipartUploadManager.getInstance(context)
		val notificationProvider = manager.config.notificationProvider

		if (notificationProvider != null) {
			notificationProvider.createNotificationChannel(context)
			return notificationProvider.createForegroundInfo(context, sessionId, progress)
		}

		// Default notification behavior
		createNotificationChannel()

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setContentTitle(progress?.fileName?.let { "Uploading $it" } ?: "Uploading file")
			.setContentText(progress?.let {
				"${it.overallProgress.toInt()}% • ${it.uploadedParts}/${it.totalParts} parts"
			} ?: "Upload in progress...")
			.setSmallIcon(android.R.drawable.stat_sys_upload)
			.setOngoing(true)
			.setProgress(100, progress?.overallProgress?.toInt() ?: 0, progress == null)
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

	/**
	 * Creates the notification channel for upload notifications.
	 *
	 * Override this method to customize the notification channel.
	 * Only called when no custom [UploadNotificationProvider][uk.co.appoly.droid.s3upload.multipart.interfaces.UploadNotificationProvider] is configured.
	 */
	protected open fun createNotificationChannel() {
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

	// ==================== Lifecycle Hooks (UploadLifecycleCallbacks) ====================
	//
	// This worker implements UploadLifecycleCallbacks to ensure signature parity.
	// Override these methods in subclasses to customize behavior without needing
	// to configure UploadLifecycleCallbacks separately.
	//
	// Default implementations delegate to the configured lifecycleCallbacks
	// from MultipartUploadConfig (if any).

	/**
	 * Called before an upload starts, before any S3 interaction.
	 *
	 * Override this method to perform pre-upload validation, check user permissions/quotas,
	 * or abort the upload if conditions aren't met.
	 *
	 * **Note:** This is called before the multipart upload is initiated with S3.
	 * If you return [BeforeUploadResult.Abort], no S3 calls will be made and no
	 * session will be created.
	 *
	 * The default implementation delegates to the configured [UploadLifecycleCallbacks]
	 * if present, otherwise returns [BeforeUploadResult.Continue].
	 *
	 * @param filePath The local file path being uploaded
	 * @return [BeforeUploadResult.Continue] to proceed, or [BeforeUploadResult.Abort] to cancel
	 */
	override suspend fun onBeforeUpload(
		filePath: String
	): BeforeUploadResult {
		return try {
			MultipartUploadManager.getInstance(context).config.lifecycleCallbacks
				?.onBeforeUpload(filePath)
				?: BeforeUploadResult.Continue
		} catch (e: Exception) {
			MultipartUploadLog.w(this, "onBeforeUpload callback failed", e)
			BeforeUploadResult.Continue // Continue on callback failure
		}
	}

	/**
	 * Called when a paused upload is resumed.
	 *
	 * Override this method to perform actions when uploads resume, such as
	 * updating UI state or logging analytics.
	 *
	 * The default implementation delegates to the configured [UploadLifecycleCallbacks]
	 * if present.
	 *
	 * @param sessionId The upload session ID
	 */
	override suspend fun onUploadResumed(sessionId: String) {
		try {
			MultipartUploadManager.getInstance(context).config.lifecycleCallbacks
				?.onUploadResumed(sessionId)
		} catch (e: Exception) {
			MultipartUploadLog.w(this, "onUploadResumed callback failed", e)
		}
	}

	/**
	 * Called when an upload completes (success, error, or cancellation).
	 *
	 * Override this method to perform post-upload actions like confirming with
	 * your backend, cleaning up temporary files, or logging analytics.
	 *
	 * The default implementation delegates to the configured [UploadLifecycleCallbacks]
	 * if present.
	 *
	 * @param sessionId The upload session ID
	 * @param result The final upload result
	 */
	override suspend fun onUploadComplete(
		sessionId: String,
		result: MultipartUploadResult
	) {
		try {
			MultipartUploadManager.getInstance(context).config.lifecycleCallbacks
				?.onUploadComplete(sessionId, result)
		} catch (e: Exception) {
			MultipartUploadLog.w(this, "onUploadComplete callback failed", e)
		}
	}

	/**
	 * Called when an upload is paused.
	 *
	 * This can happen due to:
	 * - User-initiated pause
	 * - Constraint violation (network type changed, battery low, etc.)
	 *
	 * Override this method to perform actions when uploads pause, such as
	 * notifying the user or logging analytics.
	 *
	 * The default implementation delegates to the configured [UploadLifecycleCallbacks]
	 * if present.
	 *
	 * @param sessionId The upload session ID
	 * @param reason Human-readable reason for the pause
	 * @param isConstraintViolation True if paused due to a WorkManager constraint violation
	 */
	override suspend fun onUploadPaused(
		sessionId: String,
		reason: String,
		isConstraintViolation: Boolean
	) {
		try {
			MultipartUploadManager.getInstance(context).config.lifecycleCallbacks
				?.onUploadPaused(sessionId, reason, isConstraintViolation)
		} catch (e: Exception) {
			MultipartUploadLog.w(this, "onUploadPaused callback failed", e)
		}
	}

	/**
	 * Called periodically as upload progress updates.
	 *
	 * **Note:** This may be called frequently during active uploads.
	 * Avoid heavy operations in this method, or implement your own throttling.
	 *
	 * Override this method if you need progress tracking beyond what the
	 * notification provides.
	 *
	 * The default implementation delegates to the configured [UploadLifecycleCallbacks]
	 * if present.
	 *
	 * @param sessionId The upload session ID
	 * @param progress Current upload progress
	 */
	override suspend fun onProgressUpdate(
		sessionId: String,
		progress: MultipartUploadProgress
	) {
		try {
			MultipartUploadManager.getInstance(context).config.lifecycleCallbacks
				?.onProgressUpdate(sessionId, progress)
		} catch (e: Exception) {
			MultipartUploadLog.w(this, "onProgressUpdate callback failed", e)
		}
	}

	// ==================== Properties ====================

	/**
	 * Default notification provider used when no custom provider is configured.
	 * Accessed lazily to allow subclasses to customize.
	 */
	protected val defaultNotificationProvider: DefaultUploadNotificationProvider by lazy {
		DefaultUploadNotificationProvider()
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
		const val KEY_CONSTRAINTS_JSON = "constraints_json"

		private const val CHANNEL_ID = "s3_upload_channel"
		private const val MAX_RETRY_ATTEMPTS = 3

		// WorkManager stop reason constants (from ListenableWorker, API 31+)
		// Defined locally for compatibility and to avoid import issues
		private const val STOP_REASON_UNKNOWN = 0
		private const val STOP_REASON_CANCELLED_BY_APP = 1
		private const val STOP_REASON_PREEMPT = 2
		private const val STOP_REASON_TIMEOUT = 3
		private const val STOP_REASON_DEVICE_STATE = 4
		private const val STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW = 5
		private const val STOP_REASON_CONSTRAINT_CHARGING = 6
		private const val STOP_REASON_CONSTRAINT_CONNECTIVITY = 7
		private const val STOP_REASON_CONSTRAINT_DEVICE_IDLE = 8
		private const val STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW = 9
		private const val STOP_REASON_QUOTA = 10
		private const val STOP_REASON_BACKGROUND_RESTRICTION = 11
		private const val STOP_REASON_APP_STANDBY = 12
		private const val STOP_REASON_USER = 13
		private const val STOP_REASON_SYSTEM_PROCESSING = 14
		private const val STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED = 15
		private const val STOP_REASON_FOREGROUND_SERVICE_TIMEOUT = 16

		/**
		 * Creates input data for starting a new upload.
		 *
		 * @param file The file to upload
		 * @param apiUrls API endpoints for multipart operations
		 * @param constraintsJson Optional JSON-serialized [UploadConstraints][uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints].
		 *                        If null, manager defaults will be used.
		 */
		fun createInputData(
			file: File,
			apiUrls: MultipartApiUrls,
			constraintsJson: String? = null
		): Data = workDataOf(
			KEY_FILE_PATH to file.absolutePath,
			KEY_INITIATE_URL to apiUrls.initiateUrl,
			KEY_PRESIGN_URL to apiUrls.presignPartUrl,
			KEY_COMPLETE_URL to apiUrls.completeUrl,
			KEY_ABORT_URL to apiUrls.abortUrl,
			KEY_IS_RESUME to false,
			KEY_CONSTRAINTS_JSON to constraintsJson
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
