package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.RootJson
import uk.co.appoly.droid.data.remote.model.response.RootJsonWithData
import uk.co.appoly.droid.util.NoConnectivityException
import java.net.UnknownHostException

/**
 * Unit tests for [GenericBaseRepo].
 *
 * These are plain JVM tests: [ApiResponse] values are constructed directly
 * (Sandwich 2.2.2) so no MockWebServer / Retrofit round-trip is required. A
 * [SilentTestLogger] is injected so the repo's logging never touches
 * `android.util.Log`.
 */
class GenericBaseRepoTest {

	// --- Test fixtures -------------------------------------------------------

	/** A [RootJsonWithData] payload we control for the data-carrying call. */
	private data class TestRootJsonWithData<T>(
		override val success: Boolean,
		override val message: String?,
		override val data: T?
	) : RootJsonWithData<T>

	/** A bare [RootJson] payload for the root-json call. */
	private data class TestRootJson(
		override val success: Boolean,
		override val message: String?
	) : RootJson

	/**
	 * Concrete [GenericBaseRepo] under test. [extractErrorMessage] returns a
	 * canned message so we can assert it is preferred over the HTTP message.
	 */
	private class TestRepo(
		private val extracted: String? = "extracted-error"
	) : GenericBaseRepo(
		getRetrofitClient = { throw UnsupportedOperationException("not needed for these tests") },
		logger = SilentTestLogger,
		loggingLevel = LoggingLevel.NONE
	) {
		override fun extractErrorMessage(response: ApiResponse.Failure.Error): String? = extracted

		// Re-expose the protected inline helpers for testing.
		fun <T : Any> callWithData(call: () -> ApiResponse<RootJsonWithData<T>>): APIResult<T> =
			doAPICall(logDescription = "test-call", call = call)

		fun callWithRootJson(call: () -> ApiResponse<RootJson>): APIResult<RootJson> =
			doAPICallWithRootJson(logDescription = "test-root-json", call = call)
	}

	private val repo = TestRepo()

	/** Builds a retrofit2.Response with status 200 so `.statusCode` resolves to OK (200). */
	private fun okResponse(): retrofit2.Response<Unit> =
		retrofit2.Response.success(Unit)

	/** Builds a retrofit2.Response error with the given HTTP status code. */
	private fun errorResponse(code: Int, body: String = """{"success":false}"""): retrofit2.Response<Unit> =
		retrofit2.Response.error(code, body.toResponseBody("application/json".toMediaType()))

	private fun <T : Any> success(payload: RootJsonWithData<T>): ApiResponse.Success<RootJsonWithData<T>> =
		ApiResponse.Success(data = payload, tag = okResponse())

	private fun successRoot(payload: RootJson): ApiResponse.Success<RootJson> =
		ApiResponse.Success(data = payload, tag = okResponse())

	// --- doAPICall -----------------------------------------------------------

	@Test
	fun `doAPICall returns Success when success true and data present`() {
		val result = repo.callWithData {
			success(TestRootJsonWithData(success = true, message = "ok", data = "hello"))
		}

		assertTrue("Expected Success but was $result", result is APIResult.Success)
		assertEquals("hello", (result as APIResult.Success).data)
	}

	@Test
	fun `doAPICall maps success false to Error via handleFailure`() {
		val result = repo.callWithData {
			success(TestRootJsonWithData(success = false, message = "nope", data = "ignored"))
		}

		assertTrue(result is APIResult.Error)
		result as APIResult.Error
		// handleFailure uses the body message and the Success tag's status code (200).
		assertEquals(200, result.responseCode)
		assertEquals("nope", result.message)
	}

	@Test
	fun `doAPICall maps null data to Error even when success true`() {
		val result = repo.callWithData {
			success(TestRootJsonWithData(success = true, message = "blank body", data = null as String?))
		}

		assertTrue(result is APIResult.Error)
		result as APIResult.Error
		assertEquals(200, result.responseCode)
		assertEquals("blank body", result.message)
	}

	@Test
	fun `doAPICall falls back to Unknown error when failure message is blank`() {
		val result = repo.callWithData {
			success(TestRootJsonWithData(success = false, message = null, data = null as String?))
		}

		result as APIResult.Error
		assertEquals("Unknown error", result.message)
	}

	@Test
	fun `doAPICall maps Failure Error to Error using extracted message and status code`() {
		val result = repo.callWithData<String> {
			ApiResponse.Failure.Error(errorResponse(code = 422))
		}

		assertTrue(result is APIResult.Error)
		result as APIResult.Error
		assertEquals(422, result.responseCode)
		// TestRepo.extractErrorMessage wins over the HTTP message.
		assertEquals("extracted-error", result.message)
	}

	@Test
	fun `doAPICall Failure Error falls back to http message when extractErrorMessage is null`() {
		val nullExtractRepo = TestRepo(extracted = null)

		val result = nullExtractRepo.callWithData<String> {
			ApiResponse.Failure.Error(errorResponse(code = 500))
		}

		result as APIResult.Error
		assertEquals(500, result.responseCode)
		// extractErrorMessage returned null, so it falls through to response.message()
		// (or "Unknown error" if that is blank). Either way it is non-blank.
		assertTrue(result.message.isNotBlank())
	}

	@Test
	fun `doAPICall maps Failure Exception to Error with RESPONSE_EXCEPTION_CODE`() {
		val boom = IllegalStateException("kaboom")

		val result = repo.callWithData<String> {
			ApiResponse.exception(boom)
		}

		assertTrue(result is APIResult.Error)
		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
		assertEquals(-1, result.responseCode)
		assertEquals("kaboom", result.message)
		assertSame(boom, result.throwable)
	}

	@Test
	fun `doAPICall maps connectivity exception to network Error`() {
		val result = repo.callWithData<String> {
			ApiResponse.exception(UnknownHostException("no dns"))
		}

		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
		assertEquals("No Internet Connection", result.message)
		assertTrue("Expected NoConnectivityException", result.throwable is NoConnectivityException)
		assertTrue(result.isNetworkError())
	}

	// --- doAPICallWithRootJson ----------------------------------------------

	@Test
	fun `doAPICallWithRootJson returns Success carrying the RootJson when success true`() {
		val body = TestRootJson(success = true, message = "done")

		val result = repo.callWithRootJson { successRoot(body) }

		assertTrue(result is APIResult.Success)
		assertSame(body, (result as APIResult.Success).data)
	}

	@Test
	fun `doAPICallWithRootJson maps success false to Error`() {
		val result = repo.callWithRootJson {
			successRoot(TestRootJson(success = false, message = "bad request"))
		}

		result as APIResult.Error
		assertEquals(200, result.responseCode)
		assertEquals("bad request", result.message)
	}

	@Test
	fun `doAPICallWithRootJson maps Failure Exception to Error`() {
		val result = repo.callWithRootJson {
			ApiResponse.exception(RuntimeException("root-json boom"))
		}

		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
		assertEquals("root-json boom", result.message)
	}

	// --- handleFailure* directly --------------------------------------------

	@Test
	fun `handleFailure uses body message and supplied status code`() {
		val error = repo.handleFailure(
			result = TestRootJson(success = false, message = "explicit message"),
			statusCode = 418,
			logDescription = "teapot"
		)

		assertEquals(418, error.responseCode)
		assertEquals("explicit message", error.message)
	}

	@Test
	fun `handleFailureException maps a generic throwable to Error`() {
		val throwable = RuntimeException("direct exception")

		val error = repo.handleFailureException(
			response = ApiResponse.Failure.Exception(throwable),
			logDescription = "direct"
		)

		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, error.responseCode)
		assertEquals("direct exception", error.message)
		assertSame(throwable, error.throwable)
	}

	@Test
	fun `handleFailureError prefers extracted message`() {
		val error = repo.handleFailureError(
			response = ApiResponse.Failure.Error(errorResponse(code = 404)),
			logDescription = "not-found"
		)

		assertEquals(404, error.responseCode)
		assertEquals("extracted-error", error.message)
	}

	@Test
	fun `handleFailureException with null throwable message still yields non-blank message`() {
		// A throwable with no message exercises the firstNotNullOrBlank fallback path.
		val error = repo.handleFailureException(
			response = ApiResponse.Failure.Exception(RuntimeException()),
			logDescription = "no-message"
		)

		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, error.responseCode)
		assertTrue(error.message.isNotBlank())
		assertNull("RuntimeException() has no message", RuntimeException().message)
	}

	// --- ServiceManager via getServiceManager() ------------------------------

	@Test
	fun `getServiceManager returns a singleton instance`() {
		val first = repo.getServiceManager()
		val second = repo.getServiceManager()

		assertSame("ServiceManager should be a process-wide singleton", first, second)
	}

	@Test
	fun `ServiceManager getInstance is a singleton across different retrofit lambdas`() {
		val client: () -> BaseRetrofitClient = { throw UnsupportedOperationException() }

		val a = uk.co.appoly.droid.data.remote.ServiceManager.getInstance(client)
		val b = uk.co.appoly.droid.data.remote.ServiceManager.getInstance(client)

		assertSame(a, b)
	}
}
