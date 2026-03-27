package uk.co.appoly.droid.compose.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * A wrapper around [AnimatedVisibility] that automatically scrolls the nearest
 * scrollable ancestor to keep the animating content visible.
 *
 * While the enter transition is running, a [BringIntoViewRequester] fires every
 * frame (~16 ms) so the scroll container tracks the expanding content smoothly.
 *
 * This is the standalone overload — it uses [expandIn]/[shrinkOut] defaults and
 * can be placed anywhere, not just inside a [Column][androidx.compose.foundation.layout.Column]
 * or [Row][androidx.compose.foundation.layout.Row].
 *
 * @param visible whether the content should be visible.
 * @param modifier [Modifier] applied to the [AnimatedVisibility] layout.
 * @param enter the [EnterTransition] — defaults to [fadeIn] + [expandIn].
 * @param exit the [ExitTransition] — defaults to [shrinkOut] + [fadeOut].
 * @param label a label for the animation, useful for tooling/inspection.
 * @param content the composable content to show/hide.
 */
@Composable
public fun ScrollIntoViewAnimatedVisibility(
	visible: Boolean,
	modifier: Modifier = Modifier,
	enter: EnterTransition = fadeIn() + expandIn(),
	exit: ExitTransition = shrinkOut() + fadeOut(),
	label: String = "AnimatedVisibility",
	content: @Composable() AnimatedVisibilityScope.() -> Unit,
) {
	val bringIntoViewRequester = remember { BringIntoViewRequester() }

	AnimatedVisibility(
		visible = visible,
		modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
		enter = enter,
		exit = exit,
		label = label,
	) {
		// Continuously bring into view while the expand transition is running
		if (transition.isRunning && visible) {
			LaunchedEffect(Unit) {
				while (true) {
					bringIntoViewRequester.bringIntoView()
					delay(16)
				}
			}
		}
		content()
	}
}

/**
 * [ColumnScope] overload of [ScrollIntoViewAnimatedVisibility] that automatically
 * scrolls the nearest scrollable ancestor to keep the animating content visible.
 *
 * While the enter transition is running, a [BringIntoViewRequester] fires every
 * frame (~16 ms) so the scroll container tracks the expanding content smoothly.
 *
 * Because this is scoped to a [Column][androidx.compose.foundation.layout.Column],
 * the default transitions use [expandVertically]/[shrinkVertically].
 *
 * @param visible whether the content should be visible.
 * @param modifier [Modifier] applied to the [AnimatedVisibility] layout.
 * @param enter the [EnterTransition] — defaults to [fadeIn] + [expandVertically].
 * @param exit the [ExitTransition] — defaults to [fadeOut] + [shrinkVertically].
 * @param label a label for the animation, useful for tooling/inspection.
 * @param content the composable content to show/hide.
 */
@Composable
public fun ColumnScope.ScrollIntoViewAnimatedVisibility(
	visible: Boolean,
	modifier: Modifier = Modifier,
	enter: EnterTransition = fadeIn() + expandVertically(),
	exit: ExitTransition = fadeOut() + shrinkVertically(),
	label: String = "AnimatedVisibility",
	content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
	val bringIntoViewRequester = remember { BringIntoViewRequester() }

	AnimatedVisibility(
		visible = visible,
		modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
		enter = enter,
		exit = exit,
		label = label,
	) {
		// Continuously bring into view while the expand transition is running
		if (transition.isRunning && visible) {
			LaunchedEffect(Unit) {
				while (true) {
					bringIntoViewRequester.bringIntoView()
					delay(16)
				}
			}
		}
		content()
	}
}

/**
 * [RowScope] overload of [ScrollIntoViewAnimatedVisibility] that automatically
 * scrolls the nearest scrollable ancestor to keep the animating content visible.
 *
 * While the enter transition is running, a [BringIntoViewRequester] fires every
 * frame (~16 ms) so the scroll container tracks the expanding content smoothly.
 *
 * Because this is scoped to a [Row][androidx.compose.foundation.layout.Row],
 * the default transitions use [expandHorizontally]/[shrinkHorizontally].
 *
 * @param visible whether the content should be visible.
 * @param modifier [Modifier] applied to the [AnimatedVisibility] layout.
 * @param enter the [EnterTransition] — defaults to [fadeIn] + [expandHorizontally].
 * @param exit the [ExitTransition] — defaults to [fadeOut] + [shrinkHorizontally].
 * @param label a label for the animation, useful for tooling/inspection.
 * @param content the composable content to show/hide.
 */
@Composable
public fun RowScope.ScrollIntoViewAnimatedVisibility(
	visible: Boolean,
	modifier: Modifier = Modifier,
	enter: EnterTransition = fadeIn() + expandHorizontally(),
	exit: ExitTransition = fadeOut() + shrinkHorizontally(),
	label: String = "AnimatedVisibility",
	content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
	val bringIntoViewRequester = remember { BringIntoViewRequester() }

	AnimatedVisibility(
		visible = visible,
		modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
		enter = enter,
		exit = exit,
		label = label,
	) {
		// Continuously bring into view while the expand transition is running
		if (transition.isRunning && visible) {
			LaunchedEffect(Unit) {
				while (true) {
					bringIntoViewRequester.bringIntoView()
					delay(16)
				}
			}
		}
		content()
	}
}