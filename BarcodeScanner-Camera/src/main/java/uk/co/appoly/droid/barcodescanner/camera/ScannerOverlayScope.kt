package uk.co.appoly.droid.barcodescanner.camera

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import uk.co.appoly.droid.barcodescanner.ScannedBarcode

/**
 * A barcode the scanner can currently see, with where it is on screen and how close it is to
 * counting as a scan.
 *
 * Positions are in **preview pixels**, relative to the scanner's own bounds, so they can be drawn
 * directly by an overlay without further transformation.
 *
 * A plain class rather than a `data class` on purpose: a generated `copy()` and `componentN` fix
 * the field list at the first release, and this type is expected to gain fields as overlays get
 * more ambitious.
 *
 * @property barcode the decoded barcode.
 * @property bounds its axis-aligned bounding box, in preview pixels. Simple to draw, but for a
 * barcode held at an angle it is the box *around* the code rather than the code's own outline.
 * @property corners the code's four corners in its own orientation, in preview pixels, wound
 * clockwise on either lens. Use these to draw an outline that follows a rotated barcode. Index 0 is
 * the corner the detector reported first — the code's top-left as the analyser sees it, which on
 * the mirrored front camera is drawn on the right. Empty if the detector did not report them.
 * @property dwellProgress how far through [ScanPolicy.dwell] this code is, from 0f to 1f. Already
 * 1f when the policy has no dwell. Useful for drawing a progress ring that fills as the user holds
 * steady.
 */
@Immutable
class DetectedBarcode(
	val barcode: ScannedBarcode,
	val bounds: Rect,
	val corners: List<Offset>,
	val dwellProgress: Float,
) {
	override fun equals(other: Any?): Boolean = this === other || (
		other is DetectedBarcode &&
			barcode == other.barcode &&
			bounds == other.bounds &&
			corners == other.corners &&
			dwellProgress == other.dwellProgress
		)

	override fun hashCode(): Int {
		var result = barcode.hashCode()
		result = 31 * result + bounds.hashCode()
		result = 31 * result + corners.hashCode()
		result = 31 * result + dwellProgress.hashCode()
		return result
	}

	override fun toString(): String =
		"DetectedBarcode(barcode=$barcode, bounds=$bounds, corners=$corners, dwellProgress=$dwellProgress)"
}

/**
 * What an overlay drawn over [BarcodeScannerCamera] can see.
 *
 * Extends [BoxScope], so `Modifier.align` works as it would in any `Box` and an overlay written
 * before this scope existed still compiles.
 *
 * Sealed deliberately. Only this library implements it, so **members can be added in future
 * releases without breaking anyone** — which is the whole reason the overlay takes a receiver
 * rather than parameters. A lambda's parameter list is part of its type, so adding to it would be
 * a breaking change; adding a member here is not.
 */
@Stable
sealed interface ScannerOverlayScope : BoxScope {

	/**
	 * The region a barcode must be in to count, in preview pixels.
	 *
	 * Drawing from this rather than recomputing it is what keeps the reticle and the acceptance
	 * region from drifting apart — a control that shows a box and accepts codes outside it is
	 * worse than one that shows no box at all.
	 */
	val regionRect: Rect

	/**
	 * Barcodes currently visible, nearest the centre of [regionRect] first.
	 *
	 * Includes codes that have not been reported yet — that is the point, since an overlay wants
	 * to show what it is about to accept.
	 */
	val detections: List<DetectedBarcode>
}

internal class ScannerOverlayScopeImpl(
	private val boxScope: BoxScope,
	override val regionRect: Rect,
	override val detections: List<DetectedBarcode>,
) : ScannerOverlayScope, BoxScope by boxScope
