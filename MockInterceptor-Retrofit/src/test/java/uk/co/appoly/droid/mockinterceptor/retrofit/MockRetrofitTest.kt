package uk.co.appoly.droid.mockinterceptor.retrofit

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import uk.co.appoly.droid.mockinterceptor.MockApiInterceptor
import java.io.IOException

class MockRetrofitTest {

	@Suppress("unused")
	private interface TestApi {
		@GET("api/users")
		suspend fun listUsers(): Any

		@GET("api/users/{id}")
		suspend fun getUser(@Path("id") id: Int): Any

		@POST("api/users")
		suspend fun createUser(@Body body: Any): Any

		@DELETE("api/users/{id}")
		suspend fun deleteUser(@Path("id") id: Int): Any

		@PUT("api/users/{id}")
		suspend fun updateUser(@Path("id") id: Int, @Body body: Any): Any

		@GET("api/items?type=all")
		suspend fun listItems(@Query("page") page: Int): Any

		// No mock registered for this one
		suspend fun unmockedMethod(): Any
	}

	private val client = OkHttpClient.Builder()
		.addInterceptor(
			MockApiInterceptor(tag = "RetrofitTest") {
				defaultDelay(0L)
				mockApi(TestApi::class) {
					mock(TestApi::listUsers) {
						jsonBody("""{"users":["a","b"]}""")
					}
					mock(TestApi::getUser) { request ->
						jsonBody("""{"id":${request.pathParam("id")}}""")
					}
					mock(TestApi::createUser) {
						status(201, "Created").jsonBody("""{"created":true}""")
					}
					mock(TestApi::deleteUser) {
						emptyBody()
					}
					mock(TestApi::updateUser) { request ->
						jsonBody("""{"updated":${request.pathParam("id")}}""")
					}
					mock(TestApi::listItems) {
						jsonBody("""{"items":[]}""")
					}
				}
			}
		)
		.build()

	// -- @GET --

	@Test
	fun `GET route registered from annotation`() {
		val response = get("api/users")
		assertEquals(200, response.code)
		assertEquals("""{"users":["a","b"]}""", response.body.string())
	}

	@Test
	fun `GET with path param from annotation`() {
		val response = get("api/users/42")
		assertEquals(200, response.code)
		assertEquals("""{"id":42}""", response.body.string())
	}

	// -- @POST --

	@Test
	fun `POST route registered from annotation`() {
		val response = post("api/users", """{"name":"test"}""")
		assertEquals(201, response.code)
		assertEquals("""{"created":true}""", response.body.string())
	}

	// -- @DELETE --

	@Test
	fun `DELETE route registered from annotation`() {
		val response = delete("api/users/7")
		assertEquals(204, response.code)
	}

	// -- @PUT --

	@Test
	fun `PUT route registered from annotation`() {
		val response = put("api/users/3", """{"name":"updated"}""")
		assertEquals(200, response.code)
		assertEquals("""{"updated":3}""", response.body.string())
	}

	// -- Query string stripping --

	@Test
	fun `query string stripped from annotation path`() {
		// @GET("api/items?type=all") should match path "api/items"
		val response = get("api/items?page=1")
		assertEquals(200, response.code)
		assertEquals("""{"items":[]}""", response.body.string())
	}

	// -- Unregistered methods --

	@Test
	fun `unregistered method does not produce a route`() {
		// unmockedMethod() has no @GET annotation and no mock() handler,
		// so no route is registered. Any path that doesn't match other routes passes through.
		try {
			get("api/unmocked")
			fail("Expected IOException for unmatched route")
		} catch (_: IOException) {
			// Expected — no route matches, interceptor calls chain.proceed()
		}
	}

	// -- Helpers --

	private fun get(path: String): Response =
		client.newCall(
			Request.Builder().url("https://mock.test/$path").build()
		).execute()

	private fun post(path: String, body: String): Response =
		client.newCall(
			Request.Builder()
				.url("https://mock.test/$path")
				.post(body.toRequestBody())
				.build()
		).execute()

	private fun delete(path: String): Response =
		client.newCall(
			Request.Builder()
				.url("https://mock.test/$path")
				.delete()
				.build()
		).execute()

	private fun put(path: String, body: String): Response =
		client.newCall(
			Request.Builder()
				.url("https://mock.test/$path")
				.put(body.toRequestBody())
				.build()
		).execute()
}
