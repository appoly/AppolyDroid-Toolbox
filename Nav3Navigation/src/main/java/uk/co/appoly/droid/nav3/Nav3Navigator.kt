package uk.co.appoly.droid.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
	 * **Never empties the stack:** an [inclusive] match on the root leaves the root in place (and
	 * still returns `true`), because an [androidx.navigation3.ui.NavDisplay] back stack must stay
	 * non-empty.
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
	 * **Never empties the stack:** an [inclusive] match on the root leaves the root in place (and
	 * still returns `true`), because an [androidx.navigation3.ui.NavDisplay] back stack must stay
	 * non-empty.
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

	// --- stack introspection (bottom bar, back-enablement, deep-link reconcile) ---

	/**
	 * `true` when there is a previous screen to pop to (stack size &gt; 1) — Voyager's `canPop`.
	 *
	 * Read this for UI decisions (an up arrow, a back-enabled check). **Do not** drive system back
	 * from it via [androidx.activity.compose.BackHandler]: [Nav3ScreenHost] already routes
	 * `NavDisplay.onBack` to [pop], and a handler above the host intercepts the gesture before
	 * `NavDisplay` sees it, defeating the predictive-back scrub. When `canPop` is `false` Nav3
	 * disables its back callback so the Activity finishes as usual.
	 *
	 * To intercept back on a single screen (e.g. an unsaved-changes prompt), use
	 * `NavigationBackHandler` from `androidx.navigationevent:navigationevent-compose` inside that
	 * screen's content — it shares `NavDisplay`'s dispatcher and keeps gesture progress.
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

	// --- deep links ---

	/**
	 * The [Nav3Continuation]s pending for entries on this navigator's stack. Delivered by
	 * [navigateToDeepLink] and [push] with a `continuation`; read with [rememberNav3Continuation]
	 * or [Nav3Continuations.consume].
	 *
	 * A custom navigator that wraps another should return the wrapped navigator's store. One that
	 * owns its stack should keep a [Nav3Continuations] in `rememberSaveable` (see
	 * [Nav3Continuations.saver]); hosting it in a [Nav3ScreenHost] drops continuations whose
	 * entry leaves the stack.
	 */
	val continuations: Nav3Continuations

	/**
	 * Lands [link] on this navigator's stack according to [Nav3DeepLink.mode]
	 * ([Nav3DeepLinkMode.Append] keeps the user's place, [Nav3DeepLinkMode.Reconcile] makes the
	 * stack exactly the link's). An equal key never ends up on the stack twice.
	 *
	 * [BackStackNav3Navigator] and [TabsNav3Navigator] override this; a [TabsNav3Navigator] also
	 * selects the tab the link starts in. The default implementation works through this
	 * interface's own [items], [pop], [push] and [replaceAll], so a custom navigator (an analytics
	 * wrapper, say) gets correct behaviour without overriding it. A wrapper around a
	 * [TabsNav3Navigator] should forward this call so the tab is selected too.
	 *
	 * A [Nav3DeepLink.continuation] is addressed to the link's top screen once it has landed.
	 */
	fun navigateToDeepLink(link: Nav3DeepLink) {
		val current = items
		val target = planDeepLinkStack(current, link.stack, link.mode).map { it as Nav3Screen }
		val common = commonPrefixLength(current, target)
		if (common == 0) {
			replaceAll(*target.toTypedArray())
		} else {
			while (items.size > common) {
				val before = items.size
				pop()
				if (items.size == before) break // pop() refused (e.g. at a root); never spin
			}
			push(target.subList(common, target.size))
		}
		link.continuation?.let { continuations.put(link.stack.last(), it) }
	}
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
 * @param continuations the store for [Nav3Continuation]s pending on this stack. Pass one kept in
 *   `rememberSaveable` (as [rememberBackStackNav3Navigator] does) so pending continuations survive
 *   process death.
 */
class BackStackNav3Navigator(
	private val backStack: NavBackStack<NavKey>,
	override val parent: Nav3Navigator? = null,
	override val continuations: Nav3Continuations = Nav3Continuations(),
) : Nav3Navigator {

	override fun push(screen: Nav3Screen) {
		backStack.add(screen)
		prune()
	}

	override fun push(vararg screens: Nav3Screen) {
		backStack.addAll(screens)
		prune()
	}

	override fun push(screens: Iterable<Nav3Screen>) {
		backStack.addAll(screens)
		prune()
	}

	override fun pop() {
		// Never empty the stack — NavDisplay requires a non-empty back stack.
		if (backStack.size <= 1) return
		backStack.removeLastOrNull()
		prune()
	}

	override fun replace(screen: Nav3Screen) {
		if (backStack.isEmpty()) return
		backStack.removeLastOrNull()
		backStack.add(screen)
		prune()
	}

	override fun replaceAll(screen: Nav3Screen) {
		backStack.clear()
		backStack.add(screen)
		prune()
	}

	override fun replaceAll(vararg screens: Nav3Screen) {
		if (screens.isEmpty()) return
		backStack.clear()
		backStack.addAll(screens)
		prune()
	}

	override fun popUpTo(screen: Nav3Screen, inclusive: Boolean): Boolean =
		popUntil(inclusive = inclusive) { it == screen }

	override fun popUntil(inclusive: Boolean, predicate: (Nav3Screen) -> Boolean): Boolean {
		val index = backStack.indexOfLast { key ->
			val screen = key as? Nav3Screen ?: return@indexOfLast false
			predicate(screen)
		}
		if (index < 0) return false
		// Never empty the stack — NavDisplay requires a non-empty back stack, so an inclusive
		// match on the root floors at 1 (same guard as pop() and TabsNav3Navigator.popUntil).
		val targetSize = (if (inclusive) index else index + 1).coerceAtLeast(1)
		while (backStack.size > targetSize) {
			backStack.removeLastOrNull()
		}
		prune()
		return true
	}

	override fun popUntilRoot() {
		while (backStack.size > 1) {
			backStack.removeLastOrNull()
		}
		prune()
	}

	override val canPop: Boolean
		get() = backStack.size > 1

	override val lastItem: Nav3Screen?
		get() = backStack.lastOrNull() as? Nav3Screen

	override val previousItem: Nav3Screen?
		get() = backStack.getOrNull(backStack.lastIndex - 1) as? Nav3Screen

	override val items: List<Nav3Screen>
		get() = backStack.mapNotNull { it as? Nav3Screen }

	override fun navigateToDeepLink(link: Nav3DeepLink) {
		backStack.morphInto(planDeepLinkStack(backStack.toList(), link.stack, link.mode))
		prune()
		link.continuation?.let { continuations.put(link.stack.last(), it) }
	}

	/** Drops continuations whose entry just left the stack, before anything can deliver them. */
	private fun prune() {
		continuations.retainOnly(backStack)
	}
}

/**
 * Remembers a [BackStackNav3Navigator] for [backStack], wiring [BackStackNav3Navigator.parent]
 * from the current [LocalNav3Navigator] so nested hosts get a parent chain automatically, and
 * keeping its [Nav3Continuations] across process death.
 */
@Composable
fun rememberBackStackNav3Navigator(
	backStack: NavBackStack<NavKey>,
	parent: Nav3Navigator? = LocalNav3Navigator.current,
): BackStackNav3Navigator {
	// Saved separately from the back stack (which the caller owns), so pending continuations
	// survive process death alongside it.
	val continuations = rememberSaveable(saver = Nav3Continuations.saver()) { Nav3Continuations() }
	return remember(backStack, parent, continuations) {
		BackStackNav3Navigator(backStack, parent = parent, continuations = continuations)
	}
}
