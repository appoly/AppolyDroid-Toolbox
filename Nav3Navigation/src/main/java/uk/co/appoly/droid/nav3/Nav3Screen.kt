package uk.co.appoly.droid.nav3

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

/**
 * Voyager-style screens on androidx Navigation 3: the nav key and its UI are the **same** class,
 * so `navigator.push(SomeScreen())` is cmd+B-navigable straight to the screen's code — no
 * key ↔ entryProvider hop.
 *
 * This works because Nav3's `entryProvider` is just a `(NavKey) -> NavEntry` function;
 * [nav3ScreenEntry] collapses the mapping to "the key renders itself". Nav3's docs favour the
 * separated style so keys can live in a shared module while UI lives in feature modules —
 * irrelevant to a single-module app, so we choose ergonomics.
 *
 * ### Declaring a screen
 *
 * ```kotlin
 * @Serializable
 * data class DetailScreen(val itemId: Int) : Nav3Screen {
 *     @Composable
 *     override fun Content() {
 *         val navigator = LocalNav3Navigator.current
 *         // ...
 *     }
 * }
 * ```
 *
 * ### Rules the compiler will not enforce
 *
 * - Concrete screens **must** be `@Serializable` — they ride the persisted back stack
 *   (`rememberNavBackStack`).
 * - Non-serializable body properties must be `get() =` computed (no backing field), like
 *   [metadata] overrides — kotlinx.serialization serializes stored body `val`s and will fail
 *   on lambda maps.
 * - Two pushes of an equal key (same `data object`, or data class with equal constructor args)
 *   share saved state + ViewModel store. Multi-instance screens need a distinguishing
 *   constructor arg — the same reason Voyager screens carry `uniqueScreenKey`.
 *
 * @see Nav3ScreenHost
 * @see Nav3Navigator
 * @see nav3ScreenEntry
 */
interface Nav3Screen : NavKey {
	/**
	 * Per-screen [androidx.navigation3.ui.NavDisplay] metadata, e.g. transition overrides via
	 * `NavDisplay.transitionSpec { ... }` / `NavDisplay.popTransitionSpec { ... }` keys.
	 *
	 * Override as a **computed** property (`get() = mapOf(...)`) so kotlinx.serialization does
	 * not try to persist the map.
	 */
	val metadata: Map<String, Any>
		get() = emptyMap()

	/**
	 * Composable UI for this destination. Called by [nav3ScreenEntry] when this key is on the
	 * back stack. Read [LocalNav3Navigator] here for push/pop — no host-threaded lambdas.
	 */
	@Composable
	fun Content()
}

/**
 * The entire `entryProvider` for a back stack of [Nav3Screen]s: cast the key and let it render
 * itself, forwarding [Nav3Screen.metadata] onto the [NavEntry].
 *
 * Pass this (or `::nav3ScreenEntry`) to [androidx.navigation3.ui.NavDisplay]. Prefer
 * [Nav3ScreenHost], which wires this plus [LocalNav3Navigator] and default entry decorators.
 *
 * @param key the back-stack key; **must** be a [Nav3Screen].
 * @return a [NavEntry] whose content invokes [Nav3Screen.Content].
 * @throws IllegalStateException if [key] is not a [Nav3Screen].
 */
fun nav3ScreenEntry(key: NavKey): NavEntry<NavKey> {
	val screen = key as? Nav3Screen
		?: error("Every key on this back stack must be a Nav3Screen, got $key")
	return NavEntry(
		key = key,
		metadata = screen.metadata,
	) {
		screen.Content()
	}
}
