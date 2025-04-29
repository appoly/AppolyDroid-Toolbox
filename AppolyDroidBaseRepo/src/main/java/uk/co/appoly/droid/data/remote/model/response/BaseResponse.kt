package uk.co.appoly.droid.data.remote.model.response

import kotlinx.serialization.Serializable

@Serializable
data class BaseResponse(
	val success: Boolean,
	val message: String? = null
)