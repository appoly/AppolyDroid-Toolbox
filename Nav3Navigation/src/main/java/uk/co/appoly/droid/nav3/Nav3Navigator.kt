package uk.co.appoly.droid.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Voyager's `LocalNavigator` ergonomics rebuilt on Nav3: screens grab [LocalNav3Navigator] and
 * push/pop directly instead of threading lambdas from the host.
 *
 * An interface so nested containers can re-provide it over their own stack — a tab container
 * or nested [Nav3ScreenHost] overrides it inside the child, exactly like Voyager's nested
 * navigators resolve to the innermost one. The default implementation is
 * [BackStackNav3Navigator].
 *
 * ### Nested navigators and [parent]
 *
 * When a host re-provides [LocalNav3Navigator], the outer navigator is shadowed. Pass the
 * outer instance as [parent] (or use the host defaults / [rememberTabsNav3Navigator]) so deep
 * screens can still reach up:
 *
 * ```kotlin
 * // Inside a nested flow hosted under the root:
 * LocalNav3Navigator.current?.parent?.pop()   // pop the outer stack
 * LocalNav3Navigator.current?.root()?.replaceAll(LoginScreen)
 * ```
 *
 * Prefer tab APIs ([LocalTabsNavigator] / [TabsNav3Navigator.navigateToTab]) for cross-tab
 * work — [parent] is an escape hatch for outer-stack control (dismiss nested flow, logout),
 * not everyday navigation.
 *
 * @see LocalNav3Navigator
 * @see BackStackNav3Navigator
 * @see Nav3ScreenHost
 * @see popWithResult
 * @see root
 */
interface Nav3Navigator {
	/**
	 * The navigator that nested this one, or `null` at the root.
	 *
	 * Set at construction (e.g. [BackStackNav3Navigator] / [TabsNav3Navigator] `parent` param,
	 * or via [Nav3ScreenHost] / [rememberTabsNav3Navigator] wiring the ambient navigator).
	 */
	val parent: Nav3Navigator?

	// --- stack mutation (Voyager Navigator surface) ---

	/**
	 * Pushes [screen] onto the back stack (navigates forward).
	 *
	 * @param screen the destination to show.
	 */
	fun push(screen: Nav3Screen)

	/**
	 * Pushes several screens in order — useful for building a deep stack in one call
	 * (deep-link simulation at runtime).
	 *
	 * @param screens destinations appended from first to last (last ends up on top).
	 */
	fun push(vararg screens: Nav3Screen)

	/**
	 * Pushes every screen in [screens] in iteration order (last ends up on top).
	 * Handy when a deep-link router already holds a [List].
	 */
	fun push(screens: Iterable<Nav3Screen>)

	/**
	 * Pops the top screen when there is a previous entry ([canPop] is `true`).
	 * No-op when the stack has zero or one entries — the root is never removed via [pop]
	 * (matches [TabsNav3Navigator] and Voyager). To replace or clear the root explicitly, use
	 * [replaceAll] or mutate the underlying list.
	 */
	fun pop()

	/**
	 * Replaces the top screen with [screen] (Voyager `replace`). No-op if the stack is empty
	 * — use [push] or [replaceAll] to seed an empty stack.
	 */
	fun replace(screen: Nav3Screen)

	/**
	 * Clears the stack and pushes [screen] as the sole entry (Voyager `replaceAll` with one
	 * destination). Use after flows that must not leave intermediate screens on the stack
	 * (day-setup reset, auth handoff, cross-tab Replace mode).
	 */
	fun replaceAll(screen: Nav3Screen)

	/**
	 * Clears the stack and replaces it with [screens] in order (last on top).
	 * No-op when [screens] is empty (keeps the current stack — avoid accidentally wiping).
	 */
	fun replaceAll(vararg screens: Nav3Screen)

	/**
	 * Pops until [screen] is on top — Voyager's key-based pop / Nav2's `popUpTo`.
	 *
	 * Uses [Any.equals] to find the **last** matching key on the stack. If no match is found,
	 * the stack is left unchanged.
	 *
	 * @param screen the key to leave on top (or remove when [inclusive] is true).
	 * @param inclusive when `true`, also removes the matching [screen]; when `false` (default),
	 *   leaves it as the new top.
	 * @return `true` if [screen] was found (stack may still be unchanged when it was already
	 *   on top and [inclusive] is false); `false` if no match.
	 */
	fun popUpTo(screen: Nav3Screen, inclusive: Boolean = false): Boolean

	/**
	 * Pops until the **last** screen matching [predicate] is on top (Voyager `popUntil`).
	 *
	 * If no entry matches, the stack is left unchanged.
	 *
	 * @param inclusive when `true`, also removes the matching screen; when `false` (default),
	 *   leaves it as the new top.
	 * @param predicate match against each [Nav3Screen] on the stack (non-[Nav3Screen] keys are
	 *   skipped).
	 * @return `true` if a match was found; `false` if the stack was left unchanged because
	 *   nothing matched. Callers that deliver side effects (e.g. [popUntilWithResult]) should
	 *   gate on this return value.
	 */
	fun popUntil(inclusive: Boolean = false, predicate: (Nav3Screen) -> Boolean): Boolean

	/**
	 * Pops until only the root (first) screen remains. No-op when the stack has 0–1 entries.
	 * Voyager's `popUntilRoot`.
	 */
	fun popUntilRoot()

	// --- stack introspection (bottom bar, BackHandler, deep-link reconcile) ---

	/**
	 * `true` when there is a previous screen to pop to (stack size &gt; 1) — Voyager's `canPop`.
	 * Use with system [androidx.activity.compose.BackHandler]: pop when `canPop`, otherwise
	 * switch tab / finish the activity.
	 */
	val canPop: Boolean

	/**
	 * The top of the stack, or `null` when empty. Drive bottom-bar visibility from this
	 * (`shouldShowBottomNav` / `HidesBottomBar` patterns).
	 */
	val lastItem: Nav3Screen?

	/**
	 * The screen under the top, or `null` when size &lt; 2. Used by [popWithResult] to deliver
	 * a result to the destination that will become visible after pop.
	 */
	val previousItem: Nav3Screen?

	/**
	 * Snapshot of the current stack as [Nav3Screen]s (non-[Nav3Screen] keys are omitted).
	 * Deep-link routers can inspect this to skip screens already present when reconciling a
	 * target stack.
	 */
	val items: List<Nav3Screen>
}

/**
 * Walks [Nav3Navigator.parent] until the outermost navigator (Voyager-style root).
 * Returns `this` when [Nav3Navigator.parent] is `null`.
 */
fun Nav3Navigator.root(): Nav3Navigator =
	generateSequence(this) { it.parent }.last()

/**
 * Ambient [Nav3Navigator] for the current composition.
 *
 * Nullable so composables can degrade gracefully in `@Preview`s with no host — read with
 * `LocalNav3Navigator.current?.push(...)` where a host isn't guaranteed. [Nav3ScreenHost]
 * always provides a non-null value for its content subtree.
 *
 * Prefer [currentOrThrow] inside a [Nav3ScreenHost] when a missing navigator is a programming
 * error rather than a preview/degraded path.
 */
val LocalNav3Navigator = staticCompositionLocalOf<Nav3Navigator?> { null }

/**
 * The ambient [Nav3Navigator], throwing when read outside a [Nav3ScreenHost].
 *
 * Mirrors Voyager's `LocalNavigator.currentOrThrow`. Prefer [LocalNav3Navigator.current]
 * (nullable) in composables that must also render in `@Preview` or outside a host.
 */
val ProvidableCompositionLocal<Nav3Navigator?>.currentOrThrow: Nav3Navigator
	@Composable
	get() = current
		?: error("No Nav3Navigator provided — is this composable inside a Nav3ScreenHost?")

/**
 * Root navigator: thin wrapper over the host's [NavBackStack] — navigation **is** list mutation.
 *
 * Constructed by default inside [Nav3ScreenHost]; pass a custom instance when you need to
 * intercept navigation (analytics, logging) or share one navigator across multiple hosts.
 *
 * @param backStack the caller-owned stack mutated by push/pop. Typed as [NavKey] to match
 *   `rememberNavBackStack`; every element should be a [Nav3Screen] for [nav3ScreenEntry].
 * @param parent the navigator that nested this one, or `null` at the app root. Nested hosts
 *   should pass [LocalNav3Navigator.current] from the outer composition (the default
 *   [Nav3ScreenHost] navigator does this automatically).
 */
class BackStackNav3Navigator(
	private val backStack: NavBackStack<NavKey>,
	override val parent: Nav3Navigator? = null,
) : Nav3Navigator {

	override fun push(screen: Nav3Screen) {
		backStack.add(screen)
	}

	override fun push(vararg screens: Nav3Screen) {
		backStack.addAll(screens)
	}

	override fun push(screens: Iterable<Nav3Screen>) {
		backStack.addAll(screens)
	}

	override fun pop() {
		// Never empty the stack — NavDisplay requires a non-empty back stack.
		if (backStack.size <= 1) return
		backStack.removeLastOrNull()
	}

	override fun replace(screen: Nav3Screen) {
		if (backStack.isEmpty()) return
		backStack.removeLastOrNull()
		backStack.add(screen)
	}

	override fun replaceAll(screen: Nav3Screen) {
		backStack.clear()
		backStack.add(screen)
	}

	override fun replaceAll(vararg screens: Nav3Screen) {
		if (screens.isEmpty()) return
		backStack.clear()
		backStack.addAll(screens)
	}

	override fun popUpTo(screen: Nav3Screen, inclusive: Boolean): Boolean =
		popUntil(inclusive = inclusive) { it == screen }

	override fun popUntil(inclusive: Boolean, predicate: (Nav3Screen) -> Boolean): Boolean {
		val index = backStack.indexOfLast { key ->
			val screen = key as? Nav3Screen ?: return@indexOfLast false
			predicate(screen)
		}
		if (index < 0) return false
		val targetSize = if (inclusive) index else index + 1
		while (backStack.size > targetSize) {
			backStack.removeLastOrNull()
		}
		return true
	}

	override fun popUntilRoot() {
		while (backStack.size > 1) {
			backStack.removeLastOrNull()
		}
	}

	override val canPop: Boolean
		get() = backStack.size > 1

	override val lastItem: Nav3Screen?
		get() = backStack.lastOrNull() as? Nav3Screen

	override val previousItem: Nav3Screen?
		get() = backStack.getOrNull(backStack.lastIndex - 1) as? Nav3Screen

	override val items: List<Nav3Screen>
		get() = backStack.mapNotNull { it as? Nav3Screen }
}

/**
 * Remembers a [BackStackNav3Navigator] for [backStack], wiring [BackStackNav3Navigator.parent]
 * from the current [LocalNav3Navigator] so nested hosts get a parent chain automatically.
 */
@Composable
fun rememberBackStackNav3Navigator(
	backStack: NavBackStack<NavKey>,
	parent: Nav3Navigator? = LocalNav3Navigator.current,
): BackStackNav3Navigator =
	remember(backStack, parent) { BackStackNav3Navigator(backStack, parent = parent) }
