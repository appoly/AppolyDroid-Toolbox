package uk.co.appoly.droid.s3upload.multipart.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single part of a multipart upload.
 *
 * Each part has its own status and can be uploaded independently.
 * The ETag returned from S3 after successful upload is stored here
 * for use when completing the multipart upload.
 */
@Entity(
	tableName = "multipart_upload_parts",
	foreignKeys = [
		ForeignKey(
			entity = UploadSessionEntity::class,
			parentColumns = ["session_id"],
			childColumns = ["session_id"],
			onDelete = ForeignKey.CASCADE
		)
	],
	indices = [
		Index(value = ["session_id"]),
		Index(value = ["session_id", "part_number"], unique = true)
	]
)
data class UploadPartEntity(
	/** Unique ID for this part: "{sessionId}_{partNumber}" */
	@PrimaryKey
	@ColumnInfo(name = "part_id")
	val partId: String,

	/** Reference to the parent upload session */
	@ColumnInfo(name = "session_id")
	val sessionId: String,

	/** Part number (1-based index, as required by S3) */
	@ColumnInfo(name = "part_number")
	val partNumber: Int,

	/** Start byte position in the file (inclusive) */
	@ColumnInfo(name = "start_byte")
	val startByte: Long,

	/** End byte position in the file (exclusive) */
	@ColumnInfo(name = "end_byte")
	val endByte: Long,

	/** Size of this part in bytes */
	@ColumnInfo(name = "part_size")
	val partSize: Long,

	/** Current status of this part */
	@ColumnInfo(name = "status")
	val status: PartUploadStatus,

	/** ETag returned from S3 after successful upload (required for completing multipart) */
	@ColumnInfo(name = "etag")
	val etag: String? = null,

	/** Number of bytes uploaded for this part (for progress tracking) */
	@ColumnInfo(name = "uploaded_bytes")
	val uploadedBytes: Long = 0,

	/** Number of retry attempts for this part */
	@ColumnInfo(name = "retry_count")
	val retryCount: Int = 0,

	/** Timestamp when this part was last updated */
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long
)
