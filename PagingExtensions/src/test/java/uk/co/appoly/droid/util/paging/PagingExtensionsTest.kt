package uk.co.appoly.droid.util.paging

import androidx.paging.LoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [LoadState] predicate extensions ([isLoading], [isError]) and the
 * [PagingErrorType] enum.
 */
class PagingExtensionsTest {

	private val loading: LoadState = LoadState.Loading
	private val errored: LoadState = LoadState.Error(RuntimeException("boom"))
	private val notLoadingIncomplete: LoadState = LoadState.NotLoading(endOfPaginationReached = false)
	private val notLoadingComplete: LoadState = LoadState.NotLoading(endOfPaginationReached = true)

	@Test
	fun `isLoading is true only for Loading`() {
		assertTrue(loading.isLoading())
		assertFalse(errored.isLoading())
		assertFalse(notLoadingIncomplete.isLoading())
		assertFalse(notLoadingComplete.isLoading())
	}

	@Test
	fun `isError is true only for Error`() {
		assertTrue(errored.isError())
		assertFalse(loading.isError())
		assertFalse(notLoadingIncomplete.isError())
		assertFalse(notLoadingComplete.isError())
	}

	@Test
	fun `isError smart-casts to expose the underlying throwable`() {
		val cause = IllegalStateException("offline")
		val state: LoadState = LoadState.Error(cause)
		if (state.isError()) {
			assertEquals(cause, state.error)
		} else {
			throw AssertionError("expected Error to satisfy isError()")
		}
	}

	@Test
	fun `PagingErrorType has the three paging positions`() {
		assertEquals(3, PagingErrorType.entries.size)
		assertEquals(
			listOf(PagingErrorType.PREPEND, PagingErrorType.APPEND, PagingErrorType.REFRESH),
			PagingErrorType.entries.toList()
		)
		assertEquals(PagingErrorType.REFRESH, PagingErrorType.valueOf("REFRESH"))
	}
}
