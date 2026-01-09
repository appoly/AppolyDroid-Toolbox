package uk.co.appoly.droid.s3upload.multipart.network.model

/**
 * Result of uploading a part to S3.
 *
 * S3 returns the ETag in the response headers, which is required
 * for completing the multipart upload.
 */
sealed interface S3PartUploadResult {
    /**
     * Part was uploaded successfully.
     *
     * @property etag The ETag returned by S3 for this part
     */
    data class Success(val etag: String) : S3PartUploadResult

    /**
     * Part upload failed with an HTTP error.
     *
     * @property statusCode HTTP status code
     * @property message Error message
     */
    data class HttpError(val statusCode: Int, val message: String) : S3PartUploadResult

    /**
     * Part upload failed with an exception.
     *
     * @property throwable The exception that occurred
     */
    data class Exception(val throwable: Throwable) : S3PartUploadResult
}
