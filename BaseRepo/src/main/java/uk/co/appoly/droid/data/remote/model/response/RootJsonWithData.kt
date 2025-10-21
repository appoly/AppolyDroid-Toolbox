package uk.co.appoly.droid.data.remote.model.response

/**
 * Extended root interface for API responses that include a data payload.
 *
 * This interface builds upon the base RootJson interface by adding a typed data field
 * that can contain any type of response data.
 *
 * Example JSON for a successful response with data:
 * ```json
 * {
 *   "success": true,
 *   "message": "Data retrieved successfully",
 *   "data": {
 *     "id": 123,
 *     "name": "John Doe"
 *   }
 * }
 * ```
 *
 * Example JSON for an error response with no data:
 * ```json
 * {
 *   "success": false,
 *   "message": "Resource not found",
 *   "data": null
 * }
 * ```
 *
 * @param T The type of data payload contained in the response
 * @property data Optional payload data returned by the API
 */
interface RootJsonWithData<T> : RootJson {
	val data: T?
}