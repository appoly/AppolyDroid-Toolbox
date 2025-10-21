package uk.co.appoly.droid.data.remote.model.response

import kotlinx.serialization.Serializable

/**
 * Generic response model for API responses that include data payload.
 *
 * This class extends the common response structure by adding a typed data field
 * that can contain any type of response data.
 *
 * Example JSON for a successful user profile response:
 * ```json
 * {
 *   "success": true,
 *   "message": "Profile retrieved successfully",
 *   "data": {
 *     "id": 123,
 *     "name": "John Doe",
 *     "email": "john.doe@example.com"
 *   }
 * }
 * ```
 *
 * Example JSON for a successful list response:
 * ```json
 * {
 *   "success": true,
 *   "data": [
 *     { "id": 1, "name": "Item 1" },
 *     { "id": 2, "name": "Item 2" }
 *   ]
 * }
 * ```
 *
 * Example JSON for an error:
 * ```json
 * {
 *   "success": false,
 *   "message": "Resource not found",
 *   "data": null
 * }
 * ```
 *
 * @param T The type of data payload contained in the response
 * @property success Indicates whether the API request was successful
 * @property message Optional message providing additional information about the response
 * @property data Optional payload data returned by the API
 */
@Serializable
data class GenericResponse<T>(
	override val success: Boolean = false,
	override val message: String? = null,
	override val data: T? = null
) : RootJsonWithData<T>