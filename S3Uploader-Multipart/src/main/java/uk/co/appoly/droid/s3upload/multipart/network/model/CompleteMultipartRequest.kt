package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for completing a multipart upload.
 */
@Serializable
data class CompleteMultipartRequest(
    @SerialName("upload_id")
    val uploadId: String,

    @SerialName("file_path")
    val filePath: String,

    val parts: List<CompletedPart>
)

/**
 * Represents a completed part with its part number and ETag.
 */
@Serializable
data class CompletedPart(
    @SerialName("part_number")
    val partNumber: Int,

    val etag: String
)
