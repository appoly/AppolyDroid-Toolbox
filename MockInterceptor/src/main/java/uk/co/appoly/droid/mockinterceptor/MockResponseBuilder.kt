package uk.co.appoly.droid.mockinterceptor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Builder for constructing mock HTTP responses.
 *
 * Used as the receiver in route handler lambdas. Methods return `this` for chaining.
 *
 * ```kotlin
 * get("api/users/{id}") { request ->
 *     jsonBody("""{"id":${request.pathParamInt("id")}}""")
 * }
 * ```
 */
class MockResponseBuilder {
	private var statusCode: Int = 200
	private var statusMessage: String = "OK"
	private var body: String? = null
	private var contentType: String = "application/json"
	private val headers: MutableMap<String, String> = mutableMapOf()
	internal var delayOverride: Long? = null
		private set

	/** Set the HTTP status [code] and optional status [message]. */
	fun status(code: Int, message: String = "OK"): MockResponseBuilder = apply {
		statusCode = code
		statusMessage = message
	}

	/** Add a response header. */
	fun header(name: String, value: String): MockResponseBuilder = apply {
		headers[name] = value
	}

	/** Set a raw JSON string as the response body with `application/json` content type. */
	fun jsonBody(json: String): MockResponseBuilder = apply {
		body = json
		contentType = "application/json"
	}

	/**
	 * Load a JSON response from a classpath resource file.
	 *
	 * Place JSON files in `src/debug/resources/` (or the appropriate source set's resources
	 * directory). For example, a file at `src/debug/resources/mock/categories.json` would be
	 * loaded with `jsonFile("mock/categories.json")`.
	 */
	fun jsonFile(resourcePath: String): MockResponseBuilder = apply {
		val stream = Thread.currentThread().contextClassLoader
			?.getResourceAsStream(resourcePath)
			?: error("Mock JSON resource not found on classpath: $resourcePath")
		body = stream.bufferedReader().use { it.readText() }
		contentType = "application/json"
	}

	/** Return a 204 No Content response with no body. */
	fun emptyBody(): MockResponseBuilder = apply {
		statusCode = 204
		statusMessage = "No Content"
		body = null
	}

	/** Override the default delay for this specific route response. */
	fun delay(ms: Long): MockResponseBuilder = apply {
		delayOverride = ms
	}

	// -- Status code helpers --

	/** Set status to 400 Bad Request. Chain with a body method to include a response. */
	fun badRequest(): MockResponseBuilder = apply {
		statusCode = 400
		statusMessage = "Bad Request"
	}

	/** Set status to 401 Unauthorized. */
	fun unauthorized(): MockResponseBuilder = apply {
		statusCode = 401
		statusMessage = "Unauthorized"
	}

	/** Set status to 403 Forbidden. */
	fun forbidden(): MockResponseBuilder = apply {
		statusCode = 403
		statusMessage = "Forbidden"
	}

	/** Set status to 404 Not Found. */
	fun notFound(): MockResponseBuilder = apply {
		statusCode = 404
		statusMessage = "Not Found"
	}

	/** Set status to 500 Internal Server Error. */
	fun serverError(): MockResponseBuilder = apply {
		statusCode = 500
		statusMessage = "Internal Server Error"
	}

	internal fun build(request: Request, protocol: Protocol): Response {
		val responseBody = (body ?: "").toResponseBody(contentType.toMediaType())
		return Response.Builder()
			.request(request)
			.protocol(protocol)
			.code(statusCode)
			.message(statusMessage)
			.apply { headers.forEach { (name, value) -> header(name, value) } }
			.body(responseBody)
			.build()
	}
}
