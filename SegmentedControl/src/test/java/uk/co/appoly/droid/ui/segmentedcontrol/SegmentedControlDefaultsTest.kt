package uk.co.appoly.droid.ui.segmentedcontrol

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Coverage for [SegmentedControlDefaults]: every `colors()` overload (solid + brush variants) and
 * every `textStyle()` overload, plus rendering a [SegmentedControl] with custom brush colors and
 * a fully-specified text style.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedControlDefaultsTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `defaults factory overloads build colors and text styles`() {
		composeRule.setContent {
			MaterialTheme {
				// colors() overloads
				SegmentedControlDefaults.colors()
				SegmentedControlDefaults.colors(thumbColor = Color.Red)
				SegmentedControlDefaults.colors(trackColor = Color.Yellow, thumbBrush = SolidColor(Color.Green))
				val brushColors = SegmentedControlDefaults.colors(
					trackBrush = Brush.horizontalGradient(listOf(Color.Blue, Color.Cyan))
				)
				// textStyle() overloads
				SegmentedControlDefaults.textStyle(selectedStyle = TextStyle(fontWeight = FontWeight.Black))
				SegmentedControlDefaults.textStyle(textStyle = TextStyle(fontSize = 12.sp))
				val fullStyle = SegmentedControlDefaults.textStyle(
					selectedFontSize = 16.sp,
					unselectedFontSize = 14.sp,
					selectedFontWeight = FontWeight.Bold
				)

				val current = remember { "A" }
				SegmentedControl(
					segments = listOf("A", "B"),
					selectedSegment = current,
					onSegmentSelected = {},
					colors = brushColors,
					textStyle = fullStyle
				)
			}
		}
		composeRule.onNodeWithText("A").assertIsDisplayed()
	}
}
