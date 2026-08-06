package uk.co.appoly.droid.nav3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec

/**
 * The standard entry decorators for a [Nav3ScreenHost] back stack: saveable state
 * (`rememberSaveable` per entry), a ViewModelStore per entry, and the navigation
 * result bus. Start from this list and append when a host needs an extra decorator.
 */
@Composable
fun rememberDefaultNav3EntryDecorators(): List<NavEntryDecorator<NavKey>> = listOf(
	rememberSaveableStateHolderNavEntryDecorator(),
	rememberViewModelStoreNavEntryDecorator(),
	rememberResultEventBusNavEntryDecorator(),
)

/**
 * A [NavDisplay] for a back stack of self-rendering [Nav3Screen]s, with
 * [LocalNav3Navigator] provided so screens push/pop directly instead of receiving
 * host-threaded lambdas.
 *
 * The back stack stays the caller's list: seed it via
 * [androidx.navigation3.runtime.rememberNavBackStack] (a deep link that lands 3 screens
 * deep is just a start stack of 3 screens), mutate it through the navigator.
 *
 * Nav3's wins are preserved untouched:
 * - Predictive back is native: [predictivePopTransitionSpec] is scrubbed by the gesture.
 * - Per-screen state/save/ViewModel scoping stay explicit, opt-in [entryDecorators]
 *   (defaulting to [rememberDefaultNav3EntryDecorators]).
 * - Per-screen transition overrides ride [Nav3Screen.metadata].
 *
 * @param modifier the modifier applied to the underlying [NavDisplay].
 * @param backStack the caller-owned stack of [Nav3Screen]s (typed [NavKey] to match
 *   `rememberNavBackStack`; every element must be a [Nav3Screen]).
 * @param navigator the [Nav3Navigator] provided as [LocalNav3Navigator]; override to
 *   intercept navigation (e.g. analytics) or share one navigator across hosts.
 * @param contentAlignment the [Alignment] of the underlying `AnimatedContent`.
 * @param entryDecorators decorators adding information to each entry's content.
 * @param sceneStrategies strategies deciding which scene renders a list of entries,
 *   tried in order (falling back to single-pane).
 * @param sizeTransform the [SizeTransform] for the underlying `AnimatedContent`.
 * @param transitionSpec default [ContentTransform] when pushing screens.
 * @param popTransitionSpec default [ContentTransform] when popping screens.
 * @param predictivePopTransitionSpec default [ContentTransform] for predictive-back
 *   pops; receives the gesture's swipe edge.
 * @param onBack handler for system back, defaulting to [Nav3Navigator.pop].
 */
@Composable
fun Nav3ScreenHost(
	modifier: Modifier = Modifier,
	backStack: NavBackStack<NavKey>,
	navigator: Nav3Navigator = remember(backStack) { BackStackNav3Navigator(backStack) },
	contentAlignment: Alignment = Alignment.TopStart,
	entryDecorators: List<NavEntryDecorator<NavKey>> = rememberDefaultNav3EntryDecorators(),
	sceneStrategies: List<SceneStrategy<NavKey>> = listOf(SinglePaneSceneStrategy()),
	sizeTransform: SizeTransform? = null,
	transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
		defaultTransitionSpec(),
	popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
		defaultPopTransitionSpec(),
	predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
		defaultPredictivePopTransitionSpec(),
	onBack: () -> Unit = { navigator.pop() },
) {
	CompositionLocalProvider(LocalNav3Navigator provides navigator) {
		NavDisplay(
			backStack = backStack,
			modifier = modifier,
			contentAlignment = contentAlignment,
			onBack = onBack,
			entryDecorators = entryDecorators,
			sceneStrategies = sceneStrategies,
			sizeTransform = sizeTransform,
			transitionSpec = transitionSpec,
			popTransitionSpec = popTransitionSpec,
			predictivePopTransitionSpec = predictivePopTransitionSpec,
			entryProvider = ::nav3ScreenEntry,
		)
	}
}
