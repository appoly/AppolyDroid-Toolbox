package uk.co.appoly.droid.s3upload.multipart.database.entity

/**
 * Represents the status of a multipart upload session.
 */
enum class UploadSessionStatus {
	/** Session created, not yet started uploading parts */
	PENDING,

	/** Currently uploading parts */
	IN_PROGRESS,

	/** User paused the upload */
	PAUSED,

	/** All parts uploaded, completing multipart upload with S3 */
	COMPLETING,

	/** Successfully completed */
	COMPLETED,

	/** Failed after max retries */
	FAILED,

	/** User or system aborted the upload */
	ABORTED
}
