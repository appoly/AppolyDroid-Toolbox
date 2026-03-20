package uk.co.appoly.droid.mockinterceptor.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.appoly.droid.mockinterceptor.MockApiInterceptor
import uk.co.appoly.droid.mockinterceptor.MockRouteBuilder

class MockSerializationTest {

	@Serializable
	data class TestUser(val id: Int, val name: String)

	private val json = Json { ignoreUnknownKeys = true }

	// -- jsonBody<T>() tests --

	@Test
	fun `jsonBody serializes data class`() {
		val client = buildClient {
			get("api/user") {
				jsonBody(TestUser(1, "Alice"))
			}
		}
		val body = client.get("api/user").body.string()
		assertEquals("""{"id":1,"name":"Alice"}""", body)
	}

	@Test
	fun `jsonBody serializes list`() {
		val client = buildClient {
			get("api/users") {
				jsonBody(listOf(TestUser(1, "Alice"), TestUser(2, "Bob")))
			}
		}
		val body = client.get("api/users").body.string()
		assertEquals("""[{"id":1,"name":"Alice"},{"id":2,"name":"Bob"}]""", body)
	}

	// -- paginate() tests via interceptor --
	// paginate() requires a MockRequestContext (internal constructor), so we test it
	// end-to-end through the interceptor, encoding the PageSlice fields into JSON.

	@Test
	fun `paginate empty list`() {
		val result = paginateViaInterceptor(emptyList<Int>(), page = 1, perPage = 10)
		assertEquals(0, result["count"]!!.jsonPrimitive.int)
		assertEquals(1, result["page"]!!.jsonPrimitive.int)
		assertEquals(1, result["lastPage"]!!.jsonPrimitive.int)
		assertEquals(0, result["total"]!!.jsonPrimitive.int)
		assertEquals(0, result["from"]!!.jsonPrimitive.int)
		assertEquals(0, result["to"]!!.jsonPrimitive.int)
	}

	@Test
	fun `paginate first page of 23 items`() {
		val result = paginateViaInterceptor((1..23).toList(), page = 1, perPage = 5)
		assertEquals(5, result["count"]!!.jsonPrimitive.int)
		assertEquals(1, result["page"]!!.jsonPrimitive.int)
		assertEquals(5, result["lastPage"]!!.jsonPrimitive.int)
		assertEquals(23, result["total"]!!.jsonPrimitive.int)
		assertEquals(1, result["from"]!!.jsonPrimitive.int)
		assertEquals(5, result["to"]!!.jsonPrimitive.int)
	}

	@Test
	fun `paginate last partial page`() {
		val result = paginateViaInterceptor((1..23).toList(), page = 5, perPage = 5)
		assertEquals(3, result["count"]!!.jsonPrimitive.int)
		assertEquals(5, result["page"]!!.jsonPrimitive.int)
		assertEquals(5, result["lastPage"]!!.jsonPrimitive.int)
		assertEquals(21, result["from"]!!.jsonPrimitive.int)
		assertEquals(23, result["to"]!!.jsonPrimitive.int)
	}

	@Test
	fun `paginate beyond last page`() {
		val result = paginateViaInterceptor((1..23).toList(), page = 10, perPage = 5)
		assertEquals(0, result["count"]!!.jsonPrimitive.int)
		assertEquals(10, result["page"]!!.jsonPrimitive.int)
		assertEquals(0, result["from"]!!.jsonPrimitive.int)
		assertEquals(0, result["to"]!!.jsonPrimitive.int)
	}

	@Test
	fun `paginate perPage 1`() {
		val result = paginateViaInterceptor(listOf("a", "b", "c"), page = 2, perPage = 1)
		assertEquals(1, result["count"]!!.jsonPrimitive.int)
		assertEquals(2, result["page"]!!.jsonPrimitive.int)
		assertEquals(3, result["lastPage"]!!.jsonPrimitive.int)
		assertEquals(2, result["from"]!!.jsonPrimitive.int)
		assertEquals(2, result["to"]!!.jsonPrimitive.int)
	}

	@Test
	fun `paginate exact fit`() {
		val result = paginateViaInterceptor((1..10).toList(), page = 2, perPage = 5)
		assertEquals(5, result["count"]!!.jsonPrimitive.int)
		assertEquals(2, result["page"]!!.jsonPrimitive.int)
		assertEquals(2, result["lastPage"]!!.jsonPrimitive.int)
		assertEquals(6, result["from"]!!.jsonPrimitive.int)
		assertEquals(10, result["to"]!!.jsonPrimitive.int)
	}

	@Test
	fun `paginate page 0 coerced to 1`() {
		val result = paginateViaInterceptor(listOf(1, 2, 3), page = 0, perPage = 10)
		assertEquals(1, result["page"]!!.jsonPrimitive.int)
		assertEquals(3, result["count"]!!.jsonPrimitive.int)
	}

	// -- Helpers --

	/**
	 * Runs paginate() inside an interceptor handler and returns the PageSlice fields as JSON.
	 * This avoids needing to construct MockRequestContext directly (internal constructor).
	 */
	private fun <T> paginateViaInterceptor(
		allItems: List<T>,
		page: Int,
		perPage: Int,
	): JsonObject {
		val client = buildClient {
			get("api/paginate") { request ->
				val slice = paginate(allItems, request, defaultPerPage = perPage)
				jsonBody(
					"""{"count":${slice.items.size},"page":${slice.page},"lastPage":${slice.lastPage},"total":${slice.total},"from":${slice.from},"to":${slice.to}}"""
				)
			}
		}
		val body = client.get("api/paginate?page=$page&per_page=$perPage").body.string()
		return json.parseToJsonElement(body).jsonObject
	}

	private fun buildClient(block: MockRouteBuilder.() -> Unit): OkHttpClient {
		val interceptor = MockApiInterceptor(tag = "Test") {
			defaultDelay(0L)
			block()
		}
		return OkHttpClient.Builder().addInterceptor(interceptor).build()
	}

	private fun OkHttpClient.get(path: String): Response =
		newCall(Request.Builder().url("https://mock.test/$path").build()).execute()
}
