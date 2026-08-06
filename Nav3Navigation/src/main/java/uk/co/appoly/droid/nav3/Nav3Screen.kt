package uk.co.appoly.droid.nav3

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

/**
 * Voyager-style screens on Navigation 3: the nav key and its UI are the SAME class, so
 * `navigator.push(SomeScreen())` is cmd+B-navigable straight to the screen's code — no
 * key <-> entryProvider hop.
 *
 * This works because Nav3's `entryProvider` is just a `(NavKey) -> NavEntry` function;
 * [nav3ScreenEntry] collapses the mapping to "the key renders itself". Nav3's docs
 * favour the separated style so keys can live in a shared module while UI lives in
 * feature modules — irrelevant to a single-module app, so we get to choose ergonomics.
 *
 * Rules the compiler won't enforce for you:
 * - Concrete screens must be `@Serializable` (they ride the persisted back stack).
 * - Non-serializable body properties must be `get() =` computed (no backing field),
 *   like [metadata] overrides — kotlinx.serialization serializes stored body `val`s.
 * - Two pushes of the same `data object`/equal data class share saved state + ViewModel
 *   store; give multi-instance screens a distinguishing constructor arg (the same reason
 *   Voyager screens carry `uniqueScreenKey`).
 */
interface Nav3Screen : NavKey {
	/** Per-screen [androidx.navigation3.ui.NavDisplay] metadata, e.g. transition overrides. */
	val metadata: Map<String, Any>
		get() = emptyMap()

	@Composable
	fun Content()
}

/** The entire entryProvider for a stack of [Nav3Screen]s. */
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
