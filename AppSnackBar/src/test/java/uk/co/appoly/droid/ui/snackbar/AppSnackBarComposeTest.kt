package uk.co.appoly.droid.ui.snackbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI test (Robolectric + [createComposeRule]) exercising the [AppSnackBar] composable
 * and the typed [showSnackbar] extension end to end: the extension builds a
 * [SnackBarVisualsWithType], the host renders [AppSnackBar], which displays the message.
 */
@RunWith(AndroidJUnit4::class)
class AppSnackBarComposeTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `typed snackbar renders its message`() {
		composeRule.setContent {
			val hostState = remember { SnackbarHostState() }
			Scaffold(
				snackbarHost = { SnackbarHost(hostState) { data -> AppSnackBar(data) } }
			) { padding ->
				Box(Modifier.padding(padding))
				LaunchedEffect(Unit) {
					// actionLabel set => Indefinite duration, so it stays visible for the assertion.
					hostState.showSnackbar(
						message = "Saved!",
						actionLabel = "OK",
						type = SnackBarType.Success
					)
				}
			}
		}

		composeRule.onNodeWithText("Saved!").assertIsDisplayed()
	}
}
