package uk.co.appoly.droid.nav3

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategyScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [TabsSceneStrategy]: [androidx.navigation3.scene.Scene.previousEntries] is the
 * correctness crux for system-back enablement and single-[pop] on back completed.
 *
 * [NavEntry] is constructible without composition (key + content lambda), so strategy logic is
 * exercised directly rather than through a full [androidx.navigation3.ui.NavDisplay].
 */
@RunWith(AndroidJUnit4::class)
class TabsSceneStrategyTest {

	private val homeTab = HomeScreen
	private val roomsTab = ListScreen
	private val settingsTab = SettingsScreen
	private val tabOrder = listOf(homeTab, roomsTab, settingsTab)

	private lateinit var tabs: TabsNav3Navigator
	private lateinit var strategy: TabsSceneStrategy

	@Before
	fun setUp() {
		tabs = TabsNav3Navigator(tabOrder)
		strategy = TabsSceneStrategy(tabs)
	}

	private fun entry(key: NavKey): NavEntry<NavKey> = NavEntry(key) { /* no-op content */ }

	/** Builds NavEntry list matching the navigator's current flattened [TabsNav3Navigator.backStack]. */
	private fun entriesFromBackStack(): List<NavEntry<NavKey>> =
		tabs.backStack.map { entry(it) }

	private fun calculate(entries: List<NavEntry<NavKey>>) =
		with(strategy) { SceneStrategyScope<NavKey>().calculateScene(entries) }

	@Test
	fun `deeper in a tab previousEntries is dropLast 1`() {
		tabs.push(DetailScreen(1))
		tabs.push(DetailScreen(2))

		val entries = entriesFromBackStack()
		val scene = calculate(entries)
		assertNotNull(scene)
		assertEquals(entries.dropLast(1), scene!!.previousEntries)
		assertEquals(listOf(entries.last()), scene.entries)
		assertEquals(1, entries.size - scene.previousEntries.size)
	}

	@Test
	fun `at non-start tab root previousEntries last is start tab top`() {
		tabs.push(DetailScreen(5)) // depth on start tab
		tabs.switchTab(roomsTab)

		val entries = entriesFromBackStack()
		// [home, Detail(5), rooms]
		assertEquals(listOf(homeTab, DetailScreen(5), roomsTab), tabs.backStack.toList())

		val scene = calculate(entries)
		assertNotNull(scene)
		assertEquals(entries.dropLast(1), scene!!.previousEntries)
		// dropLast(1) last entry is start tab top (DetailScreen(5))
		assertEquals(entries[entries.lastIndex - 1].contentKey, scene.previousEntries.last().contentKey)
		assertEquals(DetailScreen(5).toString(), scene.previousEntries.last().contentKey)
		assertEquals(listOf(entries.last()), scene.entries)
		assertEquals(1, entries.size - scene.previousEntries.size)
	}

	@Test
	fun `at start-tab root previousEntries is empty`() {
		// Only start tab root
		val entries = entriesFromBackStack()
		assertEquals(listOf(homeTab), tabs.backStack.toList())

		val scene = calculate(entries)
		assertNotNull(scene)
		assertTrue(scene!!.previousEntries.isEmpty())
		assertEquals(listOf(entries.last()), scene.entries)
		// entries.size - 0 would be 1, but empty previous is the "background app" signal;
		// still satisfies size difference of 1 for the single start entry.
		assertEquals(1, entries.size - scene.previousEntries.size)
	}

	@Test
	fun `at start-tab root with retained visited tabs previousEntries stays empty`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(1))
		tabs.switchTab(homeTab)

		assertTrue(tabs.isOnStartTab)
		assertTrue(tabs.isAtCurrentTabRoot)
		// rooms retained beneath home
		assertTrue(tabs.backStack.size > 1)

		val entries = entriesFromBackStack()
		val scene = calculate(entries)
		assertNotNull(scene)
		assertTrue(
			"Back must stay disabled at start-tab root even when visited tabs are retained",
			scene!!.previousEntries.isEmpty(),
		)
		assertEquals(listOf(entries.last()), scene.entries)
	}

	@Test
	fun `whenever back is enabled the pop delta is exactly 1`() {
		// Cumulative walk. The final step returns to the start-tab root with tabs retained —
		// the one case where back is DISABLED and the delta deliberately exceeds 1.
		val cases = listOf(
			{
				/* start root — already set up */
			},
			{
				tabs.push(DetailScreen(1))
			},
			{
				tabs.switchTab(roomsTab)
			},
			{
				tabs.push(DetailScreen(2))
			},
			{
				tabs.switchTab(settingsTab)
			},
			{
				// Back to start-tab root with rooms + settings retained beneath it.
				tabs.popUntilRoot()
				tabs.switchTab(homeTab)
			},
		)
		// Reset and walk cumulative mutations
		tabs = TabsNav3Navigator(tabOrder)
		strategy = TabsSceneStrategy(tabs)
		for (step in cases) {
			step()
			val entries = entriesFromBackStack()
			assertTrue(entries.isNotEmpty())
			val scene = calculate(entries)!!
			if (scene.previousEntries.isEmpty()) {
				// Back disabled — Nav3 never calls onBackCompleted, so the delta is unconstrained.
				assertTrue(
					"empty previousEntries is only correct at the start-tab root",
					tabs.isOnStartTab && tabs.isAtCurrentTabRoot,
				)
			} else {
				assertEquals(
					"expected single pop delta at $entries",
					1,
					entries.size - scene.previousEntries.size,
				)
			}
			assertEquals(listOf(entries.last()), scene.entries)
		}
	}

	@Test
	fun `pop delta exceeds 1 at start-tab root with retention but back is disabled`() {
		// Guards the documented exception to the delta rule: this is safe only because an empty
		// previousEntries means Nav3 does not enable back at all.
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(1))
		tabs.switchTab(homeTab)

		val entries = entriesFromBackStack()
		val scene = calculate(entries)!!

		assertTrue(scene.previousEntries.isEmpty())
		assertTrue(
			"retention should make the raw delta exceed 1 here",
			entries.size - scene.previousEntries.size > 1,
		)
	}

	@Test
	fun `scene renders exactly one entry the current tab top`() {
		tabs.switchTab(roomsTab)
		tabs.push(DetailScreen(9))
		tabs.switchTab(settingsTab)

		val entries = entriesFromBackStack()
		val scene = calculate(entries)!!
		assertEquals(1, scene.entries.size)
		// last backStack key is settings root
		assertEquals(settingsTab, tabs.lastItem)
		assertEquals(entries.last().contentKey, scene.entries.single().contentKey)
		assertEquals(entries.last().contentKey, scene.key)
	}

	@Test
	fun `empty entries returns null`() {
		val scene = calculate(emptyList())
		assertEquals(null, scene)
	}
}
