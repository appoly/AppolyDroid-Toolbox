package uk.co.appoly.droid.s3upload.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.s3upload.utils.StringOrListSerialiser

/**
 * Response model for the pre-signed URL generation endpoint.
 *
 * This class represents the structure of the API response when requesting
 * a pre-signed URL for S3 file uploads.
 *
 * Example JSON:
 * ```json
 * {
 *   "success": true,
 *   "data": {
 *     "file_path": "images/profile/user123.jpg",
 *     "presigned_url": "https://bucket-name.s3.amazonaws.com/...",
 *     "headers": {
 *       "Host": ["bucket-name.s3.amazonaws.com"],
 *       "x-amz-acl": ["public-read"],
 *       "Content-Type": "image/jpeg"
 *     }
 *   }
 * }
 * ```
 *
 * @property success Indicates whether the request was successful
 * @property message Optional message providing additional information
 * @property data The pre-signed URL data, if successful
 */
@Serializable
data class GetPreSignedUrlResponse(
	val success: Boolean = false,
	val message: String? = "No message from server",
	val data: PreSignedURLData?
)

/**
 * Data model containing the pre-signed URL and related information.
 *
 * @property filePath The path/key where the file will be stored in S3 bucket (generally a relative path)
 * @property presignedUrl The generated pre-signed URL for the S3 upload
 * @property headers HTTP headers to include with the S3 upload request
 */
@Serializable
data class PreSignedURLData(
	@SerialName("file_path")
	val filePath: String,
	@SerialName("presigned_url")
	val presignedUrl: String,
	val headers: Map<String, @Serializable(with = StringOrListSerialiser::class) String>
)