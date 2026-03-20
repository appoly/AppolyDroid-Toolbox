package uk.co.appoly.droid.mockinterceptor.retrofit

import uk.co.appoly.droid.mockinterceptor.MockRouteBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * Define mock routes by referencing methods on a Retrofit API interface.
 *
 * This reads the Retrofit HTTP annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`)
 * at runtime via reflection, extracting the path from each annotation. You then provide
 * mock handlers keyed by [KFunction] references to the interface methods.
 *
 * ```kotlin
 * MockApiInterceptor(tag = "Mock") {
 *     defaultDelay(250L)
 *     mockApi(PhotoLabAPI::class) {
 *         mock(PhotoLabAPI::getCategories) { successBody(categories) }
 *         mock(PhotoLabAPI::getProduct) { request ->
 *             successBody(products[request.pathParamInt("id")])
 *         }
 *     }
 * }
 * ```
 *
 * Only methods that have both a Retrofit HTTP annotation AND a registered handler
 * will produce mock routes. Unhandled methods are silently skipped.
 *
 * @param T The Retrofit API interface type.
 * @param apiInterface The [KClass] of the Retrofit interface.
 * @param block Builder block where you register handlers with [MockApiBuilder.mock].
 */
fun <T : Any> MockRouteBuilder.mockApi(
	apiInterface: KClass<T>,
	block: MockApiBuilder<T>.() -> Unit
) {
	val builder = MockApiBuilder<T>()
	builder.block()

	val javaMethods = apiInterface.java.declaredMethods

	for (javaMethod in javaMethods) {
		val handler = builder.handlers[javaMethod.name] ?: continue
		val (httpMethod, rawPath) = extractRetrofitRoute(javaMethod) ?: continue

		// Strip query string if present in the annotation path (e.g., "api/clips?collection")
		val path = rawPath.substringBefore('?')

		when (httpMethod) {
			"GET" -> get(path, handler)
			"POST" -> post(path, handler)
			"PUT" -> put(path, handler)
			"DELETE" -> delete(path, handler)
			"PATCH" -> patch(path, handler)
		}
	}
}

/**
 * Extract the HTTP method and path from Retrofit annotations on a Java method.
 * Returns `null` if no Retrofit HTTP annotation is found.
 */
private fun extractRetrofitRoute(method: java.lang.reflect.Method): Pair<String, String>? {
	for (annotation in method.annotations) {
		val (httpMethod, path) = when {
			isRetrofitAnnotation(annotation, "GET") -> "GET" to getAnnotationValue(annotation)
			isRetrofitAnnotation(annotation, "POST") -> "POST" to getAnnotationValue(annotation)
			isRetrofitAnnotation(annotation, "PUT") -> "PUT" to getAnnotationValue(annotation)
			isRetrofitAnnotation(annotation, "DELETE") -> "DELETE" to getAnnotationValue(annotation)
			isRetrofitAnnotation(annotation, "PATCH") -> "PATCH" to getAnnotationValue(annotation)
			else -> continue
		}
		if (path != null) return httpMethod to path
	}
	return null
}

/**
 * Check if an annotation is a Retrofit HTTP annotation by class name.
 */
private fun isRetrofitAnnotation(annotation: Annotation, method: String): Boolean {
	return annotation.annotationClass.qualifiedName == "retrofit2.http.$method"
}

/**
 * Extract the `value()` from a Retrofit HTTP annotation via reflection.
 */
private fun getAnnotationValue(annotation: Annotation): String? {
	return try {
		val valueMethod = annotation.annotationClass.java.getMethod("value")
		valueMethod.invoke(annotation) as? String
	} catch (_: Exception) {
		null
	}
}
