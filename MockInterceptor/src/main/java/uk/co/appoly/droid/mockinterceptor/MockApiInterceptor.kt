package uk.co.appoly.droid.mockinterceptor

import okhttp3.Interceptor
import okhttp3.Response
import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LoggingLevel

/**
 * An OkHttp [Interceptor] that matches requests against registered mock routes and returns
 * synthetic responses without hitting the network.
 *
 * Routes are defined via a DSL in the trailing [block] parameter using [MockRouteBuilder].
 * Unmatched requests are passed through to the real network via [Interceptor.Chain.proceed].
 *
 * ```kotlin
 * val interceptor = MockApiInterceptor(tag = "Mock") {
 *     defaultDelay(200L)
 *     get("api/users/{id}") { request ->
 *         jsonBody("""{"id":${request.pathParamInt("id")}}""")
 *     }
 * }
 *
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(interceptor)
 *     .build()
 * ```
 *
 * @param tag Optional log tag. If null, the class name is used.
 * @param logger [FlexiLog] instance for debug logging. Defaults to [MockInterceptorLogger].
 * @param loggingLevel Minimum [LoggingLevel] for log output. Defaults to [LoggingLevel.V].
 * @param block DSL block to define mock routes via [MockRouteBuilder].
 */
class MockApiInterceptor(
	private val tag: String? = null,
	logger: FlexiLog = MockInterceptorLogger,
	loggingLevel: LoggingLevel = LoggingLevel.V,
	block: MockRouteBuilder.() -> Unit,
) : Interceptor {

	private val routes: List<MockRoute>
	private val defaultDelay: Long

	init {
		MockInterceptorLog.updateLogger(logger, loggingLevel)
		val builder = MockRouteBuilder()
		builder.block()
		routes = builder.build()
		defaultDelay = builder.defaultDelay
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val method = request.method
		val url = request.url
		val pathSegments = url.pathSegments

		for (route in routes) {
			if (!method.equals(route.method, ignoreCase = true)) continue
			val params = matchPath(route.pathSegments, pathSegments) ?: continue

			log("Matched: ${route.method} ${route.pathPattern} -> ${url.encodedPath}")

			val context = MockRequestContext(request, params)
			val responseBuilder = MockResponseBuilder().apply { route.handler(this, context) }

			val delay = responseBuilder.delayOverride ?: route.delay ?: defaultDelay
			if (delay > 0) {
				Thread.sleep(delay)
			}

			return responseBuilder.build(request, chain.connection()?.protocol() ?: okhttp3.Protocol.HTTP_1_1)
		}

		log("No match, passing through: $method ${url.encodedPath}")
		return chain.proceed(request)
	}

	private fun matchPath(
		routeSegments: List<String>,
		requestSegments: List<String>,
	): Map<String, String>? {
		if (routeSegments.size != requestSegments.size) return null

		val params = mutableMapOf<String, String>()
		for (i in routeSegments.indices) {
			val routeSeg = routeSegments[i]
			val requestSeg = requestSegments[i]

			if (routeSeg.startsWith("{") && routeSeg.endsWith("}")) {
				val paramName = routeSeg.substring(1, routeSeg.length - 1)
				params[paramName] = requestSeg
			} else if (!routeSeg.equals(requestSeg, ignoreCase = true)) {
				return null
			}
		}
		return params
	}

	private fun log(message: String) {
		if (tag.isNullOrBlank()) {
			MockInterceptorLog.d(this, message)
		} else {
			MockInterceptorLog.d(tag, message)
		}
	}
}
