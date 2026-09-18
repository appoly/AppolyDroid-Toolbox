package uk.co.appoly.droid.barcodescanner.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.appoly.droid.barcodescanner.BarcodeFormat
import uk.co.appoly.droid.barcodescanner.ScannedBarcode
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * Pins the scan state machine, which is the whole of "did the user mean to scan that".
 *
 * None of this is reachable from a UI test — the difference between reporting a barcode and
 * declining to is invisible to the view tree — and the failure modes are the ones a client
 * actually complains about: firing at a code glimpsed in passing, or firing twice for one parcel.
 */
class BarcodeTrackerTest {

	private val a = ScannedBarcode("AAA", BarcodeFormat.Code128)
	private val b = ScannedBarcode("BBB", BarcodeFormat.Code128)

	private fun tracker(
		time: TestTimeSource,
		mode: ScanMode = ScanMode.Single,
		dwell: kotlin.time.Duration? = 500.milliseconds,
		missTolerance: kotlin.time.Duration = 750.milliseconds,
		debounceWindow: kotlin.time.Duration? = 2.5.seconds,
	) = BarcodeTracker(
		policy = ScanPolicy(
			mode = mode,
			dwell = dwell,
			missTolerance = missTolerance,
			debounceWindow = debounceWindow,
		),
		timeSource = time,
	)

	@Test
	fun `a code glimpsed briefly is never reported`() {
		// The client complaint that started all this: a barcode that passes through frame for a
		// few frames must not register.
		val time = TestTimeSource()
		val tracker = tracker(time)

		repeat(4) {
			assertTrue(tracker.accept(listOf(a)).isEmpty())
			time += 100.milliseconds
		}
	}

	@Test
	fun `a code held for the dwell is reported once`() {
		val time = TestTimeSource()
		val tracker = tracker(time)

		tracker.accept(listOf(a))
		time += 500.milliseconds

		assertEquals(listOf(a), tracker.accept(listOf(a)))
	}

	@Test
	fun `holding a code steady reports it exactly once, however long`() {
		// The old debounce measured from the moment of reporting, so a held code re-fired every
		// window. Measuring absence instead means one presentation is one report.
		val time = TestTimeSource()
		val tracker = tracker(time)

		tracker.accept(listOf(a))
		time += 500.milliseconds
		assertEquals(listOf(a), tracker.accept(listOf(a)))

		var extraReports = 0
		repeat(100) {
			time += 100.milliseconds
			extraReports += tracker.accept(listOf(a)).size
		}
		assertEquals("a held code re-fired", 0, extraReports)
	}

	@Test
	fun `a brief wobble does not restart the dwell`() {
		// 1D codes flicker in and out as the detector loses them. Restarting the dwell on every
		// dropped frame would make them almost unscannable.
		val time = TestTimeSource()
		val tracker = tracker(time)

		tracker.accept(listOf(a))
		time += 300.milliseconds
		tracker.accept(emptyList())     // dropped frame, well inside missTolerance
		time += 200.milliseconds

		assertEquals("the wobble restarted the dwell", listOf(a), tracker.accept(listOf(a)))
	}

	@Test
	fun `a reported code that dips out briefly does not re-report`() {
		val time = TestTimeSource()
		val tracker = tracker(time)

		tracker.accept(listOf(a))
		time += 500.milliseconds
		assertEquals(listOf(a), tracker.accept(listOf(a)))

		time += 1.seconds                       // gone, but under the 2.5s rearm budget
		tracker.accept(emptyList())
		assertTrue("came back too soon and re-reported", tracker.accept(listOf(a)).isEmpty())
	}

	@Test
	fun `a code properly taken away and presented again reports again`() {
		val time = TestTimeSource()
		val tracker = tracker(time)

		tracker.accept(listOf(a))
		time += 500.milliseconds
		assertEquals(listOf(a), tracker.accept(listOf(a)))

		time += 3.seconds                       // past the rearm budget
		tracker.accept(emptyList())

		tracker.accept(listOf(a))               // fresh track, so a fresh dwell
		time += 500.milliseconds
		assertEquals(listOf(a), tracker.accept(listOf(a)))
	}

	@Test
	fun `single mode locks the first-ranked code and ignores the other`() {
		// The label-with-two-codes case: a 1D tracking number and a QR on the same parcel, both in
		// the reticle. Only the one the user is aiming at — first in the ordered list — may win.
		val time = TestTimeSource()
		val tracker = tracker(time, mode = ScanMode.Single)

		tracker.accept(listOf(a, b))
		time += 500.milliseconds

		assertEquals(listOf(a), tracker.accept(listOf(a, b)))
	}

	@Test
	fun `single mode never reports a second code while the first is still in view`() {
		// Deliberately run long enough that b WOULD have dwelled if it had been given a track —
		// an earlier version of this test stopped before that point and passed whether or not the
		// focus guard existed at all.
		val time = TestTimeSource()
		val tracker = tracker(time, mode = ScanMode.Single)

		tracker.accept(listOf(a))
		time += 300.milliseconds
		tracker.accept(listOf(b, a))            // b arrives, ranked ahead of a
		time += 300.milliseconds
		assertEquals("focus was stolen mid-dwell", listOf(a), tracker.accept(listOf(b, a)))

		// a is now reported and still in view, so it holds focus: b must stay silent no matter
		// how long it sits there.
		var bReports = 0
		repeat(20) {
			time += 200.milliseconds
			bReports += tracker.accept(listOf(b, a)).count { it == b }
		}
		assertEquals("b was reported while a was still in view", 0, bReports)
	}

	@Test
	fun `multi mode reports every code on its own schedule`() {
		val time = TestTimeSource()
		val tracker = tracker(time, mode = ScanMode.Multi)

		tracker.accept(listOf(a))
		time += 300.milliseconds
		tracker.accept(listOf(a, b))            // b arrives later, so dwells later
		time += 200.milliseconds

		assertEquals("a should report on its own dwell", listOf(a), tracker.accept(listOf(a, b)))

		time += 300.milliseconds
		assertEquals("b should report once its own dwell elapses", listOf(b), tracker.accept(listOf(a, b)))
	}

	@Test
	fun `a null dwell reports on first sighting`() {
		val time = TestTimeSource()
		val tracker = tracker(time, dwell = null)

		assertEquals(listOf(a), tracker.accept(listOf(a)))
	}

	@Test
	fun `tracks expire rather than accumulating`() {
		// Sweeping past a shelf of codes must not grow the map without bound. Expiry is by time,
		// so anything not seen recently is gone regardless of how many there were.
		val time = TestTimeSource()
		val tracker = tracker(time, mode = ScanMode.Multi)

		repeat(200) { index ->
			tracker.accept(listOf(ScannedBarcode("code-$index", BarcodeFormat.Code128)))
			time += 100.milliseconds
		}

		assertTrue(
			"tracks accumulated: ${tracker.trackedCount}",
			tracker.trackedCount < 40,
		)
	}

	@Test
	fun `rearm is never shorter than missTolerance`() {
		// A code that can re-arm faster than it can be forgotten would report twice from one
		// continuous presentation.
		val policy = ScanPolicy(missTolerance = 2.seconds, debounceWindow = 100.milliseconds)

		assertEquals(2.seconds, policy.rearm)
	}

	@Test
	fun `policies compare equal by value so remember does not rebuild the tracker`() {
		// Load-bearing: the camera holds its tracking state in remember(policy). A policy that
		// does not compare equal rebuilds that state every recomposition, and nothing would ever
		// finish its dwell.
		assertEquals(ScanPolicy(), ScanPolicy())
		assertEquals(ScanPolicy().hashCode(), ScanPolicy().hashCode())
		assertEquals(ScanRegion.Reticle(), ScanRegion.Reticle())
		assertEquals(
			ScanPolicy(region = ScanRegion.Reticle(0.5f)),
			ScanPolicy(region = ScanRegion.Reticle(0.5f)),
		)
	}
}
