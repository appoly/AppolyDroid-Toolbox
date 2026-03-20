package uk.co.appoly.droid.mockinterceptor.appolyjson

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.appoly.droid.mockinterceptor.MockApiInterceptor
import uk.co.appoly.droid.mockinterceptor.MockRouteBuilder

class MockAppolyJsonTest {

	@Serializable
	data class TestUser(val id: Int, val name: String)

	private val json = Json { ignoreUnknownKeys = true }

	// -- successBody --

	@Test
	fun `successBody wraps data in envelope`() {
		val client = buildClient {
			get("api/user") {
				successBody(TestUser(1, "Alice"))
			}
		}
		val response = client.get("api/user")
		assertEquals(200, response.code)

		val obj = json.parseToJsonElement(response.body.string()).jsonObject
		assertTrue(obj["success"]!!.jsonPrimitive.boolean)
		val data = obj["data"]!!.jsonObject
		assertEquals(1, data["id"]!!.jsonPrimitive.int)
		assertEquals("Alice", data["name"]!!.jsonPrimitive.content)
	}

	@Test
	fun `successBody with list`() {
		val client = buildClient {
			get("api/users") {
				successBody(listOf(TestUser(1, "Alice"), TestUser(2, "Bob")))
			}
		}
		val obj = json.parseToJsonElement(client.get("api/users").body.string()).jsonObject
		assertTrue(obj["success"]!!.jsonPrimitive.boolean)
		assertEquals(2, obj["data"]!!.jsonArray.size)
	}

	@Test
	fun `successBody with message`() {
		val client = buildClient {
			get("api/user") {
				successBody(TestUser(1, "Alice"), message = "Found")
			}
		}
		val obj = json.parseToJsonElement(client.get("api/user").body.string()).jsonObject
		assertEquals("Found", obj["message"]!!.jsonPrimitive.content)
	}

	// -- successMessage --

	@Test
	fun `successMessage returns message-only envelope`() {
		val client = buildClient {
			delete("api/item") {
				successMessage("Item deleted")
			}
		}
		val response = client.delete("api/item")
		assertEquals(200, response.code)

		val obj = json.parseToJsonElement(response.body.string()).jsonObject
		assertTrue(obj["success"]!!.jsonPrimitive.boolean)
		assertEquals("Item deleted", obj["message"]!!.jsonPrimitive.content)
	}

	// -- errorBody --

	@Test
	fun `errorBody sets status and envelope`() {
		val client = buildClient {
			get("api/error") {
				errorBody("Not found", code = 404)
			}
		}
		val response = client.get("api/error")
		assertEquals(404, response.code)

		val obj = json.parseToJsonElement(response.body.string()).jsonObject
		assertEquals(false, obj["success"]!!.jsonPrimitive.boolean)
		assertEquals("Not found", obj["message"]!!.jsonPrimitive.content)
	}

	@Test
	fun `errorBody defaults to 400`() {
		val client = buildClient {
			get("api/bad") {
				errorBody("Bad request")
			}
		}
		assertEquals(400, client.get("api/bad").code)
	}

	// -- pagedBody --

	@Test
	fun `pagedBody returns nested pagination structure`() {
		val items = (1..12).map { TestUser(it, "User $it") }
		val client = buildClient {
			get("api/users") { request ->
				pagedBody(items, request, defaultPerPage = 5)
			}
		}
		val response = client.get("api/users?page=1")
		assertEquals(200, response.code)

		val root = json.parseToJsonElement(response.body.string()).jsonObject
		assertTrue(root["success"]!!.jsonPrimitive.boolean)

		val data = root["data"]!!.jsonObject
		assertEquals(5, data["data"]!!.jsonArray.size)
		assertEquals(1, data["current_page"]!!.jsonPrimitive.int)
		assertEquals(3, data["last_page"]!!.jsonPrimitive.int)
		assertEquals(5, data["per_page"]!!.jsonPrimitive.int)
		assertEquals(1, data["from"]!!.jsonPrimitive.int)
		assertEquals(5, data["to"]!!.jsonPrimitive.int)
		assertEquals(12, data["total"]!!.jsonPrimitive.int)
	}

	@Test
	fun `pagedBody page 2`() {
		val items = (1..12).map { TestUser(it, "User $it") }
		val client = buildClient {
			get("api/users") { request ->
				pagedBody(items, request, defaultPerPage = 5)
			}
		}
		val root = json.parseToJsonElement(
			client.get("api/users?page=2&per_page=5").body.string()
		).jsonObject
		val data = root["data"]!!.jsonObject
		assertEquals(5, data["data"]!!.jsonArray.size)
		assertEquals(2, data["current_page"]!!.jsonPrimitive.int)
		assertEquals(6, data["from"]!!.jsonPrimitive.int)
		assertEquals(10, data["to"]!!.jsonPrimitive.int)
	}

	@Test
	fun `pagedBody last partial page has correct from and to`() {
		val items = (1..12).map { TestUser(it, "User $it") }
		val client = buildClient {
			get("api/users") { request ->
				pagedBody(items, request, defaultPerPage = 5)
			}
		}
		val root = json.parseToJsonElement(
			client.get("api/users?page=3&per_page=5").body.string()
		).jsonObject
		val data = root["data"]!!.jsonObject
		assertEquals(2, data["data"]!!.jsonArray.size)
		assertEquals(11, data["from"]!!.jsonPrimitive.int)
		assertEquals(12, data["to"]!!.jsonPrimitive.int)
	}

	// -- emptyPage --

	@Test
	fun `emptyPage returns empty pagination envelope`() {
		val client = buildClient {
			get("api/empty") {
				emptyPage()
			}
		}
		val root = json.parseToJsonElement(client.get("api/empty").body.string()).jsonObject
		assertTrue(root["success"]!!.jsonPrimitive.boolean)

		val data = root["data"]!!.jsonObject
		assertEquals(0, data["data"]!!.jsonArray.size)
		assertEquals(0, data["total"]!!.jsonPrimitive.int)
		assertEquals("null", data.getValue("from").toString())
		assertEquals("null", data.getValue("to").toString())
	}

	// -- Helpers --

	private fun buildClient(block: MockRouteBuilder.() -> Unit): OkHttpClient {
		val interceptor = MockApiInterceptor(tag = "Test") {
			defaultDelay(0L)
			block()
		}
		return OkHttpClient.Builder().addInterceptor(interceptor).build()
	}

	private fun OkHttpClient.get(path: String): Response =
		newCall(Request.Builder().url("https://mock.test/$path").build()).execute()

	private fun OkHttpClient.delete(path: String): Response =
		newCall(Request.Builder().url("https://mock.test/$path").delete().build()).execute()
}
