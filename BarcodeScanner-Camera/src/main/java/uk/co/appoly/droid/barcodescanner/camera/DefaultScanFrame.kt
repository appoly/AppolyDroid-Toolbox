package uk.co.appoly.droid.barcodescanner.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The reticle [BarcodeScannerCamera] draws over its preview by default.
 *
 * **It marks the region barcodes are actually accepted in.** The frame is drawn from
 * [ScannerOverlayScope.regionRect], which is the same rectangle the analyser filters against, so
 * the two cannot drift apart — a scanner that shows a box and then accepts codes outside it is
 * worse than one that draws no box at all.
 *
 * Change the region through [ScanPolicy.region] rather than by drawing a different frame; the
 * drawing follows the policy, not the other way round. With [ScanRegion.Full] or
 * [ScanRegion.Visible] the region is the whole preview, so the frame fills it and is not
 * especially useful — pass `overlay = {}` in that case.
 *
 * @param color the outline colour.
 * @param strokeWidth the outline thickness.
 * @param cornerRadius the corner rounding.
 * @param scrimColor painted outside the region to dim what will not be scanned. Fully transparent
 * disables it.
 */
@Composable
fun ScannerOverlayScope.DefaultScanFrame(
	modifier: Modifier = Modifier,
	color: Color = Color.White,
	strokeWidth: Dp = 3.dp,
	cornerRadius: Dp = 16.dp,
	scrimColor: Color = Color.Black.copy(alpha = 0.4f),
) {
	val region = regionRect
	Canvas(modifier = modifier.fillMaxSize()) {
		if (region.width <= 0f || region.height <= 0f) return@Canvas
		val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
		val outline = RoundRect(rect = region, cornerRadius = radius)

		// Dim everything outside the region, so it reads as "this bit is live" rather than as
		// decoration. Even-odd filling punches the region out of a full-size rectangle.
		if (scrimColor.alpha > 0f) {
			val scrim = Path().apply {
				addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
				addRoundRect(outline)
				fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
			}
			drawPath(scrim, scrimColor)
		}

		drawOutline(
			outline = androidx.compose.ui.graphics.Outline.Rounded(outline),
			color = color,
			style = Stroke(width = strokeWidth.toPx()),
		)
	}
}
