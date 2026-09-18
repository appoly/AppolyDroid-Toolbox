package uk.co.appoly.droid.barcodescanner.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reticle that moves to the barcode it is about to accept and draws its dwell progress around it,
 * in the manner of Google's hosted scanner.
 *
 * Idle, it sits on [ScannerOverlayScope.regionRect] — the region the analyser is actually
 * filtering against. When a barcode appears it springs to that barcode's bounds and a progress
 * stroke closes around the outline as [DetectedBarcode.dwellProgress] fills, so the wait before a
 * scan registers is visible rather than mysterious. That matters: a scanner that pauses with no
 * feedback reads as broken, which is the usual reason people reach for a shorter dwell than they
 * want.
 *
 * Follows the *first* detection, which the scanner ranks nearest the centre of the region — the
 * same one [ScanMode.Single] would lock onto, so the frame shows what is about to be scanned.
 *
 * Costs nothing when unused: with [ScanPolicy.dwell] set to null the progress stroke is always
 * complete, and the frame simply tracks whatever is in view.
 *
 * @param color the outline colour when nothing is being tracked.
 * @param trackingColor the outline colour once a barcode is being followed.
 * @param strokeWidth the outline thickness.
 * @param cornerRadius the corner rounding.
 * @param padding how far outside the barcode's bounds to draw, so the frame does not sit on top of
 * the code it is trying to read.
 * @param scrimColor painted outside the acceptance region while idle. Transparent disables it.
 */
@Composable
fun ScannerOverlayScope.AnimatedScanFrame(
	modifier: Modifier = Modifier,
	color: Color = Color.White,
	trackingColor: Color = Color(0xFF4CAF50),
	strokeWidth: Dp = 3.dp,
	cornerRadius: Dp = 16.dp,
	padding: Dp = 12.dp,
	scrimColor: Color = Color.Black.copy(alpha = 0.4f),
) {
	val tracked = detections.firstOrNull()
	val target = tracked?.bounds ?: regionRect
	val progress = tracked?.dwellProgress ?: 0f

	// Animate the edges rather than the whole Rect so this needs no vector converter, and springs
	// rather than tweens so a barcode that jitters between frames does not look mechanical.
	val spec = spring<Float>(stiffness = 420f, dampingRatio = 0.82f)
	val left by animateFloatAsState(target.left, spec, label = "frameLeft")
	val top by animateFloatAsState(target.top, spec, label = "frameTop")
	val right by animateFloatAsState(target.right, spec, label = "frameRight")
	val bottom by animateFloatAsState(target.bottom, spec, label = "frameBottom")
	val outlineColor by animateColorAsState(
		targetValue = if (tracked != null) trackingColor else color,
		label = "frameColor",
	)

	Canvas(modifier = modifier.fillMaxSize()) {
		if (size.width <= 0f || size.height <= 0f) return@Canvas
		val pad = if (tracked != null) padding.toPx() else 0f
		val rect = Rect(
			left = (left - pad).coerceAtLeast(0f),
			top = (top - pad).coerceAtLeast(0f),
			right = (right + pad).coerceAtMost(size.width),
			bottom = (bottom + pad).coerceAtMost(size.height),
		)
		if (rect.width <= 0f || rect.height <= 0f) return@Canvas
		val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
		val rounded = RoundRect(rect = rect, cornerRadius = radius)

		// Only dim while idle. Once the frame has moved onto a barcode the scrim would be dimming
		// most of the preview, which looks like a fault rather than a hint.
		if (tracked == null && scrimColor.alpha > 0f) {
			drawPath(
				Path().apply {
					addRect(Rect(0f, 0f, size.width, size.height))
					addRoundRect(rounded)
					fillType = PathFillType.EvenOdd
				},
				scrimColor,
			)
		}

		drawOutline(
			outline = Outline.Rounded(rounded),
			color = outlineColor.copy(alpha = if (tracked != null) 0.35f else 1f),
			style = Stroke(width = strokeWidth.toPx()),
		)

		// The progress stroke: a segment of the outline, growing from nothing to the whole way
		// round as the dwell completes.
		if (tracked != null && progress > 0f) {
			val path = Path().apply { addRoundRect(rounded) }
			val measure = PathMeasure().apply { setPath(path, false) }
			val drawn = Path()
			measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, true)
			drawPath(drawn, outlineColor, style = Stroke(width = strokeWidth.toPx() * 1.6f))
		}
	}
}
