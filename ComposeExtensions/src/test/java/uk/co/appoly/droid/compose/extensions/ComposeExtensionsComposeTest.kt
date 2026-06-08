package uk.co.appoly.droid.compose.extensions

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI tests (Robolectric) for the composable helpers in ComposeExtensions:
 * [keyboardAsState], the composable [PaddingValues.plus] operator, and [getActiveActivity].
 */
@RunWith(AndroidJUnit4::class)
class ComposeExtensionsComposeTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `keyboardAsState reports not-visible when there is no IME`() {
		lateinit var keyboard: State<Boolean>
		composeRule.setContent { keyboard = keyboardAsState() }
		composeRule.runOnIdle { assertFalse(keyboard.value) }
	}

	@Test
	fun `composable PaddingValues plus sums each edge`() {
		lateinit var result: PaddingValues
		composeRule.setContent {
			result = PaddingValues(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 16.dp) +
				PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
		}
		composeRule.runOnIdle {
			assertEquals(5f, result.calculateStartPadding(LayoutDirection.Ltr).value, 0.001f)
			assertEquals(10f, result.calculateTopPadding().value, 0.001f)
			assertEquals(20f, result.calculateBottomPadding().value, 0.001f)
		}
	}

	@Test
	fun `getActiveActivity resolves the host activity`() {
		var activity: ComponentActivity? = null
		composeRule.setContent { activity = getActiveActivity() }
		composeRule.runOnIdle { assertNotNull(activity) }
	}
}
