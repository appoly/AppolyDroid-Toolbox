package uk.co.appoly.droid.s3upload

/**
 * Represents the result of a file upload operation to S3 storage.
 *
 * This sealed class hierarchy provides a type-safe way to handle both successful
 * uploads and various error conditions without using exceptions for control flow.
 */
sealed interface UploadResult {
	/**
	 * Indicates a successful upload operation.
	 *
	 * @property filePath The path/key where the file was stored in S3
	 */
	data class Success(val filePath: String) : UploadResult

	/**
	 * Indicates a failed upload operation.
	 *
	 * @property message Human-readable description of the error
	 * @property throwable Optional exception that caused the error, if available
	 */
	data class Error(val message: String, val throwable: Throwable? = null) : UploadResult
}
