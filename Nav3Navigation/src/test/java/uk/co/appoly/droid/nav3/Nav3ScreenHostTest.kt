package uk.co.appoly.droid.nav3

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose/Robolectric tests for [Nav3ScreenHost]: ambient navigator provision, push/pop
 * through [LocalNav3Navigator], and rendering the top screen.
 *
 * Entry decorators are left empty so the host does not need SavedState/ViewModel Android
 * services that are awkward under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class Nav3ScreenHostTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `renders the top screen on the back stack`() {
		val backStack = NavBackStack<NavKey>(HomeScreen)

		composeRule.setContent {
			Nav3ScreenHost(
				backStack = backStack,
				entryDecorators = emptyList(),
			)
		}

		composeRule.onNodeWithText("Home").assertIsDisplayed()
	}

	@Test
	fun `provides LocalNav3Navigator to screen content`() {
		var seen: Nav3Navigator? = null
		val probe = object : Nav3Screen {
			@Composable
			override fun Content() {
				seen = LocalNav3Navigator.current
				Text("Probe")
			}
		}
		val backStack = NavBackStack<NavKey>(probe)

		composeRule.setContent {
			Nav3ScreenHost(
				backStack = backStack,
				entryDecorators = emptyList(),
			)
		}

		composeRule.runOnIdle {
			assertNotNull(seen)
			assert(seen is BackStackNav3Navigator)
		}
	}

	@Test
	fun `LocalNav3Navigator is null outside a host`() {
		var seen: Nav3Navigator? = Nav3NavigatorSentinel
		composeRule.setContent {
			seen = LocalNav3Navigator.current
			Text("Outside")
		}
		composeRule.runOnIdle {
			assertNull(seen)
		}
	}

	@Test
	fun `push via ambient navigator shows the new top screen`() {
		val pushable = object : Nav3Screen {
			@Composable
			override fun Content() {
				val navigator = LocalNav3Navigator.current
				Button(onClick = { navigator?.push(DetailScreen(99)) }) {
					Text("Go detail")
				}
			}
		}
		val backStack = NavBackStack<NavKey>(pushable)

		composeRule.setContent {
			Nav3ScreenHost(
				backStack = backStack,
				entryDecorators = emptyList(),
			)
		}

		composeRule.onNodeWithText("Go detail").assertIsDisplayed()
		composeRule.onNodeWithText("Go detail").performClick()

		composeRule.onNodeWithText("Detail 99").assertIsDisplayed()
		composeRule.runOnIdle {
			assertEquals(listOf(pushable, DetailScreen(99)), backStack.toList())
		}
	}

	@Test
	fun `custom navigator is the one provided as LocalNav3Navigator`() {
		val backStack = NavBackStack<NavKey>(HomeScreen)
		val custom = RecordingNavigator()

		var seen: Nav3Navigator? = null
		val probe = object : Nav3Screen {
			@Composable
			override fun Content() {
				seen = LocalNav3Navigator.current
				Text("CustomProbe")
			}
		}
		// Replace top so the probe is what renders.
		backStack.clear()
		backStack.add(probe)

		composeRule.setContent {
			Nav3ScreenHost(
				backStack = backStack,
				navigator = custom,
				entryDecorators = emptyList(),
			)
		}

		composeRule.runOnIdle {
			assertEquals(custom, seen)
		}
	}

	/**
	 * Distinct sentinel so we can tell "never assigned" from "assigned null".
	 * [LocalNav3Navigator] defaults to null; we only care that the read is null.
	 */
	private object Nav3NavigatorSentinel : Nav3Navigator by EmptyNav3Navigator

	private class RecordingNavigator : Nav3Navigator by EmptyNav3Navigator {
		val pushed = mutableListOf<Nav3Screen>()
		override fun push(screen: Nav3Screen) {
			pushed += screen
		}

		override fun push(vararg screens: Nav3Screen) {
			pushed += screens
		}
	}

	/** Minimal no-op [Nav3Navigator] for test doubles. */
	private object EmptyNav3Navigator : Nav3Navigator {
		override val parent: Nav3Navigator? = null
		override fun push(screen: Nav3Screen) = Unit
		override fun push(vararg screens: Nav3Screen) = Unit
		override fun push(screens: Iterable<Nav3Screen>) = Unit
		override fun pop() = Unit
		override fun replace(screen: Nav3Screen) = Unit
		override fun replaceAll(screen: Nav3Screen) = Unit
		override fun replaceAll(vararg screens: Nav3Screen) = Unit
		override fun popUpTo(screen: Nav3Screen, inclusive: Boolean): Boolean = false
		override fun popUntil(inclusive: Boolean, predicate: (Nav3Screen) -> Boolean): Boolean = false
		override fun popUntilRoot() = Unit
		override val canPop: Boolean = false
		override val lastItem: Nav3Screen? = null
		override val previousItem: Nav3Screen? = null
		override val items: List<Nav3Screen> = emptyList()
	}
}
