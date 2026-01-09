package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.s3upload.utils.StringOrListSerialiser

/**
 * Response containing a pre-signed URL for uploading a part.
 */
@Serializable
data class PresignPartResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: PresignPartData? = null
)

@Serializable
data class PresignPartData(
    @SerialName("presigned_url")
    val presignedUrl: String,

    @SerialName("part_number")
    val partNumber: Int,

    val headers: Map<String, @Serializable(with = StringOrListSerialiser::class) String> = emptyMap()
)
