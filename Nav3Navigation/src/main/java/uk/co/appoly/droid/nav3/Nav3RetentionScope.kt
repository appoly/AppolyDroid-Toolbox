package uk.co.appoly.droid.nav3

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Owns the [ViewModelStore]s of the entries a host retains, and the handle that ends them.
 *
 * `Nav3TabsHost` keeps every visited tab's entries on the back stack so tab state survives tab
 * switches. Nav3 only clears an entry's `ViewModelStore` when its key leaves the back stack, so
 * retained entries are never torn down by navigation alone — and because tab roots are stable
 * keys, a later user of the same Activity would otherwise reattach to the previous user's
 * ViewModels. Retention therefore needs an explicit owner, and an explicit end.
 *
 * Being a [ViewModel] gives it the only correct lifetime: it survives configuration change (which
 * is why the host cannot simply clear on disposal — the two are indistinguishable in composition)
 * and dies with its [ViewModelStoreOwner].
 *
 * **Call [clear] when the identity behind this UI ends** — sign-out, account switch, or anywhere
 * the retained state must not outlive what produced it.
 */
class Nav3RetentionScope : ViewModel(), ViewModelStoreOwner {

	override val viewModelStore: ViewModelStore = ViewModelStore()

	/**
	 * Incremented by [clear] **and** by [onCleared]. Hosts fold this into the key of the
	 * [androidx.lifecycle.viewmodel.ViewModelStoreProvider] that parents their per-entry stores,
	 * so a clear forces a brand-new provider rather than reusing the cleared one.
	 *
	 * **This is load-bearing, not bookkeeping.** Clearing the store alone is not sufficient:
	 * `ViewModelStoreProvider`'s internal `StateHolder.onCleared()` deliberately refuses to tear
	 * down any entry whose reference count is above zero, and the entry composed on screen at the
	 * moment of sign-out always holds a live token. Without a key change that entry's ViewModels —
	 * the ones belonging to the screen the user was just looking at — would survive [clear].
	 *
	 * Recreating the provider disposes the old one, which drops those tokens and lets the
	 * deferred cleanup run. It also re-parents a fresh `StateHolder` into the (now empty) store;
	 * the old holder is detached by `clear()` and would otherwise never be cleared again.
	 *
	 * @see rememberDefaultNav3EntryDecorators
	 */
	var generation: Int by mutableIntStateOf(0)
		private set

	/**
	 * Ends retention: clears every retained entry [ViewModelStore] held under this scope and
	 * invalidates the provider that owns them.
	 *
	 * Safe to call more than once, and the scope stays usable afterwards — the next composition
	 * parents a fresh provider into the empty store, so retention resumes for the new session.
	 * Prefer calling this as the identity ends (sign-out / account switch).
	 *
	 * You do **not** have to call this by hand if the scope is nested in an owner you clear
	 * yourself (a session-scoped [ViewModelStoreOwner], say): destroying that owner routes through
	 * [onCleared], which is equivalent.
	 *
	 * Must be called from the main thread: [generation] is Compose state read during composition.
	 */
	@MainThread
	fun clear() {
		viewModelStore.clear()
		generation++
	}

	/**
	 * Equivalent to [clear] — deliberately, not incidentally.
	 *
	 * A consumer who nests this scope inside their own [ViewModelStoreOwner] and clears *that*
	 * on sign-out never calls [clear]; they arrive here instead. Bumping [generation] on only one
	 * of the two routes would leave that consumer with the exact defect [generation] exists to
	 * prevent — the on-screen tab's ViewModels surviving sign-out — while the hidden tabs cleared
	 * normally, which is precisely the shape that looks like it works.
	 */
	override fun onCleared() {
		viewModelStore.clear()
		generation++
		super.onCleared()
	}
}

/**
 * Remembers a [Nav3RetentionScope] against the ambient
 * [androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner], so it survives configuration
 * change. Pass [key] to keep sibling hosts' retained state separate.
 *
 * ### Which owner you actually get
 *
 * Whatever `LocalViewModelStoreOwner` resolves to at the call site — **not** always the Activity:
 *
 * - Called from Activity content (tabs are the app root) → Activity-scoped. It outlives every
 *   host, so [clear] is the *only* thing that ends retention.
 * - Called inside a screen hosted by a [Nav3ScreenHost] → scoped to **that screen's entry**,
 *   because [rememberDefaultNav3EntryDecorators]' ViewModel decorator provides a per-entry owner.
 *   The scope then dies with the entry, so popping the tab shell tears retention down for free.
 *
 * The second case is usually what you want, but do not rely on it for a security boundary:
 * a shell that stays on the back stack across sign-out still holds the previous identity's
 * ViewModels until you call [clear].
 *
 * ```kotlin
 * val retentionScope = rememberNav3RetentionScope()
 * // … on sign-out:
 * retentionScope.clear()
 * ```
 *
 * @param key optional key so two sibling hosts under the same owner do not share one scope.
 * @return a [Nav3RetentionScope] retained across configuration changes for the ambient owner.
 */
@Composable
fun rememberNav3RetentionScope(key: String? = null): Nav3RetentionScope =
	viewModel(key = key)
