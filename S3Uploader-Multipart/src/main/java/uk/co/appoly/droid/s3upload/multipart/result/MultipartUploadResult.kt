package uk.co.appoly.droid.s3upload.multipart.result

/**
 * Sealed interface representing the result of a multipart upload operation.
 */
sealed interface MultipartUploadResult {

    /**
     * Upload completed successfully.
     *
     * @property sessionId The local session ID
     * @property filePath The S3 file path where the file was stored
     * @property location The full S3 URL (if provided by server)
     */
    data class Success(
        val sessionId: String,
        val filePath: String,
        val location: String? = null
    ) : MultipartUploadResult

    /**
     * Upload failed with an error.
     *
     * @property sessionId The local session ID
     * @property message Error message
     * @property throwable The underlying exception, if any
     * @property isRecoverable Whether the upload can potentially be recovered/resumed
     */
    data class Error(
        val sessionId: String,
        val message: String,
        val throwable: Throwable? = null,
        val isRecoverable: Boolean = false
    ) : MultipartUploadResult

    /**
     * Upload was paused (either manually or due to conditions).
     *
     * @property sessionId The local session ID
     * @property uploadedParts Number of parts that have been uploaded
     * @property totalParts Total number of parts
     * @property uploadedBytes Total bytes uploaded so far
     * @property totalBytes Total file size in bytes
     */
    data class Paused(
        val sessionId: String,
        val uploadedParts: Int,
        val totalParts: Int,
        val uploadedBytes: Long,
        val totalBytes: Long
    ) : MultipartUploadResult

    /**
     * Upload was cancelled/aborted.
     *
     * @property sessionId The local session ID
     */
    data class Cancelled(
        val sessionId: String
    ) : MultipartUploadResult
}
