package uk.co.appoly.droid.data.remote.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for paginated API responses with a nested data structure.
 *
 * This class represents paginated API responses where pagination metadata is nested
 * inside a data field, following the standard response format with success and message fields.
 *
 * Example JSON:
 * ```json
 * {
 *   "success": true,
 *   "message": "Data retrieved successfully",
 *   "data": {
 *     "data": [
 *       { "id": 1, "name": "Item 1" },
 *       { "id": 2, "name": "Item 2" }
 *     ],
 *     "current_page": 1,
 *     "last_page": 5,
 *     "per_page": 10,
 *     "from": 1,
 *     "to": 10,
 *     "total": 48
 *   }
 * }
 * ```
 *
 * @param T The type of items in the paginated list
 * @property success Indicates whether the API request was successful
 * @property message Optional message providing additional information about the response
 * @property pageData Nested page data containing the items and pagination metadata
 */
@Serializable
data class GenericNestedPagedResponse<T>(
	override val success: Boolean = false,
	override val message: String?,
	@SerialName("data")
	val pageData: NestedPageData<T>?
) : RootJsonPage<T> {
	override fun hasData(): Boolean = pageData != null

	override fun asPageData(): PageData<T> = PageData(
		data = pageData?.data ?: emptyList(),
		currentPage = pageData?.currentPage ?: 1,
		lastPage = pageData?.lastPage ?: 1,
		perPage = pageData?.perPage ?: 0,
		from = pageData?.from ?: 0,
		to = pageData?.to ?: 0,
		total = pageData?.total ?: 0
	)
}

/**
 * Represents the nested page data structure within the API response.
 *
 * Contains both the list of items and the pagination metadata.
 *
 * @param T The type of items in the paginated list
 * @property data The list of items in the current page
 * @property currentPage The current page number
 * @property lastPage The last page number (total number of pages)
 * @property perPage Number of items per page
 * @property from Index of the first item on the current page
 * @property to Index of the last item on the current page
 * @property total Total number of items across all pages
 */
@Serializable
data class NestedPageData<T>(
	val data: List<T>?,
	@SerialName("current_page")
	val currentPage: Int?,
	@SerialName("last_page")
	val lastPage: Int?,
	@SerialName("per_page")
	val perPage: Int?,
	val from: Int?,
	val to: Int?,
	val total: Int?
)