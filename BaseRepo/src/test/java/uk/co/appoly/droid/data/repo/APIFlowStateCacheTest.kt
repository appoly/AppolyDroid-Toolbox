package uk.co.appoly.droid.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.data.remote.model.APIResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [cacheSuccessData] flow operators (plain + transforming, flow + stateIn
 * variants), [asApiFlowState], and [APIFlowState.map]'s non-success branches.
 */
class APIFlowStateCacheTest {

	private fun stateFlow() = flowOf<APIFlowState<Int>>(
		APIFlowState.Loading,
		APIFlowState.Success(5),
		APIFlowState.Loading,
		APIFlowState.Error(500, "boom")
	)

	@Test
	fun `cacheSuccessData retains the last success across loading and error`() = runTest {
		val emissions = stateFlow().cacheSuccessData(initial = 0).toList()
		// scan emits initial first, keeps cached for non-success states.
		assertEquals(listOf(0, 0, 5, 5, 5), emissions)
	}

	@Test
	fun `cacheSuccessData with transform maps and caches`() = runTest {
		val emissions = stateFlow().cacheSuccessData(initial = "none") { "v$it" }.toList()
		assertEquals(listOf("none", "none", "v5", "v5", "v5"), emissions)
	}

	@Test
	fun `cacheSuccessData stateIn exposes the cached value`() = runTest {
		val state = stateFlow().cacheSuccessData(scope = backgroundScope, initial = -1)
		assertEquals(5, state.first { it == 5 })
	}

	@Test
	fun `cacheSuccessData stateIn with transform exposes the mapped value`() = runTest {
		val state = stateFlow().cacheSuccessData(scope = backgroundScope, initial = "none") { "v$it" }
		assertEquals("v5", state.first { it == "v5" })
	}

	@Test
	fun `asApiFlowState maps APIResult success and error`() {
		assertEquals(APIFlowState.Success(7), APIResult.Success(7).asApiFlowState())
		val err = APIResult.Error(404, "nope").asApiFlowState()
		assertTrue(err.isError())
		assertEquals(404, (err as APIFlowState.Error).responseCode)
	}

	@Test
	fun `map transforms success and passes loading and error through`() {
		assertEquals(APIFlowState.Success("9"), APIFlowState.Success(9).map { it.toString() })
		assertTrue(APIFlowState.Loading.map { x: Int -> x.toString() } is APIFlowState.Loading)
		val mappedErr = APIFlowState.Error(500, "x").map { y: Int -> y.toString() }
		assertTrue(mappedErr.isError())
		assertEquals(500, (mappedErr as APIFlowState.Error).responseCode)
	}
}
