package uk.co.appoly.droid.mockinterceptor.appolyjson

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import uk.co.appoly.droid.mockinterceptor.MockRequestContext
import uk.co.appoly.droid.mockinterceptor.MockResponseBuilder
import uk.co.appoly.droid.mockinterceptor.serialization.PageSlice
import uk.co.appoly.droid.mockinterceptor.serialization.defaultMockJson
import uk.co.appoly.droid.mockinterceptor.serialization.jsonBody
import uk.co.appoly.droid.mockinterceptor.serialization.paginate

/**
 * Build a paginated success response using the Appoly JSON nested pagination format:
 * ```json
 * {
 *   "success": true,
 *   "data": {
 *     "data": [...items...],
 *     "current_page": 1,
 *     "last_page": 3,
 *     "per_page": 10,
 *     "from": 1,
 *     "to": 10,
 *     "total": 25
 *   }
 * }
 * ```
 *
 * Reads `page` and `per_page` query parameters from the request, slices the items list,
 * and produces the full paginated envelope.
 *
 * ```kotlin
 * get("api/items") { request ->
 *     pagedBody(allItems, request)
 * }
 * ```
 *
 * @param items The complete list of items to paginate.
 * @param request The mock request context (for reading page/per_page query params).
 * @param pageParam The query parameter name for page number. Defaults to `"page"`.
 * @param perPageParam The query parameter name for items per page. Defaults to `"per_page"`.
 * @param defaultPerPage Default items per page. Defaults to `10`.
 * @param json The [Json] instance for serialization.
 */
inline fun <reified T> MockResponseBuilder.pagedBody(
	items: List<T>,
	request: MockRequestContext,
	pageParam: String = "page",
	perPageParam: String = "per_page",
	defaultPerPage: Int = 10,
	json: Json = defaultMockJson,
): MockResponseBuilder = apply {
	val slice: PageSlice<T> = paginate(items, request, pageParam, perPageParam, defaultPerPage)
	pagedBody(slice, json)
}

/**
 * Build a paginated success response from a pre-computed [PageSlice].
 *
 * Useful when you need custom pagination logic beyond what [paginate] provides.
 */
inline fun <reified T> MockResponseBuilder.pagedBody(
	slice: PageSlice<T>,
	json: Json = defaultMockJson,
): MockResponseBuilder = apply {
	val itemsElement = json.encodeToJsonElement(slice.items)
	jsonBody(
		AppolyPagedEnvelope(
			success = true,
			message = null,
			data = AppolyNestedPageData(
				data = itemsElement,
				currentPage = slice.page,
				lastPage = slice.lastPage,
				perPage = slice.perPage,
				from = if (slice.items.isEmpty()) null else slice.from,
				to = if (slice.items.isEmpty()) null else slice.to,
				total = slice.total,
			),
		),
		json,
	)
}

/**
 * Build an empty paginated success response.
 *
 * ```kotlin
 * get("api/items") { request ->
 *     emptyPage()
 * }
 * ```
 */
fun MockResponseBuilder.emptyPage(
	json: Json = defaultMockJson,
): MockResponseBuilder = apply {
	jsonBody(
		AppolyPagedEnvelope(
			success = true,
			message = null,
			data = AppolyNestedPageData(
				data = kotlinx.serialization.json.JsonArray(emptyList()),
				currentPage = 1,
				lastPage = 1,
				perPage = 10,
				from = null,
				to = null,
				total = 0,
			),
		),
		json,
	)
}
