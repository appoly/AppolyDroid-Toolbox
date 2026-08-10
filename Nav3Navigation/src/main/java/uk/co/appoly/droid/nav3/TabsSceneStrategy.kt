package uk.co.appoly.droid.nav3

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * [SceneStrategy] for [Nav3TabsHost]: renders only the current tab's top entry while the other
 * tabs' entries stay in the back stack, so Nav3 keeps their saveable state and ViewModelStores
 * (entries are only torn down when their key leaves the back stack, not when they stop being
 * rendered). This is what gives [TabsNav3Navigator] Voyager-style per-tab state retention.
 *
 * ## previousEntries (back behaviour)
 *
 * ```
 * previousEntries =
 *   if (tabsNavigator.isOnStartTab && tabsNavigator.isAtCurrentTabRoot) emptyList()
 *   else entries.dropLast(1)
 * ```
 *
 * That single rule covers every reachable case:
 * - **Deeper in a tab** (`currentTabStack.size > 1`): `dropLast(1)` reveals that tab's previous
 *   screen.
 * - **At a non-start tab root**: the current tab contributes exactly one entry (its root) as the
 *   suffix, so `dropLast(1)` reveals [TabsNav3Navigator.startTab]'s top — precisely what
 *   exit-through-home lands on.
 * - **At the start-tab root**: `emptyList()` disables back so the system default (background the
 *   app) runs. Nav3 enables back iff `previousEntries` is non-empty.
 *
 * Whenever back is **enabled** (`previousEntries` non-empty, i.e. the first two cases),
 * `entries.size - previousEntries.size == 1`, so Nav3's `onBackCompleted` invokes `onBack`
 * exactly once (it pops `entries.size - previousEntries.size` times).
 *
 * The start-tab-root case deliberately breaks that arithmetic — with visited tabs retained,
 * `entries` may hold several keys while `previousEntries` is empty, so the difference exceeds 1.
 * That is harmless precisely because an empty `previousEntries` means Nav3 never enables back
 * there and `onBackCompleted` cannot fire.
 *
 * ## Predictive back
 *
 * During a predictive-back gesture the strategy is re-run over [Scene.previousEntries] while
 * [TabsNav3Navigator.currentTab] has not yet changed, so the nested `previousEntries` is
 * computed from the pre-gesture tab. Harmless for this single-pane strategy (the re-run still
 * produces a single-entry scene from the last entry of that list).
 *
 * @param tabsNavigator the tabs navigator whose current-tab / start-tab flags drive
 *   [Scene.previousEntries].
 *
 * @see TabsNav3Navigator
 * @see Nav3TabsHost
 */
class TabsSceneStrategy(
	private val tabsNavigator: TabsNav3Navigator,
) : SceneStrategy<NavKey> {

	override fun SceneStrategyScope<NavKey>.calculateScene(
		entries: List<NavEntry<NavKey>>,
	): Scene<NavKey>? {
		if (entries.isEmpty()) return null
		val top = entries.last()
		val previousEntries =
			if (tabsNavigator.isOnStartTab && tabsNavigator.isAtCurrentTabRoot) {
				emptyList()
			} else {
				entries.dropLast(1)
			}
		return TabsScene(
			key = top.contentKey,
			entry = top,
			previousEntries = previousEntries,
		)
	}
}

/**
 * Single-entry scene used by [TabsSceneStrategy] — same shape as Nav3's internal
 * `SinglePaneScene` (key, one entry, previousEntries, content = entry.Content()).
 */
private class TabsScene(
	override val key: Any,
	val entry: NavEntry<NavKey>,
	override val previousEntries: List<NavEntry<NavKey>>,
) : Scene<NavKey> {
	// Derived from [entry] — deliberately excluded from equals/hashCode as it carries no
	// independent identity.
	override val entries: List<NavEntry<NavKey>> = listOf(entry)

	override val content: @Composable () -> Unit = { entry.Content() }

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is TabsScene) return false

		return key == other.key &&
			entry == other.entry &&
			previousEntries == other.previousEntries
	}

	override fun hashCode(): Int {
		var result = key.hashCode()
		result = 31 * result + entry.hashCode()
		result = 31 * result + previousEntries.hashCode()
		return result
	}

	override fun toString(): String =
		"TabsScene(key=$key, entry=$entry, previousEntries=$previousEntries)"
}
