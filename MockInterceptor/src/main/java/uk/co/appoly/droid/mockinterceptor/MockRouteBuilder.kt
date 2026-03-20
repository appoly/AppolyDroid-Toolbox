package uk.co.appoly.droid.mockinterceptor

/**
 * DSL builder for defining mock routes within a [MockApiInterceptor].
 *
 * Routes are matched by HTTP method and path pattern. Path segments wrapped in `{braces}`
 * are captured as named parameters accessible via [MockRequestContext.pathParam].
 */
class MockRouteBuilder internal constructor(
	private val prefix: String = "",
	private val groupDelay: Long? = null,
) {
	private val routes = mutableListOf<MockRoute>()
	internal var defaultDelay: Long = 0L
		private set

	/** Set the default simulated network delay (in milliseconds) applied to all routes. */
	fun defaultDelay(ms: Long) {
		defaultDelay = ms
	}

	/** Register a GET route. */
	fun get(path: String, handler: MockResponseBuilder.(MockRequestContext) -> Unit) {
		addRoute("GET", path, handler)
	}

	/** Register a POST route. */
	fun post(path: String, handler: MockResponseBuilder.(MockRequestContext) -> Unit) {
		addRoute("POST", path, handler)
	}

	/** Register a PUT route. */
	fun put(path: String, handler: MockResponseBuilder.(MockRequestContext) -> Unit) {
		addRoute("PUT", path, handler)
	}

	/** Register a DELETE route. */
	fun delete(path: String, handler: MockResponseBuilder.(MockRequestContext) -> Unit) {
		addRoute("DELETE", path, handler)
	}

	/** Register a PATCH route. */
	fun patch(path: String, handler: MockResponseBuilder.(MockRequestContext) -> Unit) {
		addRoute("PATCH", path, handler)
	}

	/**
	 * Group routes under a common path prefix.
	 *
	 * ```kotlin
	 * group("api/photolab") {
	 *     get("categories") { jsonBody("...") }
	 *     get("products/{id}") { request -> ... }
	 * }
	 * ```
	 *
	 * @param pathPrefix The path prefix to prepend to all routes within the group.
	 * @param delay Optional delay override for all routes in this group.
	 * @param block The DSL block to define routes within this group.
	 */
	fun group(
		pathPrefix: String,
		delay: Long? = null,
		block: MockRouteBuilder.() -> Unit,
	) {
		val fullPrefix = combinePath(prefix, pathPrefix)
		val nested = MockRouteBuilder(prefix = fullPrefix, groupDelay = delay)
		nested.block()
		routes.addAll(nested.build())
	}

	private fun addRoute(
		method: String,
		path: String,
		handler: MockResponseBuilder.(MockRequestContext) -> Unit,
	) {
		val fullPath = combinePath(prefix, path)
		val normalizedPath = fullPath.trimStart('/')
		routes.add(
			MockRoute(
				method = method,
				pathPattern = normalizedPath,
				pathSegments = normalizedPath.split("/"),
				delay = groupDelay,
				handler = handler,
			)
		)
	}

	private fun combinePath(prefix: String, path: String): String {
		val p = prefix.trim('/')
		val s = path.trim('/')
		return if (p.isEmpty()) s else "$p/$s"
	}

	internal fun build(): List<MockRoute> = routes.toList()
}
