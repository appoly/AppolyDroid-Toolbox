package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from initiating a multipart upload.
 */
@Serializable
data class InitiateMultipartResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: InitiateMultipartData? = null
)

@Serializable
data class InitiateMultipartData(
    @SerialName("upload_id")
    val uploadId: String,

    @SerialName("file_path")
    val filePath: String,

    val key: String? = null,
    val bucket: String? = null
)
