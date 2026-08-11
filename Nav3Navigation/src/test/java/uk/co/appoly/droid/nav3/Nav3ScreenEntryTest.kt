package uk.co.appoly.droid.nav3

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [nav3ScreenEntry]: metadata forwarding, Content invocation, and the
 * non-[Nav3Screen] key hard-fail.
 */
@RunWith(AndroidJUnit4::class)
class Nav3ScreenEntryTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `forwards empty metadata by default`() {
		val entry = nav3ScreenEntry(HomeScreen)
		assertEquals(emptyMap<String, Any>(), entry.metadata)
	}

	@Test
	fun `forwards screen metadata onto the NavEntry`() {
		val entry = nav3ScreenEntry(SettingsScreen)
		assertEquals(mapOf("test_meta" to "settings"), entry.metadata)
	}

	@Test
	fun `content invokes the screen Content composable`() {
		val entry = nav3ScreenEntry(DetailScreen(7))

		composeRule.setContent {
			entry.Content()
		}

		composeRule.onNodeWithText("Detail 7").assertIsDisplayed()
	}

	@Test(expected = IllegalStateException::class)
	fun `rejects keys that are not Nav3Screen`() {
		val notAScreen = object : NavKey {}
		nav3ScreenEntry(notAScreen)
	}
}
