package uk.co.appoly.droid.data.repo

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.data.remote.model.APIResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [APIFlowState], its conversions ([asApiFlowState]), predicate/accessor
 * extensions ([isLoading], [isSuccess], [successData], [successList], [isError],
 * [errorMessage]), [map], and the [cacheSuccessData] flow operators.
 */
class APIFlowStateTest {

	private val loading: APIFlowState<Int> = APIFlowState.Loading
	private val success: APIFlowState<Int> = APIFlowState.Success(7)
	private val error: APIFlowState<Int> = APIFlowState.Error(500, "server")

	@Test
	fun `asApiFlowState maps APIResult Success and Error`() {
		assertEquals(APIFlowState.Success(7), APIResult.Success(7).asApiFlowState())

		val mapped = APIResult.Error(404, "missing").asApiFlowState()
		assertTrue(mapped.isError())
		assertEquals(404, (mapped as APIFlowState.Error).responseCode)
		assertEquals("missing", mapped.message)
	}

	@Test
	fun `Error constructor from APIResult Error copies code and message`() {
		val e = APIFlowState.Error(APIResult.Error(403, "forbidden"))
		assertEquals(403, e.responseCode)
		assertEquals("forbidden", e.message)
	}

	@Test
	fun `isLoading and isNotLoading`() {
		assertTrue(loading.isLoading())
		assertFalse(success.isLoading())
		assertFalse((null as APIFlowState<Int>?).isLoading())

		assertFalse(loading.isNotLoading())
		assertTrue(success.isNotLoading())
		assertTrue((null as APIFlowState<Int>?).isNotLoading())
	}

	@Test
	fun `isSuccess and successData`() {
		assertTrue(success.isSuccess())
		assertEquals(7, success.successData())
		assertNull(loading.successData())
		assertNull(error.successData())
		assertNull((null as APIFlowState<Int>?).successData())
	}

	@Test
	fun `successList returns data for Success and emptyList otherwise`() {
		val list: APIFlowState<List<String>> = APIFlowState.Success(listOf("a", "b"))
		assertEquals(listOf("a", "b"), list.successList())

		val loadingList: APIFlowState<List<String>> = APIFlowState.Loading
		assertEquals(emptyList<String>(), loadingList.successList())
		assertEquals(emptyList<String>(), (null as APIFlowState<List<String>>?).successList())
	}

	@Test
	fun `isError and errorMessage`() {
		assertTrue(error.isError())
		assertEquals("server", error.errorMessage())
		assertNull(success.errorMessage())
		assertNull(loading.errorMessage())
		assertNull((null as APIFlowState<Int>?).errorMessage())
	}

	@Test
	fun `map transforms Success and leaves Loading and Error unchanged`() {
		assertEquals(APIFlowState.Success("7"), success.map { it.toString() })
		assertEquals(APIFlowState.Loading, loading.map { it.toString() })

		val mappedError = error.map { it.toString() }
		assertTrue(mappedError.isError())
		assertEquals(500, (mappedError as APIFlowState.Error).responseCode)
	}

	@Test
	fun `cacheSuccessData retains last success across Loading and Error`() = runTest {
		val out = flowOf<APIFlowState<Int>>(
			APIFlowState.Loading,
			APIFlowState.Success(1),
			APIFlowState.Loading,
			APIFlowState.Error(500, "x"),
			APIFlowState.Success(2)
		).cacheSuccessData(initial = 0).toList()

		// scan emits the initial first, then one value per upstream emission.
		assertEquals(listOf(0, 0, 1, 1, 1, 2), out)
	}

	@Test
	fun `cacheSuccessData with map transforms cached success data`() = runTest {
		val out = flowOf<APIFlowState<Int>>(
			APIFlowState.Loading,
			APIFlowState.Success(2),
			APIFlowState.Loading
		).cacheSuccessData(initial = "none") { value -> "v$value" }.toList()

		assertEquals(listOf("none", "none", "v2", "v2"), out)
	}
}
