package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from completing a multipart upload.
 *
 * Supports both wrapped (with success/message/data envelope) and unwrapped formats.
 */
@Serializable
data class CompleteMultipartResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("data")
    private val _data: CompleteMultipartData? = null,

    // Fields for unwrapped response format
    @SerialName("file_path")
    private val filePath: String? = null,
    private val location: String? = null,
    private val etag: String? = null
) {
    /**
     * Returns the data, either from the wrapped [_data] field or synthesized from
     * root-level fields in an unwrapped response.
     */
    val data: CompleteMultipartData?
        get() = _data ?: if (filePath != null) {
            CompleteMultipartData(
                filePath = filePath,
                location = location,
                etag = etag
            )
        } else null
}

@Serializable
data class CompleteMultipartData(
    @SerialName("file_path")
    val filePath: String,

    val location: String? = null,
    val etag: String? = null
)
