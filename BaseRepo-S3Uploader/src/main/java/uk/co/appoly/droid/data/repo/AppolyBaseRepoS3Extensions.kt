package uk.co.appoly.droid.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.s3upload.DirectUploadResult
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.UploadResult
import java.io.File

/**
 * Uploads a file to Amazon S3 storage using a pre-signed URL.
 *
 * This extension function simplifies the process of uploading files to S3 by handling
 * the upload process and converting the [UploadResult] to the standard [APIResult] format
 * used throughout the repository layer.
 *
 * Example usage:
 * ```
 * val result = uploadFileToS3(
 *     generatePresignedURL = "https://api.example.com/generate-url",
 *     file = File("/path/to/file.jpg"),
 *     progressFlow = progressStateFlow
 * )
 * ```
 *
 * @param generatePresignedURL The API endpoint URL that generates a pre-signed S3 URL
 * @param file The file to upload to S3
 * @param progressFlow Optional MutableStateFlow to track upload progress (0.0f to 1.0f)
 * @return [APIResult.Success] with the S3 file path if successful, or [APIResult.Error] with error details if failed
 */
suspend inline fun GenericBaseRepo.uploadFileToS3(
	generatePresignedURL: String,
	file: File,
	progressFlow: MutableStateFlow<Float>? = null
): APIResult<String> {
	val uploadResult = S3Uploader.uploadFile(
		file = file,
		getPresignedUrlAPI = generatePresignedURL,
		progressFlow = progressFlow
	)
	return when (uploadResult) {
		is UploadResult.Success -> {
			APIResult.Success(uploadResult.filePath)
		}

		is UploadResult.Error -> {
			APIResult.Error(uploadResult.message, uploadResult.throwable)
		}
	}
}

/**
 * Uploads a file to Amazon S3 storage and then sends the resulting file path to another API endpoint.
 *
 * This function combines two operations:
 * 1. Upload a file to S3 using a pre-signed URL
 * 2. Send the resulting file path to another API endpoint
 *
 * This is useful for scenarios where you need to upload a file and then associate it with a database record
 * or perform additional server-side processing on the uploaded file.
 *
 * Example usage:
 * ```
 * val result = uploadFileToS3(
 *     generatePresignedURL = "https://api.example.com/generate-url",
 *     file = File("/path/to/file.jpg"),
 *     sendPathApiCall = { path -> userRepository.updateProfilePicture(userId, path) }
 * )
 * ```
 *
 * @param generatePresignedURL The API endpoint URL that generates a pre-signed S3 URL
 * @param file The file to upload to S3
 * @param sendPathApiCall Lambda function that takes the S3 file path and makes an additional API call
 * @return [APIResult] from the secondary API call if the upload was successful, or [APIResult.Error] if the upload failed
 */
suspend inline fun <T : Any> GenericBaseRepo.uploadFileToS3(
	generatePresignedURL: String,
	file: File,
	sendPathApiCall: (String) -> APIResult<T>
): APIResult<T> {
	val uploadResult = S3Uploader.uploadFile(file, generatePresignedURL)
	return when (uploadResult) {
		is UploadResult.Error -> {
			APIResult.Error(uploadResult.message)
		}

		is UploadResult.Success -> {
			sendPathApiCall(uploadResult.filePath)
		}
	}
}

/**
 * Uploads a file directly to Amazon S3 storage using a user-provided pre-signed URL.
 *
 * This extension function bypasses the API call to generate a pre-signed URL and uses
 * the provided URL directly. This is useful when you already have a pre-signed URL from
 * another source. The result is converted to the standard [APIResult] format used
 * throughout the repository layer.
 *
 * Example usage:
 * ```
 * val result = uploadFileDirectToS3(
 *     presignedUrl = "https://bucket.s3.amazonaws.com/...",
 *     file = File("/path/to/file.jpg"),
 *     mediaType = "image/jpeg".toMediaTypeOrNull()!!,
 *     headers = mapOf("x-amz-acl" to "public-read"),
 *     progressFlow = progressStateFlow
 * )
 * ```
 *
 * @param presignedUrl The pre-signed S3 URL to upload to
 * @param file The file to upload to S3
 * @param mediaType The media type (MIME type) of the file
 * @param headers Optional HTTP headers to include with the upload request (default is empty)
 * @param progressFlow Optional MutableStateFlow to track upload progress (0.0f to 100.0f)
 * @return [APIResult.Success] with Unit if successful, or [APIResult.Error] with error details if failed
 */
suspend inline fun GenericBaseRepo.uploadFileDirectToS3(
	presignedUrl: String,
	file: File,
	mediaType: MediaType,
	headers: Map<String, String> = emptyMap(),
	progressFlow: MutableStateFlow<Float>? = null
): APIResult<Unit> {
	val uploadResult = S3Uploader.uploadFileDirect(
		file = file,
		presignedUrl = presignedUrl,
		mediaType = mediaType,
		headers = headers,
		progressFlow = progressFlow
	)
	return when (uploadResult) {
		is DirectUploadResult.Success -> {
			APIResult.Success(Unit)
		}

		is DirectUploadResult.Error -> {
			APIResult.Error(uploadResult.message, uploadResult.throwable)
		}
	}
}

