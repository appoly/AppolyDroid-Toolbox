package uk.co.appoly.droid.nav3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec

/**
 * The standard entry decorators for a [Nav3ScreenHost] / [Nav3TabsHost] back stack:
 * - saveable state (`rememberSaveable` per entry)
 * - a [androidx.lifecycle.ViewModelStore] per entry
 * - the navigation result bus ([androidx.navigation3.runtime.result.ResultEventBus])
 *
 * Start from this list and append when a host needs an extra decorator. Omit or replace entries
 * when you deliberately want a slimmer stack (e.g. no ViewModels on a simple picker).
 *
 * When [retentionScope] is non-null, the ViewModel decorator is parented to that owner so
 * [Nav3RetentionScope.clear] can tear down retained entry stores. Pass the same scope a
 * [Nav3TabsHost] uses — tabs deliberately keep keys on the back stack, so without a clearable
 * parent those stores outlive the session. When `null`, falls back to the ambient
 * [LocalViewModelStoreOwner] (typically the Activity) — fine for plain stacks that pop on
 * sign-out, a footgun for retained tabs.
 *
 * **Note:** the result-bus decorator requires `androidx.navigation3` **1.2** alphas; it is not
 * on the 1.1 stable line.
 *
 * ### Replicating this in a custom decorator list
 *
 * Parenting the ViewModel decorator to the scope is necessary but **not sufficient**. The store
 * provider must also be keyed on [Nav3RetentionScope.generation], as below — otherwise
 * [Nav3RetentionScope.clear] silently fails to tear down the entry that is composed at the moment
 * of sign-out, because `ViewModelStoreProvider` defers cleanup of any entry still holding a
 * reference token. Copy the `rememberViewModelStoreProvider` call verbatim, or start from this
 * function's result and append.
 *
 * @param retentionScope optional [ViewModelStoreOwner] that parents per-entry ViewModel stores
 *   (typically a [Nav3RetentionScope]). `null` keeps the previous ambient-parent behaviour.
 */
@Composable
fun rememberDefaultNav3EntryDecorators(
	retentionScope: ViewModelStoreOwner? = null,
): List<NavEntryDecorator<NavKey>> {
	// Resolve the owner first so composition structure is stable across null/non-null —
	// a conditional around two remembers would insert/remove slots when the arg changes.
	val owner = retentionScope
		?: checkNotNull(LocalViewModelStoreOwner.current) {
			"No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
		}
	// Key on call site *and* the scope's generation. The call-site half reproduces the default
	// overload's behaviour (sibling hosts under one owner stay isolated); the generation half is
	// what makes Nav3RetentionScope.clear() actually bite — see Nav3RetentionScope.generation.
	val viewModelStoreProvider = rememberViewModelStoreProvider(
		key = currentCompositeKeyHashCode to (owner as? Nav3RetentionScope)?.generation,
		parent = owner,
	)
	return listOf(
		rememberSaveableStateHolderNavEntryDecorator(),
		rememberViewModelStoreNavEntryDecorator(viewModelStoreProvider),
		rememberResultEventBusNavEntryDecorator(),
	)
}

/**
 * A [NavDisplay] for a back stack of self-rendering [Nav3Screen]s, with [LocalNav3Navigator]
 * provided so screens push/pop directly instead of receiving host-threaded lambdas. Also
 * provides [LocalNav3HostViewModelStoreOwner] (the pre-decorator owner) so screens can resolve
 * navigator-scoped ViewModels via [nav3HostViewModelStoreOwner].
 *
 * Forwards the full primary [NavDisplay] parameter surface (entry/scene decorators, shared
 * transitions, transition specs, etc.) so advanced Nav3 features stay available without dropping
 * down to a raw [NavDisplay] call.
 *
 * The back stack stays the caller's list: seed it via
 * [androidx.navigation3.runtime.rememberNavBackStack] (a deep link that lands 3 screens deep
 * is just a start stack of 3 screens), mutate it through the navigator. The back stack must be
 * non-empty — [NavDisplay] requires it.
 *
 * ### Nav3 wins preserved
 *
 * - **Predictive back is native** — [predictivePopTransitionSpec] is scrubbed by the gesture.
 * - **Per-screen state / save / ViewModel** stay explicit, opt-in [entryDecorators]
 *   (defaulting to [rememberDefaultNav3EntryDecorators] with optional [retentionScope]).
 * - **Per-screen transition overrides** ride [Nav3Screen.metadata].
 *
 * ### Retention scope (optional here)
 *
 * [retentionScope] is optional on a plain stack host: sign-out via `replaceAll(LoginScreen)`
 * **pops while the host stays composed**, so the ViewModel decorator's `onPop` clears stores
 * naturally. Only a host that is **disposed without popping** (e.g. swapped out by a
 * `Crossfade` for the unauthenticated tree) can leak entry stores into the Activity-scoped
 * provider — pass a [Nav3RetentionScope] and [Nav3RetentionScope.clear] it on that boundary.
 * Tabs are different: [Nav3TabsHost] deliberately manufactures un-popped retention, so the
 * scope is **required** there.
 *
 * ### Minimal host
 *
 * ```kotlin
 * val backStack = rememberNavBackStack(HomeScreen)
 * Nav3ScreenHost(
 *     modifier = Modifier.fillMaxSize(),
 *     backStack = backStack,
 * )
 * ```
 *
 * @param modifier the modifier applied to the underlying [NavDisplay].
 * @param backStack the caller-owned stack of [Nav3Screen]s (typed [NavKey] to match
 *   `rememberNavBackStack`; every element must be a [Nav3Screen] when using the default
 *   [entryProvider]). Must not be empty.
 * @param navigator the [Nav3Navigator] provided as [LocalNav3Navigator]. When `null` (default),
 *   a [BackStackNav3Navigator] is remembered with [Nav3Navigator.parent] set to the ambient
 *   [LocalNav3Navigator] (so nested hosts get a parent chain automatically). Pass an explicit
 *   navigator to intercept navigation or share one instance across hosts.
 * @param retentionScope optional owner for per-entry ViewModel stores. When non-null, the
 *   default [entryDecorators] parent the ViewModel decorator to it so [Nav3RetentionScope.clear]
 *   can tear them down. See “Retention scope” above for when this matters on a plain stack.
 * @param contentAlignment the [Alignment] of the underlying `AnimatedContent`.
 * @param onBack handler for system back, defaulting to [Nav3Navigator.pop] on the resolved
 *   navigator (preferred over raw list mutation so nested / custom navigators stay consistent).
 * @param entryDecorators decorators adding information to each entry's content; defaults to
 *   [rememberDefaultNav3EntryDecorators] with [retentionScope]. A custom list that still uses
 *   a ViewModel decorator **must** parent that decorator to the same [retentionScope], or the
 *   clear handle is theatre.
 * @param sceneStrategies strategies deciding which scene renders a list of entries,
 *   tried in order (falling back to single-pane).
 * @param sceneDecoratorStrategies strategies that decorate whole scenes (shared chrome / shared
 *   state across entries in a scene); defaults to none.
 * @param sharedTransitionScope optional [SharedTransitionScope] so scenes can participate in
 *   shared-element transitions; pass a scope from a parent `SharedTransitionLayout` when needed.
 * @param sizeTransform the [SizeTransform] for the underlying `AnimatedContent`.
 * @param transitionSpec default [ContentTransform] when pushing screens.
 * @param popTransitionSpec default [ContentTransform] when popping screens.
 * @param predictivePopTransitionSpec default [ContentTransform] for predictive-back pops;
 *   receives the gesture's swipe edge.
 * @param entryProvider maps each back-stack key to a [NavEntry]. **Defaults to
 *   [nav3ScreenEntry]** (fused [Nav3Screen] rendering). Override only for mixed stacks or
 *   advanced cases — a custom provider can break the “key is the screen” contract.
 *
 * @see Nav3Screen
 * @see Nav3Navigator
 * @see nav3ScreenEntry
 * @see rememberDefaultNav3EntryDecorators
 * @see Nav3RetentionScope
 */
@Composable
fun Nav3ScreenHost(
	modifier: Modifier = Modifier,
	backStack: NavBackStack<NavKey>,
	navigator: Nav3Navigator? = null,
	retentionScope: ViewModelStoreOwner? = null,
	contentAlignment: Alignment = Alignment.TopStart,
	onBack: (() -> Unit)? = null,
	entryDecorators: List<NavEntryDecorator<NavKey>> =
		rememberDefaultNav3EntryDecorators(retentionScope),
	sceneStrategies: List<SceneStrategy<NavKey>> = listOf(SinglePaneSceneStrategy()),
	sceneDecoratorStrategies: List<SceneDecoratorStrategy<NavKey>> = emptyList(),
	sharedTransitionScope: SharedTransitionScope? = null,
	sizeTransform: SizeTransform? = null,
	transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
		defaultTransitionSpec(),
	popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
		defaultPopTransitionSpec(),
	predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
		defaultPredictivePopTransitionSpec(),
	entryProvider: (key: NavKey) -> NavEntry<NavKey> = ::nav3ScreenEntry,
) {
	val ambientParent = LocalNav3Navigator.current
	val resolvedNavigator = navigator
		?: remember(backStack, ambientParent) {
			BackStackNav3Navigator(backStack, parent = ambientParent)
		}
	val resolvedOnBack = onBack ?: { resolvedNavigator.pop() }
	// Captured pre-decorator: inside entries the ViewModelStore decorator shadows
	// LocalViewModelStoreOwner, so this is the only route back to the host's owner.
	val hostViewModelStoreOwner = LocalViewModelStoreOwner.current

	CompositionLocalProvider(
		LocalNav3Navigator provides resolvedNavigator,
		LocalNav3HostViewModelStoreOwner provides hostViewModelStoreOwner,
	) {
		NavDisplay(
			backStack = backStack,
			modifier = modifier,
			contentAlignment = contentAlignment,
			onBack = resolvedOnBack,
			entryDecorators = entryDecorators,
			sceneStrategies = sceneStrategies,
			sceneDecoratorStrategies = sceneDecoratorStrategies,
			sharedTransitionScope = sharedTransitionScope,
			sizeTransform = sizeTransform,
			transitionSpec = transitionSpec,
			popTransitionSpec = popTransitionSpec,
			predictivePopTransitionSpec = predictivePopTransitionSpec,
			entryProvider = entryProvider,
		)
	}
}

/**
 * Convenience host for [TabsNav3Navigator]: same as [Nav3ScreenHost] but also provides
 * [LocalTabsNavigator], wires [TabsNav3Navigator.backStack] / [navigator], and defaults
 * transition specs to the tab-aware helpers ([TabsNav3Navigator.transitionSpec],
 * [TabsNav3Navigator.popTransitionSpec], [TabsNav3Navigator.predictivePopTransitionSpec]).
 *
 * Defaults [sceneStrategies] to [TabsSceneStrategy] so only the current tab's top entry is
 * rendered while visited tabs stay in the back stack — that is what retains ViewModels and
 * `rememberSaveable` state across tab switches. Callers can still pass a custom list.
 *
 * ### Retention scope is required
 *
 * Tabs deliberately keep visited keys on the back stack, so Nav3 never fires `onPop` for them
 * on tab switch — and stable tab-root `contentKey`s would otherwise reattach a later user of
 * the same Activity to the previous user's ViewModels. [retentionScope] names who owns that
 * retention; call [Nav3RetentionScope.clear] when the identity ends (sign-out / account switch).
 * Create it with [rememberNav3RetentionScope]. This is a deliberate breaking choice on an
 * unreleased API: a host that manufactures retention must not be constructible without the
 * caller deciding who ends it.
 *
 * Bottom-bar chrome stays app-owned — only ambient navigators + display are handled here.
 *
 * ```kotlin
 * val retentionScope = rememberNav3RetentionScope()
 * val tabs = rememberTabsNav3Navigator(listOf(HomeTab, RoomsTab, SettingsTab))
 * // on sign-out: retentionScope.clear()
 * Scaffold(
 *     bottomBar = { /* tabs.currentTab / tabs.switchTab */ },
 * ) { padding ->
 *     Nav3TabsHost(
 *         modifier = Modifier.padding(padding),
 *         tabsNavigator = tabs,
 *         retentionScope = retentionScope,
 *     )
 * }
 * // In a tab page: LocalTabsNavigator.current?.navigateToTab(RoomsTab, Detail(...))
 * // Dismiss whole shell: LocalNav3Navigator.current?.parent?.pop()
 * ```
 *
 * @param tabsNavigator the tabs navigator; provided as both [LocalNav3Navigator] and
 *   [LocalTabsNavigator], and used as the [Nav3ScreenHost] back stack / onBack source.
 *   Create with [rememberTabsNav3Navigator] so [Nav3Navigator.parent] points at the outer host.
 * @param retentionScope owns the ViewModel stores of retained tab entries. Required — call
 *   [Nav3RetentionScope.clear] on sign-out / account switch. See “Retention scope is required”.
 * @param contentAlignment the [Alignment] of the underlying `AnimatedContent`.
 * @param onBack handler for system back; defaults to [TabsNav3Navigator.pop].
 * @param entryDecorators defaults to [rememberDefaultNav3EntryDecorators] parented to
 *   [retentionScope]. **Custom lists must build their ViewModel decorator from the same
 *   [retentionScope]** — otherwise [Nav3RetentionScope.clear] does not tear down entry stores
 *   and the required parameter is theatre.
 * @param sceneStrategies defaults to a remembered [TabsSceneStrategy] for per-tab state
 *   retention; override for multi-pane / custom scenes (you lose retention unless your strategy
 *   still keeps inactive tabs' keys in the back stack without rendering them).
 * @param transitionSpec defaults to tab-slide when [TabsNav3Navigator.pendingTabSlide] is set,
 *   otherwise [Nav3Transitions.springSlidePush] using [TabsNav3Navigator.currentTabDepth].
 * @param popTransitionSpec same as [transitionSpec] for pops.
 * @param predictivePopTransitionSpec infers exit-through-home tab-slide at non-start tab roots;
 *   otherwise spring-slide pop.
 *
 * @see TabsNav3Navigator
 * @see TabsSceneStrategy
 * @see LocalTabsNavigator
 * @see Nav3ScreenHost
 * @see Nav3Transitions
 * @see Nav3RetentionScope
 * @see rememberNav3RetentionScope
 */
@Composable
fun Nav3TabsHost(
	modifier: Modifier = Modifier,
	tabsNavigator: TabsNav3Navigator,
	retentionScope: Nav3RetentionScope,
	contentAlignment: Alignment = Alignment.TopStart,
	onBack: () -> Unit = { tabsNavigator.pop() },
	entryDecorators: List<NavEntryDecorator<NavKey>> =
		rememberDefaultNav3EntryDecorators(retentionScope),
	sceneStrategies: List<SceneStrategy<NavKey>> = remember(tabsNavigator) {
		listOf(TabsSceneStrategy(tabsNavigator))
	},
	sceneDecoratorStrategies: List<SceneDecoratorStrategy<NavKey>> = emptyList(),
	sharedTransitionScope: SharedTransitionScope? = null,
	sizeTransform: SizeTransform? = null,
	transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
		tabsNavigator.transitionSpec(),
	popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
		tabsNavigator.popTransitionSpec(),
	predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
		tabsNavigator.predictivePopTransitionSpec(),
	entryProvider: (key: NavKey) -> NavEntry<NavKey> = ::nav3ScreenEntry,
) {
	CompositionLocalProvider(LocalTabsNavigator provides tabsNavigator) {
		Nav3ScreenHost(
			modifier = modifier,
			backStack = tabsNavigator.backStack,
			navigator = tabsNavigator,
			retentionScope = retentionScope,
			contentAlignment = contentAlignment,
			onBack = onBack,
			entryDecorators = entryDecorators,
			sceneStrategies = sceneStrategies,
			sceneDecoratorStrategies = sceneDecoratorStrategies,
			sharedTransitionScope = sharedTransitionScope,
			sizeTransform = sizeTransform,
			transitionSpec = transitionSpec,
			popTransitionSpec = popTransitionSpec,
			predictivePopTransitionSpec = predictivePopTransitionSpec,
			entryProvider = entryProvider,
		)
	}
}
