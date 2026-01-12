package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Holds the API endpoint URLs for multipart upload operations.
 *
 * These URLs point to your backend which proxies requests to AWS S3.
 */
@Serializable
data class MultipartApiUrls(
    /** URL for initiating a multipart upload */
    @SerialName("initiate_url")
    val initiateUrl: String,

    /** URL for getting pre-signed URL for each part */
    @SerialName("presign_part_url")
    val presignPartUrl: String,

    /** URL for completing the multipart upload */
    @SerialName("complete_url")
    val completeUrl: String,

    /** URL for aborting the multipart upload */
    @SerialName("abort_url")
    val abortUrl: String
) {
    companion object {
        /**
         * Creates MultipartApiUrls from a base URL.
         *
         * Assumes standard endpoint paths:
         * - /initiate
         * - /presign-part
         * - /complete
         * - /abort
         *
         * @param baseUrl Base URL (e.g., "https://api.example.com/api/s3/multipart")
         */
        fun fromBaseUrl(baseUrl: String): MultipartApiUrls {
            val base = baseUrl.trimEnd('/')
            return MultipartApiUrls(
                initiateUrl = "$base/initiate",
                presignPartUrl = "$base/presign-part",
                completeUrl = "$base/complete",
                abortUrl = "$base/abort"
            )
        }
    }
}
