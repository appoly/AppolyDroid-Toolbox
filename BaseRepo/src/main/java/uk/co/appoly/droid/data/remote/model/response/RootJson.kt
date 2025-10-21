package uk.co.appoly.droid.data.remote.model.response

/**
 * Root interface for all API response models.
 *
 * This interface defines the common properties shared by all API responses,
 * including success status and an optional message.
 *
 * Example JSON for a successful response:
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
 * Example JSON for an error response:
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
interface RootJson {
	val success: Boolean
	val message: String?
}

