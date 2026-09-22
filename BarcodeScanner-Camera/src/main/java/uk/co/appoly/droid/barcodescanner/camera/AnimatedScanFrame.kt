package uk.co.appoly.droid.barcodescanner.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
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
 * @param scrimColor painted outside the acceptance region while idle. Transparent disables it.
 */
@Composable
fun ScannerOverlayScope.AnimatedScanFrame(
	modifier: Modifier = Modifier,
	color: Color = Color.White,
	trackingColor: Color = Color(0xFF4CAF50),
	strokeWidth: Dp = 3.dp,
	scrimColor: Color = Color.Black.copy(alpha = 0.4f),
) {
	val tracked = detections.firstOrNull()
	val progress = tracked?.dwellProgress ?: 0f

	// Follow the code's own corners rather than its bounding box: ML Kit's boundingBox is always
	// axis-aligned, so on a barcode held at an angle it is the box *around* the code and the
	// outline visibly fails to sit on it. Falling back to the bounds keeps this working for a
	// detector that reports no corners.
	val targetCorners = tracked?.corners?.takeIf { it.size == 4 }
		?: tracked?.bounds?.cornersClockwise()
		?: regionRect.cornersClockwise()

	val spec = spring<Float>(stiffness = 420f, dampingRatio = 0.82f)
	// Four corners as eight springs: one animation that covers moving, resizing and rotating,
	// because a rotation is just the corners travelling to new places.
	val animated = targetCorners.mapIndexed { index, corner ->
		val x by animateFloatAsState(corner.x, spec, label = "cornerX$index")
		val y by animateFloatAsState(corner.y, spec, label = "cornerY$index")
		Offset(x, y)
	}
	val outlineColor by animateColorAsState(
		targetValue = if (tracked != null) trackingColor else color,
		label = "frameColor",
	)

	Canvas(modifier = modifier.fillMaxSize()) {
		if (size.width <= 0f || size.height <= 0f) return@Canvas
		val outline = Path().apply {
			moveTo(animated[0].x, animated[0].y)
			animated.drop(1).forEach { lineTo(it.x, it.y) }
			close()
		}

		// Only dim while idle. Once the frame is on a barcode the scrim would be dimming most of
		// the preview, which looks like a fault rather than a hint.
		if (tracked == null && scrimColor.alpha > 0f) {
			drawPath(
				Path().apply {
					addRect(Rect(0f, 0f, size.width, size.height))
					addPath(outline)
					fillType = PathFillType.EvenOdd
				},
				scrimColor,
			)
		}

		drawPath(
			outline,
			outlineColor.copy(alpha = if (tracked != null) 0.35f else 1f),
			style = Stroke(width = strokeWidth.toPx()),
		)

		// The progress stroke: a segment of the outline, closing around the code as the dwell
		// completes.
		if (tracked != null && progress > 0f) {
			val measure = PathMeasure().apply { setPath(outline, false) }
			val drawn = Path()
			measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, true)
			drawPath(drawn, outlineColor, style = Stroke(width = strokeWidth.toPx() * 1.6f))
		}
	}
}

/** The four corners of an axis-aligned rect, clockwise from top-left, to match ML Kit's ordering. */
internal fun Rect.cornersClockwise(): List<Offset> =
	listOf(topLeft, topRight, bottomRight, bottomLeft)
