package uk.co.appoly.droid.barcodescanner

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for a bug found by running the demo app, not by compiling it.
 *
 * `kotlinx-coroutines-play-services` maps a *cancelled* Play services `Task` to a
 * [CancellationException] — and Play services cancels the Task when the user backs out of the
 * scanner UI. Rethrowing it (the obvious "never swallow CancellationException" reflex) cancels the
 * caller instead of returning a result, which made [OneShotScanResult.Cancelled] unreachable: the
 * demo screen showed no result at all after tapping ✕.
 *
 * Nothing in the type system catches that, so it is pinned here.
 */
class OneShotCancellationTest {

	@Test
	fun `a live caller treats task cancellation as a user cancellation`() = runTest {
		// Returning normally is the signal for "the user backed out" — the caller then maps it to
		// OneShotScanResult.Cancelled.
		awaitUserCancellation()
	}

	@Test
	fun `a cancelled caller propagates instead of reporting a user cancellation`() = runTest {
		val job = Job()
		var enteredBlock = false
		var returnedNormally = false

		val outcome = runCatching {
			withContext(job) {
				// Cancel from *inside*, after the block is running. Cancelling beforehand would
				// make withContext throw on entry and the test would pass without ever calling
				// the thing under test.
				enteredBlock = true
				job.cancel()
				awaitUserCancellation()
				returnedNormally = true
			}
		}

		assertTrue("the block never ran, so nothing was actually exercised", enteredBlock)
		assertTrue(
			"awaitUserCancellation returned normally inside a cancelled caller, so a cancelled " +
				"screen would be misreported as the user cancelling the scan",
			!returnedNormally,
		)
		assertTrue(
			"expected the caller's own cancellation to propagate, got ${outcome.exceptionOrNull()}",
			outcome.exceptionOrNull() is CancellationException,
		)
	}
}
