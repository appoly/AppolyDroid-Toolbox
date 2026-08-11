package uk.co.appoly.droid.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * The [ViewModelStoreOwner] the nearest [Nav3ScreenHost] was composed under — captured
 * **before** the per-entry ViewModelStore decorator shadows [LocalViewModelStoreOwner] inside
 * each screen. This is Voyager's navigator-scoped ScreenModel
 * (`koinNavigatorScreenModel()`), rebuilt on Nav3 scoping:
 *
 * - Host at the activity root → the activity: ViewModels resolved against it are shared
 *   across the whole stack and live as long as the activity.
 * - Nested host inside a screen (checkout flow, tab shell) → that screen's **entry** store:
 *   ViewModels are shared across the nested stack and `onCleared()` when the hosting screen
 *   pops — the flow's lifetime, not the activity's.
 *
 * Nullable so composables degrade gracefully in `@Preview`s / outside a host — prefer
 * [nav3HostViewModelStoreOwner] in screens for a non-null read with a useful error.
 *
 * @see nav3HostViewModelStoreOwner
 * @see Nav3ScreenHost
 */
val LocalNav3HostViewModelStoreOwner = staticCompositionLocalOf<ViewModelStoreOwner?> { null }

/**
 * The nearest host's [ViewModelStoreOwner], for navigator-scoped ViewModels shared across
 * every screen in that host's stack:
 *
 * ```kotlin
 * val vm: CheckoutViewModel = koinViewModel(
 *     viewModelStoreOwner = nav3HostViewModelStoreOwner(),
 * )
 * ```
 *
 * Two sibling hosts composed under the same owner share the same store — pass a distinct
 * `key` to `viewModel()` / `koinViewModel()` when the same ViewModel class must stay
 * per-host.
 *
 * @return the owner captured by the nearest enclosing [Nav3ScreenHost] / [Nav3TabsHost].
 * @throws IllegalStateException when called outside a [Nav3ScreenHost].
 */
@Composable
fun nav3HostViewModelStoreOwner(): ViewModelStoreOwner =
	checkNotNull(LocalNav3HostViewModelStoreOwner.current) {
		"No Nav3ScreenHost above this composable — nav3HostViewModelStoreOwner() must be " +
			"called from a screen hosted in a Nav3ScreenHost / Nav3TabsHost."
	}
