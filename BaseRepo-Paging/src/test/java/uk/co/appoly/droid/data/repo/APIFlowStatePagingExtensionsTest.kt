package uk.co.appoly.droid.data.repo

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for [asPagingData] / [mapToPagingData], covering the Success / Loading / Error
 * branches of the APIFlowState -> PagingData conversion.
 */
class APIFlowStatePagingExtensionsTest {

	@Test
	fun `asPagingData maps each state to non-null PagingData`() {
		assertNotNull(APIFlowState.Success(listOf("a", "b")).asPagingData())
		assertNotNull(APIFlowState.Loading.asPagingData<String>())
		assertNotNull(APIFlowState.Error(500, "boom").asPagingData<String>())
	}

	@Test
	fun `mapToPagingData converts each emission`() = runTest {
		val out = flowOf<APIFlowState<List<String>>>(
			APIFlowState.Loading,
			APIFlowState.Success(listOf("x")),
			APIFlowState.Error(404, "nope")
		).mapToPagingData().toList()
		assertEquals(3, out.size)
		out.forEach { assertNotNull(it) }
	}
}
