package uk.co.appoly.droid.compose.animation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI tests (Robolectric) for the three [ScrollIntoViewAnimatedVisibility] overloads
 * (standalone, Column-scoped, Row-scoped).
 */
@RunWith(AndroidJUnit4::class)
class ScrollIntoViewAnimatedVisibilityTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `standalone shows content when visible`() {
		composeRule.setContent {
			ScrollIntoViewAnimatedVisibility(visible = true) { BasicText("Shown") }
		}
		composeRule.onNodeWithText("Shown").assertIsDisplayed()
	}

	@Test
	fun `standalone hides content when not visible`() {
		composeRule.setContent {
			ScrollIntoViewAnimatedVisibility(visible = false) { BasicText("Hidden") }
		}
		composeRule.onNodeWithText("Hidden").assertDoesNotExist()
	}

	@Test
	fun `column-scoped overload shows content when visible`() {
		composeRule.setContent {
			Column { ScrollIntoViewAnimatedVisibility(visible = true) { BasicText("InColumn") } }
		}
		composeRule.onNodeWithText("InColumn").assertIsDisplayed()
	}

	@Test
	fun `row-scoped overload shows content when visible`() {
		composeRule.setContent {
			Row { ScrollIntoViewAnimatedVisibility(visible = true) { BasicText("InRow") } }
		}
		composeRule.onNodeWithText("InRow").assertIsDisplayed()
	}
}
