package uk.co.appoly.droid.nav3

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [popWithResult] / [popUntilWithResult] and [Nav3ResultReceiver].
 */
class Nav3ResultsTest {

	private lateinit var backStack: NavBackStack<NavKey>
	private lateinit var navigator: BackStackNav3Navigator
	private lateinit var listScreen: ResultListScreen

	@Before
	fun setUp() {
		listScreen = ResultListScreen()
		backStack = NavBackStack(listScreen)
		navigator = BackStackNav3Navigator(backStack)
	}

	@Test
	fun `popWithResult delivers to previousItem and pops`() {
		navigator.push(PickerScreen)

		navigator.popWithResult("chosen")

		assertEquals(listOf(listScreen), backStack.toList())
		assertEquals(listOf("chosen"), listScreen.results)
	}

	@Test
	fun `popWithResult drops result when previous is not a receiver`() {
		// HomeScreen is not a Nav3ResultReceiver
		backStack.clear()
		backStack.add(HomeScreen)
		navigator.push(PickerScreen)

		navigator.popWithResult("ignored")

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `popWithResult on a single-entry stack drops result and leaves root`() {
		// Only the picker on the stack — no previous receiver; root pop is a no-op.
		backStack.clear()
		backStack.add(PickerScreen)

		navigator.popWithResult("nobody")

		assertEquals(listOf(PickerScreen), backStack.toList())
	}

	@Test
	fun `popUntilWithResult pops to match then delivers to new top`() {
		navigator.push(ListScreen, PickerScreen)

		assertTrue(navigator.popUntilWithResult(true) { it is ResultListScreen })

		assertEquals(listOf(listScreen), backStack.toList())
		assertEquals(listOf(true), listScreen.results)
	}

	@Test
	fun `popUntilWithResult inclusive delivers to the screen under the match`() {
		val root = ResultListScreen()
		backStack.clear()
		backStack.add(root)
		navigator.push(listScreen, PickerScreen)

		assertTrue(
			navigator.popUntilWithResult("x", inclusive = true) {
				it is ResultListScreen && it === listScreen
			},
		)

		assertEquals(listOf(root), backStack.toList())
		assertEquals(listOf("x"), root.results)
		assertTrue(listScreen.results.isEmpty())
	}

	@Test
	fun `popUntilWithResult with no match does not deliver even when top is a receiver`() {
		// Top IS a Nav3ResultReceiver — pre-fix this incorrectly delivered despite no match.
		assertFalse(navigator.popUntilWithResult("nope") { it is SettingsScreen })

		assertEquals(listOf(listScreen), backStack.toList())
		assertTrue(listScreen.results.isEmpty())
	}

	@Test
	fun `popUntilWithResult with no match does not deliver under a non-receiver top either`() {
		navigator.push(PickerScreen)

		assertFalse(navigator.popUntilWithResult("nope") { it is SettingsScreen })

		assertEquals(listOf(listScreen, PickerScreen), backStack.toList())
		assertTrue(listScreen.results.isEmpty())
	}

	/** Receiver fixture that records every [onResult] payload. */
	internal class ResultListScreen : Nav3Screen, Nav3ResultReceiver {
		val results = mutableListOf<Any?>()

		override fun onResult(result: Any?) {
			results += result
		}

		@Composable
		override fun Content() {
			Text("ResultList")
		}
	}

	internal data object PickerScreen : Nav3Screen {
		@Composable
		override fun Content() {
			Text("Picker")
		}
	}
}
