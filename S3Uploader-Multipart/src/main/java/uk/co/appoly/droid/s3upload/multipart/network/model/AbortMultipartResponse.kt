package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.Serializable

/**
 * Response from aborting a multipart upload.
 */
@Serializable
data class AbortMultipartResponse(
    val success: Boolean = false,
    val message: String? = null
)
