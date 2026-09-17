package uk.co.appoly.droid.barcodescanner.camera

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The reticle [BarcodeScannerCamera] draws over its preview by default: a centred, rounded
 * rectangle outline.
 *
 * It is decoration, not a constraint — the detector reads the whole frame, so a code outside the
 * frame still scans. It exists to tell the user where to point, which measurably speeds them up.
 * Pass your own `overlay` to replace it, or `overlay = {}` for a bare preview.
 *
 * @param widthFraction how much of the preview's width the frame spans.
 * @param aspectRatio width:height of the frame. 1f suits QR codes; try 2f or wider for the long
 * thin labels of 1D symbologies.
 * @param color the outline colour.
 * @param strokeWidth the outline thickness.
 * @param cornerRadius the corner rounding.
 */
@Composable
fun DefaultScanFrame(
	modifier: Modifier = Modifier,
	widthFraction: Float = 0.7f,
	aspectRatio: Float = 1f,
	color: Color = Color.White,
	strokeWidth: Dp = 3.dp,
	cornerRadius: Dp = 16.dp,
) {
	Box(
		modifier = modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth(widthFraction)
				.aspectRatio(aspectRatio)
				.border(
					width = strokeWidth,
					color = color,
					shape = RoundedCornerShape(cornerRadius),
				),
		)
	}
}
