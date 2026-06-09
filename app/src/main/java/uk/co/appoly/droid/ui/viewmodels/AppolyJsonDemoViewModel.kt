package uk.co.appoly.droid.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import uk.co.appoly.droid.Log
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.BaseService
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.BaseResponse
import uk.co.appoly.droid.data.remote.model.response.GenericNestedPagedResponse
import uk.co.appoly.droid.data.remote.model.response.GenericResponse
import uk.co.appoly.droid.data.remote.model.response.PageData
import uk.co.appoly.droid.data.repo.APIFlowState
import uk.co.appoly.droid.data.repo.AppolyBaseRepo
import uk.co.appoly.droid.data.repo.asApiFlowState
import uk.co.appoly.droid.data.repo.doNestedPagedAPICall
import uk.co.appoly.droid.mockinterceptor.MockApiInterceptor
import uk.co.appoly.droid.mockinterceptor.appolyjson.errorBody
import uk.co.appoly.droid.mockinterceptor.appolyjson.pagedBody
import uk.co.appoly.droid.mockinterceptor.appolyjson.successBody
import uk.co.appoly.droid.mockinterceptor.appolyjson.successMessage

/**
 * Demonstrates the **BaseRepo-AppolyJson** and **BaseRepo-Paging-AppolyJson** modules end to end:
 * a real [AppolyBaseRepo] parses Appoly's standardized response envelopes
 * ([GenericResponse] / [GenericNestedPagedResponse] / [BaseResponse]) into [APIResult]s.
 *
 * The transport is the **MockInterceptor** module, whose `successBody` / `pagedBody` / `errorBody`
 * helpers emit exactly the envelope shapes the AppolyJson models expect — so the whole round-trip
 * runs offline with no real backend.
 */
class AppolyJsonDemoViewModel : ViewModel() {

	@Serializable
	data class DemoUser(val id: Int, val name: String, val email: String)

	@Serializable
	data class DemoProduct(val id: Int, val name: String, val price: Double)

	/** Service returning Appoly JSON envelopes, wrapped by Sandwich's [ApiResponse]. */
	private interface AppolyDemoApi : BaseService.API {
		@GET("api/appoly/users")
		suspend fun getUsers(): ApiResponse<GenericResponse<List<DemoUser>>>

		@GET("api/appoly/products")
		suspend fun getProducts(
			@Query("page") page: Int,
			@Query("per_page") perPage: Int
		): ApiResponse<GenericNestedPagedResponse<DemoProduct>>

		@GET("api/appoly/error")
		suspend fun getError(): ApiResponse<GenericResponse<List<DemoUser>>>

		@DELETE("api/appoly/users/{id}")
		suspend fun deleteUser(@Path("id") id: Int): ApiResponse<BaseResponse>
	}

	/** Repo built on [AppolyBaseRepo] — `doAPICall` / `doNestedPagedAPICall` unwrap the envelopes. */
	private class AppolyDemoRepo(
		getClient: () -> BaseRetrofitClient
	) : AppolyBaseRepo(getClient) {
		private val service by lazyService<AppolyDemoApi>()

		suspend fun fetchUsers(): APIResult<List<DemoUser>> =
			doAPICall("fetchUsers") { service.api.getUsers() }

		suspend fun fetchProducts(page: Int): APIResult<PageData<DemoProduct>> =
			doNestedPagedAPICall("fetchProducts") { service.api.getProducts(page = page, perPage = PER_PAGE) }

		suspend fun fetchErrorEnvelope(): APIResult<List<DemoUser>> =
			doAPICall("fetchError") { service.api.getError() }

		suspend fun deleteUser(id: Int): APIResult<BaseResponse> =
			doAPICallWithBaseResponse("deleteUser") { service.api.deleteUser(id) }
	}

	private val mockInterceptor = MockApiInterceptor(tag = "AppolyJson", logger = Log) {
		defaultDelay(600L)

		get("api/appoly/users") {
			successBody(
				listOf(
					DemoUser(1, "Alice", "alice@example.com"),
					DemoUser(2, "Bob", "bob@example.com"),
					DemoUser(3, "Charlie", "charlie@example.com"),
				)
			)
		}
		get("api/appoly/products") { request ->
			pagedBody(
				items = (1..23).map { DemoProduct(it, "Product $it", 9.99 + it) },
				request = request,
				defaultPerPage = PER_PAGE,
			)
		}
		get("api/appoly/error") {
			errorBody("The requested resource could not be found", code = 404)
		}
		delete("api/appoly/users/{id}") { request ->
			successMessage("User ${request.pathParam("id")} deleted")
		}
	}

	private val client = OkHttpClient.Builder()
		.addInterceptor(mockInterceptor)
		.build()

	private val retrofitClient = object : BaseRetrofitClient {
		override val json: Json = Json { ignoreUnknownKeys = true }
		private val retrofit: Retrofit = Retrofit.Builder()
			.baseUrl("https://mock.test/")
			.client(client)
			.addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
			.addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
			.build()

		override fun <T> createService(serviceClass: Class<T>): T = retrofit.create(serviceClass)
	}

	private val repo = AppolyDemoRepo { retrofitClient }

	// --- Users (GenericResponse<List<T>>) — null = not yet requested ---
	private val _usersState = MutableStateFlow<APIFlowState<List<DemoUser>>?>(null)
	val usersState: StateFlow<APIFlowState<List<DemoUser>>?> = _usersState.asStateFlow()

	// --- Products (GenericNestedPagedResponse<T>) — null = not yet requested ---
	private val _productsState = MutableStateFlow<APIFlowState<PageData<DemoProduct>>?>(null)
	val productsState: StateFlow<APIFlowState<PageData<DemoProduct>>?> = _productsState.asStateFlow()
	private val _productsPage = MutableStateFlow(1)
	val productsPage: StateFlow<Int> = _productsPage.asStateFlow()

	// --- Free-text log of the most recent envelope outcomes ---
	private val _log = MutableStateFlow("Tap a button to call the mocked Appoly JSON backend.")
	val log: StateFlow<String> = _log.asStateFlow()

	fun fetchUsers() {
		_usersState.value = APIFlowState.Loading
		viewModelScope.launch {
			val result = repo.fetchUsers()
			_usersState.value = result.asApiFlowState()
			_log.value = when (result) {
				is APIResult.Success -> "✅ GenericResponse parsed → ${result.data.size} users"
				is APIResult.Error -> "❌ ${result.message} (code ${result.responseCode})"
			}
		}
	}

	fun fetchProducts(page: Int = 1) {
		_productsPage.value = page
		_productsState.value = APIFlowState.Loading
		viewModelScope.launch {
			val result = repo.fetchProducts(page)
			_productsState.value = result.asApiFlowState()
			_log.value = when (result) {
				is APIResult.Success -> with(result.data) {
					"✅ GenericNestedPagedResponse parsed → page $currentPage/$lastPage, " +
						"${data.size} of $total items"
				}

				is APIResult.Error -> "❌ ${result.message} (code ${result.responseCode})"
			}
		}
	}

	fun nextProductsPage() = fetchProducts((_productsPage.value + 1))

	fun previousProductsPage() = fetchProducts((_productsPage.value - 1).coerceAtLeast(1))

	fun triggerErrorEnvelope() {
		viewModelScope.launch {
			when (val result = repo.fetchErrorEnvelope()) {
				is APIResult.Success -> _log.value = "Unexpected success: ${result.data}"
				is APIResult.Error ->
					_log.value = "❌ Error envelope parsed → \"${result.message}\" (code ${result.responseCode})"
			}
		}
	}

	fun deleteUser(id: Int) {
		viewModelScope.launch {
			when (val result = repo.deleteUser(id)) {
				is APIResult.Success ->
					_log.value = "✅ BaseResponse parsed → \"${result.data.message}\""

				is APIResult.Error -> _log.value = "❌ ${result.message} (code ${result.responseCode})"
			}
		}
	}

	companion object {
		private const val PER_PAGE = 5
	}
}
