package uk.co.appoly.droid.barcodescanner.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.appoly.droid.barcodescanner.BarcodeFormat
import uk.co.appoly.droid.barcodescanner.ScannedBarcode
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The debouncer is the one piece of the camera module that can be tested without a camera, and
 * the one most likely to be got subtly wrong — a global debounce looks identical to a per-code
 * one until there are two barcodes in frame, at which point it stops working entirely.
 */
class BarcodeDebouncerTest {

	private fun barcode(raw: String, format: BarcodeFormat = BarcodeFormat.Ean13) =
		ScannedBarcode(rawValue = raw, format = format)

	@Test
	fun `the first sighting of a code is always emitted`() {
		val debouncer = BarcodeDebouncer(window = 1.seconds, timeSource = TestTimeSource())

		assertTrue(debouncer.shouldEmit(barcode("A")))
	}

	@Test
	fun `a repeat inside the window is suppressed`() {
		val time = TestTimeSource()
		val debouncer = BarcodeDebouncer(window = 1.seconds, timeSource = time)

		assertTrue(debouncer.shouldEmit(barcode("A")))
		time += 400.milliseconds
		assertFalse(debouncer.shouldEmit(barcode("A")))
		time += 400.milliseconds
		assertFalse(debouncer.shouldEmit(barcode("A")))
	}

	@Test
	fun `a repeat after the window is emitted again`() {
		val time = TestTimeSource()
		val debouncer = BarcodeDebouncer(window = 1.seconds, timeSource = time)

		assertTrue(debouncer.shouldEmit(barcode("A")))
		time += 1.seconds
		assertTrue(debouncer.shouldEmit(barcode("A")))
	}

	@Test
	fun `suppression does not extend the window`() {
		// A code held in frame is re-detected constantly. If each suppressed sighting reset the
		// clock, the code would never be emitted a second time at all.
		val time = TestTimeSource()
		val debouncer = BarcodeDebouncer(window = 1.seconds, timeSource = time)

		assertTrue(debouncer.shouldEmit(barcode("A")))
		repeat(9) {
			time += 100.milliseconds
			assertFalse(debouncer.shouldEmit(barcode("A")))
		}
		time += 100.milliseconds
		assertTrue("the window should have expired 1s after the emission, not after the last sighting", debouncer.shouldEmit(barcode("A")))
	}

	@Test
	fun `debouncing is per code, not global`() {
		// Two labels in frame: ML Kit reports both on every frame. A global debounce would let
		// them alternate and fire on every single frame — the exact bug this design avoids.
		val time = TestTimeSource()
		val debouncer = BarcodeDebouncer(window = 1.seconds, timeSource = time)

		assertTrue(debouncer.shouldEmit(barcode("A")))
		assertTrue(debouncer.shouldEmit(barcode("B")))

		time += 100.milliseconds
		assertFalse(debouncer.shouldEmit(barcode("A")))
		assertFalse(debouncer.shouldEmit(barcode("B")))
	}

	@Test
	fun `codes are keyed on raw value, not on format`() {
		val time = TestTimeSource()
		val debouncer = BarcodeDebouncer(window = 1.seconds, timeSource = time)

		assertTrue(debouncer.shouldEmit(barcode("A", BarcodeFormat.Ean13)))
		time += 100.milliseconds
		assertFalse(debouncer.shouldEmit(barcode("A", BarcodeFormat.QrCode)))
	}

	@Test
	fun `a null window disables debouncing entirely`() {
		// Callers that de-duplicate downstream pass null and expect every detection through.
		val time = TestTimeSource()
		val debouncer = BarcodeDebouncer(window = null, timeSource = time)

		repeat(50) {
			assertTrue(debouncer.shouldEmit(barcode("A")))
		}
	}

	@Test
	fun `expired entries are pruned rather than accumulating`() {
		// A long scanning session over many distinct codes must not grow the map without bound.
		val time = TestTimeSource()
		val debouncer = BarcodeDebouncer(window = 1.seconds, timeSource = time)

		repeat(500) { index ->
			assertTrue(debouncer.shouldEmit(barcode("code-$index")))
			time += 100.milliseconds
		}

		assertTrue(
			"expired entries should have been pruned, leaving roughly one window's worth",
			debouncer.trackedCodeCount < 100,
		)
	}

	@Test
	fun `pruning does not drop entries that are still suppressing`() {
		val time = TestTimeSource()
		val debouncer = BarcodeDebouncer(window = 10.seconds, timeSource = time)

		// Push past the prune threshold with codes that are all still inside their window.
		repeat(200) { index ->
			assertTrue(debouncer.shouldEmit(barcode("code-$index")))
		}
		time += 1.seconds

		repeat(200) { index ->
			assertFalse(
				"code-$index was pruned while still inside its window",
				debouncer.shouldEmit(barcode("code-$index")),
			)
		}
	}
}
