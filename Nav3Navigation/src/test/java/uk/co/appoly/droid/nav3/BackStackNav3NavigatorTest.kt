package uk.co.appoly.droid.nav3

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure unit tests for [BackStackNav3Navigator]: navigation is list mutation on a
 * [NavBackStack], so no Compose host or Robolectric is required.
 */
class BackStackNav3NavigatorTest {

	private lateinit var backStack: NavBackStack<NavKey>
	private lateinit var navigator: BackStackNav3Navigator

	@Before
	fun setUp() {
		backStack = NavBackStack(HomeScreen)
		navigator = BackStackNav3Navigator(backStack)
	}

	// --- push / pop ---

	@Test
	fun `push appends a single screen`() {
		navigator.push(DetailScreen(1))

		assertEquals(listOf(HomeScreen, DetailScreen(1)), backStack.toList())
	}

	@Test
	fun `push vararg appends screens in order with last on top`() {
		navigator.push(ListScreen, DetailScreen(2), SettingsScreen)

		assertEquals(
			listOf(HomeScreen, ListScreen, DetailScreen(2), SettingsScreen),
			backStack.toList(),
		)
	}

	@Test
	fun `push iterable appends all screens`() {
		navigator.push(listOf(ListScreen, DetailScreen(3)))

		assertEquals(listOf(HomeScreen, ListScreen, DetailScreen(3)), backStack.toList())
	}

	@Test
	fun `pop removes the top screen`() {
		navigator.push(DetailScreen(1))
		navigator.pop()

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `pop on a single-entry stack is a no-op`() {
		navigator.pop()

		assertEquals(listOf(HomeScreen), backStack.toList())
		assertFalse(navigator.canPop)
	}

	@Test
	fun `pop on an empty stack is a no-op`() {
		backStack.clear()
		navigator.pop()

		assertTrue(backStack.isEmpty())
	}

	// --- replace / replaceAll ---

	@Test
	fun `replace swaps the top screen only`() {
		navigator.push(ListScreen, DetailScreen(1))

		navigator.replace(SettingsScreen)

		assertEquals(listOf(HomeScreen, ListScreen, SettingsScreen), backStack.toList())
	}

	@Test
	fun `replace on empty stack is a no-op`() {
		backStack.clear()

		navigator.replace(SettingsScreen)

		assertTrue(backStack.isEmpty())
	}

	@Test
	fun `replaceAll single clears and seeds one screen`() {
		navigator.push(ListScreen, DetailScreen(1))

		navigator.replaceAll(SettingsScreen)

		assertEquals(listOf(SettingsScreen), backStack.toList())
	}

	@Test
	fun `replaceAll vararg clears and seeds the full stack`() {
		navigator.push(ListScreen)

		navigator.replaceAll(HomeScreen, DetailScreen(9))

		assertEquals(listOf(HomeScreen, DetailScreen(9)), backStack.toList())
	}

	@Test
	fun `replaceAll empty vararg leaves the stack unchanged`() {
		navigator.push(ListScreen)

		navigator.replaceAll()

		assertEquals(listOf(HomeScreen, ListScreen), backStack.toList())
	}

	// --- popUpTo / popUntil / popUntilRoot ---

	@Test
	fun `popUpTo exclusive leaves the match on top`() {
		navigator.push(ListScreen, DetailScreen(1), SettingsScreen)

		navigator.popUpTo(ListScreen, inclusive = false)

		assertEquals(listOf(HomeScreen, ListScreen), backStack.toList())
	}

	@Test
	fun `popUpTo inclusive also removes the match`() {
		navigator.push(ListScreen, DetailScreen(1), SettingsScreen)

		navigator.popUpTo(ListScreen, inclusive = true)

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `popUpTo finds the last matching key`() {
		navigator.push(DetailScreen(1), ListScreen, DetailScreen(1), SettingsScreen)

		navigator.popUpTo(DetailScreen(1), inclusive = false)

		assertEquals(
			listOf(HomeScreen, DetailScreen(1), ListScreen, DetailScreen(1)),
			backStack.toList(),
		)
	}

	@Test
	fun `popUpTo with no match leaves the stack unchanged`() {
		navigator.push(ListScreen)

		navigator.popUpTo(SettingsScreen, inclusive = false)

		assertEquals(listOf(HomeScreen, ListScreen), backStack.toList())
	}

	@Test
	fun `popUpTo inclusive on the only entry keeps the root`() {
		// NavDisplay requires a non-empty back stack, so the inclusive match floors at the root
		// rather than draining it. Still reports true — the predicate did match.
		assertTrue(navigator.popUpTo(HomeScreen, inclusive = true))

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `popUpTo inclusive on the root keeps the root and drops everything above it`() {
		navigator.push(ListScreen, DetailScreen(1))

		assertTrue(navigator.popUpTo(HomeScreen, inclusive = true))

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `popUntil inclusive matching the root keeps the root`() {
		navigator.push(ListScreen, SettingsScreen)

		assertTrue(navigator.popUntil(inclusive = true) { it is HomeScreen })

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `data class equality drives popUpTo matches`() {
		navigator.push(DetailScreen(42), SettingsScreen)

		navigator.popUpTo(DetailScreen(42), inclusive = false)

		assertEquals(listOf(HomeScreen, DetailScreen(42)), backStack.toList())
	}

	@Test
	fun `popUntil predicate leaves the last match on top`() {
		navigator.push(ListScreen, DetailScreen(1), DetailScreen(2), SettingsScreen)

		assertTrue(navigator.popUntil { it is DetailScreen })

		assertEquals(
			listOf(HomeScreen, ListScreen, DetailScreen(1), DetailScreen(2)),
			backStack.toList(),
		)
	}

	@Test
	fun `popUntil inclusive removes the match`() {
		navigator.push(ListScreen, DetailScreen(1), SettingsScreen)

		assertTrue(navigator.popUntil(inclusive = true) { it is ListScreen })

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `popUntil with no match leaves the stack unchanged and returns false`() {
		navigator.push(ListScreen)

		assertFalse(navigator.popUntil { it is SettingsScreen })

		assertEquals(listOf(HomeScreen, ListScreen), backStack.toList())
	}

	@Test
	fun `popUpTo returns true when the key is found`() {
		navigator.push(ListScreen)

		assertTrue(navigator.popUpTo(HomeScreen))
		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `popUpTo returns false when the key is missing`() {
		navigator.push(ListScreen)

		assertFalse(navigator.popUpTo(SettingsScreen))
		assertEquals(listOf(HomeScreen, ListScreen), backStack.toList())
	}

	@Test
	fun `popUntilRoot leaves only the first screen`() {
		navigator.push(ListScreen, DetailScreen(1), SettingsScreen)

		navigator.popUntilRoot()

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	@Test
	fun `popUntilRoot on a single-entry stack is a no-op`() {
		navigator.popUntilRoot()

		assertEquals(listOf(HomeScreen), backStack.toList())
	}

	// --- stack introspection ---

	@Test
	fun `canPop is false on a single-entry stack`() {
		assertFalse(navigator.canPop)
	}

	@Test
	fun `canPop is true when size is greater than one`() {
		navigator.push(DetailScreen(1))

		assertTrue(navigator.canPop)
	}

	@Test
	fun `lastItem and previousItem track the stack`() {
		assertEquals(HomeScreen, navigator.lastItem)
		assertNull(navigator.previousItem)

		navigator.push(DetailScreen(1))

		assertEquals(DetailScreen(1), navigator.lastItem)
		assertEquals(HomeScreen, navigator.previousItem)
	}

	@Test
	fun `items returns the full Nav3Screen stack`() {
		navigator.push(ListScreen, DetailScreen(1))

		assertEquals(listOf(HomeScreen, ListScreen, DetailScreen(1)), navigator.items)
	}
}
