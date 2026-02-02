package uk.co.appoly.droid.ui.segmentedcontrol

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import uk.co.appoly.droid.ui.segmentedcontrol.SegmentedControlDefaults.textStyle

/**
 * Colors for a [SegmentedControl].
 *
 * @param trackBrush The brush to use for the track background.
 * @param thumbBrush The brush to use for the selected segment thumb.
 * @param dividerBrush The brush to use for dividers between segments.
 * @param textSelectedColor The color to use for text in the selected segment.
 * @param textUnselectedColor The color to use for text in unselected segments.
 */
@Stable
data class SegmentedControlColors(
    val trackBrush: Brush,
    val thumbBrush: Brush,
    val dividerBrush: Brush,
    val textSelectedColor: Color,
    val textUnselectedColor: Color
) {
    constructor(
        trackColor: Color,
        thumbColor: Color,
        dividerColor: Color,
        textSelectedColor: Color,
        textUnselectedColor: Color
    ) : this(
        trackBrush = SolidColor(trackColor),
        thumbBrush = SolidColor(thumbColor),
        dividerBrush = SolidColor(dividerColor),
        textSelectedColor = textSelectedColor,
        textUnselectedColor = textUnselectedColor
    )
    constructor(
        trackColor: Color,
        thumbBrush: Brush,
        dividerColor: Color,
        textSelectedColor: Color,
        textUnselectedColor: Color
    ) : this(
        trackBrush = SolidColor(trackColor),
        thumbBrush = thumbBrush,
        dividerBrush = SolidColor(dividerColor),
        textSelectedColor = textSelectedColor,
        textUnselectedColor = textUnselectedColor
    )
    constructor(
        trackColor: Color,
        thumbBrush: Brush,
        dividerBrush: Brush,
        textSelectedColor: Color,
        textUnselectedColor: Color
    ) : this(
        trackBrush = SolidColor(trackColor),
        thumbBrush = thumbBrush,
        dividerBrush = dividerBrush,
        textSelectedColor = textSelectedColor,
        textUnselectedColor = textUnselectedColor
    )
    constructor(
        trackColor: Color,
        thumbColor: Color,
        dividerBrush: Brush,
        textSelectedColor: Color,
        textUnselectedColor: Color
    ) : this(
        trackBrush = SolidColor(trackColor),
        thumbBrush = SolidColor(thumbColor),
        dividerBrush = dividerBrush,
        textSelectedColor = textSelectedColor,
        textUnselectedColor = textUnselectedColor
    )
}

/**
 * Text styles for a [SegmentedControl].
 *
 * @param selectedStyle The text style to use for the selected segment.
 * @param unselectedStyle The text style to use for unselected segments.
 */
@Stable
data class SegmentedControlTextStyle(
    val selectedStyle: TextStyle,
    val unselectedStyle: TextStyle
)

object SegmentedControlDefaults {
    /**
     * Creates Default [SegmentedControlColors] with solid colors.
     */
    @Composable
    fun colors(): SegmentedControlColors {
        return SegmentedControlColors(
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
            thumbColor = MaterialTheme.colorScheme.primary,
            dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
            textSelectedColor = MaterialTheme.colorScheme.onPrimary,
            textUnselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
        )
    }

    /**
     * Creates [SegmentedControlColors] with solid colors.
     */
    @Composable
    fun colors(
        trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        thumbColor: Color = MaterialTheme.colorScheme.primary,
        dividerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
        textSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
        textUnselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)
    ): SegmentedControlColors {
        return SegmentedControlColors(
            trackColor = trackColor,
            thumbColor = thumbColor,
            dividerColor = dividerColor,
            textSelectedColor = textSelectedColor,
            textUnselectedColor = textUnselectedColor,
        )
    }

    /**
     * Creates [SegmentedControlColors] with brush support for gradients.
     */
    @Composable
    fun colors(
        trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        thumbBrush: Brush = SolidColor(MaterialTheme.colorScheme.primary),
        dividerBrush: Brush = SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)),
        textSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
        textUnselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)
    ): SegmentedControlColors {
        return SegmentedControlColors(
            trackColor = trackColor,
            thumbBrush = thumbBrush,
            dividerBrush = dividerBrush,
            textSelectedColor = textSelectedColor,
            textUnselectedColor = textUnselectedColor,
        )
    }

    /**
     * Creates [SegmentedControlColors] with brush support for gradients.
     */
    @Composable
    fun colors(
        trackBrush: Brush = SolidColor(MaterialTheme.colorScheme.surfaceContainer),
        thumbBrush: Brush = SolidColor(MaterialTheme.colorScheme.primary),
        dividerBrush: Brush = SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)),
        textSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
        textUnselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)
    ): SegmentedControlColors {
        return SegmentedControlColors(
            trackBrush = trackBrush,
            thumbBrush = thumbBrush,
            dividerBrush = dividerBrush,
            textSelectedColor = textSelectedColor,
            textUnselectedColor = textUnselectedColor,
        )
    }

    /**
     * Creates [SegmentedControlTextStyle] with customizable text styles for selected and unselected states.
     *
     * @param selectedStyle The text style for the selected segment. Defaults to bold weight.
     * @param unselectedStyle The text style for unselected segments. Defaults to medium weight.
     */
    @Composable
    fun textStyle(
        selectedStyle: TextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
        unselectedStyle: TextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium)
    ): SegmentedControlTextStyle {
        return SegmentedControlTextStyle(
            selectedStyle = selectedStyle,
            unselectedStyle = unselectedStyle
        )
    }


    /**
     * Creates [SegmentedControlTextStyle] using a base [TextStyle] with customizable font weights
     * for selected and unselected states.
     *
     * @param textStyle The base text style to use for both states.
     * @param selectedFontWeight The font weight for the selected segment. Defaults to [FontWeight.Bold].
     * @param unselectedFontWeight The font weight for unselected segments. Defaults to [FontWeight.Medium].
     */
    @Composable
    fun textStyle(
        textStyle: TextStyle,
        selectedFontWeight: FontWeight? = FontWeight.Bold,
        unselectedFontWeight: FontWeight? = FontWeight.Medium
    ): SegmentedControlTextStyle {
        return SegmentedControlTextStyle(
            selectedStyle = textStyle.copy(fontWeight = selectedFontWeight),
            unselectedStyle = textStyle.copy(fontWeight = unselectedFontWeight)
        )
    }

    /**
     * Creates [SegmentedControlTextStyle] with individual text style properties.
     *
     * Most properties are shared between selected and unselected states. Properties that commonly
     * differ between states have separate selected/unselected parameters, with unselected values
     * defaulting to their selected counterpart (except fontWeight which defaults to Bold/Medium).
     *
     * @param selectedFontSize The font size for the selected segment.
     * @param unselectedFontSize The font size for unselected segments. Defaults to [selectedFontSize].
     * @param selectedFontWeight The font weight for the selected segment. Defaults to [FontWeight.Bold].
     * @param unselectedFontWeight The font weight for unselected segments. Defaults to [FontWeight.Medium].
     * @param selectedFontStyle The font style for the selected segment.
     * @param unselectedFontStyle The font style for unselected segments. Defaults to [selectedFontStyle].
     * @param selectedLetterSpacing The letter spacing for the selected segment.
     * @param unselectedLetterSpacing The letter spacing for unselected segments. Defaults to [selectedLetterSpacing].
     * @param selectedBackground The background color for the selected segment text.
     * @param unselectedBackground The background color for unselected segment text. Defaults to [selectedBackground].
     * @param selectedTextDecoration The text decoration for the selected segment.
     * @param unselectedTextDecoration The text decoration for unselected segments. Defaults to [selectedTextDecoration].
     * @param selectedShadow The shadow for the selected segment text.
     * @param unselectedShadow The shadow for unselected segment text. Defaults to [selectedShadow].
     */
    @Composable
    fun textStyle(
        selectedFontSize: TextUnit = TextUnit.Unspecified,
        unselectedFontSize: TextUnit = selectedFontSize,
        selectedFontWeight: FontWeight? = FontWeight.Bold,
        unselectedFontWeight: FontWeight? = FontWeight.Medium,
        selectedFontStyle: FontStyle? = null,
        unselectedFontStyle: FontStyle? = selectedFontStyle,
        fontSynthesis: FontSynthesis? = null,
        fontFamily: FontFamily? = null,
        fontFeatureSettings: String? = null,
        selectedLetterSpacing: TextUnit = TextUnit.Unspecified,
        unselectedLetterSpacing: TextUnit = selectedLetterSpacing,
        baselineShift: BaselineShift? = null,
        textGeometricTransform: TextGeometricTransform? = null,
        localeList: LocaleList? = null,
        selectedBackground: Color = Color.Unspecified,
        unselectedBackground: Color = selectedBackground,
        selectedTextDecoration: TextDecoration? = null,
        unselectedTextDecoration: TextDecoration? = selectedTextDecoration,
        selectedShadow: Shadow? = null,
        unselectedShadow: Shadow? = selectedShadow,
        drawStyle: DrawStyle? = null,
        textAlign: TextAlign = TextAlign.Unspecified,
        textDirection: TextDirection = TextDirection.Unspecified,
        lineHeight: TextUnit = TextUnit.Unspecified,
        textIndent: TextIndent? = null,
        platformStyle: PlatformTextStyle? = null,
        lineHeightStyle: LineHeightStyle? = null,
        lineBreak: LineBreak = LineBreak.Unspecified,
        hyphens: Hyphens = Hyphens.Unspecified,
        textMotion: TextMotion? = null,
    ): SegmentedControlTextStyle {
        return SegmentedControlTextStyle(
            selectedStyle = TextStyle(
                fontSize = selectedFontSize,
                fontWeight = selectedFontWeight,
                fontStyle = selectedFontStyle,
                fontSynthesis = fontSynthesis,
                fontFamily = fontFamily,
                fontFeatureSettings = fontFeatureSettings,
                letterSpacing = selectedLetterSpacing,
                baselineShift = baselineShift,
                textGeometricTransform = textGeometricTransform,
                localeList = localeList,
                background = selectedBackground,
                textDecoration = selectedTextDecoration,
                shadow = selectedShadow,
                drawStyle = drawStyle,
                textAlign = textAlign,
                textDirection = textDirection,
                lineHeight = lineHeight,
                textIndent = textIndent,
                platformStyle = platformStyle,
                lineHeightStyle = lineHeightStyle,
                lineBreak = lineBreak,
                hyphens = hyphens,
                textMotion = textMotion,
            ),
            unselectedStyle = TextStyle(
                fontSize = unselectedFontSize,
                fontWeight = unselectedFontWeight,
                fontStyle = unselectedFontStyle,
                fontSynthesis = fontSynthesis,
                fontFamily = fontFamily,
                fontFeatureSettings = fontFeatureSettings,
                letterSpacing = unselectedLetterSpacing,
                baselineShift = baselineShift,
                textGeometricTransform = textGeometricTransform,
                localeList = localeList,
                background = unselectedBackground,
                textDecoration = unselectedTextDecoration,
                shadow = unselectedShadow,
                drawStyle = drawStyle,
                textAlign = textAlign,
                textDirection = textDirection,
                lineHeight = lineHeight,
                textIndent = textIndent,
                platformStyle = platformStyle,
                lineHeightStyle = lineHeightStyle,
                lineBreak = lineBreak,
                hyphens = hyphens,
                textMotion = textMotion,
            )
        )
    }
}

private const val NO_SEGMENT_INDEX = -1

/**
 * A segmented control for string segments.
 *
 * This is the simplest overload for when your segments are already strings.
 *
 * @param segments The list of string segments to display.
 * @param selectedSegment The segment that should be selected.
 * @param onSegmentSelected A callback that will be called when the user selects a segment.
 * @param modifier A modifier to apply to the control.
 * @param trackShape The shape of the track that the segments are placed on.
 * @param trackPadding The padding around the track.
 * @param trackPressedPadding The padding around the track when a segment is pressed.
 * @param thumbShape The shape of the thumb that indicates the selected segment.
 * @param minThumbHeight Optional minimum height for the thumb/segments. Content will be centered vertically.
 * @param segmentsPadding The padding around each segment.
 * @param showDividers Whether to show dividers between segments.
 * @param colors The colors to use for the control.
 * @param textStyle The text styles to use for selected and unselected segments.
 * @param pressedUnselectedAlpha The alpha to use for unselected segments when they are pressed.
 */
@Composable
fun SegmentedControl(
    segments: List<String>,
    selectedSegment: String,
    onSegmentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    trackShape: Shape = RoundedCornerShape(8.dp),
    trackPadding: Dp = 2.dp,
    trackPressedPadding: Dp = 1.dp,
    thumbShape: Shape = trackShape,
    minThumbHeight: Dp? = null,
    segmentsPadding: Dp = 5.dp,
    showDividers: Boolean = true,
    colors: SegmentedControlColors = SegmentedControlDefaults.colors(),
    textStyle: SegmentedControlTextStyle = SegmentedControlDefaults.textStyle(),
    pressedUnselectedAlpha: Float = 0.6f
) {
    SegmentedControl(
        segments = segments,
        selectedSegment = selectedSegment,
        onSegmentSelected = onSegmentSelected,
        modifier = modifier,
        trackShape = trackShape,
        trackPadding = trackPadding,
        trackPressedPadding = trackPressedPadding,
        thumbShape = thumbShape,
        minThumbHeight = minThumbHeight,
        segmentsPadding = segmentsPadding,
        showDividers = showDividers,
        colors = colors,
        textStyle = textStyle,
        pressedUnselectedAlpha = pressedUnselectedAlpha,
        segmentText = { it }
    )
}

/**
 * A segmented control that displays text for each segment.
 *
 * This is a convenience overload that uses [SegmentText] for each segment. For custom segment
 * content, use the overload that accepts a `content` composable lambda.
 *
 * @param segments The list of segments to display.
 * @param selectedSegment The segment that should be selected.
 * @param onSegmentSelected A callback that will be called when the user selects a segment.
 * @param modifier A modifier to apply to the control.
 * @param trackShape The shape of the track that the segments are placed on.
 * @param trackPadding The padding around the track.
 * @param trackPressedPadding The padding around the track when a segment is pressed.
 * @param thumbShape The shape of the thumb that indicates the selected segment.
 * @param minThumbHeight Optional minimum height for the thumb/segments. Content will be centered vertically.
 * @param segmentsPadding The padding around each segment.
 * @param showDividers Whether to show dividers between segments.
 * @param colors The colors to use for the control.
 * @param textStyle The text styles to use for selected and unselected segments.
 * @param pressedUnselectedAlpha The alpha to use for unselected segments when they are pressed.
 * @param segmentText A lambda that returns the text to display for each segment. Defaults to [toString].
 */
@Composable
fun <T : Any> SegmentedControl(
    segments: List<T>,
    selectedSegment: T,
    onSegmentSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    trackShape: Shape = RoundedCornerShape(8.dp),
    trackPadding: Dp = 2.dp,
    trackPressedPadding: Dp = 1.dp,
    thumbShape: Shape = trackShape,
    minThumbHeight: Dp? = null,
    segmentsPadding: Dp = 5.dp,
    showDividers: Boolean = true,
    colors: SegmentedControlColors = SegmentedControlDefaults.colors(),
    textStyle: SegmentedControlTextStyle = SegmentedControlDefaults.textStyle(),
    pressedUnselectedAlpha: Float = 0.6f,
    segmentText: @Composable (T) -> String = { it.toString() }
) {
    SegmentedControl(
        segments = segments,
        selectedSegment = selectedSegment,
        onSegmentSelected = onSegmentSelected,
        modifier = modifier,
        trackShape = trackShape,
        trackPadding = trackPadding,
        trackPressedPadding = trackPressedPadding,
        thumbShape = thumbShape,
        minThumbHeight = minThumbHeight,
        segmentsPadding = segmentsPadding,
        showDividers = showDividers,
        colors = colors,
        textStyle = textStyle,
        pressedUnselectedAlpha = pressedUnselectedAlpha,
        content = { segment, _ ->
            SegmentText(text = segmentText(segment))
        }
    )
}
/**
 * A segmented control that allows the user to select one of a number of segments. Each segment is
 * represented by a piece of content that is passed in as a lambda.
 *
 * @param segments The list of segments to display.
 * @param selectedSegment The segment that should be selected.
 * @param onSegmentSelected A callback that will be called when the user selects a segment.
 * @param modifier A modifier to apply to the control.
 * @param trackShape The shape of the track that the segments are placed on.
 * @param trackPadding The padding around the track.
 * @param trackPressedPadding The padding around the track when a segment is pressed.
 * @param thumbShape The shape of the thumb that indicates the selected segment.
 * @param minThumbHeight Optional minimum height for the thumb/segments. Content will be centered vertically.
 * @param segmentsPadding The padding around each segment.
 * @param showDividers Whether to show dividers between segments.
 * @param colors The colors to use for the control.
 * @param textStyle The text styles to use for selected and unselected segments.
 * @param pressedUnselectedAlpha The alpha to use for unselected segments when they are pressed.
 * @param content A lambda that will be called to display each segment. The lambda will be passed the
 * segment and a boolean indicating whether the segment is selected.
 */
@Composable
fun <T : Any> SegmentedControl(
    segments: List<T>,
    selectedSegment: T,
    onSegmentSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    trackShape: Shape = RoundedCornerShape(8.dp),
    trackPadding: Dp = 2.dp,
    trackPressedPadding: Dp = 1.dp,
    thumbShape: Shape = trackShape,
    minThumbHeight: Dp? = null,
    segmentsPadding: Dp = 5.dp,
    showDividers: Boolean = true,
    colors: SegmentedControlColors = SegmentedControlDefaults.colors(),
    textStyle: SegmentedControlTextStyle = SegmentedControlDefaults.textStyle(),
    pressedUnselectedAlpha: Float = 0.6f,
    content: @Composable (T, isSelected: Boolean) -> Unit
) {
    val state = remember { SegmentedControlState(trackPressedPadding = trackPressedPadding) }
    state.segmentCount = segments.size
    state.selectedSegment = segments.indexOf(selectedSegment)
    state.onSegmentSelected = { onSegmentSelected(segments[it]) }

    // Animate between whole-number indices so we don't need to do pixel calculations.
    val selectedIndexOffset by animateFloatAsState(
        state.selectedSegment.toFloat(),
        label = "selectedIndexOffset_animatedFloatAsState"
    )

    // Use a custom layout so that we can measure the thumb using the height of the segments. The thumb
    // is whole composable that draws itself – this layout is just responsible for placing it under
    // the correct segment.
    Layout(
        content = {
            // Each of these produces a single measurable.
            Thumb(
                state = state,
                thumbShape = thumbShape,
                colors = colors
            )
            Dividers(
                state = state,
                show = showDividers,
                trackPadding = trackPadding,
                trackPressedPadding = trackPressedPadding,
                colors = colors
            )
            Segments(
                state = state,
                segments = segments,
                trackPadding = trackPadding,
                segmentsPadding = segmentsPadding,
                content = content,
                colors = colors,
                textStyle = textStyle,
                pressedUnselectedAlpha = pressedUnselectedAlpha,
                minHeight = minThumbHeight
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .then(state.inputModifier)
            .background(colors.trackBrush, trackShape)
            .padding(trackPadding)
    ) { (thumbMeasurable, dividersMeasurable, segmentsMeasurable), constraints ->

        // Measure the segments first so we know how tall to make the thumb.
        val segmentsPlaceable = segmentsMeasurable.measure(constraints)
        state.updatePressedScale(segmentsPlaceable.height, this)

        // Now we can measure the thumb and dividers to be the right size.
        val thumbPlaceable = thumbMeasurable.measure(
            Constraints.fixed(
                width = segmentsPlaceable.width / segments.size,
                height = segmentsPlaceable.height
            )
        )
        val dividersPlaceable = dividersMeasurable.measure(
            Constraints.fixed(
                width = segmentsPlaceable.width,
                height = segmentsPlaceable.height
            )
        )

        layout(segmentsPlaceable.width, segmentsPlaceable.height) {
            val segmentWidth = segmentsPlaceable.width / segments.size

            // Place the thumb first since it should be drawn below the segments.
            thumbPlaceable.placeRelative(
                x = (selectedIndexOffset * segmentWidth).toInt(),
                y = 0
            )
            dividersPlaceable.placeRelative(IntOffset.Zero)
            segmentsPlaceable.placeRelative(IntOffset.Zero)
        }
    }
}

/**
 * Wrapper around [Text] that is configured to display appropriately inside of a [SegmentedControl].
 *
 * Uses [LocalTextStyle] and [LocalContentColor] provided by the [SegmentedControl] which are
 * automatically set based on the selection state.
 *
 * @param text The text to display.
 * @param modifier Modifier to apply to the text.
 */
@Composable
fun SegmentText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier,
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Draws the thumb (selected indicator) on a [SegmentedControl] track, underneath the [Segments].
 */
@Composable
private fun Thumb(
    state: SegmentedControlState,
    thumbShape: Shape,
    colors: SegmentedControlColors
) {
    val density = LocalDensity.current
    val pressed = state.pressedSegment == state.selectedSegment
    val scale by animateFloatAsState(
        targetValue = if (pressed) state.pressedSelectedScale else 1f,
        label = "thumbScale"
    )
    val xOffset by animateDpAsState(
        targetValue = if (pressed) state.trackPressedPadding else 0.dp,
        label = "thumbXOffset"
    )

    Box(
        Modifier
            .segmentScale(
                scale = scale,
                xOffset = with(density) { xOffset.toPx() },
                segment = state.selectedSegment,
                segmentCount = state.segmentCount
            )
            .shadow(4.dp, thumbShape)
            .background(colors.thumbBrush, thumbShape)
    )
}

/**
 * Draws dividers between segments. No dividers are drawn around the selected segment.
 */
@Composable
private fun Dividers(
    state: SegmentedControlState,
    show: Boolean,
    trackPadding: Dp,
    trackPressedPadding: Dp,
    colors: SegmentedControlColors
) {
    // Animate each divider independently.
    val alphas = (1 until state.segmentCount).map { i ->
        val selectionAdjacent = i == state.selectedSegment || i - 1 == state.selectedSegment
        animateFloatAsState(
            targetValue = if (selectionAdjacent) 0f else 1f,
            label = "dividerAlpha_$i"
        )
    }

    Canvas(Modifier.fillMaxSize()) {
        val segmentWidth = size.width / state.segmentCount
        val dividerPadding = trackPadding + trackPressedPadding

        alphas.forEachIndexed { i, alpha ->
            val x = (i + 1) * segmentWidth
            drawLine(
                if (show) colors.dividerBrush else SolidColor(Color.Transparent),
                alpha = alpha.value,
                start = Offset(x, dividerPadding.toPx()),
                end = Offset(x, size.height - dividerPadding.toPx())
            )
        }
    }
}

/**
 * Draws the actual segments in a [SegmentedControl].
 */
@Composable
private fun <T> Segments(
    state: SegmentedControlState,
    segments: List<T>,
    trackPadding: Dp,
    segmentsPadding: Dp,
    colors: SegmentedControlColors,
    textStyle: SegmentedControlTextStyle,
    pressedUnselectedAlpha: Float,
    minHeight: Dp?,
    content: @Composable (T, isSelected: Boolean) -> Unit
) {
    val density = LocalDensity.current

    Row(
        horizontalArrangement = spacedBy(trackPadding),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (minHeight != null) Modifier.heightIn(min = minHeight) else Modifier)
            .selectableGroup()
    ) {
        segments.forEachIndexed { i, segment ->
            val isSelected = i == state.selectedSegment
            val isPressed = i == state.pressedSegment
            val isPressedAndSelected = isPressed && isSelected

            // Unselected presses are represented by fading.
            val alpha by animateFloatAsState(
                targetValue = if (!isSelected && isPressed) pressedUnselectedAlpha else 1f,
                label = "segmentAlpha_$i"
            )

            // Selected presses are represented by scaling.
            val scale by animateFloatAsState(
                targetValue = if (isPressedAndSelected) state.pressedSelectedScale else 1f,
                label = "segmentScale_$i"
            )
            val xOffset by animateDpAsState(
                targetValue = if (isPressedAndSelected) state.trackPressedPadding else 0.dp,
                label = "segmentXOffset_$i"
            )

            // We can't use Modifier.selectable because it does way too much: it does its own input
            // handling and wires into Compose's indication/interaction system, which we don't want because
            // we've got our own indication mechanism.
            val semanticsModifier = Modifier.semantics(mergeDescendants = true) {
                selected = isSelected
                role = Role.Button
                onClick { state.onSegmentSelected(i); true }
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }

            Box(
                Modifier
                    // Divide space evenly between all segments.
                    .weight(1f)
                    .then(semanticsModifier)
                    .padding(segmentsPadding)
                    // Draw pressed indication when not selected.
                    .alpha(alpha)
                    // Selected presses are represented by scaling.
                    .segmentScale(
                        scale = scale,
                        xOffset = with(density) { xOffset.toPx() },
                        segment = i,
                        segmentCount = state.segmentCount
                    )
                    // Center the segment content.
                    .wrapContentWidth()
            ) {
                val activeTextColor by animateColorAsState(
                    targetValue = if (isSelected) colors.textSelectedColor else colors.textUnselectedColor,
                    label = "textColor_$i"
                )
                val activeTextStyle = if (isSelected) textStyle.selectedStyle else textStyle.unselectedStyle
                CompositionLocalProvider(
                    LocalContentColor provides activeTextColor,
                    LocalTextStyle provides activeTextStyle
                ) {
                    content(segment, isSelected)
                }
            }
        }
    }
}

private class SegmentedControlState(
    val trackPressedPadding: Dp
) {
    var segmentCount by mutableIntStateOf(0)
    var selectedSegment by mutableIntStateOf(0)
    var onSegmentSelected: (Int) -> Unit by mutableStateOf({})
    var pressedSegment by mutableIntStateOf(NO_SEGMENT_INDEX)

    /**
     * Scale factor that should be used to scale pressed segments (both the segment itself and the
     * thumb). When this scale is applied, exactly [trackPressedPadding] will be added around the
     * element's usual size.
     */
    var pressedSelectedScale by mutableFloatStateOf(1f)
        private set

    /**
     * Calculates the scale factor we need to use for pressed segments to get the desired padding.
     */
    fun updatePressedScale(controlHeight: Int, density: Density) {
        with(density) {
            val pressedPadding = trackPressedPadding * 2
            val pressedHeight = controlHeight - pressedPadding.toPx()
            pressedSelectedScale = pressedHeight / controlHeight
        }
    }

    /**
     * A [Modifier] that will listen for touch gestures and update the selected and pressed properties
     * of this state appropriately.
     *
     * Input will be reset if the [segmentCount] changes.
     */
    val inputModifier = Modifier.pointerInput(segmentCount) {
        val segmentWidth = size.width / segmentCount

        // Helper to calculate which segment an event occurred in.
        fun segmentIndex(change: PointerInputChange): Int =
            ((change.position.x / size.width.toFloat()) * segmentCount)
                .toInt()
                .coerceIn(0, segmentCount - 1)

        awaitEachGesture {
            val down = awaitFirstDown()

            pressedSegment = segmentIndex(down)
            val downOnSelected = pressedSegment == selectedSegment
            val segmentBounds = Rect(
                left = pressedSegment * segmentWidth.toFloat(),
                right = (pressedSegment + 1) * segmentWidth.toFloat(),
                top = 0f,
                bottom = size.height.toFloat()
            )

            // Now that the pointer is down, the rest of the gesture depends on whether the segment that
            // was "pressed" was selected.
            if (downOnSelected) {
                // When the selected segment is pressed, it can be dragged to other segments to animate the
                // thumb moving and the segments scaling.
                horizontalDrag(down.id) { change ->
                    pressedSegment = segmentIndex(change)

                    // Notify the SegmentedControl caller when the pointer changes segments.
                    if (pressedSegment != selectedSegment) {
                        onSegmentSelected(pressedSegment)
                    }
                }
            } else {
                // When an unselected segment is pressed, we just animate the alpha of the segment while
                // the pointer is down. No dragging is supported.
                waitForUpOrCancellation(inBounds = segmentBounds)
                    // Null means the gesture was cancelled (e.g. dragged out of bounds).
                    ?.let { onSegmentSelected(pressedSegment) }
            }

            // In either case, once the gesture is cancelled, stop showing the pressed indication.
            pressedSegment = NO_SEGMENT_INDEX
        }
    }
}

/**
 * Copy of nullary waitForUpOrCancellation that works with bounds that may not be at 0,0.
 */
private suspend fun AwaitPointerEventScope.waitForUpOrCancellation(inBounds: Rect): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.all { it.changedToUp() }) {
            // All pointers are up
            return event.changes[0]
        }

        if (event.changes.any { it.isConsumed || !inBounds.contains(it.position) }) {
            return null // Canceled
        }

        // Check for cancel by position consumption. We can look on the Final pass of the
        // existing pointer event because it comes after the Main pass we checked above.
        val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
        if (consumeCheck.changes.any { it.isConsumed }) {
            return null
        }
    }
}

/**
 * Applies a scale and translation transform to create a "pressed" effect for segments.
 *
 * The scale is performed around the appropriate edge based on segment position:
 * - First segment: scales from left edge
 * - Last segment: scales from right edge
 * - Middle segments: scales from center
 *
 * @param scale The scale factor to apply (1f = no scale).
 * @param xOffset The horizontal offset in pixels to apply based on segment position.
 * @param segment The index of this segment.
 * @param segmentCount The total number of segments.
 */
private fun Modifier.segmentScale(
    scale: Float,
    xOffset: Float,
    segment: Int,
    segmentCount: Int
): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale

    // Scales on the ends should gravitate to that edge.
    transformOrigin = TransformOrigin(
        pivotFractionX = when (segment) {
            0 -> 0f
            segmentCount - 1 -> 1f
            else -> .5f
        },
        pivotFractionY = .5f
    )

    // But should still move inwards to keep the pressed padding consistent with top and bottom.
    translationX = when (segment) {
        0 -> xOffset
        segmentCount - 1 -> -xOffset
        else -> 0f
    }
}
