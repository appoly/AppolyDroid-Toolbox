package uk.co.appoly.droid.ui.snackbar

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure (no-composition) tests for [AppSnackBarColors.get] and [AppSnackBarDefaults].
 */
class AppSnackBarColorsTest {

	@Test
	fun `get returns the matching color for each type`() {
		val colors = AppSnackBarColors(info = Color.Blue, success = Color.Green, error = Color.Red)
		assertEquals(Color.Blue, colors.get(SnackBarType.Info))
		assertEquals(Color.Green, colors.get(SnackBarType.Success))
		assertEquals(Color.Red, colors.get(SnackBarType.Error))
	}

	@Test
	fun `defaults are blue, green and red`() {
		val defaults = AppSnackBarDefaults.colors
		assertEquals(Color.Blue, defaults.info)
		assertEquals(Color.Green, defaults.success)
		assertEquals(Color.Red, defaults.error)
		assertEquals(Color.Red, defaults.get(SnackBarType.Error))
	}

	@Test
	fun `SnackBarType has the three expected values`() {
		assertEquals(3, SnackBarType.entries.size)
	}
}
