package uk.co.appoly.droid.nav3

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState

/**
 * Direction of a tab-strip move, used for directional tab-switch animations.
 *
 * @see Nav3Transitions.tabSlide
 * @see TabsNav3Navigator.pendingTabSlide
 */
enum class TabSlide {
	/** Higher index in [TabsNav3Navigator] tab order (e.g. Home → Settings). */
	Forward,

	/** Lower index in tab order (e.g. Settings → Home). */
	Backward,
}

/**
 * Ambient [TabsNav3Navigator] for cross-tab APIs ([TabsNav3Navigator.navigateToTab],
 * [TabsNav3Navigator.switchTab], [TabsNav3Navigator.currentTab]).
 *
 * In-tab push/pop still go through [LocalNav3Navigator] (which should be the same instance
 * when the host is wired with `navigator = tabsNavigator`).
 *
 * Nullable for previews / non-tab hosts — use `LocalTabsNavigator.current?.navigateToTab(...)`.
 */
val LocalTabsNavigator = staticCompositionLocalOf<TabsNav3Navigator?> { null }

/**
 * Per-tab back stacks flattened into a single [backStack] for one [Nav3ScreenHost] /
 * [androidx.navigation3.ui.NavDisplay].
 *
 * ## Model
 *
 * - Each tab has its own stack; the first entry of [tabOrder] is the **start tab** (exit-through-home).
 * - The display stack is `startTabStack + (currentTabStack if not start)` so system back walks
 *   current-tab screens → start tab → (caller finishes the host).
 * - Implements [Nav3Navigator] so tab pages use [LocalNav3Navigator] for tab-local push/pop
 *   without knowing they are in a tab.
 * - [pendingTabSlide] records whether the latest mutation was a tab switch (and its direction)
 *   so transition specs can animate tab changes differently from intra-tab push/pop.
 *
 * ## Persistence
 *
 * Create via [rememberTabsNav3Navigator] so [currentTab] and every per-tab stack survive
 * configuration change and process death (same reflection-based [NavKey] serialization as
 * [androidx.navigation3.runtime.rememberNavBackStack]). [parent] is **not** saved — it is
 * re-wired from composition on restore.
 *
 * ## Equal keys across tabs
 *
 * The flattened display stack is `startTabStack + currentTabStack`. If the same equal key
 * appears in both (e.g. `DetailScreen(1)` on Home and on Rooms), Nav3 treats them as the same
 * entry for saveable state / ViewModelStore — the same rule as duplicate keys on a single
 * stack. Prefer distinguishing constructor args when the same destination can live under more
 * than one tab.
 *
 * ## Chrome
 *
 * Bottom bars / tab icons stay app-owned. Read [currentTab] and call [switchTab] /
 * [navigateToTab] from your `NavigationBar`.
 *
 * ## Example
 *
 * ```kotlin
 * val tabs = rememberTabsNav3Navigator(listOf(HomeTab, RoomsTab, SettingsTab))
 * Nav3TabsHost(tabsNavigator = tabs)
 * // From a tab page: LocalNav3Navigator.current?.parent?.pop() // dismiss whole tab shell
 * ```
 *
 * @param tabOrder tab roots in strip order (first = start / home tab). Must be non-empty.
 *   Each root is kept as the first entry of that tab's stack and is never popped or replaced.
 *   Only tabs in this list may be passed to [switchTab] / [navigateToTab].
 * @param parent the navigator that nested this tab shell (typically the root host), or `null`
 *   when tabs are the app root. Prefer [rememberTabsNav3Navigator] so parent is wired from
 *   [LocalNav3Navigator].
 */
class TabsNav3Navigator(
	tabOrder: List<Nav3Screen>,
	override val parent: Nav3Navigator? = null,
) : Nav3Navigator {

	init {
		require(tabOrder.isNotEmpty()) { "tabOrder must not be empty" }
	}

	/** Tab roots in strip order; first is the start tab. */
	val tabOrder: List<Nav3Screen> = tabOrder.toList()

	private val startTab: Nav3Screen = this.tabOrder.first()

	private val tabStacks: LinkedHashMap<NavKey, MutableList<NavKey>> = linkedMapOf(
		startTab to mutableStateListOf<NavKey>(startTab),
	)

	/**
	 * Flattened stack for [Nav3ScreenHost] / [androidx.navigation3.ui.NavDisplay].
	 * Mutated only via this navigator — do not edit directly.
	 */
	val backStack: NavBackStack<NavKey> = NavBackStack(startTab)

	/** Currently selected tab root. */
	var currentTab: NavKey by mutableStateOf<NavKey>(startTab)
		private set

	/**
	 * Non-null when the latest mutation was a tab switch (and its direction);
	 * `null` means an intra-tab stack change. Transition specs should prefer
	 * [Nav3Transitions.tabSlide] when this is set. Always `null` after process-death restore.
	 */
	var pendingTabSlide: TabSlide? by mutableStateOf(null)
		private set

	/** True when the current tab shows only its root. */
	val isAtCurrentTabRoot: Boolean
		get() = tabStacks.getValue(currentTab).size == 1

	/** True when the start (home) tab is selected. */
	val isOnStartTab: Boolean
		get() = currentTab == startTab

	/**
	 * Selects [tab] without pushing extra screens. No-op if already current.
	 * Records [pendingTabSlide] for directional animation.
	 *
	 * @throws IllegalArgumentException if [tab] is not in [tabOrder]
	 */
	fun switchTab(tab: Nav3Screen) {
		if (tab == currentTab) return
		requireTab(tab) // validates membership and ensures stack entry
		pendingTabSlide = slideDirectionTo(tab)
		currentTab = tab
		rebuild()
	}

	/**
	 * Cross-tab navigation: select [tab] and push [screens] onto **that** tab's stack
	 * (skipping a push when the screen is already on top, to avoid equal-key duplicates).
	 *
	 * Example: from Home, open Bedroom detail inside the Rooms tab:
	 * `navigateToTab(RoomsTab, RoomDetailScreen("Bedroom"))`.
	 *
	 * @throws IllegalArgumentException if [tab] is not in [tabOrder]
	 */
	fun navigateToTab(tab: Nav3Screen, vararg screens: Nav3Screen) {
		val targetStack = requireTab(tab)
		screens.forEach { screen ->
			if (targetStack.lastOrNull() != screen) {
				targetStack.add(screen)
			}
		}
		pendingTabSlide = if (tab == currentTab) null else slideDirectionTo(tab)
		currentTab = tab
		rebuild()
	}

	// --- Nav3Navigator (tab-local, with exit-through-home on pop) ---

	override fun push(screen: Nav3Screen) {
		tabStacks.getValue(currentTab).add(screen)
		pendingTabSlide = null
		rebuild()
	}

	override fun push(vararg screens: Nav3Screen) {
		tabStacks.getValue(currentTab).addAll(screens)
		pendingTabSlide = null
		rebuild()
	}

	override fun push(screens: Iterable<Nav3Screen>) {
		tabStacks.getValue(currentTab).addAll(screens)
		pendingTabSlide = null
		rebuild()
	}

	/**
	 * Pops within the current tab; if already at that tab's root and not on the start tab,
	 * switches to the start tab (exit-through-home). No-op at start-tab root.
	 */
	override fun pop() {
		val currentStack = tabStacks.getValue(currentTab)
		if (currentStack.size > 1) {
			currentStack.removeAt(currentStack.lastIndex)
			pendingTabSlide = null
		} else if (currentTab != startTab) {
			pendingTabSlide = TabSlide.Backward
			currentTab = startTab
		}
		rebuild()
	}

	override fun replace(screen: Nav3Screen) {
		val currentStack = tabStacks.getValue(currentTab)
		// Never replace the tab root — it doubles as the tab's key in tabStacks.
		if (currentStack.size <= 1) return
		currentStack[currentStack.lastIndex] = screen
		pendingTabSlide = null
		rebuild()
	}

	/**
	 * Tab-local replaceAll: keeps the tab root, clears screens above it, then appends [screen].
	 */
	override fun replaceAll(screen: Nav3Screen) {
		replaceAll(*arrayOf(screen))
	}

	/**
	 * Tab-local replaceAll: keeps the tab root, clears screens above it, then appends [screens].
	 * No-op when [screens] is empty.
	 */
	override fun replaceAll(vararg screens: Nav3Screen) {
		if (screens.isEmpty()) return
		val currentStack = tabStacks.getValue(currentTab)
		while (currentStack.size > 1) {
			currentStack.removeAt(currentStack.lastIndex)
		}
		currentStack.addAll(screens)
		pendingTabSlide = null
		rebuild()
	}

	override fun popUpTo(screen: Nav3Screen, inclusive: Boolean): Boolean =
		popUntil(inclusive = inclusive) { it == screen }

	/**
	 * Pops within the **current tab** only. Never removes the tab root
	 * (target size is coerced to at least 1).
	 */
	override fun popUntil(inclusive: Boolean, predicate: (Nav3Screen) -> Boolean): Boolean {
		val currentStack = tabStacks.getValue(currentTab)
		val index = currentStack.indexOfLast { key ->
			val screen = key as? Nav3Screen ?: return@indexOfLast false
			predicate(screen)
		}
		if (index < 0) return false
		val targetSize = (if (inclusive) index else index + 1).coerceAtLeast(1)
		while (currentStack.size > targetSize) {
			currentStack.removeAt(currentStack.lastIndex)
		}
		pendingTabSlide = null
		rebuild()
		return true
	}

	/** Pops to the current tab's root (does not switch tabs). */
	override fun popUntilRoot() {
		val currentStack = tabStacks.getValue(currentTab)
		while (currentStack.size > 1) {
			currentStack.removeAt(currentStack.lastIndex)
		}
		pendingTabSlide = null
		rebuild()
	}

	override val canPop: Boolean
		get() = tabStacks.getValue(currentTab).size > 1 || currentTab != startTab

	override val lastItem: Nav3Screen?
		get() = backStack.lastOrNull() as? Nav3Screen

	override val previousItem: Nav3Screen?
		get() = backStack.getOrNull(backStack.lastIndex - 1) as? Nav3Screen

	override val items: List<Nav3Screen>
		get() = backStack.mapNotNull { it as? Nav3Screen }

	/** Snapshot of the given tab's stack (root first). Empty if the tab was never visited. */
	fun stackFor(tab: Nav3Screen): List<Nav3Screen> =
		tabStacks[tab]?.mapNotNull { it as? Nav3Screen } ?: emptyList()

	/**
	 * Restores [currentTab] and per-tab stacks from a prior [rememberTabsNav3Navigator] save.
	 * [pendingTabSlide] is cleared. Used by the [Saver]; package-visible for unit tests.
	 *
	 * @param currentTabIndex index into [tabOrder] for the selected tab (clamped to range).
	 * @param stacksByTabIndex one stack per [tabOrder] entry (root first). Missing / empty
	 *   entries become `[tabRoot]`.
	 */
	internal fun restoreFrom(
		currentTabIndex: Int,
		stacksByTabIndex: List<List<NavKey>>,
	) {
		tabStacks.clear()
		tabOrder.forEachIndexed { index, root ->
			val saved = stacksByTabIndex.getOrNull(index).orEmpty()
			val restored = mutableStateListOf<NavKey>()
			when {
				saved.isEmpty() -> restored.add(root)
				saved.first() == root -> restored.addAll(saved)
				else -> {
					restored.add(root)
					restored.addAll(saved)
				}
			}
			tabStacks[root] = restored
		}
		val safeIndex = currentTabIndex.coerceIn(0, tabOrder.lastIndex)
		currentTab = tabOrder[safeIndex]
		pendingTabSlide = null
		rebuild()
	}

	/**
	 * Snapshot of stacks in [tabOrder] order (for the [Saver]).
	 */
	internal fun snapshotStacks(): List<List<NavKey>> =
		tabOrder.map { root ->
			tabStacks[root]?.toList() ?: listOf(root)
		}

	internal fun currentTabIndex(): Int =
		tabOrder.indexOfFirst { it == currentTab }.coerceAtLeast(0)

	private fun requireTab(tab: Nav3Screen): MutableList<NavKey> {
		require(tab in tabOrder) {
			"Tab $tab is not in tabOrder. Known tabs: $tabOrder"
		}
		return tabStacks.getOrPut(tab) { mutableStateListOf(tab) }
	}

	private fun slideDirectionTo(tab: Nav3Screen): TabSlide {
		val to = tabOrder.indexOf(tab)
		val from = tabOrder.indexOf(currentTab as? Nav3Screen)
		// After [requireTab], [to] is always >= 0. Unknown [from] (should not happen) → Forward.
		if (to < 0 || from < 0) return TabSlide.Forward
		return if (to >= from) TabSlide.Forward else TabSlide.Backward
	}

	private fun rebuild() {
		backStack.clear()
		backStack.addAll(tabStacks.getValue(startTab))
		if (currentTab != startTab) {
			backStack.addAll(tabStacks.getValue(currentTab))
		}
	}

	companion object {
		private const val KEY_CURRENT = "tabs_nav3_current"
		private const val KEY_STACK_COUNT = "tabs_nav3_stack_count"
		private const val KEY_STACK_PREFIX = "tabs_nav3_stack_"

		/**
		 * [Saver] for [rememberSaveable] / [rememberTabsNav3Navigator].
		 *
		 * Persists [currentTab] and each tab's stack via the same reflection-based
		 * [NavKeySerializer] that [androidx.navigation3.runtime.rememberNavBackStack] uses.
		 * [parent] is not saved — pass it again on restore.
		 */
		fun saver(
			tabOrder: List<Nav3Screen>,
			parent: Nav3Navigator?,
		): Saver<TabsNav3Navigator, Bundle> {
			// Same reflection-based NavKey path as rememberNavBackStack (NavKeySerializer).
			val keySerializer = NavKeySerializer<NavKey>()
			return Saver(
				save = { nav ->
					Bundle().apply {
						putInt(KEY_CURRENT, nav.currentTabIndex())
						val snapshots = nav.snapshotStacks()
						putInt(KEY_STACK_COUNT, snapshots.size)
						snapshots.forEachIndexed { index, keys ->
							// One nested Bundle per tab; each key is its own SavedState (Bundle).
							val stackBundle = Bundle()
							stackBundle.putInt("n", keys.size)
							keys.forEachIndexed { keyIndex, key ->
								stackBundle.putBundle(
									"k$keyIndex",
									encodeToSavedState(keySerializer, key),
								)
							}
							putBundle("$KEY_STACK_PREFIX$index", stackBundle)
						}
					}
				},
				restore = { bundle ->
					val count = bundle.getInt(KEY_STACK_COUNT, 0).takeIf { it > 0 } ?: tabOrder.size
					val stacks = (0 until count).map { index ->
						val stackBundle = bundle.getBundle("$KEY_STACK_PREFIX$index")
						if (stackBundle == null) {
							listOf(tabOrder.getOrElse(index) { tabOrder.first() } as NavKey)
						} else {
							val n = stackBundle.getInt("n", 0)
							(0 until n).mapNotNull { keyIndex ->
								stackBundle.getBundle("k$keyIndex")?.let { keyBundle ->
									decodeFromSavedState(keySerializer, keyBundle)
								}
							}
						}
					}
					TabsNav3Navigator(tabOrder, parent = parent).also { nav ->
						nav.restoreFrom(
							currentTabIndex = bundle.getInt(KEY_CURRENT, 0),
							stacksByTabIndex = stacks,
						)
					}
				},
			)
		}
	}
}

/**
 * Remembers a [TabsNav3Navigator] for [tabOrder] across **configuration change and process
 * death**, wiring [TabsNav3Navigator.parent] from the current [LocalNav3Navigator].
 *
 * Tab roots and every pushed [Nav3Screen] must be `@Serializable` (same contract as
 * [androidx.navigation3.runtime.rememberNavBackStack]). [parent] is re-applied from
 * composition on restore and is not itself saved.
 *
 * @param tabOrder tab roots in strip order (first = start tab). Pass a stable list (e.g. from
 *   [androidx.compose.runtime.remember]).
 * @param parent override parent; defaults to the ambient navigator.
 */
@Composable
fun rememberTabsNav3Navigator(
	tabOrder: List<Nav3Screen>,
	parent: Nav3Navigator? = LocalNav3Navigator.current,
): TabsNav3Navigator =
	rememberSaveable(
		tabOrder,
		parent,
		saver = TabsNav3Navigator.saver(tabOrder, parent),
	) {
		TabsNav3Navigator(tabOrder, parent = parent)
	}
