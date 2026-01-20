package uk.co.appoly.droid.s3upload.multipart.database.entity

/**
 * Represents the status of an individual upload part.
 */
enum class PartUploadStatus {
	/** Not yet started */
	PENDING,

	/** Currently uploading */
	UPLOADING,

	/** Successfully uploaded with ETag */
	UPLOADED,

	/** Failed (will retry) */
	FAILED
}
