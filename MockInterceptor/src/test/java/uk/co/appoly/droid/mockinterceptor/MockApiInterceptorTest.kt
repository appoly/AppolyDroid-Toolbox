package uk.co.appoly.droid.mockinterceptor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class MockApiInterceptorTest {

	private val interceptor = MockApiInterceptor(tag = "Test") {
		defaultDelay(0L)

		get("api/hello") {
			jsonBody("""{"message":"hello"}""")
		}

		get("api/users/{id}") { request ->
			val id = request.pathParamInt("id")
			jsonBody("""{"id":$id,"name":"User $id"}""")
		}

		get("api/users/{id}/name") { request ->
			jsonBody("""{"name":"${request.pathParam("id")}"}""")
		}

		post("api/login") { request ->
			val body = request.bodyString() ?: ""
			if (body.contains("admin")) {
				jsonBody("""{"token":"abc"}""")
			} else {
				unauthorized().jsonBody("""{"error":"denied"}""")
			}
		}

		get("api/search") { request ->
			val page = request.queryParamInt("page", 1)
			val perPage = request.queryParamInt("per_page", 10)
			jsonBody("""{"page":$page,"per_page":$perPage}""")
		}

		delete("api/items/{id}") {
			emptyBody()
		}

		get("api/custom-header") {
			header("X-Custom", "test-value")
			jsonBody("""{"ok":true}""")
		}

		get("api/not-found") {
			notFound().jsonBody("""{"error":"not found"}""")
		}

		get("api/bad-request") {
			badRequest().jsonBody("""{"error":"bad"}""")
		}

		get("api/forbidden") {
			forbidden().jsonBody("""{"error":"forbidden"}""")
		}

		get("api/server-error") {
			serverError().jsonBody("""{"error":"server"}""")
		}

		get("api/custom-status") {
			status(418, "I'm a teapot").jsonBody("""{"teapot":true}""")
		}

		group("api/v1") {
			get("items") {
				jsonBody("""[{"id":1},{"id":2}]""")
			}
			get("items/{id}") { request ->
				jsonBody("""{"id":${request.pathParam("id")}}""")
			}
		}

		get("api/from-file") {
			jsonFile("mock/test-response.json")
		}
	}

	private val client = OkHttpClient.Builder()
		.addInterceptor(interceptor)
		.build()

	private fun get(path: String): Response =
		client.newCall(
			Request.Builder().url("https://mock.test/$path").build()
		).execute()

	private fun post(path: String, body: String): Response =
		client.newCall(
			Request.Builder()
				.url("https://mock.test/$path")
				.post(body.toRequestBody("application/json".toMediaType()))
				.build()
		).execute()

	private fun delete(path: String): Response =
		client.newCall(
			Request.Builder()
				.url("https://mock.test/$path")
				.delete()
				.build()
		).execute()

	// -- Route matching --

	@Test
	fun `GET exact path returns 200 with body`() {
		val response = get("api/hello")
		assertEquals(200, response.code)
		assertEquals("""{"message":"hello"}""", response.body.string())
	}

	@Test
	fun `path parameters extracted correctly`() {
		val response = get("api/users/42")
		assertEquals(200, response.code)
		assertEquals("""{"id":42,"name":"User 42"}""", response.body.string())
	}

	@Test
	fun `path parameter as string`() {
		val response = get("api/users/abc/name")
		assertEquals(200, response.code)
		assertEquals("""{"name":"abc"}""", response.body.string())
	}

	// -- Method matching --

	@Test
	fun `POST route not matched by GET`() {
		// api/login is POST-only; GET should fall through (and fail with no real server)
		try {
			get("api/login")
			fail("Expected IOException for unmatched route pass-through")
		} catch (_: IOException) {
			// Expected — unmatched request tried to reach real server
		}
	}

	@Test
	fun `POST route matched correctly`() {
		val response = post("api/login", """{"user":"admin"}""")
		assertEquals(200, response.code)
		assertEquals("""{"token":"abc"}""", response.body.string())
	}

	// -- Request body --

	@Test
	fun `request body readable in handler`() {
		val response = post("api/login", """{"user":"guest"}""")
		assertEquals(401, response.code)
		assertEquals("""{"error":"denied"}""", response.body.string())
	}

	// -- Query parameters --

	@Test
	fun `query parameters accessible in handler`() {
		val response = get("api/search?page=3&per_page=20")
		assertEquals(200, response.code)
		assertEquals("""{"page":3,"per_page":20}""", response.body.string())
	}

	@Test
	fun `query parameters default when absent`() {
		val response = get("api/search")
		assertEquals(200, response.code)
		assertEquals("""{"page":1,"per_page":10}""", response.body.string())
	}

	// -- Status helpers --

	@Test
	fun `emptyBody returns 204`() {
		val response = delete("api/items/99")
		assertEquals(204, response.code)
	}

	@Test
	fun `notFound sets 404`() {
		assertEquals(404, get("api/not-found").code)
	}

	@Test
	fun `badRequest sets 400`() {
		assertEquals(400, get("api/bad-request").code)
	}

	@Test
	fun `forbidden sets 403`() {
		assertEquals(403, get("api/forbidden").code)
	}

	@Test
	fun `unauthorized sets 401`() {
		val response = post("api/login", """{"user":"guest"}""")
		assertEquals(401, response.code)
	}

	@Test
	fun `serverError sets 500`() {
		assertEquals(500, get("api/server-error").code)
	}

	@Test
	fun `custom status code`() {
		val response = get("api/custom-status")
		assertEquals(418, response.code)
	}

	// -- Headers --

	@Test
	fun `custom header present in response`() {
		val response = get("api/custom-header")
		assertEquals("test-value", response.header("X-Custom"))
	}

	// -- Grouping --

	@Test
	fun `grouped route matches with prefix`() {
		val response = get("api/v1/items")
		assertEquals(200, response.code)
		assertEquals("""[{"id":1},{"id":2}]""", response.body.string())
	}

	@Test
	fun `grouped route with path param`() {
		val response = get("api/v1/items/7")
		assertEquals(200, response.code)
		assertEquals("""{"id":7}""", response.body.string())
	}

	// -- Unmatched pass-through --

	@Test
	fun `unmatched route passes through to network`() {
		try {
			get("api/nonexistent")
			fail("Expected IOException for unmatched route")
		} catch (_: IOException) {
			// Expected — interceptor called chain.proceed() but no real server exists
		}
	}

	// -- jsonFile --

	@Test
	fun `jsonFile loads from classpath resource`() {
		val response = get("api/from-file")
		assertEquals(200, response.code)
		assertTrue(response.body.string().contains("loaded from file"))
	}
}
