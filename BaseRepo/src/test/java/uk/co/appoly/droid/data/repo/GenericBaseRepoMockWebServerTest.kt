package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.envelope.ApiEnvelope
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import com.skydoves.sandwich.retrofit.errorBody
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.RootJsonWithData
import uk.co.appoly.droid.util.ServerTimeoutException
import uk.co.appoly.droid.util.ServerUnreachableException
import java.util.concurrent.TimeUnit

/**
 * Integration tests for [GenericBaseRepo] through the REAL request pipeline:
 * MockWebServer -> OkHttp -> Retrofit -> Sandwich's [ApiResponseCallAdapterFactory]
 * (including the globally-registered success mappers such as `ApiEnvelopeMapper`).
 *
 * The plain [GenericBaseRepoTest] hand-constructs [ApiResponse] values, which verifies the
 * repo's own branching but silently skips the adapter layer — exactly where Sandwich 2.4.0
 * changed behaviour. These tests exist so a future Sandwich upgrade that alters adapter-layer
 * classification breaks loudly here instead of in a consumer app.
 */
class GenericBaseRepoMockWebServerTest {

	// --- Wire models ----------------------------------------------------------

	@Serializable
	private data class Payload(
		val id: Int,
		val name: String,
	)

	/** The standard toolbox wire shape: RootJsonWithData parsed straight off the body. */
	@Serializable
	private data class WireResponse<T>(
		override val success: Boolean,
		override val message: String? = null,
		override val data: T? = null,
	) : RootJsonWithData<T>

	/**
	 * The consumer-app scenario this suite exists for: a response model that ALSO implements
	 * Sandwich's [ApiEnvelope]. Sandwich registers `ApiEnvelopeMapper` on `SandwichInitializer`
	 * by default, so an HTTP 200 whose body reports a business failure is demoted to
	 * [ApiResponse.Failure.Error] carrying [envelopeError] (a String, not a retrofit2.Response)
	 * as its payload — before doAPICall ever sees it.
	 */
	@Serializable
	private data class EnvelopeWireResponse<T>(
		override val success: Boolean,
		override val message: String? = null,
		override val data: T? = null,
	) : RootJsonWithData<T>, ApiEnvelope<T?, String> {
		override val isEnvelopeSuccessful: Boolean get() = success
		override val envelopeBody: T? get() = data
		override val envelopeError: String get() = message ?: "Unknown business error"
	}

	@Serializable
	private data class WireErrorBody(
		val message: String? = null,
	)

	private interface TestApi {
		@GET("data")
		suspend fun getData(): ApiResponse<WireResponse<Payload>>

		@GET("enveloped")
		suspend fun getEnveloped(): ApiResponse<EnvelopeWireResponse<Payload>>
	}

	// --- Repo under test ------------------------------------------------------

	private class TestRepo(
		client: BaseRetrofitClient,
		private val api: TestApi,
	) : GenericBaseRepo(
		getRetrofitClient = { client },
		logger = SilentTestLogger,
		loggingLevel = LoggingLevel.NONE
	) {
		override fun extractErrorMessage(response: ApiResponse.Failure.Error): String? {
			if (response.payload !is retrofit2.Response<*>) return null
			return try {
				response.errorBody?.string()?.let {
					getRetrofitClient().json.decodeFromString<WireErrorBody>(it).message
				}
			} catch (e: Exception) {
				null
			}
		}

		suspend fun fetchData(): APIResult<Payload> =
			doAPICall("fetchData") { api.getData() }

		suspend fun fetchEnveloped(): APIResult<Payload> =
			doAPICall("fetchEnveloped") { api.getEnveloped() }
	}

	// --- Fixtures ---------------------------------------------------------------

	private lateinit var server: MockWebServer
	private lateinit var repo: TestRepo

	private val json = Json { ignoreUnknownKeys = true }

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()
		repo = buildRepo()
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	private fun buildRepo(okHttp: OkHttpClient = OkHttpClient()): TestRepo {
		val retrofit = Retrofit.Builder()
			.baseUrl(server.url("/"))
			.client(okHttp)
			.addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
			.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
			.build()
		val client = object : BaseRetrofitClient {
			override val json: Json = this@GenericBaseRepoMockWebServerTest.json
			override fun <T> createService(serviceClass: Class<T>): T = retrofit.create(serviceClass)
		}
		return TestRepo(client, retrofit.create(TestApi::class.java))
	}

	private fun enqueueJson(code: Int, body: String) {
		server.enqueue(
			MockResponse()
				.setResponseCode(code)
				.setHeader("Content-Type", "application/json")
				.setBody(body)
		)
	}

	// --- The standard pipeline ------------------------------------------------

	@Test
	fun `HTTP 200 with success true returns Success through the real adapter`() = runBlocking {
		enqueueJson(200, """{"success":true,"message":"ok","data":{"id":7,"name":"Widget"}}""")

		val result = repo.fetchData()

		assertTrue("Expected Success but was $result", result is APIResult.Success)
		assertEquals(Payload(7, "Widget"), (result as APIResult.Success).data)
	}

	@Test
	fun `HTTP 200 with success false maps to Error carrying the real status code`() = runBlocking {
		enqueueJson(200, """{"success":false,"message":"nothing to see here"}""")

		val result = repo.fetchData()

		result as APIResult.Error
		assertEquals(200, result.responseCode)
		assertEquals("nothing to see here", result.message)
	}

	@Test
	fun `HTTP 422 parses the error body message through the real error path`() = runBlocking {
		enqueueJson(422, """{"message":"validation failed"}""")

		val result = repo.fetchData()

		result as APIResult.Error
		assertEquals(422, result.responseCode)
		assertEquals("validation failed", result.message)
	}

	// --- The ApiEnvelope consumer scenario -------------------------------------

	@Test
	fun `envelope model business failure is demoted by ApiEnvelopeMapper and handled without crashing`() = runBlocking {
		// HTTP 200 on the wire — the demotion to Failure.Error happens inside Sandwich's
		// adapter via the globally-registered ApiEnvelopeMapper, so handleFailureError
		// receives a String payload instead of a retrofit2.Response.
		enqueueJson(200, """{"success":false,"message":"insufficient credit"}""")

		val result = repo.fetchEnveloped()

		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_NON_HTTP_ERROR_CODE, result.responseCode)
		assertEquals("insufficient credit", result.message)
	}

	@Test
	fun `envelope model business failure with no message surfaces the envelope fallback`() = runBlocking {
		enqueueJson(200, """{"success":false}""")

		val result = repo.fetchEnveloped()

		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_NON_HTTP_ERROR_CODE, result.responseCode)
		assertEquals("Unknown business error", result.message)
	}

	@Test
	fun `envelope model business success passes through the mapper untouched`() = runBlocking {
		enqueueJson(200, """{"success":true,"data":{"id":3,"name":"Gadget"}}""")

		val result = repo.fetchEnveloped()

		assertTrue("Expected Success but was $result", result is APIResult.Success)
		assertEquals(Payload(3, "Gadget"), (result as APIResult.Success).data)
	}

	@Test
	fun `envelope model HTTP error still reports the real status code`() = runBlocking {
		// A transport-level failure must NOT be affected by the envelope demotion path.
		enqueueJson(500, """{"message":"server exploded"}""")

		val result = repo.fetchEnveloped()

		result as APIResult.Error
		assertEquals(500, result.responseCode)
		assertEquals("server exploded", result.message)
	}

	// --- Exception classification through a real socket -------------------------

	@Test
	fun `callTimeout expiry classifies as ServerTimeoutException`() = runBlocking {
		// OkHttp's call-level timeout surfaces as InterruptedIOException("timeout"), not a
		// SocketTimeoutException — the quirk RetrofitExceptionClassifier encodes for us.
		val timeoutRepo = buildRepo(
			OkHttpClient.Builder()
				.callTimeout(250, TimeUnit.MILLISECONDS)
				.build()
		)
		server.enqueue(
			MockResponse()
				.setHeader("Content-Type", "application/json")
				.setBody("""{"success":true,"data":{"id":1,"name":"late"}}""")
				.setHeadersDelay(2, TimeUnit.SECONDS)
		)

		val result = timeoutRepo.fetchData()

		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
		assertEquals("Server took too long to respond", result.message)
		assertTrue("Expected ServerTimeoutException", result.throwable is ServerTimeoutException)
		assertTrue(result.isNetworkError())
		assertTrue(result.isServerUnreachable())
	}

	@Test
	fun `unreachable server classifies as ServerUnreachableException`() = runBlocking {
		// Shut the server down and reuse its (now closed) port so the connection is refused.
		server.shutdown()

		val result = repo.fetchData()

		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
		assertEquals("Couldn't reach the server", result.message)
		assertTrue("Expected ServerUnreachableException", result.throwable is ServerUnreachableException)
		assertTrue(result.isNetworkError())
	}

	@Test
	fun `malformed response body is NOT classified as a network error`() = runBlocking {
		// The server was reached and answered — a converter failure must stay a generic error,
		// not trigger offline/retry UI in consumer apps.
		enqueueJson(200, """{"success":true,"data":{"id":"not-an-int"}}""")

		val result = repo.fetchData()

		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
		assertFalse("A parsing failure is not a network error", result.isNetworkError())
		assertFalse(result.isServerUnreachable())
	}
}
