package uk.co.appoly.droid.mockinterceptor.appolyjson

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Internal envelope model matching the Appoly JSON base response format:
 * `{ "success": bool, "message": "..." }`
 */
@Serializable
@PublishedApi internal data class AppolyBaseEnvelope(
	val success: Boolean,
	val message: String? = null,
)

/**
 * Internal envelope model matching the Appoly JSON generic response format:
 * `{ "success": bool, "message": "...", "data": ... }`
 *
 * Uses [JsonElement] for the data field so any serializable type can be encoded
 * without requiring this module to know the concrete type at compile time.
 */
@Serializable
@PublishedApi internal data class AppolySuccessEnvelope(
	val success: Boolean,
	val message: String? = null,
	val data: JsonElement? = null,
)

/**
 * Internal envelope model matching the Appoly JSON nested paginated response format:
 * ```json
 * {
 *   "success": true,
 *   "message": null,
 *   "data": {
 *     "data": [...],
 *     "current_page": 1,
 *     "last_page": 3,
 *     "per_page": 10,
 *     "from": 1,
 *     "to": 10,
 *     "total": 25
 *   }
 * }
 * ```
 */
@Serializable
@PublishedApi internal data class AppolyPagedEnvelope(
	val success: Boolean,
	val message: String? = null,
	val data: AppolyNestedPageData? = null,
)

/**
 * Internal model for the nested page data structure.
 *
 * Uses [JsonElement] for the items list so that any serializable item type can be encoded.
 */
@Serializable
@PublishedApi internal data class AppolyNestedPageData(
	val data: JsonElement,
	@SerialName("current_page")
	val currentPage: Int,
	@SerialName("last_page")
	val lastPage: Int,
	@SerialName("per_page")
	val perPage: Int,
	val from: Int?,
	val to: Int?,
	val total: Int,
)
