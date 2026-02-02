# SegmentedControl

A highly customizable iOS-style segmented control for Jetpack Compose with smooth animations, drag-to-switch gestures, and flexible styling options.

## Features

- Smooth animated thumb sliding between segments
- Drag gesture support on the selected segment to switch
- Press animations with configurable scale effect
- Customizable colors with support for solid colors and gradients (Brush)
- Flexible text styling with separate selected/unselected states
- Optional dividers between segments
- Support for custom segment content (not just text)
- Accessibility support with proper semantics
- Material 3 theming integration with sensible defaults

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:SegmentedControl:1.2.4")
```

## Usage

### Basic Usage with Strings

```kotlin
@Composable
fun MyScreen() {
    val segments = listOf("Option A", "Option B", "Option C")
    var selectedSegment by remember { mutableStateOf(segments.first()) }

    SegmentedControl(
        segments = segments,
        selectedSegment = selectedSegment,
        onSegmentSelected = { selectedSegment = it }
    )
}
```

### With Custom Objects

```kotlin
enum class ViewMode { LIST, GRID, MAP }

@Composable
fun ViewModeSelector() {
    var selectedMode by remember { mutableStateOf(ViewMode.LIST) }

    SegmentedControl(
        segments = ViewMode.entries,
        selectedSegment = selectedMode,
        onSegmentSelected = { selectedMode = it },
        segmentText = { mode ->
            when (mode) {
                ViewMode.LIST -> "List"
                ViewMode.GRID -> "Grid"
                ViewMode.MAP -> "Map"
            }
        }
    )
}
```

### Custom Colors

```kotlin
@Composable
fun CustomColoredSegmentedControl() {
    val segments = listOf("Light", "Dark")
    var selected by remember { mutableStateOf(segments.first()) }

    SegmentedControl(
        segments = segments,
        selectedSegment = selected,
        onSegmentSelected = { selected = it },
        colors = SegmentedControlDefaults.colors(
            trackColor = Color.LightGray,
            thumbColor = Color.Blue,
            dividerColor = Color.Gray,
            textSelectedColor = Color.White,
            textUnselectedColor = Color.DarkGray
        )
    )
}
```

### Gradient Thumb

```kotlin
@Composable
fun GradientSegmentedControl() {
    val segments = listOf("Start", "End")
    var selected by remember { mutableStateOf(segments.first()) }

    SegmentedControl(
        segments = segments,
        selectedSegment = selected,
        onSegmentSelected = { selected = it },
        colors = SegmentedControlDefaults.colors(
            trackColor = Color.LightGray,
            thumbBrush = Brush.horizontalGradient(
                colors = listOf(Color.Blue, Color.Cyan)
            ),
            textSelectedColor = Color.White,
            textUnselectedColor = Color.DarkGray
        )
    )
}
```

### Custom Text Styling

```kotlin
@Composable
fun StyledTextSegmentedControl() {
    val segments = listOf("Small", "Medium", "Large")
    var selected by remember { mutableStateOf(segments.first()) }

    // Simple: just change font size
    SegmentedControl(
        segments = segments,
        selectedSegment = selected,
        onSegmentSelected = { selected = it },
        textStyle = SegmentedControlDefaults.textStyle(
            selectedFontSize = 16.sp
        )
    )
}
```

### Different Selected/Unselected Text Styles

```kotlin
@Composable
fun DifferentStylesSegmentedControl() {
    val segments = listOf("Active", "Inactive")
    var selected by remember { mutableStateOf(segments.first()) }

    SegmentedControl(
        segments = segments,
        selectedSegment = selected,
        onSegmentSelected = { selected = it },
        textStyle = SegmentedControlDefaults.textStyle(
            selectedFontSize = 18.sp,
            unselectedFontSize = 14.sp,
            selectedFontWeight = FontWeight.Bold,
            unselectedFontWeight = FontWeight.Normal,
            selectedLetterSpacing = 1.sp,
            unselectedLetterSpacing = 0.sp
        )
    )
}
```

### Full Text Style Customization

```kotlin
@Composable
fun FullyCustomTextSegmentedControl() {
    val segments = listOf("One", "Two")
    var selected by remember { mutableStateOf(segments.first()) }

    SegmentedControl(
        segments = segments,
        selectedSegment = selected,
        onSegmentSelected = { selected = it },
        textStyle = SegmentedControlDefaults.textStyle(
            selectedStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            ),
            unselectedStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        )
    )
}
```

### Custom Segment Content

```kotlin
@Composable
fun IconSegmentedControl() {
    val segments = listOf("home" to Icons.Default.Home, "search" to Icons.Default.Search)
    var selected by remember { mutableStateOf(segments.first()) }

    SegmentedControl(
        segments = segments,
        selectedSegment = selected,
        onSegmentSelected = { selected = it }
    ) { segment, isSelected ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = segment.second,
                contentDescription = segment.first,
                modifier = Modifier.size(16.dp)
            )
            SegmentText(text = segment.first.replaceFirstChar { it.uppercase() })
        }
    }
}
```

### With Minimum Thumb Height

```kotlin
@Composable
fun TallSegmentedControl() {
    val segments = listOf("A", "B")
    var selected by remember { mutableStateOf(segments.first()) }

    SegmentedControl(
        segments = segments,
        selectedSegment = selected,
        onSegmentSelected = { selected = it },
        minThumbHeight = 48.dp  // Ensures consistent height
    )
}
```

### Custom Shapes

```kotlin
@Composable
fun RoundedSegmentedControl() {
    val segments = listOf("Left", "Right")
    var selected by remember { mutableStateOf(segments.first()) }

    SegmentedControl(
        segments = segments,
        selectedSegment = selected,
        onSegmentSelected = { selected = it },
        trackShape = RoundedCornerShape(16.dp),
        thumbShape = RoundedCornerShape(14.dp)
    )
}
```

## API Reference

### SegmentedControl Composable

Three overloads are available:

```kotlin
// For List<String>
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
)

// For generic types with text extraction
@Composable
fun <T : Any> SegmentedControl(
    segments: List<T>,
    selectedSegment: T,
    onSegmentSelected: (T) -> Unit,
    // ... same parameters ...
    segmentText: (T) -> String = { it.toString() }
)

// For generic types with custom content
@Composable
fun <T : Any> SegmentedControl(
    segments: List<T>,
    selectedSegment: T,
    onSegmentSelected: (T) -> Unit,
    // ... same parameters ...
    content: @Composable (T, isSelected: Boolean) -> Unit
)
```

### SegmentedControlColors

```kotlin
@Stable
data class SegmentedControlColors(
    val trackBrush: Brush,
    val thumbBrush: Brush,
    val dividerBrush: Brush,
    val textSelectedColor: Color,
    val textUnselectedColor: Color
)
```

Multiple constructors available for convenience with Color or Brush parameters.

### SegmentedControlTextStyle

```kotlin
@Stable
data class SegmentedControlTextStyle(
    val selectedStyle: TextStyle,
    val unselectedStyle: TextStyle
)
```

### SegmentedControlDefaults

Factory methods for creating colors and text styles:

```kotlin
object SegmentedControlDefaults {
    // Default colors using MaterialTheme
    @Composable fun colors(): SegmentedControlColors

    // Solid colors
    @Composable fun colors(
        trackColor: Color,
        thumbColor: Color,
        dividerColor: Color,
        textSelectedColor: Color,
        textUnselectedColor: Color
    ): SegmentedControlColors

    // With Brush support
    @Composable fun colors(
        trackBrush: Brush,
        thumbBrush: Brush,
        dividerBrush: Brush,
        textSelectedColor: Color,
        textUnselectedColor: Color
    ): SegmentedControlColors

    // Full TextStyle control
    @Composable fun textStyle(
        selectedStyle: TextStyle,
        unselectedStyle: TextStyle
    ): SegmentedControlTextStyle

    // Base style with font weight customization
    @Composable fun textStyle(
        textStyle: TextStyle,
        selectedFontWeight: FontWeight? = FontWeight.Bold,
        unselectedFontWeight: FontWeight? = FontWeight.Medium
    ): SegmentedControlTextStyle

    // Individual properties with selected/unselected variants
    @Composable fun textStyle(
        selectedFontSize: TextUnit = TextUnit.Unspecified,
        unselectedFontSize: TextUnit = selectedFontSize,
        selectedFontWeight: FontWeight? = FontWeight.Bold,
        unselectedFontWeight: FontWeight? = FontWeight.Medium,
        // ... and many more TextStyle properties
    ): SegmentedControlTextStyle
}
```

### SegmentText

Helper composable for displaying text within segments:

```kotlin
@Composable
fun SegmentText(
    text: String,
    modifier: Modifier = Modifier
)
```

Uses `LocalTextStyle` and `LocalContentColor` provided by the parent `SegmentedControl`.

## Dependencies

- Jetpack Compose Material 3
