package uk.co.appoly.droid.s3upload.multipart.database.entity

/**
 * Represents the status of a multipart upload session.
 */
enum class UploadSessionStatus {
	/** Session created, not yet started uploading parts */
	PENDING,

	/** Currently uploading parts */
	IN_PROGRESS,

	/** User paused the upload manually */
	PAUSED,

	/**
	 * Upload paused due to constraint violation (e.g., WiFi switched to cellular
	 * when WiFi-only was required, battery too low, etc.).
	 *
	 * Different from [PAUSED] to distinguish user-initiated pauses from
	 * system-initiated pauses. When [autoResumeWhenSatisfied][uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints.autoResumeWhenSatisfied]
	 * is enabled, uploads in this state will automatically resume when
	 * constraints are satisfied again.
	 */
	PAUSED_CONSTRAINT_VIOLATION,

	/** All parts uploaded, completing multipart upload with S3 */
	COMPLETING,

	/** Successfully completed */
	COMPLETED,

	/** Failed after max retries */
	FAILED,

	/** User or system aborted the upload */
	ABORTED
}
