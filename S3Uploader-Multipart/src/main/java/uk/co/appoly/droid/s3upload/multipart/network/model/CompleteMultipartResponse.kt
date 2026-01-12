package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from completing a multipart upload.
 */
@Serializable
data class CompleteMultipartResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: CompleteMultipartData? = null
)

@Serializable
data class CompleteMultipartData(
    @SerialName("file_path")
    val filePath: String,

    val location: String? = null,
    val etag: String? = null
)
