package uk.co.appoly.droid.ui.segmentedcontrol

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the "nothing selected yet" state, which exists so a form can render an unanswered
 * question honestly.
 *
 * Before `selectedSegment` accepted null, the only way to render such a control was to pass a
 * sentinel — typically the first segment — which showed a required, unanswered field as though the
 * user had already answered it. That is a correctness problem rather than a cosmetic one, so these
 * tests pin the behaviour rather than leaving it to the rendering.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedControlNullSelectionTest {

	@get:Rule
	val composeRule = createComposeRule()

	private val segments = listOf("Yes", "No", "N/A")

	@Test
	fun `a null selection leaves every segment unselected`() {
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = null,
					onSegmentSelected = {},
				)
			}
		}

		segments.forEach { composeRule.onNodeWithText(it).assertIsNotSelected() }
	}

	@Test
	fun `a segment absent from the list also selects nothing`() {
		// indexOf yields -1 for a value that is not present, which lands on the same sentinel as
		// null. Pinned so the two paths cannot drift apart.
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = "Maybe",
					onSegmentSelected = {},
				)
			}
		}

		segments.forEach { composeRule.onNodeWithText(it).assertIsNotSelected() }
	}

	@Test
	fun `selecting from an empty state reports and marks the tapped segment`() {
		var reported: String? = null
		composeRule.setContent {
			MaterialTheme {
				var current by remember { mutableStateOf<String?>(null) }
				SegmentedControl(
					segments = segments,
					selectedSegment = current,
					onSegmentSelected = {
						current = it
						reported = it
					},
				)
			}
		}

		composeRule.onNodeWithText("N/A").assertIsNotSelected()
		composeRule.onNodeWithText("N/A").performClick()

		assertEquals("N/A", reported)
		composeRule.onNodeWithText("N/A").assertIsSelected()
		composeRule.onNodeWithText("Yes").assertIsNotSelected()
	}

	@Test
	fun `a selection can be cleared back to nothing`() {
		// Forms reset. Going back to null must genuinely deselect rather than strand the selection
		// on the previously chosen segment.
		//
		// Driven from outside the composition rather than by tapping: tapping the *already
		// selected* segment is deliberately a no-op in this control (that gesture is the start of
		// a drag, and only fires once the pointer reaches a different segment), so a click here
		// would prove nothing.
		val selection = mutableStateOf<String?>("No")
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = selection.value,
					onSegmentSelected = { selection.value = it },
				)
			}
		}

		composeRule.onNodeWithText("No").assertIsSelected()

		composeRule.runOnIdle { selection.value = null }

		segments.forEach { composeRule.onNodeWithText(it).assertIsNotSelected() }
	}

	@Test
	fun `a non-null selection still behaves exactly as before`() {
		// The widening is meant to be invisible to existing callers; this is the guard against a
		// regression in the common path.
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = "No",
					onSegmentSelected = {},
				)
			}
		}

		composeRule.onNodeWithText("No").assertIsSelected()
		composeRule.onNodeWithText("Yes").assertIsNotSelected()
		composeRule.onNodeWithText("N/A").assertIsNotSelected()
	}
}
