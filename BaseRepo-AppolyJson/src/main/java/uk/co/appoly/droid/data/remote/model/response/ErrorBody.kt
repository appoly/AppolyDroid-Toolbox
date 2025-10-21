package uk.co.appoly.droid.data.remote.model.response

import kotlinx.serialization.Serializable

/**
 * Error response model for API error responses.
 *
 * This class represents error responses from the API, including validation errors
 * with field-specific error messages.
 *
 * Example JSON for a general error:
 * ```json
 * {
 *   "success": false,
 *   "message": "Authentication failed"
 * }
 * ```
 *
 * Example JSON for validation errors:
 * ```json
 * {
 *   "success": false,
 *   "message": "Validation failed",
 *   "errors": {
 *     "email": ["Email is required", "Email format is invalid"],
 *     "password": ["Password must be at least 8 characters long"]
 *   }
 * }
 * ```
 *
 * @property success Always false for error responses
 * @property message General error message describing the failure
 * @property errors Optional map of field names to lists of error messages for field-specific validation errors
 */
@Serializable
data class ErrorBody(
	override val success: Boolean = false,
	override val message: String? = null,
	val errors: Map<String, List<String>>? = null
) : RootJson