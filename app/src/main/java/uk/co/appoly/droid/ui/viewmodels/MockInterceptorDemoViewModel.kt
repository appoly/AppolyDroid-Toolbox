package uk.co.appoly.droid.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import uk.co.appoly.droid.Log
import uk.co.appoly.droid.mockinterceptor.MockApiInterceptor
import uk.co.appoly.droid.mockinterceptor.appolyjson.emptyPage
import uk.co.appoly.droid.mockinterceptor.appolyjson.errorBody
import uk.co.appoly.droid.mockinterceptor.appolyjson.pagedBody
import uk.co.appoly.droid.mockinterceptor.appolyjson.successBody
import uk.co.appoly.droid.mockinterceptor.appolyjson.successMessage
import uk.co.appoly.droid.mockinterceptor.retrofit.mockApi
import uk.co.appoly.droid.mockinterceptor.serialization.jsonBody

@Serializable
data class MockUser(val id: Int, val name: String, val email: String)

@Serializable
data class MockProduct(val id: Int, val name: String, val price: Double)

data class RequestResult(
	val label: String,
	val method: String,
	val url: String,
	val statusCode: Int,
	val responseBody: String,
)

private interface DemoRetrofitApi {
	@GET("api/retrofit/users")
	suspend fun listUsers(): ResponseBody

	@GET("api/retrofit/users/{id}")
	suspend fun getUser(@Path("id") id: Int): ResponseBody

	@POST("api/retrofit/users")
	suspend fun createUser(@Body body: okhttp3.RequestBody): ResponseBody
}

class MockInterceptorDemoViewModel : ViewModel() {

	private val mockInterceptor = MockApiInterceptor(tag = "Demo", logger = Log) {
		defaultDelay(150L)

		// -- Core DSL --
		get("api/hello") {
			jsonBody("""{"message": "Hello from MockInterceptor!"}""")
		}
		get("api/users/{id}") { request ->
			val id = request.pathParamInt("id")
			when (id) {
				1 -> jsonBody("""{"id":1,"name":"Alice","email":"alice@example.com"}""")
				2 -> jsonBody("""{"id":2,"name":"Bob","email":"bob@example.com"}""")
				else -> notFound().jsonBody("""{"error":"User $id not found"}""")
			}
		}
		post("api/login") { request ->
			val body = request.bodyString() ?: ""
			if (body.contains("admin")) {
				jsonBody("""{"token":"mock-jwt-token-12345"}""")
			} else {
				unauthorized().jsonBody("""{"error":"Invalid credentials"}""")
			}
		}
		group("api/grouped") {
			get("items") { jsonBody("""[{"id":1},{"id":2}]""") }
			get("items/{id}") { req -> jsonBody("""{"id":${req.pathParam("id")}}""") }
		}
		delete("api/items/{id}") { emptyBody() }

		// -- Serialization: jsonBody<T>() --
		get("api/typed/users") {
			jsonBody(
				listOf(
					MockUser(1, "Alice", "alice@example.com"),
					MockUser(2, "Bob", "bob@example.com"),
				)
			)
		}

		// -- Appoly JSON envelope --
		get("api/appoly/users") {
			successBody(
				listOf(
					MockUser(1, "Alice", "alice@example.com"),
					MockUser(2, "Bob", "bob@example.com"),
					MockUser(3, "Charlie", "charlie@example.com"),
				)
			)
		}
		delete("api/appoly/users/{id}") { req ->
			successMessage("User ${req.pathParam("id")} deleted")
		}
		get("api/appoly/error") {
			errorBody("Resource not found", code = 404)
		}
		get("api/appoly/products") { request ->
			pagedBody(
				items = (1..23).map { MockProduct(it, "Product $it", 9.99 + it) },
				request = request,
				defaultPerPage = 5,
			)
		}
		get("api/appoly/empty") { emptyPage() }

		// -- Retrofit mockApi() --
		mockApi(DemoRetrofitApi::class) {
			mock(DemoRetrofitApi::listUsers) {
				successBody(
					listOf(
						MockUser(1, "Alice", "alice@example.com"),
						MockUser(2, "Bob", "bob@example.com"),
					)
				)
			}
			mock(DemoRetrofitApi::getUser) { req ->
				val id = req.pathParamInt("id")
				successBody(MockUser(id, "User $id", "user$id@example.com"))
			}
			mock(DemoRetrofitApi::createUser) {
				successMessage("User created")
			}
		}
	}

	private val client = OkHttpClient.Builder()
		.addInterceptor(mockInterceptor)
		.build()

	private val demoApi: DemoRetrofitApi = Retrofit.Builder()
		.baseUrl("https://mock.test/")
		.client(client)
		.build()
		.create(DemoRetrofitApi::class.java)

	private val _results = MutableStateFlow<List<RequestResult>>(emptyList())
	val results: StateFlow<List<RequestResult>> = _results.asStateFlow()

	private fun executeRequest(label: String, request: Request) {
		viewModelScope.launch {
			val result = withContext(Dispatchers.IO) {
				val response = client.newCall(request).execute()
				RequestResult(
					label = label,
					method = request.method,
					url = request.url.toString(),
					statusCode = response.code,
					responseBody = response.body?.string() ?: "",
				)
			}
			_results.update { listOf(result) + it }
		}
	}

	private fun executeRetrofitCall(label: String, method: String, url: String, block: suspend () -> ResponseBody) {
		viewModelScope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					val body = block()
					RequestResult(
						label = label,
						method = method,
						url = url,
						statusCode = 200,
						responseBody = body.string(),
					)
				} catch (e: retrofit2.HttpException) {
					RequestResult(
						label = label,
						method = method,
						url = url,
						statusCode = e.code(),
						responseBody = e.response()?.errorBody()?.string() ?: e.message(),
					)
				}
			}
			_results.update { listOf(result) + it }
		}
	}

	private fun buildRequest(path: String): Request =
		Request.Builder().url("https://mock.test/$path").build()

	// -- Core DSL --

	fun fetchHello() {
		executeRequest("Hello", buildRequest("api/hello"))
	}

	fun fetchUserById(id: Int) {
		executeRequest("User $id", buildRequest("api/users/$id"))
	}

	fun postLogin(asAdmin: Boolean) {
		val json = if (asAdmin) """{"user":"admin"}""" else """{"user":"guest"}"""
		val body = json.toRequestBody("application/json".toMediaType())
		val request = Request.Builder()
			.url("https://mock.test/api/login")
			.post(body)
			.build()
		executeRequest(if (asAdmin) "Login (admin)" else "Login (guest)", request)
	}

	fun fetchGroupedItems() {
		executeRequest("Grouped Items", buildRequest("api/grouped/items"))
	}

	fun fetchGroupedItem(id: Int) {
		executeRequest("Grouped Item $id", buildRequest("api/grouped/items/$id"))
	}

	fun deleteItem(id: Int) {
		val request = Request.Builder()
			.url("https://mock.test/api/items/$id")
			.delete()
			.build()
		executeRequest("Delete Item $id", request)
	}

	// -- Serialization --

	fun fetchTypedUsers() {
		executeRequest("Typed Users", buildRequest("api/typed/users"))
	}

	// -- Appoly JSON --

	fun fetchAppolyUsers() {
		executeRequest("Appoly Users", buildRequest("api/appoly/users"))
	}

	fun deleteAppolyUser(id: Int) {
		val request = Request.Builder()
			.url("https://mock.test/api/appoly/users/$id")
			.delete()
			.build()
		executeRequest("Delete Appoly User $id", request)
	}

	fun fetchAppolyError() {
		executeRequest("Appoly Error", buildRequest("api/appoly/error"))
	}

	fun fetchPagedProducts(page: Int) {
		executeRequest("Products Page $page", buildRequest("api/appoly/products?page=$page&per_page=5"))
	}

	fun fetchEmptyPage() {
		executeRequest("Empty Page", buildRequest("api/appoly/empty"))
	}

	// -- Retrofit mockApi --

	fun retrofitListUsers() {
		executeRetrofitCall("Retrofit List Users", "GET", "https://mock.test/api/retrofit/users") {
			demoApi.listUsers()
		}
	}

	fun retrofitGetUser(id: Int) {
		executeRetrofitCall("Retrofit Get User $id", "GET", "https://mock.test/api/retrofit/users/$id") {
			demoApi.getUser(id)
		}
	}

	fun retrofitCreateUser() {
		val json = """{"name":"New User","email":"new@example.com"}"""
		val body = json.toRequestBody("application/json".toMediaType())
		executeRetrofitCall("Retrofit Create User", "POST", "https://mock.test/api/retrofit/users") {
			demoApi.createUser(body)
		}
	}

	fun clearResults() {
		_results.value = emptyList()
	}
}
