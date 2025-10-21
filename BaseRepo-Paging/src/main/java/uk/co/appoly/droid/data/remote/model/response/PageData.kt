package uk.co.appoly.droid.data.remote.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Flattened representation of paginated data with normalized properties.
 *
 * This class provides a simpler access pattern to the paginated data by
 * extracting the nested data structure into a flat object with non-null
 * properties and additional helper properties for pagination.
 *
 * @param T The type of items in the paginated list
 * @property data The list of items in the current page (never null, empty list if no data)
 * @property currentPage The current page number (1-based)
 * @property lastPage The last page number (total number of pages)
 * @property perPage Number of items per page
 * @property from Index of the first item on the current page
 * @property to Index of the last item on the current page
 * @property total Total number of items across all pages
 * @property itemsBefore Number of items before the current page
 * @property itemsAfter Number of items after the current page
 * @property prevPage Previous page number or null if current page is the first page
 * @property nextPage Next page number or null if current page is the last page
 */
@Serializable
data class PageData<T>(
	val data: List<T>,
	@SerialName("current_page")
	val currentPage: Int,
	@SerialName("last_page")
	val lastPage: Int,
	@SerialName("per_page")
	val perPage: Int,
	val from: Int,
	val to: Int,
	val total: Int
) {
	/**
	 * Number of items before the current page.
	 * Calculated based on the 'from' field.
	 */
	val itemsBefore: Int = if (from > 0) from - 1 else 0

	/**
	 * Number of items after the current page.
	 * Calculated based on the 'to' and 'total' fields.
	 */
	val itemsAfter: Int = if (to < total) total - to else 0

	/**
	 * Previous page number or null if current page is the first page.
	 */
	val prevPage: Int? = if (currentPage > 1) currentPage - 1 else null

	/**
	 * Next page number or null if current page is the last page.
	 */
	val nextPage: Int? = if (currentPage < lastPage) currentPage + 1 else null
}