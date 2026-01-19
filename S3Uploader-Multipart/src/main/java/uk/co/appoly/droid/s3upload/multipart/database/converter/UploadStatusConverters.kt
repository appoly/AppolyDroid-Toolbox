package uk.co.appoly.droid.s3upload.multipart.database.converter

import androidx.room.TypeConverter
import uk.co.appoly.droid.s3upload.multipart.database.entity.PartUploadStatus
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus

/**
 * Room type converters for upload status enums.
 */
class UploadStatusConverters {

	@TypeConverter
	fun fromUploadSessionStatus(status: UploadSessionStatus): String = status.name

	@TypeConverter
	fun toUploadSessionStatus(value: String): UploadSessionStatus =
		UploadSessionStatus.valueOf(value)

	@TypeConverter
	fun fromPartUploadStatus(status: PartUploadStatus): String = status.name

	@TypeConverter
	fun toPartUploadStatus(value: String): PartUploadStatus =
		PartUploadStatus.valueOf(value)
}
