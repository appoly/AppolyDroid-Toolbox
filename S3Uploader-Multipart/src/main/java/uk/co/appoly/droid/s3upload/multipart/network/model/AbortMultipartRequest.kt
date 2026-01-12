package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for aborting a multipart upload.
 */
@Serializable
data class AbortMultipartRequest(
    @SerialName("upload_id")
    val uploadId: String,

    @SerialName("file_path")
    val filePath: String
)
