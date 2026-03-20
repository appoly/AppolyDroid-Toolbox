package uk.co.appoly.droid.mockinterceptor

import okhttp3.Request
import okio.Buffer

/**
 * Provides access to the matched request's path parameters, query parameters, and body
 * within a mock route handler.
 *
 * @property request The original OkHttp [Request].
 */
class MockRequestContext internal constructor(
	val request: Request,
	private val pathParams: Map<String, String>,
) {

	/**
	 * Get a path parameter value by [name].
	 *
	 * @throws IllegalStateException if the parameter is not present in the matched route.
	 */
	fun pathParam(name: String): String =
		pathParams[name] ?: error("No path parameter '$name' found in matched route")

	/** Get a path parameter as [Int], returning [default] if absent or not a valid integer. */
	fun pathParamInt(name: String, default: Int = 0): Int =
		pathParams[name]?.toIntOrNull() ?: default

	/** Get a path parameter as [Long], returning [default] if absent or not a valid long. */
	fun pathParamLong(name: String, default: Long = 0L): Long =
		pathParams[name]?.toLongOrNull() ?: default

	/** Get a query parameter value by [name], or `null` if not present. */
	fun queryParam(name: String): String? =
		request.url.queryParameter(name)

	/** Get a query parameter as [Int], returning [default] if absent or not a valid integer. */
	fun queryParamInt(name: String, default: Int): Int =
		queryParam(name)?.toIntOrNull() ?: default

	/** Get a query parameter as [Long], returning [default] if absent or not a valid long. */
	fun queryParamLong(name: String, default: Long): Long =
		queryParam(name)?.toLongOrNull() ?: default

	/** Read the request body as a UTF-8 string, or `null` if there is no body. */
	fun bodyString(): String? {
		val body = request.body ?: return null
		val buffer = Buffer()
		body.writeTo(buffer)
		return buffer.readUtf8()
	}
}
