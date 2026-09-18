package uk.co.appoly.droid.barcodescanner.camera

import uk.co.appoly.droid.barcodescanner.ScannedBarcode
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Decides which decoded barcodes are deliberate scans.
 *
 * ML Kit re-reports every barcode in view on every analysed frame — tens of times a second. Turning
 * that into "the user scanned this" is one state machine, not a stack of independent filters, which
 * is why dwell, miss tolerance and the repeat window all live here rather than in separate gates
 * that would have to agree with each other.
 *
 * The whole model is one **track** per raw value, holding when it was first and last seen and
 * whether it has been reported. Everything else is derived:
 *
 * | State | Seen this frame | Absent, within budget | Absent, past budget |
 * |---|---|---|---|
 * | Dwelling | report once [ScanPolicy.dwell] has elapsed | keep waiting | forget it |
 * | Reported | nothing | nothing | forget it |
 *
 * The budget is [ScanPolicy.missTolerance] before a code has been reported and
 * [ScanPolicy.rearm] after. Both are absence budgets measured from the last sighting, which is what
 * keeps them from interacting confusingly — they are the same kind of number applied to different
 * states.
 *
 * The rule that falls out, and the one worth remembering: **a code is reported at most once per
 * track; a track lives while the code keeps being seen; reporting it again needs a fresh track.**
 * So holding one barcode steady reports it once however long you hold it, and drifting out briefly
 * and back does not re-report it.
 *
 * Deliberately *not* storing when a code was reported. The moment that field exists someone
 * measures the repeat window from it, and a code held for ten seconds that dips out for one
 * re-fires — the exact behaviour this design removes.
 *
 * Not thread-safe, and does not need to be: the camera composable only ever touches it from the
 * main thread, where ML Kit's callbacks are marshalled.
 *
 * @param timeSource injectable so tests can drive the clock instead of sleeping.
 */
internal class BarcodeTracker(
	private val policy: ScanPolicy,
	private val timeSource: TimeSource = TimeSource.Monotonic,
) {
	private class Track(
		val firstSeen: TimeMark,
		var lastSeen: TimeMark,
		var reported: Boolean,
	)

	private val tracks = LinkedHashMap<String, Track>()

	/** How many codes are currently tracked. Exists so the expiry pass is observable to tests. */
	internal val trackedCount: Int get() = tracks.size

	/**
	 * How far through its dwell [rawValue] is, from 0f to 1f, for an overlay to draw.
	 *
	 * 1f for a code with no track yet (nothing to wait for), for one already reported, and when
	 * the policy has no dwell — in every one of those cases there is no progress left to show.
	 */
	fun dwellProgress(rawValue: String): Float {
		val dwell = policy.dwell ?: return 1f
		if (dwell <= Duration.ZERO) return 1f
		val track = tracks[rawValue] ?: return 0f
		if (track.reported) return 1f
		return (track.firstSeen.elapsedNow() / dwell).toFloat().coerceIn(0f, 1f)
	}

	/**
	 * Feeds one frame's worth of decodes and returns those that count as scans.
	 *
	 * @param visible barcodes that passed the region filter, ordered nearest-to-region-centre
	 * first. The ordering is what makes [ScanMode.Single] lock onto the code the user is actually
	 * aiming at rather than whichever one ML Kit happened to list first.
	 */
	fun accept(visible: List<ScannedBarcode>): List<ScannedBarcode> {
		// 1. Expire first, and before touching anything. A code gone longer than its budget must
		//    get a fresh track — and therefore a fresh dwell — rather than resuming the old one.
		tracks.entries.removeAll { (_, track) ->
			track.lastSeen.elapsedNow() >= if (track.reported) policy.rearm else policy.missTolerance
		}

		// 2. Touch surviving tracks so presence keeps them alive.
		val now = timeSource.markNow()
		visible.forEach { barcode -> tracks[barcode.rawValue]?.lastSeen = now }

		// 3. Create tracks for codes we are not already following.
		//
		//    In Single mode a new track may only start when nothing else is still in play, which
		//    is what stops a second barcode in the reticle stealing focus mid-dwell. The cost is
		//    that panning from one code to the next takes missTolerance + dwell; that is the knob
		//    to turn if it feels sluggish, rather than a new one.
		val somethingInPlay = tracks.values.any { it.lastSeen.elapsedNow() < policy.missTolerance }
		if (policy.mode == ScanMode.Multi || !somethingInPlay) {
			visible.asSequence()
				.filter { it.rawValue !in tracks }
				.let { if (policy.mode == ScanMode.Single) it.take(1) else it }
				.forEach { tracks[it.rawValue] = Track(firstSeen = now, lastSeen = now, reported = false) }
		}

		// 4. Report anything present that has now dwelled long enough.
		val dwell = policy.dwell ?: Duration.ZERO
		return visible.filter { barcode ->
			val track = tracks[barcode.rawValue] ?: return@filter false
			!track.reported && track.firstSeen.elapsedNow() >= dwell
		}.onEach { tracks.getValue(it.rawValue).reported = true }
	}
}
