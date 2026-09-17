package uk.co.appoly.droid.ui.segmentedcontrol

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers `enabled = false`, which exists so a submitted or locked form cannot be edited.
 *
 * This is a data-integrity concern rather than a styling one: without it, a read-only form still
 * accepts taps and silently changes the answer underneath the user. Dimming alone would not be
 * enough, which is why these tests assert behaviour and semantics rather than appearance.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedControlEnabledTest {

	@get:Rule
	val composeRule = createComposeRule()

	private val segments = listOf("Yes", "No", "N/A")

	@Test
	fun `a disabled control reports every segment as disabled`() {
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = "Yes",
					onSegmentSelected = {},
					enabled = false,
				)
			}
		}

		segments.forEach { composeRule.onNodeWithText(it).assertIsNotEnabled() }
	}

	@Test
	fun `an enabled control reports every segment as enabled`() {
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = "Yes",
					onSegmentSelected = {},
				)
			}
		}

		segments.forEach { composeRule.onNodeWithText(it).assertIsEnabled() }
	}

	@Test
	fun `a disabled control advertises no click action`() {
		// Withholding the semantics onClick action is not what blocks activation — disabled() is
		// enough on its own, confirmed by leaving the action in place and watching these tests
		// still pass. This pins the separate property it does buy: a locked control should not
		// advertise a capability it will not honour, so an accessibility service reading the tree
		// sees no click action rather than one that silently does nothing.
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = "Yes",
					onSegmentSelected = {},
					enabled = false,
				)
			}
		}

		segments.forEach {
			composeRule.onNodeWithText(it)
				.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
		}
	}

	@Test
	fun `a disabled control ignores activation`() {
		var reported: String? = null
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = "Yes",
					onSegmentSelected = { reported = it },
					enabled = false,
				)
			}
		}

		composeRule.onNodeWithText("No").performClick()

		assertNull("a disabled control changed its value", reported)
		composeRule.onNodeWithText("Yes").assertIsSelected()
		composeRule.onNodeWithText("No").assertIsNotSelected()
	}

	@Test
	fun `a disabled control still shows its selection`() {
		// Disabled means "you cannot change this", not "this has no value". A locked form must
		// still display the answer it holds.
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = "N/A",
					onSegmentSelected = {},
					enabled = false,
				)
			}
		}

		composeRule.onNodeWithText("N/A").assertIsSelected()
	}

	@Test
	fun `a disabled and unanswered control shows nothing selected`() {
		// The two new states compose: an unanswered question on a locked form.
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = null,
					onSegmentSelected = {},
					enabled = false,
				)
			}
		}

		segments.forEach {
			composeRule.onNodeWithText(it).assertIsNotSelected()
			composeRule.onNodeWithText(it).assertIsNotEnabled()
		}
	}

	@Test
	fun `re-enabling restores interaction`() {
		// Forms unlock as well as lock, and the pointer input is rebuilt when enabled flips, so
		// this guards against it not being reattached.
		val enabled = mutableStateOf(false)
		var reported: String? = null
		composeRule.setContent {
			MaterialTheme {
				SegmentedControl(
					segments = segments,
					selectedSegment = "Yes",
					onSegmentSelected = { reported = it },
					enabled = enabled.value,
				)
			}
		}

		composeRule.onNodeWithText("No").performClick()
		assertNull(reported)

		composeRule.runOnIdle { enabled.value = true }

		composeRule.onNodeWithText("No").assertIsEnabled()
		composeRule.onNodeWithText("No").performClick()
		assertEquals("No", reported)
	}
}
