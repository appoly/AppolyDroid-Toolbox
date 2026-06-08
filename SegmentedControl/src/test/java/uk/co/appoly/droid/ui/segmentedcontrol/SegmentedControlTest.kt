package uk.co.appoly.droid.ui.segmentedcontrol

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI test (Robolectric) for the string [SegmentedControl] overload: it renders every
 * segment and reports the tapped one via onSegmentSelected. This exercises the String overload,
 * the generic delegate, [SegmentText], and the internal layout/gesture state.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedControlTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `renders all segments and reports selection on tap`() {
		var selected = "Day"
		composeRule.setContent {
			MaterialTheme {
				var current by mutableStateOf(selected)
				SegmentedControl(
					segments = listOf("Day", "Week", "Month"),
					selectedSegment = current,
					onSegmentSelected = {
						current = it
						selected = it
					}
				)
			}
		}

		composeRule.onNodeWithText("Day").assertIsDisplayed()
		composeRule.onNodeWithText("Week").assertIsDisplayed()
		composeRule.onNodeWithText("Month").assertIsDisplayed()

		composeRule.onNodeWithText("Month").performClick()
		assertEquals("Month", selected)
	}
}
