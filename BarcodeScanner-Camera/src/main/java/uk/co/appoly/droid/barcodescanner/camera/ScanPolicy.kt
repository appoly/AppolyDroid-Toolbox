package uk.co.appoly.droid.barcodescanner.camera

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How many barcodes the scanner tracks at once. */
enum class ScanMode {
	/**
	 * One code at a time — the scanner locks onto a single barcode and ignores others until it
	 * has gone.
	 *
	 * Right for "scan this item, then the next": a label carrying both a 1D code and a QR, or two
	 * parcels in shot, cannot produce a result the user did not aim at.
	 */
	Single,

	/**
	 * Every code in the region is tracked independently and reported on its own schedule.
	 *
	 * Right for collecting several codes from one view — a shelf, a pallet label set.
	 */
	Multi,
}

/**
 * Which part of the camera frame a barcode must be in to count.
 *
 * The distinction matters more than it looks: the image the analyser sees is **wider** than the
 * preview the user sees, because the preview is cropped to the composable's bounds. So "the
 * scanner found it" and "the user could see it" are genuinely different things.
 */
sealed interface ScanRegion {

	/**
	 * Anything the analyser can decode, including barcodes outside the visible preview.
	 *
	 * The widest setting and rarely what you want — a code can be read from off-screen, which
	 * looks to the user like the scanner inventing results.
	 */
	data object Full : ScanRegion

	/** Only barcodes actually visible in the preview. */
	data object Visible : ScanRegion

	/**
	 * Only barcodes inside the aiming reticle — the default, and what [DefaultScanFrame] draws.
	 *
	 * A barcode counts when the *centre* of its bounding box falls inside the region, so a code
	 * larger than the reticle still scans when aimed at properly.
	 *
	 * @param widthFraction how much of the preview's width the region spans.
	 * @param aspectRatio width:height of the region. 1f is square; widen it for the long thin
	 * labels of 1D symbologies.
	 */
	class Reticle(
		val widthFraction: Float = 0.7f,
		val aspectRatio: Float = 1f,
	) : ScanRegion {
		override fun equals(other: Any?): Boolean = this === other ||
			(other is Reticle && widthFraction == other.widthFraction && aspectRatio == other.aspectRatio)

		override fun hashCode(): Int = 31 * widthFraction.hashCode() + aspectRatio.hashCode()

		override fun toString(): String = "Reticle(widthFraction=$widthFraction, aspectRatio=$aspectRatio)"
	}
}

/**
 * How [BarcodeScannerCamera] decides that a barcode is a deliberate scan rather than something
 * that drifted through the frame.
 *
 * Deliberately a single parameter rather than several on the composable. Adding a parameter to a
 * `@Composable` is a binary-incompatible change, so every future tuning knob would force consumers
 * to recompile; adding one here is a retained secondary constructor and breaks nobody.
 *
 * Not a `data class` for the same reason — a generated `copy()` and `componentN` would themselves
 * break on growth. `equals` and `hashCode` are implemented by hand instead, and they are
 * load-bearing: the scanner keeps its tracking state in `remember(policy)`, so a policy that does
 * not compare equal rebuilds that state on every recomposition and nothing would ever complete its
 * dwell.
 *
 * @param mode how many barcodes to track at once.
 * @param dwell how long a barcode must be held in the region before it is reported. Null reports
 * the first sighting immediately, which is what makes a scanner feel "trigger-happy".
 * @param missTolerance how long a barcode that has not yet been reported survives not being seen.
 * Absorbs the decode flicker that is normal for 1D symbologies, so a wobble does not restart the
 * dwell.
 * @param debounceWindow how long an **already reported** barcode must be absent before it can be
 * reported again. Null falls back to [missTolerance]. Note this is measured from when the code was
 * last *seen*, not from when it was reported: holding one code steady reports it once, however
 * long you hold it.
 * @param region which part of the frame a barcode must be in to count at all.
 */
@Immutable
class ScanPolicy(
	val mode: ScanMode = ScanMode.Single,
	val dwell: Duration? = 500.milliseconds,
	val missTolerance: Duration = 750.milliseconds,
	val debounceWindow: Duration? = 2.5.seconds,
	val region: ScanRegion = ScanRegion.Reticle(),
) {
	/**
	 * How long a reported barcode must be absent before it may be reported again.
	 *
	 * Never shorter than [missTolerance] — a code that can re-arm faster than it can be forgotten
	 * would report twice from one continuous presentation.
	 */
	internal val rearm: Duration
		get() = maxOf(debounceWindow ?: missTolerance, missTolerance)

	override fun equals(other: Any?): Boolean = this === other || (
		other is ScanPolicy &&
			mode == other.mode &&
			dwell == other.dwell &&
			missTolerance == other.missTolerance &&
			debounceWindow == other.debounceWindow &&
			region == other.region
		)

	override fun hashCode(): Int {
		var result = mode.hashCode()
		result = 31 * result + dwell.hashCode()
		result = 31 * result + missTolerance.hashCode()
		result = 31 * result + debounceWindow.hashCode()
		result = 31 * result + region.hashCode()
		return result
	}

	override fun toString(): String =
		"ScanPolicy(mode=$mode, dwell=$dwell, missTolerance=$missTolerance, " +
			"debounceWindow=$debounceWindow, region=$region)"

	companion object {
		/** Sensible behaviour for aiming at one code at a time. */
		val Default = ScanPolicy()

		/**
		 * One result per presentation, with no dwell and no region — every code in frame reports
		 * the moment it is decoded.
		 *
		 * This is not a fire-every-frame firehose: [debounceWindow] keeps its default, so a code
		 * that stays in shot reports once and then stays quiet until it has been absent for that
		 * long. Lowering [debounceWindow] shortens that wait but cannot remove it — [rearm] is
		 * floored at [missTolerance], and a held code is never absent — so there is no policy that
		 * reports the same code every frame. Dedup downstream of this is unnecessary.
		 */
		val Immediate = ScanPolicy(
			mode = ScanMode.Multi,
			dwell = null,
			region = ScanRegion.Full,
		)
	}
}
