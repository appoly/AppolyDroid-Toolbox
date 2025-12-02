package uk.co.appoly.droid.s3upload

/**
 * Represents the result of a direct upload operation to S3 storage.
 *
 * This sealed class hierarchy provides a type-safe way to handle both successful
 * uploads and various error conditions without using exceptions for control flow.
 */
sealed interface DirectUploadResult {
	/**
	 * Indicates a successful direct upload operation.
	 */
	data object Success : DirectUploadResult

	/**
	 * Indicates a failed direct upload operation.
	 *
	 * @property message Human-readable description of the error
	 * @property throwable Optional exception that caused the error, if available
	 */
	data class Error(val message: String, val throwable: Throwable? = null) : DirectUploadResult
}