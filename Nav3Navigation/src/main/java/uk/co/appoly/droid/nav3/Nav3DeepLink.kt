package uk.co.appoly.droid.nav3

import androidx.navigation3.runtime.NavKey

/**
 * How [Nav3Navigator.navigateToDeepLink] lands a [Nav3DeepLink] on a stack that already has
 * screens on it.
 */
enum class Nav3DeepLinkMode {
	/**
	 * Keep the user's place (the default).
	 *
	 * If any screen of the link (other than a matching root) is already on the stack, the stack is
	 * popped back to the **last** such screen ("surface existing") and the rest of the link is
	 * pushed above it. Otherwise the link's screens are pushed on top of the current stack.
	 *
	 * An equal key never ends up on the stack twice. With a current stack `S`:
	 * - `S = [Root, A, B]`, link `[Root, C]` → `[Root, A, B, C]`
	 * - `S = [Root, A, D1, B]`, link `[Root, D1]` → `[Root, A, D1]`
	 * - `S = [Root, SB, X]`, link `[Root, SB, FD]` → `[Root, SB, FD]`
	 */
	Append,

	/**
	 * Make the stack exactly the link's stack.
	 *
	 * Screens shared with the current stack as a common prefix are kept (so they keep their
	 * saved state), everything above them is popped, and the rest of the link is pushed. A tab
	 * root is never popped. `S = [Root, A, B]`, link `[Root, C]` → `[Root, C]`.
	 */
	Reconcile,
}

/**
 * A resolved deep-link destination: the stack to show, and how to land it.
 *
 * Produced by [Nav3DeepLinkRouter.resolve] and consumed by [Nav3Navigator.navigateToDeepLink].
 *
 * With a [TabsNav3Navigator], a [stack] whose first screen is a tab root selects that tab; any
 * other first screen targets the current tab, with its root implied underneath.
 *
 * @param stack the screens to show, bottom first (the last one ends up on top). Must not be
 *   empty, and should not contain equal keys twice.
 * @param mode how to combine [stack] with what is already on the navigator. Defaults to
 *   [Nav3DeepLinkMode.Append].
 */
class Nav3DeepLink(
	val stack: List<Nav3Screen>,
	val mode: Nav3DeepLinkMode = Nav3DeepLinkMode.Append,
) {
	init {
		require(stack.isNotEmpty()) { "A Nav3DeepLink stack must not be empty" }
	}

	/** Convenience for a link to a single [screen] (the navigator supplies anything beneath it). */
	constructor(screen: Nav3Screen, mode: Nav3DeepLinkMode = Nav3DeepLinkMode.Append) :
		this(listOf(screen), mode)

	override fun equals(other: Any?): Boolean =
		other is Nav3DeepLink && stack == other.stack && mode == other.mode

	override fun hashCode(): Int = 31 * stack.hashCode() + mode.hashCode()

	override fun toString(): String = "Nav3DeepLink(stack=$stack, mode=$mode)"
}

/**
 * Computes the stack that results from landing [link] on [current] with [mode].
 *
 * Pure, so every navigator applies the same rule (see [Nav3DeepLinkMode] for the semantics and
 * examples). An empty [current] always yields [link].
 */
internal fun planDeepLinkStack(
	current: List<NavKey>,
	link: List<NavKey>,
	mode: Nav3DeepLinkMode,
): List<NavKey> {
	if (current.isEmpty()) return link
	return when (mode) {
		// The common prefix is kept by construction: S[0, p) == L[0, p), so the result is L.
		Nav3DeepLinkMode.Reconcile -> link
		Nav3DeepLinkMode.Append -> {
			// Surface the deepest screen of the link that is already open. A link root that matches
			// the current root doesn't count: sharing only the root means "push on top", not "pop
			// back to the root".
			for (k in link.indices.reversed()) {
				val i = current.lastIndexOf(link[k])
				if (i < 0 || (k == 0 && i == 0)) continue
				return current.subList(0, i + 1) + link.subList(k + 1, link.size)
			}
			// Nothing beyond a shared root is open: push the rest of the link on top.
			current + if (link.first() == current.first()) link.drop(1) else link
		}
	}
}

/**
 * Mutates this stack into [target] with the fewest removals: the common prefix stays in place
 * (keeping those entries' identity), the rest is popped and [target]'s remainder is pushed.
 */
internal fun MutableList<NavKey>.morphInto(target: List<NavKey>) {
	val common = commonPrefixLength(this, target)
	if (common == 0) {
		clear()
		addAll(target)
		return
	}
	while (size > common) removeAt(lastIndex)
	addAll(target.subList(common, target.size))
}

internal fun commonPrefixLength(a: List<Any?>, b: List<Any?>): Int {
	val max = minOf(a.size, b.size)
	var i = 0
	while (i < max && a[i] == b[i]) i++
	return i
}
