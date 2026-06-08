package uk.co.appoly.droid.ui.paging

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI tests (Robolectric) for the default paging-state providers: [EmptyStateText],
 * [LoadingState] and [ErrorState].
 */
@RunWith(AndroidJUnit4::class)
class PagingStateComposablesTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `default empty state renders its text`() {
		composeRule.setContent {
			MaterialTheme {
				DefaultEmptyStateTextProvider().EmptyStateText(
					modifier = Modifier,
					text = "No items found",
					contentPadding = PaddingValues(0.dp)
				)
			}
		}
		composeRule.onNodeWithText("No items found").assertIsDisplayed()
	}

	@Test
	fun `default loading state renders without error`() {
		composeRule.setContent {
			MaterialTheme {
				DefaultLoadingStateProvider().LoadingState(
					modifier = Modifier,
					contentPadding = PaddingValues(0.dp)
				)
			}
		}
		composeRule.onRoot().assertExists()
	}

	@Test
	fun `default error state shows the message and invokes retry`() {
		var retried = false
		composeRule.setContent {
			MaterialTheme {
				DefaultErrorStateProvider().ErrorState(
					modifier = Modifier,
					text = "Failed to load",
					onRetry = { retried = true },
					contentPadding = PaddingValues(0.dp)
				)
			}
		}
		composeRule.onNodeWithText("Failed to load").assertIsDisplayed()
		// The only clickable node is the retry button.
		composeRule.onNode(hasClickAction()).performClick()
		assertTrue(retried)
	}

	@Test
	fun `error state without a retry callback shows no button`() {
		composeRule.setContent {
			MaterialTheme {
				DefaultErrorStateProvider().ErrorState(
					modifier = Modifier,
					text = "Failed",
					onRetry = null,
					contentPadding = PaddingValues(0.dp)
				)
			}
		}
		composeRule.onNodeWithText("Failed").assertIsDisplayed()
		composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
	}
}
