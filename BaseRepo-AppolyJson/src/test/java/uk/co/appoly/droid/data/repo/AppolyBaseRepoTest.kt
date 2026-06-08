package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.BaseResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppolyBaseRepo] — the AppolyJson flavour that adds
 * [AppolyBaseRepo.doAPICallWithBaseResponse] and overrides [extractErrorMessage] to parse
 * the standard `ErrorBody` JSON envelope. [ApiResponse] values are constructed directly so no
 * MockWebServer round-trip is needed.
 */
class AppolyBaseRepoTest {

	/** Minimal client supplying the kotlinx Json used by `parseBody` for error extraction. */
	private val fakeClient = object : BaseRetrofitClient {
		override val json: Json = Json { ignoreUnknownKeys = true }
		override fun <T> createService(serviceClass: Class<T>): T =
			throw UnsupportedOperationException("not needed")
	}

	private inner class TestAppolyRepo : AppolyBaseRepo(
		getRetrofitClient = { fakeClient },
		logger = SilentTestLogger,
		loggingLevel = LoggingLevel.NONE
	) {
		fun callBase(call: () -> ApiResponse<BaseResponse>): APIResult<BaseResponse> =
			doAPICallWithBaseResponse(logDescription = "test-base", call = call)
	}

	private val repo = TestAppolyRepo()

	private fun okResponse(): retrofit2.Response<Unit> = retrofit2.Response.success(Unit)

	private fun errorResponse(code: Int, body: String): retrofit2.Response<Unit> =
		retrofit2.Response.error(code, body.toResponseBody("application/json".toMediaType()))

	private fun success(payload: BaseResponse): ApiResponse.Success<BaseResponse> =
		ApiResponse.Success(data = payload, tag = okResponse())

	@Test
	fun `doAPICallWithBaseResponse returns Success when success true`() {
		val body = BaseResponse(success = true, message = "done")
		val result = repo.callBase { success(body) }

		assertTrue(result is APIResult.Success)
		assertEquals(body, (result as APIResult.Success).data)
	}

	@Test
	fun `doAPICallWithBaseResponse maps success false to Error via handleFailure`() {
		val result = repo.callBase {
			success(BaseResponse(success = false, message = "validation failed"))
		}

		result as APIResult.Error
		assertEquals(200, result.responseCode)
		assertEquals("validation failed", result.message)
	}

	@Test
	fun `doAPICallWithBaseResponse maps Failure Error and extracts the ErrorBody message`() {
		val result = repo.callBase {
			ApiResponse.Failure.Error(
				errorResponse(code = 422, body = """{"success":false,"message":"email is required"}""")
			)
		}

		result as APIResult.Error
		assertEquals(422, result.responseCode)
		assertEquals("email is required", result.message)
	}

	@Test
	fun `doAPICallWithBaseResponse maps Failure Exception to Error`() {
		val boom = IllegalStateException("kaboom")
		val result = repo.callBase { ApiResponse.exception(boom) }

		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
		assertEquals("kaboom", result.message)
	}

	@Test
	fun `extractErrorMessage parses the message field from the error body`() {
		val msg = repo.extractErrorMessage(
			ApiResponse.Failure.Error(
				errorResponse(code = 400, body = """{"success":false,"message":"bad request"}""")
			)
		)
		assertEquals("bad request", msg)
	}

	@Test
	fun `extractErrorMessage returns null message when body omits it`() {
		val msg = repo.extractErrorMessage(
			ApiResponse.Failure.Error(errorResponse(code = 400, body = """{"success":false}"""))
		)
		assertNull(msg)
	}
}
