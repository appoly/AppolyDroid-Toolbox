package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for getting a pre-signed URL for a specific part.
 */
@Serializable
data class PresignPartRequest(
    @SerialName("upload_id")
    val uploadId: String,

    @SerialName("file_path")
    val filePath: String,

    @SerialName("part_number")
    val partNumber: Int
)
