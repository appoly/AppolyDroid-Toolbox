package uk.co.appoly.droid.barcodescanner.camera

import androidx.annotation.VisibleForTesting
import uk.co.appoly.droid.barcodescanner.ScannedBarcode
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Per-code rate limiter for the continuous scanner.
 *
 * ML Kit reports *every* barcode in the frame on *every* analysed frame, which is tens of
 * callbacks a second for a code the user is simply holding still. Debouncing per code rather
 * than globally matters: with two labels in shot, a global debounce would let them alternate and
 * fire on every frame anyway, while this drops each one until its own window expires.
 *
 * Not thread-safe by design — the camera composable only ever touches it from the main thread,
 * where ML Kit's callbacks are marshalled to.
 *
 * @param window how long a given raw value stays suppressed after being emitted. Null disables
 * debouncing entirely, so every detection is reported.
 * @param timeSource injectable for tests; production uses the monotonic clock.
 */
internal class BarcodeDebouncer(
	private val window: Duration?,
	private val timeSource: TimeSource = TimeSource.Monotonic,
) {
	private val lastEmitted = HashMap<String, TimeMark>()

	/** How many codes are currently being tracked. Exists so [pruneExpired] is observable. */
	@get:VisibleForTesting
	internal val trackedCodeCount: Int get() = lastEmitted.size

	/**
	 * Returns true if [barcode] should be reported to the caller, recording the emission when so.
	 */
	fun shouldEmit(barcode: ScannedBarcode): Boolean {
		val window = window ?: return true
		val previous = lastEmitted[barcode.rawValue]
		if (previous != null && previous.elapsedNow() < window) return false
		pruneExpired(window)
		lastEmitted[barcode.rawValue] = timeSource.markNow()
		return true
	}

	/**
	 * Drops entries whose window has already expired. Without this, a session spent scanning a
	 * long tail of distinct codes — a warehouse pick, say — grows the map without bound for no
	 * benefit, since an expired entry can never suppress anything again.
	 */
	private fun pruneExpired(window: Duration) {
		if (lastEmitted.size < PRUNE_THRESHOLD) return
		lastEmitted.entries.removeAll { (_, mark) -> mark.elapsedNow() >= window }
	}

	private companion object {
		/** Only worth walking the map once it is big enough to be worth the walk. */
		const val PRUNE_THRESHOLD = 64
	}
}
