package uk.co.appoly.droid.nav3

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [Nav3Navigator.navigateToDeepLink] on each navigator: [BackStackNav3Navigator],
 * [TabsNav3Navigator] (tab selection, atomic switch-and-land) and the interface default that
 * custom navigators inherit. Also covers [TabsNav3Navigator.navigateToTab]'s whole-stack dedup.
 */
@RunWith(AndroidJUnit4::class)
class Nav3NavigateToDeepLinkTest {

	private val homeTab = HomeScreen
	private val roomsTab = ListScreen
	private val settingsTab = SettingsScreen

	private fun tabs() = TabsNav3Navigator(listOf(homeTab, roomsTab, settingsTab))

	// --- BackStackNav3Navigator ---

	@Test
	fun `back stack append pushes the link on top`() {
		val backStack = NavBackStack<NavKey>(HomeScreen, DetailScreen(1))
		BackStackNav3Navigator(backStack).navigateToDeepLink(Nav3DeepLink(listOf(HomeScreen, DetailScreen(2))))

		assertEquals(listOf(HomeScreen, DetailScreen(1), DetailScreen(2)), backStack.toList())
	}

	@Test
	fun `back stack append surfaces an open screen`() {
		val backStack = NavBackStack<NavKey>(HomeScreen, DetailScreen(1), SettingsScreen)
		BackStackNav3Navigator(backStack).navigateToDeepLink(Nav3DeepLink(listOf(HomeScreen, DetailScreen(1))))

		assertEquals(listOf(HomeScreen, DetailScreen(1)), backStack.toList())
	}

	@Test
	fun `back stack reconcile keeps the shared prefix entry`() {
		val shared = DetailScreen(1)
		val backStack = NavBackStack<NavKey>(HomeScreen, shared, SettingsScreen)
		BackStackNav3Navigator(backStack).navigateToDeepLink(
			Nav3DeepLink(listOf(HomeScreen, DetailScreen(1), ListScreen), Nav3DeepLinkMode.Reconcile)
		)

		assertEquals(listOf(HomeScreen, DetailScreen(1), ListScreen), backStack.toList())
		assertSame(shared, backStack[1])
	}

	@Test
	fun `back stack reconcile with a different root replaces the stack`() {
		val backStack = NavBackStack<NavKey>(SettingsScreen)
		BackStackNav3Navigator(backStack).navigateToDeepLink(
			Nav3DeepLink(listOf(HomeScreen, DetailScreen(7)), Nav3DeepLinkMode.Reconcile)
		)

		assertEquals(listOf(HomeScreen, DetailScreen(7)), backStack.toList())
	}

	// --- TabsNav3Navigator.navigateToDeepLink ---

	@Test
	fun `a link starting with a tab root selects that tab and lands on it`() {
		val tabs = tabs()
		tabs.navigateToDeepLink(Nav3DeepLink(listOf(roomsTab, DetailScreen(3))))

		assertEquals(roomsTab, tabs.currentTab)
		assertEquals(listOf(roomsTab, DetailScreen(3)), tabs.items)
		assertEquals(DetailScreen(3), tabs.backStack.last())
		assertEquals(TabSlide.Forward, tabs.pendingTabSlide)
	}

	@Test
	fun `a link without a tab root lands on the current tab under its root`() {
		val tabs = tabs()
		tabs.switchTab(settingsTab)
		tabs.navigateToDeepLink(Nav3DeepLink(DetailScreen(4)))

		assertEquals(settingsTab, tabs.currentTab)
		assertEquals(listOf(settingsTab, DetailScreen(4)), tabs.items)
		assertNull("staying on the tab is not a tab switch", tabs.pendingTabSlide)
	}

	@Test
	fun `append on a tab keeps the user's place`() {
		val tabs = tabs()
		tabs.navigateToTab(roomsTab, DetailScreen(3))
		tabs.switchTab(homeTab)

		tabs.navigateToDeepLink(Nav3DeepLink(listOf(roomsTab, DetailScreen(7))))

		assertEquals(listOf(roomsTab, DetailScreen(3), DetailScreen(7)), tabs.items)
	}

	@Test
	fun `reconcile on a tab pops to the shared prefix and never past the root`() {
		val tabs = tabs()
		tabs.navigateToTab(roomsTab, DetailScreen(3), DetailScreen(4))
		tabs.switchTab(homeTab)

		tabs.navigateToDeepLink(Nav3DeepLink(listOf(roomsTab, DetailScreen(7)), Nav3DeepLinkMode.Reconcile))

		assertEquals(roomsTab, tabs.currentTab)
		assertEquals(listOf(roomsTab, DetailScreen(7)), tabs.items)
	}

	@Test
	fun `landing on one tab leaves the other tabs' stacks alone`() {
		val tabs = tabs()
		tabs.push(DetailScreen(1)) // on home
		tabs.navigateToDeepLink(Nav3DeepLink(listOf(roomsTab, DetailScreen(2)), Nav3DeepLinkMode.Reconcile))

		assertEquals(listOf(homeTab, DetailScreen(1)), tabs.stackFor(homeTab))
	}

	@Test
	fun `a tab root inside a link is rejected`() {
		val tabs = tabs()
		assertThrows(IllegalArgumentException::class.java) {
			tabs.navigateToDeepLink(Nav3DeepLink(listOf(homeTab, settingsTab)))
		}
		assertThrows(IllegalArgumentException::class.java) {
			tabs.navigateToDeepLink(Nav3DeepLink(listOf(DetailScreen(1), roomsTab)))
		}
	}

	// --- TabsNav3Navigator.navigateToTab whole-stack dedup ---

	@Test
	fun `navigateToTab surfaces a screen already deeper in the tab`() {
		val tabs = tabs()
		tabs.navigateToTab(roomsTab, DetailScreen(1), DetailScreen(2))

		tabs.navigateToTab(roomsTab, DetailScreen(1))

		// Previously this pushed a second DetailScreen(1), which shared state with the first.
		assertEquals(listOf(roomsTab, DetailScreen(1)), tabs.items)
	}

	@Test
	fun `navigateToTab with no screens just selects the tab`() {
		val tabs = tabs()
		tabs.navigateToTab(roomsTab, DetailScreen(1))
		tabs.switchTab(homeTab)

		tabs.navigateToTab(roomsTab)

		assertEquals(roomsTab, tabs.currentTab)
		assertEquals(listOf(roomsTab, DetailScreen(1)), tabs.items)
	}

	// --- Interface default (custom navigators) ---

	/** Delegates everything except [navigateToDeepLink], so the interface default runs. */
	private class WrappingNavigator(private val inner: BackStackNav3Navigator) : Nav3Navigator {
		override val parent: Nav3Navigator? get() = null
		override fun push(screen: Nav3Screen) = inner.push(screen)
		override fun push(vararg screens: Nav3Screen) = inner.push(*screens)
		override fun push(screens: Iterable<Nav3Screen>) = inner.push(screens)
		override fun pop() = inner.pop()
		override fun replace(screen: Nav3Screen) = inner.replace(screen)
		override fun replaceAll(screen: Nav3Screen) = inner.replaceAll(screen)
		override fun replaceAll(vararg screens: Nav3Screen) = inner.replaceAll(*screens)
		override fun popUpTo(screen: Nav3Screen, inclusive: Boolean) = inner.popUpTo(screen, inclusive)
		override fun popUntil(inclusive: Boolean, predicate: (Nav3Screen) -> Boolean) =
			inner.popUntil(inclusive, predicate)
		override fun popUntilRoot() = inner.popUntilRoot()
		override val canPop get() = inner.canPop
		override val lastItem get() = inner.lastItem
		override val previousItem get() = inner.previousItem
		override val items get() = inner.items
	}

	@Test
	fun `the interface default matches the back stack navigator`() {
		val cases = listOf(
			listOf(HomeScreen, DetailScreen(1), SettingsScreen) to Nav3DeepLink(listOf(HomeScreen, DetailScreen(1))),
			listOf(HomeScreen, ListScreen) to Nav3DeepLink(listOf(HomeScreen, DetailScreen(2))),
			listOf(HomeScreen, ListScreen, SettingsScreen) to
				Nav3DeepLink(listOf(HomeScreen, DetailScreen(3)), Nav3DeepLinkMode.Reconcile),
			listOf(SettingsScreen, ListScreen) to
				Nav3DeepLink(listOf(HomeScreen, DetailScreen(4)), Nav3DeepLinkMode.Reconcile),
		)
		for ((start, link) in cases) {
			val expected = NavBackStack<NavKey>(*start.toTypedArray())
			BackStackNav3Navigator(expected).navigateToDeepLink(link)

			val actual = NavBackStack<NavKey>(*start.toTypedArray())
			WrappingNavigator(BackStackNav3Navigator(actual)).navigateToDeepLink(link)

			assertEquals("landing $link on $start", expected.toList(), actual.toList())
		}
	}
}
