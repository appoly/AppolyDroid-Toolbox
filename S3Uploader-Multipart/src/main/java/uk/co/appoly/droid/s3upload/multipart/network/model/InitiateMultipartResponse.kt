package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from initiating a multipart upload.
 *
 * Supports both wrapped (with success/message/data envelope) and unwrapped formats.
 * When the response is unwrapped, the upload_id and file_path are at the root level,
 * and [data] will return a synthesized [InitiateMultipartData] from those fields.
 */
@Serializable
data class InitiateMultipartResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("data")
    private val _data: InitiateMultipartData? = null,

    // Fields for unwrapped response format
    @SerialName("upload_id")
    private val uploadId: String? = null,
    @SerialName("file_path")
    private val filePath: String? = null,
    private val key: String? = null,
    private val bucket: String? = null
) {
    /**
     * Returns the data, either from the wrapped [_data] field or synthesized from
     * root-level fields in an unwrapped response.
     */
    val data: InitiateMultipartData?
        get() = _data ?: if (uploadId != null && filePath != null) {
            InitiateMultipartData(
                uploadId = uploadId,
                filePath = filePath,
                key = key,
                bucket = bucket
            )
        } else null
}

@Serializable
data class InitiateMultipartData(
    @SerialName("upload_id")
    val uploadId: String,

    @SerialName("file_path")
    val filePath: String,

    val key: String? = null,
    val bucket: String? = null
)
