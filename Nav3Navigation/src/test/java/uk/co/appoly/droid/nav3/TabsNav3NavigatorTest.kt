package uk.co.appoly.droid.nav3

import androidx.compose.runtime.saveable.SaverScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [TabsNav3Navigator]: per-tab stacks, flatten display stack, exit-through-home,
 * cross-tab [TabsNav3Navigator.navigateToTab], [TabSlide] hints, and [TabsNav3Navigator.saver]
 * Bundle round-trips (needs a real [android.os.Bundle] → Robolectric).
 */
@RunWith(AndroidJUnit4::class)
class TabsNav3NavigatorTest {

	private lateinit var tabs: TabsNav3Navigator

	// Tab roots: file-level @Serializable fixtures (nested private types break NavKeySerializer reflection).
	private val homeTab = HomeScreen
	private val roomsTab = ListScreen
	private val settingsTab = SettingsScreen
	private val tabOrder = listOf(homeTab, roomsTab, settingsTab)

	@Before
	fun setUp() {
		tabs = TabsNav3Navigator(tabOrder)
	}

	@Test
	fun `starts on first tab with single-entry display stack`() {
		assertEquals(homeTab, tabs.currentTab)
		assertEquals(listOf(homeTab), tabs.backStack.toList())
		assertTrue(tabs.isOnStartTab)
		assertTrue(tabs.isAtCurrentTabRoot)
		assertFalse(tabs.canPop)
		assertNull(tabs.pendingTabSlide)
	}

	@Test
	fun `push is tab-local and clears pendingTabSlide`() {
		tabs.push(DetailScreen(1))

		assertEquals(listOf(homeTab, DetailScreen(1)), tabs.backStack.toList())
		assertEquals(listOf(homeTab, DetailScreen(1)), tabs.stackFor(homeTab))
		assertNull(tabs.pendingTabSlide)
		assertTrue(tabs.canPop)
		assertFalse(tabs.isAtCurrentTabRoot)
	}

	@Test
	fun `switchTab records forward slide and flattens start plus current`() {
		tabs.switchTab(roomsTab)

		assertEquals(roomsTab, tabs.currentTab)
		assertEquals(TabSlide.Forward, tabs.pendingTabSlide)
		// start stack + rooms stack
		assertEquals(listOf(homeTab, roomsTab), tabs.backStack.toList())
		assertTrue(tabs.canPop) // can fall back to start tab
	}

	@Test
	fun `switchTab backward records Backward slide`() {
		tabs.switchTab(settingsTab)
		tabs.switchTab(homeTab)

		assertEquals(homeTab, tabs.currentTab)
		assertEquals(TabSlide.Backward, tabs.pendingTabSlide)
		// settings was visited → retained beneath start/current
		assertEquals(listOf(settingsTab, homeTab), tabs.backStack.toList())
	}

	@Test
	fun `switchTab no-op when already current leaves pendingSlide alone`() {
		tabs.switchTab(roomsTab)
		assertEquals(TabSlide.Forward, tabs.pendingTabSlide)

		tabs.switchTab(roomsTab)

		assertEquals(TabSlide.Forward, tabs.pendingTabSlide)
		assertEquals(roomsTab, tabs.currentTab)
	}

	@Test
	fun `per-tab stacks retain screens when switching away and back`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(9))
		tabs.switchTab(settingsTab)

		// visited-other (rooms) + start + current
		assertEquals(
			listOf(roomsTab, DetailScreen(9), homeTab, settingsTab),
			tabs.backStack.toList(),
		)
		assertEquals(listOf(roomsTab, DetailScreen(9)), tabs.stackFor(roomsTab))

		tabs.switchTab(roomsTab)

		// visited-other (settings) + start + current (rooms stack)
		assertEquals(
			listOf(settingsTab, homeTab, roomsTab, DetailScreen(9)),
			tabs.backStack.toList(),
		)
	}

	@Test
	fun `pop within tab removes top then at root switches to start tab`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(1))
		tabs.pop()

		assertEquals(listOf(homeTab, roomsTab), tabs.backStack.toList())
		assertNull(tabs.pendingTabSlide)

		tabs.pop()

		assertEquals(homeTab, tabs.currentTab)
		assertEquals(TabSlide.Backward, tabs.pendingTabSlide)
		// rooms remains visited after exit-through-home
		assertEquals(listOf(roomsTab, homeTab), tabs.backStack.toList())
	}

	@Test
	fun `pop at start tab root is a no-op`() {
		tabs.pop()

		assertEquals(listOf(homeTab), tabs.backStack.toList())
		assertFalse(tabs.canPop)
	}

	@Test
	fun `navigateToTab switches and seeds target stack`() {
		tabs.navigateToTab(roomsTab, DetailScreen(3))

		assertEquals(roomsTab, tabs.currentTab)
		assertEquals(TabSlide.Forward, tabs.pendingTabSlide)
		assertEquals(listOf(homeTab, roomsTab, DetailScreen(3)), tabs.backStack.toList())
		assertEquals(listOf(roomsTab, DetailScreen(3)), tabs.stackFor(roomsTab))
	}

	@Test
	fun `navigateToTab skips equal top to avoid duplicate keys`() {
		tabs.navigateToTab(roomsTab, DetailScreen(3))
		tabs.navigateToTab(roomsTab, DetailScreen(3))

		assertEquals(listOf(roomsTab, DetailScreen(3)), tabs.stackFor(roomsTab))
	}

	@Test
	fun `navigateToTab on current tab with screens does not set tab slide`() {
		tabs.switchTab(roomsTab)
		tabs.navigateToTab(roomsTab, DetailScreen(1))

		assertNull(tabs.pendingTabSlide)
		assertEquals(listOf(roomsTab, DetailScreen(1)), tabs.stackFor(roomsTab))
	}

	@Test
	fun `replace never replaces the tab root`() {
		tabs.replace(DetailScreen(1))

		assertEquals(listOf(homeTab), tabs.backStack.toList())
	}

	@Test
	fun `replace swaps only the top of the current tab`() {
		tabs.push(DetailScreen(1))
		tabs.replace(DetailScreen(2))

		assertEquals(listOf(homeTab, DetailScreen(2)), tabs.backStack.toList())
	}

	@Test
	fun `replaceAll keeps tab root and appends`() {
		tabs.push(DetailScreen(1))
		tabs.replaceAll(DetailScreen(5), DetailScreen(6))

		assertEquals(listOf(homeTab, DetailScreen(5), DetailScreen(6)), tabs.stackFor(homeTab))
	}

	@Test
	fun `popUntil is tab-local and never removes the root`() {
		tabs.push(DetailScreen(0), DetailScreen(1))

		assertTrue(tabs.popUntil { it is DetailScreen && it.itemId == 0 })
		assertEquals(listOf(homeTab, DetailScreen(0)), tabs.stackFor(homeTab))

		// inclusive would want size 0 but coerceAtLeast(1) keeps root
		assertTrue(tabs.popUntil(inclusive = true) { it is HomeScreen })
		assertEquals(listOf(homeTab), tabs.stackFor(homeTab))
	}

	@Test
	fun `popUntilRoot is tab-local`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(1), DetailScreen(2))
		tabs.popUntilRoot()

		assertEquals(roomsTab, tabs.currentTab)
		assertEquals(listOf(roomsTab), tabs.stackFor(roomsTab))
		assertEquals(listOf(homeTab, roomsTab), tabs.backStack.toList())
	}

	@Test
	fun `push vararg and iterable land on current tab`() {
		tabs.push(DetailScreen(1), DetailScreen(2))
		assertEquals(listOf(homeTab, DetailScreen(1), DetailScreen(2)), tabs.stackFor(homeTab))

		tabs.push(listOf(DetailScreen(3)))
		assertEquals(
			listOf(homeTab, DetailScreen(1), DetailScreen(2), DetailScreen(3)),
			tabs.stackFor(homeTab),
		)
	}

	@Test(expected = IllegalArgumentException::class)
	fun `empty tabOrder is rejected`() {
		// The default-startTab path: the default expression runs before the constructor body, so
		// it must reject empty tabOrder itself rather than letting first() throw NoSuchElement.
		TabsNav3Navigator(emptyList())
	}

	@Test
	fun `empty tabOrder failure names the offending argument`() {
		val error = runCatching { TabsNav3Navigator(emptyList()) }.exceptionOrNull()
		assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
		assertEquals("tabOrder must not be empty", error!!.message)
	}

	@Test(expected = IllegalArgumentException::class)
	fun `empty tabOrder is rejected even with an explicit startTab`() {
		// Explicit startTab skips the default expression, so this exercises the init require.
		TabsNav3Navigator(emptyList(), startTab = homeTab)
	}

	@Test(expected = IllegalArgumentException::class)
	fun `switchTab rejects tabs not in tabOrder`() {
		tabs.switchTab(OtherTabScreen)
	}

	@Test(expected = IllegalArgumentException::class)
	fun `navigateToTab rejects tabs not in tabOrder`() {
		tabs.navigateToTab(OtherTabScreen, DetailScreen(1))
	}

	@Test
	fun `restoreFrom rebuilds current tab and per-tab stacks`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(9))
		tabs.switchTab(settingsTab)
		tabs.push(DetailScreen(2))

		val snapshot = tabs.snapshotStacks()
		val current = tabs.currentTabIndex()

		val restored = TabsNav3Navigator(tabOrder, parent = null)
		restored.restoreFrom(currentTabIndex = current, stacksByTabIndex = snapshot)

		assertEquals(settingsTab, restored.currentTab)
		assertEquals(listOf(homeTab), restored.stackFor(homeTab))
		assertEquals(listOf(roomsTab, DetailScreen(9)), restored.stackFor(roomsTab))
		assertEquals(listOf(settingsTab, DetailScreen(2)), restored.stackFor(settingsTab))
		// restoreFrom populates every tabOrder entry → all count as visited
		assertEquals(
			listOf(roomsTab, DetailScreen(9), homeTab, settingsTab, DetailScreen(2)),
			restored.backStack.toList(),
		)
		assertNull(restored.pendingTabSlide)
	}

	@Test
	fun `restoreFrom is the in-memory path the Saver uses after decode`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(42))
		tabs.switchTab(settingsTab)

		assertEquals(2, tabs.currentTabIndex())
		val restored = TabsNav3Navigator(tabOrder, parent = null)
		restored.restoreFrom(
			currentTabIndex = tabs.currentTabIndex(),
			stacksByTabIndex = tabs.snapshotStacks(),
		)

		assertEquals(settingsTab, restored.currentTab)
		assertEquals(listOf(roomsTab, DetailScreen(42)), restored.stackFor(roomsTab))
		assertEquals(listOf(settingsTab), restored.stackFor(settingsTab))
		assertNull(restored.pendingTabSlide)
	}

	@Test
	fun `NavKeySerializer round-trips DetailScreen for stack persistence`() {
		// Same reflection path TabsNav3Navigator.Saver uses (and rememberNavBackStack).
		val keySerializer = NavKeySerializer<NavKey>()
		val original: NavKey = DetailScreen(42)
		val encoded = encodeToSavedState(keySerializer, original)
		val decoded = decodeFromSavedState(keySerializer, encoded)
		assertEquals(DetailScreen(42), decoded)
	}

	@Test
	fun `NavKeySerializer round-trips data object tab roots`() {
		val keySerializer = NavKeySerializer<NavKey>()
		val encoded = encodeToSavedState(keySerializer, HomeScreen as NavKey)
		assertEquals(HomeScreen, decodeFromSavedState(keySerializer, encoded))
	}

	@Test
	fun `saver Bundle encode and decode round-trips stacks and current tab`() {
		// Finding 1 acceptance: non-start current tab, depth on two tabs, data-class DetailScreen.
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(42))
		tabs.switchTab(settingsTab)
		assertEquals(2, tabs.currentTabIndex())

		val saver = TabsNav3Navigator.saver(tabOrder = tabOrder, startTab = homeTab, parent = null)
		val saved = with(saver) {
			object : SaverScope {
				override fun canBeSaved(value: Any): Boolean = true
			}.save(tabs)
		}
		assertTrue(saved != null)
		assertEquals(2, saved!!.getInt("tabs_nav3_current", -1))
		assertEquals(3, saved.getInt("tabs_nav3_stack_count", -1))

		val restored = saver.restore(saved)!!
		assertEquals(settingsTab, restored.currentTab)
		assertEquals(listOf(homeTab), restored.stackFor(homeTab))
		assertEquals(listOf(roomsTab, DetailScreen(42)), restored.stackFor(roomsTab))
		assertEquals(listOf(settingsTab), restored.stackFor(settingsTab))
		// Saver restore populates every tab → rooms retained + start + current
		assertEquals(
			listOf(roomsTab, DetailScreen(42), homeTab, settingsTab),
			restored.backStack.toList(),
		)
		assertNull(restored.pendingTabSlide)
	}

	// --- Visited-tab retention / currentTabDepth / items ---------------------------

	@Test
	fun `visited inactive tabs remain in backStack after switching away`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(1))
		tabs.switchTab(settingsTab)

		assertTrue(tabs.backStack.contains(roomsTab))
		assertTrue(tabs.backStack.contains(DetailScreen(1)))
		assertEquals(settingsTab, tabs.backStack.last())
	}

	@Test
	fun `current tab stack is always the suffix and lastItem is its top`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(1))
		tabs.switchTab(settingsTab)
		tabs.push(DetailScreen(2))

		val suffix = listOf(settingsTab, DetailScreen(2))
		assertEquals(suffix, tabs.backStack.takeLast(suffix.size))
		assertEquals(DetailScreen(2), tabs.lastItem)
		assertEquals(settingsTab, tabs.currentTab)
	}

	@Test
	fun `never-visited tab contributes nothing to backStack`() {
		tabs.switchTab(roomsTab)
		// settings never selected
		assertFalse(tabs.backStack.contains(settingsTab))
		assertEquals(emptyList<Nav3Screen>(), tabs.stackFor(settingsTab))
		assertEquals(listOf(homeTab, roomsTab), tabs.backStack.toList())
	}

	@Test
	fun `currentTabDepth tracks current tab depth not total backStack size`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(1))
		tabs.switchTab(settingsTab)
		tabs.push(DetailScreen(2), DetailScreen(3))

		// rooms(2) + home(1) + settings(3) = 6 retained entries
		assertEquals(6, tabs.backStack.size)
		assertEquals(3, tabs.currentTabDepth) // settings + two details
		assertEquals(listOf(settingsTab, DetailScreen(2), DetailScreen(3)), tabs.items)
	}

	@Test
	fun `items returns only the current tab stack`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(9))
		tabs.switchTab(settingsTab)

		assertEquals(listOf(settingsTab), tabs.items)
		assertEquals(listOf(roomsTab, DetailScreen(9)), tabs.stackFor(roomsTab))

		tabs.switchTab(roomsTab)
		assertEquals(listOf(roomsTab, DetailScreen(9)), tabs.items)
	}

	// --- Explicit startTab (non-first in tabOrder) ---------------------------------

	/**
	 * Centre-start strip: display order A · B · C with C as launch / exit-through-home.
	 * Mirrors Stations · Kerbside · Home with Home mid-strip.
	 */
	private fun centreStartTabs(): TabsNav3Navigator {
		// homeTab at index 2
		return TabsNav3Navigator(
			tabOrder = listOf(roomsTab, settingsTab, homeTab),
			startTab = homeTab,
		)
	}

	@Test
	fun `non-first startTab is launch tab and isOnStartTab`() {
		val centre = centreStartTabs()

		assertEquals(homeTab, centre.startTab)
		assertEquals(homeTab, centre.currentTab)
		assertTrue(centre.isOnStartTab)
		assertEquals(listOf(homeTab), centre.backStack.toList())
		assertFalse(centre.canPop)
	}

	@Test
	fun `non-first startTab rebuild flattens start stack underneath`() {
		val centre = centreStartTabs()
		centre.push(DetailScreen(1))
		centre.switchTab(roomsTab)
		centre.push(DetailScreen(9))

		// startTab stack + current (rooms) stack
		assertEquals(
			listOf(homeTab, DetailScreen(1), roomsTab, DetailScreen(9)),
			centre.backStack.toList(),
		)
		assertEquals(listOf(homeTab, DetailScreen(1)), centre.stackFor(homeTab))
		assertEquals(listOf(roomsTab, DetailScreen(9)), centre.stackFor(roomsTab))
	}

	@Test
	fun `exit-through-home from tab before startTab records Forward`() {
		val centre = centreStartTabs()
		// roomsTab index 0, startTab (home) index 2 → exit slides Forward
		centre.switchTab(roomsTab)
		assertEquals(roomsTab, centre.currentTab)
		assertEquals(TabSlide.Forward, centre.exitToStartTabSlide)

		centre.pop()

		assertEquals(homeTab, centre.currentTab)
		assertEquals(TabSlide.Forward, centre.pendingTabSlide)
	}

	@Test
	fun `exit-through-home from tab after startTab records Backward`() {
		// startTab at index 0 is the default; switch to settings (index 2) then pop → Backward
		tabs.switchTab(settingsTab)
		assertEquals(TabSlide.Backward, tabs.exitToStartTabSlide)

		tabs.pop()

		assertEquals(homeTab, tabs.currentTab)
		assertEquals(TabSlide.Backward, tabs.pendingTabSlide)
	}

	@Test
	fun `exit-through-home from tab after mid startTab records Backward`() {
		// Strip: rooms(0) · home(1) · settings(2), start = home
		val mid = TabsNav3Navigator(
			tabOrder = listOf(roomsTab, homeTab, settingsTab),
			startTab = homeTab,
		)
		mid.switchTab(settingsTab)
		assertEquals(TabSlide.Backward, mid.exitToStartTabSlide)

		mid.pop()

		assertEquals(homeTab, mid.currentTab)
		assertEquals(TabSlide.Backward, mid.pendingTabSlide)
	}

	@Test(expected = IllegalArgumentException::class)
	fun `startTab not in tabOrder is rejected`() {
		TabsNav3Navigator(tabOrder, startTab = OtherTabScreen)
	}

	@Test
	fun `saver round-trip with non-first startTab preserves currentTab and stacks`() {
		val centre = centreStartTabs()
		centre.switchTab(roomsTab)
		centre.push(DetailScreen(7))
		centre.switchTab(settingsTab)
		centre.push(DetailScreen(3))

		val saver = TabsNav3Navigator.saver(
			tabOrder = listOf(roomsTab, settingsTab, homeTab),
			startTab = homeTab,
			parent = null,
		)
		val saved = with(saver) {
			object : SaverScope {
				override fun canBeSaved(value: Any): Boolean = true
			}.save(centre)
		}
		assertTrue(saved != null)

		val restored = saver.restore(saved!!)!!
		assertEquals(settingsTab, restored.currentTab)
		assertEquals(homeTab, restored.startTab)
		assertEquals(listOf(homeTab), restored.stackFor(homeTab))
		assertEquals(listOf(roomsTab, DetailScreen(7)), restored.stackFor(roomsTab))
		assertEquals(listOf(settingsTab, DetailScreen(3)), restored.stackFor(settingsTab))
		// tabOrder = rooms · settings · home; other visited = rooms; start = home; current = settings
		assertEquals(
			listOf(roomsTab, DetailScreen(7), homeTab, settingsTab, DetailScreen(3)),
			restored.backStack.toList(),
		)
		assertNull(restored.pendingTabSlide)
	}

	@Test
	fun `saver KEY_CURRENT missing falls back to startTab not tabOrder first`() {
		// Centre-start: rooms(0) · settings(1) · home(2). Missing KEY_CURRENT must restore to home,
		// not rooms (index 0).
		val order = listOf(roomsTab, settingsTab, homeTab)
		val centre = TabsNav3Navigator(order, startTab = homeTab)
		centre.switchTab(roomsTab)
		centre.push(DetailScreen(1))

		val saver = TabsNav3Navigator.saver(tabOrder = order, startTab = homeTab, parent = null)
		val saved = with(saver) {
			object : SaverScope {
				override fun canBeSaved(value: Any): Boolean = true
			}.save(centre)
		}!!

		// Drop KEY_CURRENT so restore uses the default (startTab index).
		saved.remove("tabs_nav3_current")

		val restored = saver.restore(saved)!!
		assertEquals(homeTab, restored.currentTab)
		assertTrue(restored.isOnStartTab)
		// Stacks still restored from the bundle
		assertEquals(listOf(roomsTab, DetailScreen(1)), restored.stackFor(roomsTab))
		assertEquals(listOf(homeTab), restored.stackFor(homeTab))
		// restore populates all tabOrder roots as visited (settings root + rooms depth + home)
		assertEquals(
			listOf(roomsTab, DetailScreen(1), settingsTab, homeTab),
			restored.backStack.toList(),
		)
	}
}
