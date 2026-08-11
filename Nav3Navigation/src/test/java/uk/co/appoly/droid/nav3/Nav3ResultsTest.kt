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
	fun `popWithResult pops then delivers to the revealed screen`() {
		navigator.push(PickerScreen)

		navigator.popWithResult("chosen")

		assertEquals(listOf(listScreen), backStack.toList())
		assertEquals(listOf("chosen"), listScreen.results)
	}

	@Test
	fun `popWithResult pops before delivering so a reentrant push survives`() {
		val reentrant = PushingReceiverScreen()
		backStack.clear()
		backStack.add(reentrant)
		reentrant.navigator = navigator
		navigator.push(PickerScreen)

		navigator.popWithResult("go")

		// Pop ran first, so the push from inside onResult lands on top of the receiver.
		// Delivering first popped the *pushed* screen instead of the picker.
		assertEquals(listOf(reentrant, DetailScreen(99)), backStack.toList())
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
		// Only the picker on the stack — canPop is false, so nothing pops and nothing is delivered.
		backStack.clear()
		backStack.add(PickerScreen)

		navigator.popWithResult("nobody")

		assertEquals(listOf(PickerScreen), backStack.toList())
	}

	// --- tabs: previousItem can be an off-screen entry, so delivery gates on canPop ---

	@Test
	fun `popWithResult does not deliver to a hidden tab at the start-tab root`() {
		val hidden = ResultListScreen()
		val tabs = TabsNav3Navigator(listOf(HomeScreen, ListScreen, SettingsScreen))
		tabs.switchTab(ListScreen)
		tabs.push(hidden)
		tabs.switchTab(HomeScreen)

		// The retained rooms tab sits beneath home in the flatten, so previousItem is off-screen.
		assertEquals(hidden, tabs.previousItem)
		assertFalse(tabs.canPop)

		tabs.popWithResult("leaked")

		assertTrue(hidden.results.isEmpty())
		assertEquals(listOf(ListScreen, hidden, HomeScreen), tabs.backStack.toList())
	}

	@Test
	fun `popWithResult delivers through exit-through-home to the revealed start-tab top`() {
		val homeReceiver = ResultListScreen()
		val tabs = TabsNav3Navigator(listOf(HomeScreen, ListScreen, SettingsScreen))
		tabs.push(homeReceiver)
		tabs.switchTab(ListScreen)

		// At a non-start tab root, pop() exits through home rather than popping within the tab.
		assertTrue(tabs.canPop)

		tabs.popWithResult("home")

		assertEquals(HomeScreen, tabs.currentTab)
		assertEquals(homeReceiver, tabs.lastItem)
		assertEquals(listOf("home"), homeReceiver.results)
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

	/** Receiver that navigates from inside [onResult] — exercises the pop-before-deliver order. */
	internal class PushingReceiverScreen : Nav3Screen, Nav3ResultReceiver {
		var navigator: Nav3Navigator? = null

		override fun onResult(result: Any?) {
			navigator?.push(DetailScreen(99))
		}

		@Composable
		override fun Content() {
			Text("PushingReceiver")
		}
	}

	internal data object PickerScreen : Nav3Screen {
		@Composable
		override fun Content() {
			Text("Picker")
		}
	}
}
