package uk.co.appoly.droid.mockinterceptor.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import uk.co.appoly.droid.mockinterceptor.MockResponseBuilder

/**
 * Default [Json] instance used by serialization extensions.
 *
 * Configured with `encodeDefaults = true` so that all fields appear in mock responses,
 * matching real API behavior where default values are typically included.
 */
val defaultMockJson: Json = Json {
	encodeDefaults = true
	ignoreUnknownKeys = true
}

/**
 * Serialize a `@Serializable` object as the JSON response body.
 *
 * ```kotlin
 * get("api/users/{id}") { request ->
 *     jsonBody(User(id = 1, name = "Test"), json)
 * }
 * ```
 *
 * @param data The object to serialize. Must be annotated with `@Serializable`.
 * @param json The [Json] instance to use for serialization. Defaults to [defaultMockJson].
 */
inline fun <reified T> MockResponseBuilder.jsonBody(
	data: T,
	json: Json = defaultMockJson,
): MockResponseBuilder = apply {
	jsonBody(json.encodeToString(serializer<T>(), data))
}
