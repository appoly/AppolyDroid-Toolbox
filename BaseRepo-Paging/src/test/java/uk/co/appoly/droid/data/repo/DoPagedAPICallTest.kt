package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.PageData
import uk.co.appoly.droid.data.remote.model.response.RootJsonPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GenericBaseRepo.doPagedAPICall], covering the success-with-data, success-false,
 * empty-data, Failure.Error and Failure.Exception branches with directly-constructed
 * [ApiResponse] values.
 */
class DoPagedAPICallTest {

	private data class TestRootJsonPage<T>(
		override val success: Boolean,
		override val message: String?,
		private val items: List<T>?
	) : RootJsonPage<T> {
		override fun hasData(): Boolean = items != null
		override fun asPageData(): PageData<T> = PageData(
			data = items.orEmpty(),
			currentPage = 1, lastPage = 1, perPage = 20,
			from = 1, to = items?.size ?: 0, total = items?.size ?: 0
		)
	}

	private class PagingRepo : GenericBaseRepo(
		getRetrofitClient = { throw UnsupportedOperationException() },
		logger = SilentTestLogger,
		loggingLevel = LoggingLevel.NONE
	) {
		override fun extractErrorMessage(response: ApiResponse.Failure.Error): String? = "extracted"
		fun <T : Any> call(c: () -> ApiResponse<RootJsonPage<T>>) = doPagedAPICall("paged", c)
	}

	private val repo = PagingRepo()
	private fun ok() = retrofit2.Response.success(Unit)
	private fun <T : Any> success(p: RootJsonPage<T>) = ApiResponse.Success(data = p, tag = ok())

	@Test
	fun `success with data returns PageData`() {
		val result = repo.call { success(TestRootJsonPage(true, "ok", listOf("a", "b"))) }
		assertTrue(result is APIResult.Success)
		assertEquals(listOf("a", "b"), (result as APIResult.Success).data.data)
	}

	@Test
	fun `success false maps to error`() {
		val result = repo.call { success(TestRootJsonPage(false, "bad", listOf("x"))) }
		result as APIResult.Error
		assertEquals(200, result.responseCode)
		assertEquals("bad", result.message)
	}

	@Test
	fun `success with no data maps to error`() {
		val result = repo.call<String> { success(TestRootJsonPage(true, "empty", null)) }
		assertTrue(result is APIResult.Error)
	}

	@Test
	fun `failure error maps to error with extracted message`() {
		val result = repo.call<String> {
			ApiResponse.Failure.Error(
				retrofit2.Response.error<Unit>(422, """{}""".toResponseBody("application/json".toMediaType()))
			)
		}
		result as APIResult.Error
		assertEquals(422, result.responseCode)
		assertEquals("extracted", result.message)
	}

	@Test
	fun `failure exception maps to error`() {
		val result = repo.call<String> { ApiResponse.exception(RuntimeException("boom")) }
		result as APIResult.Error
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, result.responseCode)
	}
}
