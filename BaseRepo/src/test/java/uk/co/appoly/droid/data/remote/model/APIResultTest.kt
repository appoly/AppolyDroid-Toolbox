package uk.co.appoly.droid.data.remote.model

import uk.co.appoly.droid.util.NoConnectivityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [APIResult], its [APIResult.Error] convenience constructors, and the
 * predicate/transform extensions ([isSuccess], [successOrNull], [isError], [isNetworkError],
 * [onSuccess], [onError], [map], [mapSuccess]).
 */
class APIResultTest {

	private val success: APIResult<Int> = APIResult.Success(42)
	private val error: APIResult<Int> = APIResult.Error(404, "not found")

	@Test
	fun `isSuccess and successOrNull`() {
		assertTrue(success.isSuccess())
		assertFalse(error.isSuccess())
		assertFalse((null as APIResult<Int>?).isSuccess())

		assertEquals(42, success.successOrNull())
		assertNull(error.successOrNull())
		assertNull((null as APIResult<Int>?).successOrNull())
	}

	@Test
	fun `isError`() {
		assertTrue(error.isError())
		assertFalse(success.isError())
		assertFalse((null as APIResult<Int>?).isError())
	}

	@Test
	fun `Error message-only constructor defaults responseCode to -1`() {
		val e = APIResult.Error("boom")
		assertEquals(-1, e.responseCode)
		assertEquals("boom", e.message)
		assertNull(e.throwable)
	}

	@Test
	fun `Error copy constructor preserves code, message and throwable`() {
		val cause = IllegalStateException("x")
		val original = APIResult.Error(500, "server", cause)
		val copy = APIResult.Error(original)
		assertEquals(500, copy.responseCode)
		assertEquals("server", copy.message)
		assertSame(cause, copy.throwable)
	}

	@Test
	fun `exception falls back to APIError wrapping the message when throwable is null`() {
		val e = APIResult.Error(400, "bad")
		val ex = e.exception
		assertTrue(ex is APIError)
		assertEquals("bad", ex.message)
	}

	@Test
	fun `exception returns the supplied throwable when present`() {
		val cause = RuntimeException("real")
		val e = APIResult.Error(400, "bad", cause)
		assertSame(cause, e.exception)
	}

	@Test
	fun `isNetworkError true only for Error wrapping NoConnectivityException`() {
		val networkError: APIResult<Int> = APIResult.Error(-1, "offline", NoConnectivityException())
		assertTrue(networkError.isNetworkError())

		val otherError: APIResult<Int> = APIResult.Error(500, "server", RuntimeException())
		assertFalse(otherError.isNetworkError())

		assertFalse(success.isNetworkError())
		assertFalse((null as APIResult<Int>?).isNetworkError())
	}

	@Test
	fun `onSuccess runs action only for Success`() {
		var ran = false
		val out = success.onSuccess { ran = true; APIResult.Success(it.data + 1) }
		assertTrue(ran)
		assertEquals(43, out.successOrNull())

		ran = false
		val passthrough = error.onSuccess { ran = true; APIResult.Success(0) }
		assertFalse(ran)
		assertSame(error, passthrough)
	}

	@Test
	fun `onError runs action only for Error`() {
		var ran = false
		val recovered = error.onError { ran = true; APIResult.Success(0) }
		assertTrue(ran)
		assertEquals(0, recovered.successOrNull())

		ran = false
		val passthrough = success.onError { ran = true; APIResult.Success(-1) }
		assertFalse(ran)
		assertSame(success, passthrough)
	}

	@Test
	fun `map transforms the whole result`() {
		val mapped: APIResult<String> = success.map { r ->
			when (r) {
				is APIResult.Success -> APIResult.Success(r.data.toString())
				is APIResult.Error -> r
			}
		}
		assertEquals("42", mapped.successOrNull())
	}

	@Test
	fun `mapSuccess transforms Success data and passes Error through`() {
		val mapped = success.mapSuccess { this * 2 }
		assertEquals(84, mapped.successOrNull())

		val mappedError = error.mapSuccess { this * 2 }
		assertTrue(mappedError.isError())
		assertEquals(404, (mappedError as APIResult.Error).responseCode)
	}

	@Test
	fun `toString includes the payload and message`() {
		// Success/Error are data classes, so their generated toString is used (the custom
		// override on the sealed parent is shadowed) — assert on content, not exact format.
		assertTrue(success.toString().contains("42"))
		assertTrue(error.toString().contains("not found"))
	}
}
