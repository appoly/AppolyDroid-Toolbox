package uk.co.appoly.droid.compose.extensions

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Returns the max of IME and navigation bar insets at every frame,
 * animating smoothly with the keyboard.
 */
val WindowInsets.Companion.navigationBarsOrIme: WindowInsets
	@Composable
	get() = WindowInsets.ime.union(WindowInsets.navigationBars)

/**
 * Returns navigation bar insets that smoothly shrink to zero as the IME appears,
 * animating frame-by-frame with the keyboard.
 */
val WindowInsets.Companion.navigationBarsOrNoneIfIme: WindowInsets
	@Composable
	get() = WindowInsets.navigationBars.exclude(WindowInsets.ime)

val WindowInsets.Companion.None: WindowInsets
	get() {
		return WindowInsets(0)
	}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardAsState(): State<Boolean> {
	return rememberUpdatedState(WindowInsets.isImeVisible)
}

tailrec fun Context.getActivity(): ComponentActivity? = when (this) {
	is ComponentActivity -> this
	is ContextWrapper -> baseContext.getActivity()
	else -> null
}

@Composable
fun getActiveActivity(): ComponentActivity? {
	val context = LocalContext.current
	return context.getActivity()
}

fun Modifier.gradientTint(brush: Brush): Modifier =
	this then
			Modifier
				.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
				.drawWithCache {
					onDrawWithContent {
						drawContent()
						drawRect(brush, blendMode = BlendMode.SrcAtop)
					}
				}

/**
 * Hides this composable in sync with the IME (keyboard) animation.
 * As the keyboard slides up, the composable's layout height smoothly shrinks to zero
 * (clipped from the bottom), and the space it occupied is reclaimed by sibling layouts.
 */
@Composable
fun Modifier.hideWithIme(): Modifier {
	val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
	return this
		.clipToBounds()
		.layout { measurable, constraints ->
			val placeable = measurable.measure(constraints)
			val offset = imeBottom.coerceAtMost(placeable.height)
			layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
				placeable.placeRelative(0, 0)
			}
		}
}

/**
 * Combines two [PaddingValues] instances, taking into account the current layout direction.
 */
@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues =
	plus(other, LocalLayoutDirection.current)

/**
 * Combines [PaddingValues] with [WindowInsets] to create a new [PaddingValues].
 */
@Composable
operator fun PaddingValues.plus(windowInsets: WindowInsets): PaddingValues =
	this + windowInsets.asPaddingValues()

/**
 * Combines two [PaddingValues] instances, taking into account the layout direction.
 */
fun PaddingValues.plus(other: PaddingValues, layoutDirection: LayoutDirection): PaddingValues {
	return PaddingValues(
		start = this.calculateStartPadding(layoutDirection) + other.calculateStartPadding(
			layoutDirection
		),
		top = this.calculateTopPadding() + other.calculateTopPadding(),
		end = this.calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
		bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
	)
}