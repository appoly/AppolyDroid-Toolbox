package uk.co.appoly.droid.data.remote.model.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialization tests for the Appoly JSON response envelopes [BaseResponse],
 * [GenericResponse] and [ErrorBody], covering decode-from-server-JSON and round-trips.
 */
class ResponseModelsTest {

	@Serializable
	private data class Item(val id: Int, val name: String)

	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun `BaseResponse decodes success with and without message`() {
		assertEquals(
			BaseResponse(success = true),
			json.decodeFromString<BaseResponse>("""{"success":true}""")
		)
		val withMsg = json.decodeFromString<BaseResponse>(
			"""{"success":true,"message":"Operation completed successfully"}"""
		)
		assertTrue(withMsg.success)
		assertEquals("Operation completed successfully", withMsg.message)
	}

	@Test
	fun `GenericResponse decodes a typed data payload`() {
		val decoded = json.decodeFromString<GenericResponse<Item>>(
			"""{"success":true,"message":"ok","data":{"id":123,"name":"John Doe"}}"""
		)
		assertTrue(decoded.success)
		assertEquals("ok", decoded.message)
		assertEquals(Item(123, "John Doe"), decoded.data)
	}

	@Test
	fun `GenericResponse decodes a list payload`() {
		val decoded = json.decodeFromString<GenericResponse<List<Item>>>(
			"""{"success":true,"data":[{"id":1,"name":"Item 1"},{"id":2,"name":"Item 2"}]}"""
		)
		assertTrue(decoded.success)
		assertEquals(listOf(Item(1, "Item 1"), Item(2, "Item 2")), decoded.data)
	}

	@Test
	fun `GenericResponse defaults are applied for an error-shaped body`() {
		val decoded = json.decodeFromString<GenericResponse<Item>>(
			"""{"success":false,"message":"Resource not found","data":null}"""
		)
		assertEquals(false, decoded.success)
		assertEquals("Resource not found", decoded.message)
		assertNull(decoded.data)
	}

	@Test
	fun `GenericResponse round-trips`() {
		val original = GenericResponse(success = true, message = "ok", data = Item(9, "Nine"))
		val restored = json.decodeFromString<GenericResponse<Item>>(json.encodeToString(original))
		assertEquals(original, restored)
	}

	@Test
	fun `ErrorBody decodes field validation errors`() {
		val decoded = json.decodeFromString<ErrorBody>(
			"""{"success":false,"message":"Validation failed","errors":{"email":["Email is required","Email format is invalid"],"password":["Password must be at least 8 characters long"]}}"""
		)
		assertEquals(false, decoded.success)
		assertEquals("Validation failed", decoded.message)
		assertEquals(
			listOf("Email is required", "Email format is invalid"),
			decoded.errors?.get("email")
		)
		assertEquals(1, decoded.errors?.get("password")?.size)
	}

	@Test
	fun `ErrorBody defaults a general error with no errors map`() {
		val decoded = json.decodeFromString<ErrorBody>(
			"""{"success":false,"message":"Authentication failed"}"""
		)
		assertEquals(false, decoded.success)
		assertEquals("Authentication failed", decoded.message)
		assertNull(decoded.errors)
	}
}
