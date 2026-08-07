package uk.co.appoly.droid.nav3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * The standard entry decorators for a [Nav3ScreenHost] back stack:
 * - saveable state (`rememberSaveable` per entry)
 * - a [androidx.lifecycle.ViewModelStore] per entry
 * - the navigation result bus ([androidx.navigation3.runtime.result.ResultEventBus])
 *
 * Start from this list and append when a host needs an extra decorator. Omit or replace entries
 * when you deliberately want a slimmer stack (e.g. no ViewModels on a simple picker).
 *
 * **Note:** the result-bus decorator requires `androidx.navigation3` **1.2** alphas; it is not
 * on the 1.1 stable line.
 */
@Composable
fun rememberDefaultNav3EntryDecorators(): List<NavEntryDecorator<NavKey>> = listOf(
	rememberSaveableStateHolderNavEntryDecorator(),
	rememberViewModelStoreNavEntryDecorator(),
	rememberResultEventBusNavEntryDecorator(),
)

/**
 * A [NavDisplay] for a back stack of self-rendering [Nav3Screen]s, with [LocalNav3Navigator]
 * provided so screens push/pop directly instead of receiving host-threaded lambdas.
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
 *   (defaulting to [rememberDefaultNav3EntryDecorators]).
 * - **Per-screen transition overrides** ride [Nav3Screen.metadata].
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
 * @param contentAlignment the [Alignment] of the underlying `AnimatedContent`.
 * @param onBack handler for system back, defaulting to [Nav3Navigator.pop] on the resolved
 *   navigator (preferred over raw list mutation so nested / custom navigators stay consistent).
 * @param entryDecorators decorators adding information to each entry's content; defaults to
 *   [rememberDefaultNav3EntryDecorators].
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
 */
@Composable
fun Nav3ScreenHost(
	modifier: Modifier = Modifier,
	backStack: NavBackStack<NavKey>,
	navigator: Nav3Navigator? = null,
	contentAlignment: Alignment = Alignment.TopStart,
	onBack: (() -> Unit)? = null,
	entryDecorators: List<NavEntryDecorator<NavKey>> = rememberDefaultNav3EntryDecorators(),
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

	CompositionLocalProvider(LocalNav3Navigator provides resolvedNavigator) {
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
 * Bottom-bar chrome stays app-owned — only ambient navigators + display are handled here.
 *
 * ```kotlin
 * val tabs = rememberTabsNav3Navigator(listOf(HomeTab, RoomsTab, SettingsTab))
 * Scaffold(
 *     bottomBar = { /* tabs.currentTab / tabs.switchTab */ },
 * ) { padding ->
 *     Nav3TabsHost(
 *         modifier = Modifier.padding(padding),
 *         tabsNavigator = tabs,
 *     )
 * }
 * // In a tab page: LocalTabsNavigator.current?.navigateToTab(RoomsTab, Detail(...))
 * // Dismiss whole shell: LocalNav3Navigator.current?.parent?.pop()
 * ```
 *
 * @param tabsNavigator the tabs navigator; provided as both [LocalNav3Navigator] and
 *   [LocalTabsNavigator], and used as the [Nav3ScreenHost] back stack / onBack source.
 *   Create with [rememberTabsNav3Navigator] so [Nav3Navigator.parent] points at the outer host.
 * @param transitionSpec defaults to tab-slide when [TabsNav3Navigator.pendingTabSlide] is set,
 *   otherwise [Nav3Transitions.springSlidePush].
 * @param popTransitionSpec same as [transitionSpec] for pops.
 * @param predictivePopTransitionSpec infers backward tab-slide at non-start tab roots;
 *   otherwise spring-slide pop.
 *
 * @see TabsNav3Navigator
 * @see LocalTabsNavigator
 * @see Nav3ScreenHost
 * @see Nav3Transitions
 */
@Composable
fun Nav3TabsHost(
	modifier: Modifier = Modifier,
	tabsNavigator: TabsNav3Navigator,
	contentAlignment: Alignment = Alignment.TopStart,
	onBack: () -> Unit = { tabsNavigator.pop() },
	entryDecorators: List<NavEntryDecorator<NavKey>> = rememberDefaultNav3EntryDecorators(),
	sceneStrategies: List<SceneStrategy<NavKey>> = listOf(SinglePaneSceneStrategy()),
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
