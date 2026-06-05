package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.model.APIResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RefreshableAPIFlow] and [GenericBaseRepo.callApiAsFlow]. Coroutines run on
 * the test scheduler via [runTest]'s backgroundScope; library logging is inert in JVM tests
 * (isReturnDefaultValues).
 */
class RefreshableAPIFlowTest {

	/** Minimal repo exposing the protected callApiAsFlow helper. */
	private class FlowRepo : GenericBaseRepo(
		getRetrofitClient = { throw UnsupportedOperationException() },
		logger = SilentTestLogger,
		loggingLevel = LoggingLevel.NONE
	) {
		override fun extractErrorMessage(response: ApiResponse.Failure.Error): String? = null
		fun <T : Any> exposeFlow(call: suspend () -> APIResult<T>) = callApiAsFlow(call)
		fun <T : Any> exposeRefreshable(
			scope: kotlinx.coroutines.CoroutineScope,
			call: suspend () -> APIResult<T>
		) = callApiAsRefreshableFlow(scope = scope, apiCall = call)
	}

	@Test
	fun `callApiAsFlow emits Loading then the mapped result`() = runTest {
		val emissions = FlowRepo().exposeFlow { APIResult.Success(7) }.toList()
		assertEquals(listOf(APIFlowState.Loading, APIFlowState.Success(7)), emissions)
	}

	@Test
	fun `callApiAsFlow maps an error result`() = runTest {
		val emissions = FlowRepo().exposeFlow<Int> { APIResult.Error(404, "nope") }.toList()
		assertEquals(APIFlowState.Loading, emissions[0])
		assertTrue(emissions[1].isError())
		assertEquals(404, (emissions[1] as APIFlowState.Error).responseCode)
	}

	@Test
	fun `initial refresh fetches and emits success`() = runTest {
		val flow = RefreshableAPIFlow(
			apiCall = { APIResult.Success("hello") },
			scope = backgroundScope
		)
		assertEquals("hello", flow.first { it is APIFlowState.Success }.successData())
	}

	@Test
	fun `an initial value is emitted without refreshing`() = runTest {
		var called = false
		val flow = RefreshableAPIFlow(
			initialValue = "seed",
			apiCall = { called = true; APIResult.Success("fetched") },
			scope = backgroundScope
		)
		assertEquals("seed", flow.first { it is APIFlowState.Success }.successData())
		advanceUntilIdle()
		assertEquals(false, called) // initialValue != null => no initial refresh
	}

	@Test
	fun `refresh re-invokes the api call`() = runTest {
		var n = 0
		val flow = RefreshableAPIFlow(
			apiCall = { APIResult.Success("call${n++}") },
			scope = backgroundScope
		)
		assertEquals("call0", flow.first { it.successData() == "call0" }.successData())

		flow.refresh()
		assertEquals("call1", flow.first { it.successData() == "call1" }.successData())
	}

	@Test
	fun `refresh with a simulated error emits that error`() = runTest {
		val flow = RefreshableAPIFlow(
			initialValue = "seed",
			apiCall = { APIResult.Success("x") },
			scope = backgroundScope
		)
		flow.first { it is APIFlowState.Success }
		flow.refresh(simulatedError = APIFlowState.Error(503, "simulated"))
		val state = flow.first { it is APIFlowState.Error }
		assertEquals("simulated", (state as APIFlowState.Error).message)
	}

	@Test
	fun `an exception in the api call becomes an error state`() = runTest {
		val flow = RefreshableAPIFlow<String>(
			apiCall = { throw RuntimeException("boom") },
			scope = backgroundScope
		)
		val state = flow.first { it is APIFlowState.Error }
		assertEquals(GenericBaseRepo.RESPONSE_EXCEPTION_CODE, (state as APIFlowState.Error).responseCode)
	}

	@Test
	fun `manualUpdate and update mutate the success value`() = runTest {
		val flow = RefreshableAPIFlow<String>(
			initialValue = "seed",
			apiCall = { APIResult.Success("x") },
			scope = backgroundScope
		)
		flow.first { it.successData() == "seed" }

		flow.manualUpdate("manual")
		assertEquals("manual", flow.first { it.successData() == "manual" }.successData())

		flow.update { (it ?: "") + "!" }
		assertEquals("manual!", flow.first { it.successData() == "manual!" }.successData())

		flow.updateState { APIFlowState.Error(1, "forced") }
		assertTrue(flow.first { it is APIFlowState.Error }.isError())
	}

	@Test
	fun `callApiAsRefreshableFlow produces a flow that fetches success`() = runTest {
		val flow = FlowRepo().exposeRefreshable(scope = backgroundScope) { APIResult.Success("repo-data") }
		assertEquals("repo-data", flow.first { it is APIFlowState.Success }.successData())
	}

	@Test
	fun `RefreshableAPIFlow stateIn exposes the latest state`() = runTest {
		val flow = RefreshableAPIFlow(
			apiCall = { APIResult.Success("via-stateIn") },
			scope = backgroundScope
		)
		val stateFlow = flow.stateIn(scope = backgroundScope)
		assertEquals("via-stateIn", stateFlow.first { it is APIFlowState.Success }.successData())
	}
}
