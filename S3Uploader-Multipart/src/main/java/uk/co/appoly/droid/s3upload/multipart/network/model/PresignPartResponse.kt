package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response containing a pre-signed URL for uploading a part.
 *
 * Supports both wrapped (with success/message/data envelope) and unwrapped formats.
 */
@Serializable
data class PresignPartResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("data")
    private val _data: PresignPartData? = null,

    // Fields for unwrapped response format
    @SerialName("presigned_url")
    private val presignedUrl: String? = null,
    @SerialName("part_number")
    private val partNumber: Int? = null,
    @Serializable(with = EmptyArrayAsEmptyMapSerializer::class)
    private val headers: Map<String, String> = emptyMap()
) {
    /**
     * Returns the data, either from the wrapped [_data] field or synthesized from
     * root-level fields in an unwrapped response.
     */
    val data: PresignPartData?
        get() = _data ?: if (presignedUrl != null && partNumber != null) {
            PresignPartData(
                presignedUrl = presignedUrl,
                partNumber = partNumber,
                headers = headers
            )
        } else null
}

@Serializable
data class PresignPartData(
    @SerialName("presigned_url")
    val presignedUrl: String,

    @SerialName("part_number")
    val partNumber: Int,

    @Serializable(with = EmptyArrayAsEmptyMapSerializer::class)
    val headers: Map<String, String> = emptyMap()
)
