package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.GenericNestedPagedResponse
import uk.co.appoly.droid.data.remote.model.response.NestedPageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppolyBaseRepo.doNestedPagedAPICall], covering success-with-pageData,
 * success-false, null-pageData, Failure.Error (with ErrorBody extraction) and Failure.Exception.
 */
class DoNestedPagedAPICallTest {

	private val fakeClient = object : BaseRetrofitClient {
		override val json: Json = Json { ignoreUnknownKeys = true }
		override fun <T> createService(serviceClass: Class<T>): T = throw UnsupportedOperationException()
	}

	private inner class Repo : AppolyBaseRepo(
		getRetrofitClient = { fakeClient },
		logger = SilentTestLogger,
		loggingLevel = LoggingLevel.NONE
	) {
		fun <T : Any> call(c: () -> ApiResponse<GenericNestedPagedResponse<T>>) = doNestedPagedAPICall("nested", c)
	}

	private val repo = Repo()
	private fun ok() = retrofit2.Response.success(Unit)
	private fun <T : Any> success(p: GenericNestedPagedResponse<T>) = ApiResponse.Success(data = p, tag = ok())

	private fun pageData(items: List<String>) = NestedPageData(
		data = items, currentPage = 1, lastPage = 1, perPage = 20, from = 1, to = items.size, total = items.size
	)

	@Test
	fun `success with pageData returns PageData`() {
		val result = repo.call { success(GenericNestedPagedResponse(true, "ok", pageData(listOf("a", "b")))) }
		assertTrue(result is APIResult.Success)
		assertEquals(listOf("a", "b"), (result as APIResult.Success).data.data)
	}

	@Test
	fun `success false maps to error`() {
		val result = repo.call { success(GenericNestedPagedResponse(false, "bad", pageData(listOf("x")))) }
		result as APIResult.Error
		assertEquals("bad", result.message)
	}

	@Test
	fun `null pageData maps to error`() {
		val result = repo.call<String> { success(GenericNestedPagedResponse(true, "empty", null)) }
		assertTrue(result is APIResult.Error)
	}

	@Test
	fun `failure error extracts the ErrorBody message`() {
		val result = repo.call<String> {
			ApiResponse.Failure.Error(
				retrofit2.Response.error<Unit>(
					422, """{"success":false,"message":"nested boom"}""".toResponseBody("application/json".toMediaType())
				)
			)
		}
		result as APIResult.Error
		assertEquals(422, result.responseCode)
		assertEquals("nested boom", result.message)
	}

	@Test
	fun `failure exception maps to error`() {
		val result = repo.call<String> { ApiResponse.exception(RuntimeException("boom")) }
		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
	}
}
