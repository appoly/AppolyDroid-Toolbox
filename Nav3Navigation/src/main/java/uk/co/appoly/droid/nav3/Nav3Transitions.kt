package uk.co.appoly.droid.nav3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/**
 * Optional [ContentTransform] builders for [Nav3ScreenHost] / [androidx.navigation3.ui.NavDisplay].
 *
 * **Not defaults** — [Nav3ScreenHost] still uses NavDisplay's built-in specs unless you pass these.
 * Use when you want consistent spring-slide / tab-slide motion across apps.
 *
 * ### Intra-tab / single-stack
 *
 * - [springSlidePush] / [springSlidePop] — spring with parallax (outgoing moves ~¼ distance);
 *   sets `targetContentZIndex` from stack depth so pushes cover and pops reveal cleanly.
 * - [slidePush] / [slidePop] — full-width spring slide (no parallax).
 *
 * ### Tabs
 *
 * - [tabSlide] — full directional slide for tab switches ([TabSlide.Forward] / [TabSlide.Backward]).
 * - [TabsNav3Navigator.transitionSpec], [TabsNav3Navigator.popTransitionSpec], and
 *   [TabsNav3Navigator.predictivePopTransitionSpec] consult [TabsNav3Navigator.pendingTabSlide]
 *   and fall back to spring-slide for in-tab changes.
 */
object Nav3Transitions {

	/** Full-width horizontal spring (medium-low stiffness). */
	val slideSpring = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntOffset.VisibilityThreshold,
	)

	/** Parallax spring-slide (slightly under-damped). */
	val parallaxSpring = spring(
		dampingRatio = 0.86f,
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntOffset.VisibilityThreshold,
	)

	/** Full slide in from the right, out to the left (push). */
	fun slidePush(): ContentTransform =
		slideInHorizontally(slideSpring) { it } togetherWith
			slideOutHorizontally(slideSpring) { -it }

	/** Full slide in from the left, out to the right (pop). */
	fun slidePop(): ContentTransform =
		slideInHorizontally(slideSpring) { -it } togetherWith
			slideOutHorizontally(slideSpring) { it }

	/**
	 * Spring-slide push with parallax: incoming covers full width, outgoing drifts ~¼.
	 *
	 * @param stackSize used as `targetContentZIndex` so deeper entries draw on top.
	 */
	fun springSlidePush(stackSize: Int): ContentTransform =
		(
			slideInHorizontally(parallaxSpring) { it } togetherWith
				slideOutHorizontally(parallaxSpring) { -it / 4 }
			).apply {
			targetContentZIndex = stackSize.toFloat()
		}

	/**
	 * Spring-slide pop with parallax (inverse of [springSlidePush]).
	 *
	 * @param stackSize used as `targetContentZIndex`.
	 */
	fun springSlidePop(stackSize: Int): ContentTransform =
		(
			slideInHorizontally(parallaxSpring) { -it / 4 } togetherWith
				slideOutHorizontally(parallaxSpring) { it }
			).apply {
			targetContentZIndex = stackSize.toFloat()
		}

	/**
	 * Full directional slide for tab-strip changes.
	 * Forward = enter from right; Backward = enter from left.
	 */
	fun tabSlide(direction: TabSlide): ContentTransform = when (direction) {
		TabSlide.Forward ->
			slideInHorizontally(slideSpring) { it } togetherWith
				slideOutHorizontally(slideSpring) { -it }
		TabSlide.Backward ->
			slideInHorizontally(slideSpring) { -it } togetherWith
				slideOutHorizontally(slideSpring) { it }
	}
}

/**
 * [Nav3ScreenHost] / [androidx.navigation3.ui.NavDisplay] `transitionSpec` that prefers a
 * directional [Nav3Transitions.tabSlide] when [TabsNav3Navigator.pendingTabSlide] is set,
 * otherwise [forIntraTabPush] (default: spring-slide parallax) using
 * [TabsNav3Navigator.currentTabDepth] as the z-index basis (not the multi-tab [backStack] size).
 */
fun TabsNav3Navigator.transitionSpec(
	forIntraTabPush: (stackSize: Int) -> ContentTransform = Nav3Transitions::springSlidePush,
): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
	pendingTabSlide?.let(Nav3Transitions::tabSlide)
		?: forIntraTabPush(currentTabDepth)
}

/**
 * Pop counterpart of [transitionSpec]. Uses [TabsNav3Navigator.currentTabDepth] for in-tab pops.
 */
fun TabsNav3Navigator.popTransitionSpec(
	forIntraTabPop: (stackSize: Int) -> ContentTransform = Nav3Transitions::springSlidePop,
): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
	pendingTabSlide?.let(Nav3Transitions::tabSlide)
		?: forIntraTabPop(currentTabDepth)
}

/**
 * Predictive-back counterpart: before [TabsNav3Navigator.pop] commits there is no
 * [TabsNav3Navigator.pendingTabSlide] yet, so a gesture on a non-start tab root is inferred as
 * the same direction a committed exit-through-home [TabsNav3Navigator.pop] would use
 * ([TabsNav3Navigator.exitToStartTabSlide]); otherwise [forIntraTabPop] with
 * [TabsNav3Navigator.currentTabDepth].
 */
fun TabsNav3Navigator.predictivePopTransitionSpec(
	forIntraTabPop: (stackSize: Int) -> ContentTransform = Nav3Transitions::springSlidePop,
): AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = {
	if (isAtCurrentTabRoot && !isOnStartTab) {
		Nav3Transitions.tabSlide(exitToStartTabSlide)
	} else {
		forIntraTabPop(currentTabDepth)
	}
}
