package uk.co.appoly.droid.barcodescanner.camera

import android.graphics.Rect as AndroidRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.google.mlkit.vision.barcode.common.Barcode
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a [ScanRegion] into the concrete rectangles the scanner needs — one in analyser image
 * coordinates for deciding what counts, and one in preview pixels for drawing.
 *
 * The two are kept consistent by binding preview and analysis through a single `ViewPort`, which
 * makes `ImageProxy.cropRect` the region the camera is sharing between them, and then by taking
 * account of how the viewfinder fits that region into its bounds — see [visibleInImage]. Without
 * the `ViewPort` the analyser's field of view is wider than the preview and the two rectangles
 * describe different parts of the world, which is exactly how a scanner ends up reading a barcode
 * that is not on screen.
 */
internal object ScanRegionResolver {

	/**
	 * The acceptance region in the analysed image's coordinate space.
	 *
	 * @param cropRect what the camera shares between preview and analysis, as reported by CameraX
	 * for a view-ported binding.
	 * @param previewSize the viewfinder's bounds in pixels, needed because it centre-crops
	 * [cropRect] rather than stretching it.
	 * @param imageWidth the full analysed width, after rotation correction.
	 * @param imageHeight the full analysed height, after rotation correction.
	 */
	fun inImage(
		region: ScanRegion,
		cropRect: AndroidRect,
		previewSize: Size,
		imageWidth: Int,
		imageHeight: Int,
	): AndroidRect = when (region) {
		ScanRegion.Full -> AndroidRect(0, 0, imageWidth, imageHeight)
		ScanRegion.Visible -> visibleInImage(cropRect, previewSize)
		is ScanRegion.Reticle -> visibleInImage(cropRect, previewSize).centredSubRect(region)
	}

	/** The same region in preview pixels, for an overlay to draw. */
	fun inPreview(region: ScanRegion, previewSize: Size): Rect = when (region) {
		// Both cover the whole preview: Full also takes in more than the preview shows, but an
		// overlay can only meaningfully draw the part the user can see.
		ScanRegion.Full, ScanRegion.Visible -> Rect(0f, 0f, previewSize.width, previewSize.height)

		is ScanRegion.Reticle -> {
			val width = previewSize.width * region.widthFraction
			val height = (width / region.aspectRatio).coerceAtMost(previewSize.height)
			Rect(
				offset = Offset(
					x = (previewSize.width - width) / 2f,
					y = (previewSize.height - height) / 2f,
				),
				size = Size(width, height),
			)
		}
	}

	private fun AndroidRect.centredSubRect(reticle: ScanRegion.Reticle): AndroidRect {
		val w = (width() * reticle.widthFraction).roundToInt()
		val h = (w / reticle.aspectRatio).roundToInt().coerceAtMost(height())
		val cx = centerX()
		val cy = centerY()
		return AndroidRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
	}
}

/**
 * Whether this barcode counts as being in [region].
 *
 * Tested by the *centre* of the bounding box rather than requiring full containment, so a barcode
 * larger than the reticle still scans when it is aimed at properly — which is the common case for
 * a long 1D label inside a square guide.
 */
internal fun Barcode.isWithin(region: AndroidRect): Boolean {
	val box = boundingBox ?: return false
	return region.contains(box.centerX(), box.centerY())
}

/**
 * Distance from this barcode's centre to the centre of [region].
 *
 * Ranking by this is what makes single-code mode lock onto the code the user is pointing at. The
 * obvious alternative — take whichever the detector listed first — is arbitrary, and on a label
 * carrying both a 1D code and a QR it picks the wrong one about half the time.
 */
internal fun Barcode.distanceToCentreOf(region: AndroidRect): Float {
	val box = boundingBox ?: return Float.MAX_VALUE
	return hypot(
		(box.centerX() - region.centerX()).toFloat(),
		(box.centerY() - region.centerY()).toFloat(),
	)
}

/**
 * Maps a rectangle from the camera buffer's coordinate space into the rotation-corrected space
 * ML Kit reports barcodes in.
 *
 * [rotationDegrees] is the clockwise rotation needed to make the buffer upright, so this applies
 * exactly that rotation. Transposing the rectangle instead — swapping x and y — is a reflection
 * about the diagonal rather than a rotation, and happens to look correct only while the crop is
 * centred. An off-centre crop lands the region on the wrong side of the frame.
 */
internal fun AndroidRect.rotatedInto(
	rotationDegrees: Int,
	bufferWidth: Int,
	bufferHeight: Int,
): AndroidRect = when (((rotationDegrees % 360) + 360) % 360) {
	90 -> AndroidRect(bufferHeight - bottom, left, bufferHeight - top, right)
	180 -> AndroidRect(bufferWidth - right, bufferHeight - bottom, bufferWidth - left, bufferHeight - top)
	270 -> AndroidRect(top, bufferWidth - right, bottom, bufferWidth - left)
	else -> AndroidRect(this)
}

/**
 * The single scale factor the viewfinder applies to the camera's crop rectangle.
 *
 * The viewfinder **fills** its bounds and centre-crops the overflow, so the scale is the larger of
 * the two ratios and the same on both axes. Scaling each axis independently — stretching the crop
 * onto the bounds — is only equivalent while the crop and the bounds share an aspect ratio, which
 * is why treating them as interchangeable survives a portrait phone and falls apart the moment the
 * preview is any other shape.
 */
private fun fillCentreScale(crop: AndroidRect, previewSize: Size): Float =
	max(previewSize.width / crop.width(), previewSize.height / crop.height())

/**
 * The part of [crop] the viewfinder actually puts on screen, in image coordinates.
 *
 * The camera shares one crop rectangle between preview and analysis, but the viewfinder only shows
 * the part of it that fits its bounds — everything beyond is scaled off the edges. That remainder
 * is analysed and invisible at once, so accepting a barcode there means accepting one the user
 * cannot see. This is the rectangle [ScanRegion.Visible] means, and the one a reticle is measured
 * against.
 *
 * Falls back to the whole crop when the preview has not been laid out yet, so scanning still works
 * for the frame or two before the first measurement arrives.
 */
internal fun visibleInImage(crop: AndroidRect, previewSize: Size): AndroidRect {
	if (crop.width() <= 0 || crop.height() <= 0) return AndroidRect(crop)
	if (previewSize.width <= 0f || previewSize.height <= 0f) return AndroidRect(crop)
	val scale = fillCentreScale(crop, previewSize)
	val halfWidth = previewSize.width / (2f * scale)
	val halfHeight = previewSize.height / (2f * scale)
	val centreX = crop.exactCenterX()
	val centreY = crop.exactCenterY()
	return AndroidRect(
		(centreX - halfWidth).roundToInt(),
		(centreY - halfHeight).roundToInt(),
		(centreX + halfWidth).roundToInt(),
		(centreY + halfHeight).roundToInt(),
	)
}

/**
 * Maps a point from the rotation-corrected analyser space into preview pixels.
 *
 * [mirrored] handles the front camera, whose preview is flipped for display while the analysed
 * buffer is not.
 *
 * Everything is measured from the centre of [crop] outwards at a single [fillCentreScale], because
 * that is what the viewfinder does: the crop's centre lands on the preview's centre and both axes
 * share one scale. Stretching the crop onto the preview instead makes every box wrong by the ratio
 * of the two aspect ratios — invisible while they match, and a barcode outline that is the right
 * width and half the height the moment they do not.
 */
internal fun mapToPreview(
	x: Int,
	y: Int,
	crop: AndroidRect,
	previewSize: Size,
	mirrored: Boolean = false,
): Offset {
	if (crop.width() <= 0 || crop.height() <= 0) return Offset.Zero
	val scale = fillCentreScale(crop, previewSize)
	val fromCentreX = (x - crop.exactCenterX()) * scale
	val fromCentreY = (y - crop.exactCenterY()) * scale
	return Offset(
		// The front camera's preview is mirrored for display — you expect to move left and see
		// yourself move left — but the analyser receives the unmirrored buffer, so ML Kit's
		// coordinates are in the frame the user is NOT looking at. Without this every overlay on
		// the front lens is drawn on the wrong side of the screen.
		x = previewSize.width / 2f + if (mirrored) -fromCentreX else fromCentreX,
		y = previewSize.height / 2f + fromCentreY,
	)
}
