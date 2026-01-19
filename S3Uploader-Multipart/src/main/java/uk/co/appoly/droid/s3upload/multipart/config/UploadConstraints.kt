package uk.co.appoly.droid.s3upload.multipart.config

import kotlinx.serialization.Serializable

/**
 * Configures constraints for multipart upload execution.
 *
 * Constraints determine when uploads can run and how they respond to
 * constraint violations (e.g., switching from WiFi to cellular).
 *
 * @property networkType Required network type for uploads. Default is [UploadNetworkType.CONNECTED] (any network).
 * @property requiresCharging Whether device must be charging during upload. Default is false.
 * @property requiresBatteryNotLow Whether battery must not be low during upload. Default is false.
 * @property requiresStorageNotLow Whether storage must not be low during upload. Default is false.
 * @property autoResumeWhenSatisfied Whether to automatically resume uploads when constraints are satisfied again.
 *                                   Default is true. When true, paused uploads will be re-enqueued with WorkManager
 *                                   to start automatically when constraints are met.
 * @property autoResumeDelayMs Delay in milliseconds before auto-resuming after constraints are satisfied.
 *                             Default is 2000ms. Helps avoid rapid pause/resume cycles during network fluctuations.
 */
@Serializable
data class UploadConstraints(
	val networkType: UploadNetworkType = UploadNetworkType.CONNECTED,
	val requiresCharging: Boolean = false,
	val requiresBatteryNotLow: Boolean = false,
	val requiresStorageNotLow: Boolean = false,
	val autoResumeWhenSatisfied: Boolean = true,
	val autoResumeDelayMs: Long = DEFAULT_AUTO_RESUME_DELAY_MS
) {
	init {
		require(autoResumeDelayMs >= 0) {
			"Auto-resume delay must be non-negative"
		}
	}

	companion object {
		/** Default auto-resume delay: 2 seconds */
		const val DEFAULT_AUTO_RESUME_DELAY_MS: Long = 2000L

		/** Default constraints: any network, no other requirements */
		val DEFAULT = UploadConstraints()

		/**
		 * Creates constraints for WiFi-only uploads.
		 *
		 * Uses [UploadNetworkType.UNMETERED] which maps to WiFi or Ethernet connections.
		 */
		fun wifiOnly() = UploadConstraints(networkType = UploadNetworkType.UNMETERED)

		/**
		 * Creates constraints optimized for power-saving scenarios.
		 *
		 * Requires WiFi, charging, and battery not low. Ideal for large uploads
		 * that should wait for optimal conditions.
		 */
		fun powerSaving() = UploadConstraints(
			networkType = UploadNetworkType.UNMETERED,
			requiresCharging = true,
			requiresBatteryNotLow = true
		)

		/**
		 * Creates constraints for background uploads that should only run
		 * when storage and battery conditions are good.
		 */
		fun lowPriority() = UploadConstraints(
			networkType = UploadNetworkType.CONNECTED,
			requiresBatteryNotLow = true,
			requiresStorageNotLow = true
		)
	}
}

/**
 * Network type requirements for uploads.
 *
 * Maps to WorkManager's [androidx.work.NetworkType] for constraint enforcement.
 */
@Serializable
enum class UploadNetworkType {
	/**
	 * No network required. Upload can proceed even when offline
	 * (will likely fail but won't be blocked by WorkManager constraints).
	 */
	NOT_REQUIRED,

	/**
	 * Requires any network connection (WiFi, cellular, etc.).
	 * This is the default and most common option.
	 */
	CONNECTED,

	/**
	 * Requires an unmetered network connection (typically WiFi or Ethernet).
	 * Use this for large uploads to avoid cellular data charges.
	 */
	UNMETERED,

	/**
	 * Requires a non-roaming network connection.
	 * Useful when cellular data roaming is expensive.
	 */
	NOT_ROAMING,

	/**
	 * Requires a metered network connection.
	 * Rarely used - only when you specifically want cellular.
	 */
	METERED
}
