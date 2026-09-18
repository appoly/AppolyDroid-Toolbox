package uk.co.appoly.droid.barcodescanner.camera

import android.graphics.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Pins the two coordinate transforms, both of which shipped wrong and were caught on a device
 * rather than here.
 *
 * The preview mapping scaled by the full analyser image instead of the visible crop, which made
 * every outline undersized *and* shifted toward the top-left at once. The rotation was a transpose
 * — a reflection about the diagonal — which is indistinguishable from a real rotation while the
 * crop is centred, and wrong the moment it is not.
 *
 * Both are pure arithmetic, so there was never a reason for a camera to be the thing that found
 * them.
 *
 * Runs under Robolectric because android.graphics.Rect is a stub on the plain JVM classpath — with
 * isReturnDefaultValues on it silently reports every edge as 0, which would make these assertions
 * meaningless rather than failing.
 */
@RunWith(AndroidJUnit4::class)
class ScanRegionResolverTest {

	@Test
	fun `no rotation leaves a rect alone`() {
		val rect = Rect(10, 20, 110, 220)

		assertEquals(rect, rect.rotatedInto(0, bufferWidth = 640, bufferHeight = 480))
	}

	@Test
	fun `90 degrees moves the top-left corner to the top-right`() {
		// A 640x480 buffer rotated clockwise is 480x640. The buffer's top-left corner region
		// should land against the right edge of the upright frame.
		val topLeft = Rect(0, 0, 100, 50)

		val rotated = topLeft.rotatedInto(90, bufferWidth = 640, bufferHeight = 480)

		assertEquals(Rect(430, 0, 480, 100), rotated)
	}

	@Test
	fun `180 degrees mirrors both axes`() {
		val rect = Rect(0, 0, 100, 50)

		assertEquals(
			Rect(540, 430, 640, 480),
			rect.rotatedInto(180, bufferWidth = 640, bufferHeight = 480),
		)
	}

	@Test
	fun `270 degrees moves the top-left corner to the bottom-left`() {
		val topLeft = Rect(0, 0, 100, 50)

		assertEquals(
			Rect(0, 540, 50, 640),
			topLeft.rotatedInto(270, bufferWidth = 640, bufferHeight = 480),
		)
	}

	@Test
	fun `an off-centre crop is not merely transposed`() {
		// The case the old transpose got wrong. A crop hugging the buffer's left edge must end up
		// against the *bottom* of a 90-degree-rotated frame, not against its left edge.
		val leftEdge = Rect(0, 100, 40, 300)

		val rotated = leftEdge.rotatedInto(90, bufferWidth = 640, bufferHeight = 480)

		assertEquals("a transpose would have left this at x=100", 180, rotated.left)
		assertEquals(0, rotated.top)
		assertEquals(40, rotated.height())
	}

	@Test
	fun `rotating four times returns the original`() {
		val rect = Rect(10, 20, 110, 220)

		val once = rect.rotatedInto(90, 640, 480)
		val twice = once.rotatedInto(90, 480, 640)
		val thrice = twice.rotatedInto(90, 640, 480)
		val full = thrice.rotatedInto(90, 480, 640)

		assertEquals(rect, full)
	}

	@Test
	fun `preview mapping scales by the crop, not the whole image`() {
		// The shipped bug: a 1000-wide image cropped to its middle 500 shown in a 500px preview is
		// 1:1 against the crop. Scaling by the image instead halves everything.
		val crop = Rect(250, 0, 750, 500)
		val preview = Size(500f, 500f)

		val mapped = mapToPreview(x = 500, y = 250, crop = crop, previewSize = preview)

		assertEquals(250f, mapped.x, 0.01f)
		assertEquals(250f, mapped.y, 0.01f)
	}

	@Test
	fun `preview mapping subtracts the crop origin`() {
		// The other half of the same bug: without this every outline drifts toward the top-left by
		// the crop's offset.
		val crop = Rect(200, 100, 600, 500)
		val preview = Size(400f, 400f)

		val atCropOrigin = mapToPreview(x = 200, y = 100, crop = crop, previewSize = preview)

		assertEquals(0f, atCropOrigin.x, 0.01f)
		assertEquals(0f, atCropOrigin.y, 0.01f)
	}

	@Test
	fun `preview mapping puts the crop's far corner at the preview's far corner`() {
		val crop = Rect(200, 100, 600, 500)
		val preview = Size(800f, 800f)

		val farCorner = mapToPreview(x = 600, y = 500, crop = crop, previewSize = preview)

		assertEquals(800f, farCorner.x, 0.01f)
		assertEquals(800f, farCorner.y, 0.01f)
	}

	@Test
	fun `the front camera mirrors x but leaves y alone`() {
		// The preview is flipped for display while the analysed buffer is not, so a code on the
		// user's left arrives with coordinates on the right. Without mirroring here, every overlay
		// on the front lens lands on the wrong side of the screen.
		val crop = Rect(0, 0, 100, 100)
		val preview = Size(100f, 100f)

		val mirrored = mapToPreview(x = 10, y = 30, crop = crop, previewSize = preview, mirrored = true)

		assertEquals(90f, mirrored.x, 0.01f)
		assertEquals("vertical must not flip — the mirror is horizontal only", 30f, mirrored.y, 0.01f)
	}

	@Test
	fun `mirroring twice returns the original x`() {
		val crop = Rect(0, 0, 200, 200)
		val preview = Size(200f, 200f)

		val once = mapToPreview(x = 40, y = 0, crop = crop, previewSize = preview, mirrored = true)
		val back = mapToPreview(x = once.x.toInt(), y = 0, crop = crop, previewSize = preview, mirrored = true)

		assertEquals(40f, back.x, 0.01f)
	}

	@Test
	fun `the back camera is unmirrored`() {
		val crop = Rect(0, 0, 100, 100)
		val preview = Size(100f, 100f)

		val plain = mapToPreview(x = 10, y = 30, crop = crop, previewSize = preview, mirrored = false)

		assertEquals(10f, plain.x, 0.01f)
	}

	@Test
	fun `a degenerate crop maps to the origin rather than dividing by zero`() {
		val empty = Rect(0, 0, 0, 0)

		val mapped = mapToPreview(x = 10, y = 10, crop = empty, previewSize = Size(100f, 100f))

		assertEquals(0f, mapped.x, 0.01f)
		assertEquals(0f, mapped.y, 0.01f)
	}
}
