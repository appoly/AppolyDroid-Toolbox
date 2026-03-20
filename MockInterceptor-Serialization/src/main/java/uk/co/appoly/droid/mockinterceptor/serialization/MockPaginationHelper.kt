package uk.co.appoly.droid.mockinterceptor.serialization

import uk.co.appoly.droid.mockinterceptor.MockRequestContext

/**
 * Represents a computed page slice from a list of items.
 *
 * This is a pure data class — it contains the sliced items and pagination metadata
 * but does NOT impose any JSON envelope format. Consumers wrap this in their own
 * API-specific response envelope.
 *
 * @param T The type of items in the list.
 * @property items The items in the current page slice.
 * @property page The current page number (1-indexed).
 * @property perPage The number of items per page.
 * @property total The total number of items across all pages.
 * @property lastPage The last page number.
 * @property from The 1-indexed position of the first item on this page (0 if empty).
 * @property to The 1-indexed position of the last item on this page (0 if empty).
 */
data class PageSlice<T>(
	val items: List<T>,
	val page: Int,
	val perPage: Int,
	val total: Int,
	val lastPage: Int,
	val from: Int,
	val to: Int,
)

/**
 * Compute a [PageSlice] from a full list of items using pagination query parameters
 * from the mock request.
 *
 * ```kotlin
 * get("api/items") { request ->
 *     val slice = paginate(allItems, request)
 *     // wrap slice in your API envelope and call jsonBody(...)
 * }
 * ```
 *
 * @param allItems The complete list of items to paginate.
 * @param request The mock request context to read page/perPage query parameters from.
 * @param pageParam The query parameter name for the page number. Defaults to `"page"`.
 * @param perPageParam The query parameter name for items per page. Defaults to `"per_page"`.
 * @param defaultPerPage The default items per page if not specified in the request. Defaults to `10`.
 */
fun <T> paginate(
	allItems: List<T>,
	request: MockRequestContext,
	pageParam: String = "page",
	perPageParam: String = "per_page",
	defaultPerPage: Int = 10,
): PageSlice<T> {
	val page = request.queryParamInt(pageParam, 1).coerceAtLeast(1)
	val perPage = request.queryParamInt(perPageParam, defaultPerPage).coerceAtLeast(1)
	val total = allItems.size
	val lastPage = if (total == 0) 1 else ((total + perPage - 1) / perPage)
	val fromIndex = (page - 1) * perPage
	val pageItems = allItems.drop(fromIndex).take(perPage)
	val from = if (pageItems.isEmpty()) 0 else fromIndex + 1
	val to = if (pageItems.isEmpty()) 0 else (fromIndex + pageItems.size)

	return PageSlice(
		items = pageItems,
		page = page,
		perPage = perPage,
		total = total,
		lastPage = lastPage,
		from = from,
		to = to,
	)
}
