package uk.co.appoly.droid.ui.snackbar

import uk.co.appoly.droid.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the [snackBarType] extension mapping [UiState] to [SnackBarType].
 */
class AppSnackBarExtensionsTest {

	@Test
	fun `Success maps to Success`() {
		assertEquals(SnackBarType.Success, (UiState.Success() as UiState?).snackBarType)
	}

	@Test
	fun `Error maps to Error`() {
		assertEquals(SnackBarType.Error, (UiState.Error("boom") as UiState?).snackBarType)
	}

	@Test
	fun `Idle, Loading and null map to Info`() {
		assertEquals(SnackBarType.Info, (UiState.Idle() as UiState?).snackBarType)
		assertEquals(SnackBarType.Info, (UiState.Loading() as UiState?).snackBarType)
		assertEquals(SnackBarType.Info, (null as UiState?).snackBarType)
	}
}
