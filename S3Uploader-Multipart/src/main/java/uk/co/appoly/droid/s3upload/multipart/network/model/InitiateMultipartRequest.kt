package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for initiating a multipart upload.
 */
@Serializable
data class InitiateMultipartRequest(
    @SerialName("file_name")
    val fileName: String,

    @SerialName("content_type")
    val contentType: String? = null
)
