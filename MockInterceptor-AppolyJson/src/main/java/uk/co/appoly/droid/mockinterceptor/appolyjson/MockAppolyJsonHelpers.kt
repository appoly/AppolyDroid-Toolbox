package uk.co.appoly.droid.mockinterceptor.appolyjson

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import uk.co.appoly.droid.mockinterceptor.MockResponseBuilder
import uk.co.appoly.droid.mockinterceptor.serialization.defaultMockJson
import uk.co.appoly.droid.mockinterceptor.serialization.jsonBody

/**
 * Build a success response with a typed data payload using the Appoly JSON envelope:
 * `{ "success": true, "message": "...", "data": ... }`
 *
 * ```kotlin
 * get("api/users") {
 *     successBody(listOf(User(1, "Alice"), User(2, "Bob")))
 * }
 * ```
 *
 * @param data The data payload to include in the response. Must be `@Serializable`.
 * @param message Optional message field.
 * @param json The [Json] instance to use for serialization.
 */
inline fun <reified T> MockResponseBuilder.successBody(
	data: T,
	message: String? = null,
	json: Json = defaultMockJson,
): MockResponseBuilder = apply {
	val dataElement: JsonElement = json.encodeToJsonElement(data)
	jsonBody(
		AppolySuccessEnvelope(success = true, message = message, data = dataElement),
		json,
	)
}

/**
 * Build a success response with only a message (no data payload):
 * `{ "success": true, "message": "..." }`
 *
 * ```kotlin
 * delete("api/items/{id}") {
 *     successMessage("Item deleted")
 * }
 * ```
 */
fun MockResponseBuilder.successMessage(
	message: String,
	json: Json = defaultMockJson,
): MockResponseBuilder = apply {
	jsonBody(
		AppolyBaseEnvelope(success = true, message = message),
		json,
	)
}

/**
 * Build an error response with the Appoly JSON envelope:
 * `{ "success": false, "message": "..." }`
 *
 * Also sets the HTTP status code.
 *
 * ```kotlin
 * get("api/items/{id}") { request ->
 *     errorBody("Item not found", code = 404)
 * }
 * ```
 */
fun MockResponseBuilder.errorBody(
	message: String,
	code: Int = 400,
	json: Json = defaultMockJson,
): MockResponseBuilder = apply {
	status(code)
	jsonBody(
		AppolyBaseEnvelope(success = false, message = message),
		json,
	)
}
