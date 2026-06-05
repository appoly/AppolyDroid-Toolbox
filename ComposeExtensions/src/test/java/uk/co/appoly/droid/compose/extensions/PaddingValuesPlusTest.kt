package uk.co.appoly.droid.compose.extensions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the direction-aware [PaddingValues.plus] (the non-Composable overload that takes
 * an explicit [LayoutDirection]). The arithmetic is pure Compose-foundation math, so it runs
 * as a plain JVM unit test without a composition.
 */
class PaddingValuesPlusTest {

	private val a = PaddingValues(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 16.dp)
	private val b = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)

	@Test
	fun `sums each edge in LTR`() {
		val sum = a.plus(b, LayoutDirection.Ltr)
		assertEquals(5f, sum.calculateStartPadding(LayoutDirection.Ltr).value, 0.001f)
		assertEquals(10f, sum.calculateTopPadding().value, 0.001f)
		assertEquals(15f, sum.calculateEndPadding(LayoutDirection.Ltr).value, 0.001f)
		assertEquals(20f, sum.calculateBottomPadding().value, 0.001f)
	}

	@Test
	fun `top and bottom sums are independent of layout direction`() {
		val sum = a.plus(b, LayoutDirection.Rtl)
		assertEquals(10f, sum.calculateTopPadding().value, 0.001f)
		assertEquals(20f, sum.calculateBottomPadding().value, 0.001f)
	}

	@Test
	fun `adding zero padding leaves values unchanged`() {
		val sum = a.plus(PaddingValues(0.dp), LayoutDirection.Ltr)
		assertEquals(4f, sum.calculateStartPadding(LayoutDirection.Ltr).value, 0.001f)
		assertEquals(8f, sum.calculateTopPadding().value, 0.001f)
		assertEquals(12f, sum.calculateEndPadding(LayoutDirection.Ltr).value, 0.001f)
		assertEquals(16f, sum.calculateBottomPadding().value, 0.001f)
	}
}
