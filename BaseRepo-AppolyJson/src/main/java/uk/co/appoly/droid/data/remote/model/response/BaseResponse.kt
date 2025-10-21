package uk.co.appoly.droid.data.remote.model.response

import kotlinx.serialization.Serializable

/**
 * Base response model for API responses.
 *
 * This class represents the common structure shared by all API responses
 * in the system, containing success status and an optional message.
 *
 * Example JSON:
 * ```json
 * {
 *   "success": true
 * }
 * ```
 *
 * Or with a message:
 * ```json
 * {
 *   "success": true,
 *   "message": "Operation completed successfully"
 * }
 * ```
 *
 * Or for an error (a message, while optional, is expected to be present in error cases):
 * ```json
 * {
 *   "success": false,
 *   "message": "Invalid credentials"
 * }
 * ```
 *
 * @property success Indicates whether the API request was successful
 * @property message Optional message providing additional information about the response
 */
@Serializable
data class BaseResponse(
	override val success: Boolean,
	override val message: String? = null
) : RootJson