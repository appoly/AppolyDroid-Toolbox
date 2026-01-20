package uk.co.appoly.droid.s3upload.multipart.config

import uk.co.appoly.droid.s3upload.multipart.interfaces.UploadLifecycleCallbacks
import uk.co.appoly.droid.s3upload.multipart.interfaces.UploadNotificationProvider

/**
 * Configuration for multipart uploads.
 *
 * @property chunkSize Size of each upload chunk in bytes. Must be at least 5MB per S3 requirements.
 *                     Default is 5MB. Larger chunks mean fewer API calls but more data to re-upload on failure.
 * @property maxConcurrentParts Maximum number of parts to upload concurrently. Default is 3.
 *                              Higher values can improve throughput but use more bandwidth.
 * @property maxRetries Maximum number of retry attempts for failed parts. Default is 3.
 * @property retryDelayMs Initial delay between retries in milliseconds. Default is 1000ms (1 second).
 * @property useExponentialBackoff Whether to use exponential backoff for retries. Default is true.
 *                                 When true, delay doubles with each retry (1s, 2s, 4s, etc.)
 * @property defaultConstraints Default constraints for all uploads. Per-upload overrides take precedence
 *                               when scheduling via [uk.co.appoly.droid.s3upload.multipart.worker.S3UploadWorkManager].
 * @property notificationProvider Custom notification provider for upload notifications. If null, uses
 *                                default system notifications. See [UploadNotificationProvider] for details.
 * @property lifecycleCallbacks Callbacks for upload lifecycle events (before/after upload, pause/resume).
 *                              See [UploadLifecycleCallbacks] for available hooks.
 */
data class MultipartUploadConfig(
	val chunkSize: Long = DEFAULT_CHUNK_SIZE,
	val maxConcurrentParts: Int = DEFAULT_MAX_CONCURRENT_PARTS,
	val maxRetries: Int = DEFAULT_MAX_RETRIES,
	val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
	val useExponentialBackoff: Boolean = true,
	val defaultConstraints: UploadConstraints = UploadConstraints.DEFAULT,
	val notificationProvider: UploadNotificationProvider? = null,
	val lifecycleCallbacks: UploadLifecycleCallbacks? = null
) {
	init {
		require(chunkSize >= MIN_CHUNK_SIZE) {
			"Chunk size must be at least ${MIN_CHUNK_SIZE / (1024 * 1024)}MB per S3 requirements"
		}
		require(maxConcurrentParts in 1..MAX_CONCURRENT_PARTS_LIMIT) {
			"Concurrent parts must be between 1 and $MAX_CONCURRENT_PARTS_LIMIT"
		}
		require(maxRetries >= 0) {
			"Max retries must be non-negative"
		}
		require(retryDelayMs >= 0) {
			"Retry delay must be non-negative"
		}
	}

	/**
	 * Calculates the number of parts needed for a file of the given size.
	 */
	fun calculatePartCount(fileSize: Long): Int {
		return ((fileSize + chunkSize - 1) / chunkSize).toInt()
	}

	/**
	 * Calculates the retry delay for a given attempt number.
	 *
	 * @param attemptNumber The current attempt (0-based)
	 * @return The delay in milliseconds before the next retry
	 */
	fun getRetryDelay(attemptNumber: Int): Long {
		return if (useExponentialBackoff) {
			retryDelayMs * (1L shl attemptNumber.coerceAtMost(10))
		} else {
			retryDelayMs
		}
	}

	companion object {
		/** Minimum chunk size: 5MB (S3 requirement) */
		const val MIN_CHUNK_SIZE: Long = 5L * 1024L * 1024L

		/** Default chunk size: 5MB */
		const val DEFAULT_CHUNK_SIZE: Long = MIN_CHUNK_SIZE

		/** Default maximum concurrent part uploads */
		const val DEFAULT_MAX_CONCURRENT_PARTS: Int = 3

		/** Maximum allowed concurrent part uploads */
		const val MAX_CONCURRENT_PARTS_LIMIT: Int = 10

		/** Default maximum retry attempts */
		const val DEFAULT_MAX_RETRIES: Int = 3

		/** Default retry delay: 1 second */
		const val DEFAULT_RETRY_DELAY_MS: Long = 1000L

		/** Default configuration */
		val DEFAULT = MultipartUploadConfig()

		/**
		 * Creates a configuration optimized for large files.
		 * Uses larger chunks to reduce API calls.
		 */
		fun forLargeFiles(): MultipartUploadConfig = MultipartUploadConfig(
			chunkSize = 10L * 1024L * 1024L, // 10MB chunks
			maxConcurrentParts = 5,
			maxRetries = 5
		)

		/**
		 * Creates a configuration optimized for slow/unreliable networks.
		 * Uses smaller chunks and more retries.
		 */
		fun forUnreliableNetwork(): MultipartUploadConfig = MultipartUploadConfig(
			chunkSize = MIN_CHUNK_SIZE,
			maxConcurrentParts = 1,
			maxRetries = 5,
			retryDelayMs = 2000L
		)

		/**
		 * Creates a configuration for WiFi-only uploads.
		 * Uploads will pause when switching to cellular and resume when WiFi is available.
		 */
		fun wifiOnly(): MultipartUploadConfig = MultipartUploadConfig(
			defaultConstraints = UploadConstraints.wifiOnly()
		)

		/**
		 * Creates a configuration for power-saving scenarios.
		 * Uploads only proceed on WiFi when charging with sufficient battery.
		 */
		fun powerSaving(): MultipartUploadConfig = MultipartUploadConfig(
			defaultConstraints = UploadConstraints.powerSaving()
		)
	}
}
