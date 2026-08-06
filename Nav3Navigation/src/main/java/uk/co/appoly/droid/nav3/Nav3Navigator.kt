package uk.co.appoly.droid.nav3

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Voyager's `LocalNavigator` ergonomics rebuilt on Nav3: screens grab
 * [LocalNav3Navigator] and push/pop directly instead of threading lambdas from the host.
 *
 * An interface so nested containers can re-provide it over their own stack — a tab
 * container overrides it inside each tab, exactly like Voyager's nested navigators
 * resolve to the innermost one.
 */
interface Nav3Navigator {
	fun push(screen: Nav3Screen)

	fun push(vararg screens: Nav3Screen)

	fun pop()

	/** Pops until [screen] is on top ([inclusive] also removes it) — Voyager's popUntil. */
	fun popUpTo(screen: Nav3Screen, inclusive: Boolean = false)
}

/**
 * Nullable so composables can degrade gracefully in @Previews with no host —
 * read with `LocalNav3Navigator.current?.push(...)` where a host isn't guaranteed.
 */
val LocalNav3Navigator = staticCompositionLocalOf<Nav3Navigator?> { null }

/** Root navigator: thin wrapper over the host's [NavBackStack] — navigation is list mutation. */
class BackStackNav3Navigator(
	private val backStack: NavBackStack<NavKey>,
) : Nav3Navigator {
	override fun push(screen: Nav3Screen) {
		backStack.add(screen)
	}

	override fun push(vararg screens: Nav3Screen) {
		backStack.addAll(screens)
	}

	override fun pop() {
		backStack.removeLastOrNull()
	}

	override fun popUpTo(screen: Nav3Screen, inclusive: Boolean) {
		val index = backStack.indexOfLast { it == screen }
		if (index < 0) return
		val targetSize = if (inclusive) index else index + 1
		while (backStack.size > targetSize) {
			backStack.removeLastOrNull()
		}
	}
}
