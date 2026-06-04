package uk.co.appoly.droid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [UiState] sealed class and its predicate extensions
 * ([isIdle], [isLoading], [isSuccess], [isError], [isNotLoading], [isNotError]).
 *
 * These predicates back smart-casting via Kotlin contracts and are used pervasively in
 * consuming UIs, so each is verified against every state plus the `null` receiver.
 */
class UiStateTest {

	private val idle: UiState = UiState.Idle()
	private val loading: UiState = UiState.Loading()
	private val success: UiState = UiState.Success()
	private val error: UiState = UiState.Error("boom")

	@Test
	fun `isIdle is true only for Idle`() {
		assertTrue(idle.isIdle())
		assertFalse(loading.isIdle())
		assertFalse(success.isIdle())
		assertFalse(error.isIdle())
		assertFalse((null as UiState?).isIdle())
	}

	@Test
	fun `isLoading is true only for Loading`() {
		assertTrue(loading.isLoading())
		assertFalse(idle.isLoading())
		assertFalse(success.isLoading())
		assertFalse(error.isLoading())
		assertFalse((null as UiState?).isLoading())
	}

	@Test
	fun `isSuccess is true only for Success`() {
		assertTrue(success.isSuccess())
		assertFalse(idle.isSuccess())
		assertFalse(loading.isSuccess())
		assertFalse(error.isSuccess())
		assertFalse((null as UiState?).isSuccess())
	}

	@Test
	fun `isError is true only for Error`() {
		assertTrue(error.isError())
		assertFalse(idle.isError())
		assertFalse(loading.isError())
		assertFalse(success.isError())
		assertFalse((null as UiState?).isError())
	}

	@Test
	fun `isNotLoading is false only for Loading`() {
		assertFalse(loading.isNotLoading())
		assertTrue(idle.isNotLoading())
		assertTrue(success.isNotLoading())
		assertTrue(error.isNotLoading())
		// A null receiver is "not Loading".
		assertTrue((null as UiState?).isNotLoading())
	}

	@Test
	fun `isNotError is false only for Error`() {
		assertFalse(error.isNotError())
		assertTrue(idle.isNotError())
		assertTrue(loading.isNotError())
		assertTrue(success.isNotError())
		// A null receiver is "not Error".
		assertTrue((null as UiState?).isNotError())
	}

	@Test
	fun `isError smart-casts to expose the message`() {
		val state: UiState = UiState.Error("network down")
		// If the contract holds, the compiler smart-casts and `.message` is accessible.
		if (state.isError()) {
			assertEquals("network down", state.message)
		} else {
			throw AssertionError("expected Error to satisfy isError()")
		}
	}

	@Test
	fun `key is carried on every state and distinguishes instances`() {
		assertEquals("k", UiState.Idle("k").key)
		assertEquals("k", UiState.Loading("k").key)
		assertEquals("k", UiState.Success("k").key)
		assertEquals("k", UiState.Error("msg", "k").key)
		assertEquals(null, UiState.Idle().key)
		// Same type, different keys -> not equal (key is part of the data class).
		assertNotEquals(UiState.Loading("a"), UiState.Loading("b"))
		assertEquals(UiState.Loading("a"), UiState.Loading("a"))
	}

	@Test
	fun `Error equality considers message and key`() {
		assertEquals(UiState.Error("m", "k"), UiState.Error("m", "k"))
		assertNotEquals(UiState.Error("m", "k"), UiState.Error("other", "k"))
	}
}
