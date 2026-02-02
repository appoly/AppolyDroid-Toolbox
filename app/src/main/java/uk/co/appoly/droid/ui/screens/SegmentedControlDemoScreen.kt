package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import uk.co.appoly.droid.ui.segmentedcontrol.SegmentText
import uk.co.appoly.droid.ui.segmentedcontrol.SegmentedControl
import uk.co.appoly.droid.ui.segmentedcontrol.SegmentedControlDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedControlDemoScreen(navController: NavController) {
	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("SegmentedControl Demo") },
				navigationIcon = {
					IconButton(onClick = { navController.navigateUp() }) {
						Text("←")
					}
				}
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.padding(16.dp)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text(
				text = "SegmentedControl Demo",
				style = MaterialTheme.typography.headlineMedium
			)

			Text(
				text = "iOS-style segmented control with smooth animations and customizable styling",
				style = MaterialTheme.typography.bodyLarge
			)

			// Basic usage with strings
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Basic Usage",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					val segments = remember { listOf("Option A", "Option B", "Option C") }
					var selected by remember { mutableStateOf(segments.first()) }

					SegmentedControl(
						segments = segments,
						selectedSegment = selected,
						onSegmentSelected = { selected = it }
					)

					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = "Selected: $selected",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.primary
					)
				}
			}

			// Custom colors
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Custom Colors",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					val segments = remember { listOf("Light", "Dark", "Auto") }
					var selected by remember { mutableStateOf(segments.first()) }

					SegmentedControl(
						segments = segments,
						selectedSegment = selected,
						onSegmentSelected = { selected = it },
						colors = SegmentedControlDefaults.colors(
							trackColor = Color(0xFFE8E8E8),
							thumbColor = Color(0xFF6750A4),
							dividerColor = Color(0xFFBDBDBD),
							textSelectedColor = Color.White,
							textUnselectedColor = Color(0xFF666666)
						)
					)
				}
			}

			// Gradient thumb
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Gradient Thumb",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					val segments = remember { listOf("Start", "Middle", "End") }
					var selected by remember { mutableStateOf(segments.first()) }

					SegmentedControl(
						segments = segments,
						selectedSegment = selected,
						onSegmentSelected = { selected = it },
						colors = SegmentedControlDefaults.colors(
							trackColor = Color(0xFFF0F0F0),
							thumbBrush = Brush.horizontalGradient(
								colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
							),
							textSelectedColor = Color.White,
							textUnselectedColor = Color(0xFF666666)
						)
					)
				}
			}

			// Custom text styling
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Custom Text Styling",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					val segments = remember { listOf("Small", "Medium", "Large") }
					var selected by remember { mutableStateOf(segments[1]) }

					SegmentedControl(
						segments = segments,
						selectedSegment = selected,
						onSegmentSelected = { selected = it },
						textStyle = SegmentedControlDefaults.textStyle(
							selectedFontSize = 18.sp,
							unselectedFontSize = 14.sp,
							selectedFontWeight = FontWeight.Bold,
							unselectedFontWeight = FontWeight.Normal
						)
					)
				}
			}

			// With enum
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "With Enum Type",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					var selected by remember { mutableStateOf(ViewMode.LIST) }

					SegmentedControl(
						segments = ViewMode.entries,
						selectedSegment = selected,
						onSegmentSelected = { selected = it },
						segmentText = { it.displayName }
					)

					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = "View mode: ${selected.displayName}",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.primary
					)
				}
			}

			// Custom content with icons
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Custom Content with Icons",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					var selected by remember { mutableStateOf(ViewMode.LIST) }

					SegmentedControl(
						segments = ViewMode.entries,
						selectedSegment = selected,
						onSegmentSelected = { selected = it },
						minThumbHeight = 44.dp
					) { segment, _ ->
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp)
						) {
							Icon(
								imageVector = segment.icon,
								contentDescription = segment.displayName,
								modifier = Modifier.size(18.dp)
							)
							SegmentText(text = segment.displayName)
						}
					}
				}
			}

			// Rounded shape
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Custom Shape (Pill)",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					val segments = remember { listOf("Yes", "No") }
					var selected by remember { mutableStateOf(segments.first()) }

					SegmentedControl(
						segments = segments,
						selectedSegment = selected,
						onSegmentSelected = { selected = it },
						trackShape = RoundedCornerShape(50),
						thumbShape = RoundedCornerShape(50),
						trackPadding = 4.dp,
						minThumbHeight = 40.dp
					)
				}
			}

			// No dividers
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Without Dividers",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					val segments = remember { listOf("All", "Active", "Completed") }
					var selected by remember { mutableStateOf(segments.first()) }

					SegmentedControl(
						segments = segments,
						selectedSegment = selected,
						onSegmentSelected = { selected = it },
						showDividers = false
					)
				}
			}

			// Two segments
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Two Segments (Toggle)",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					val segments = remember { listOf("On", "Off") }
					var selected by remember { mutableStateOf(segments.first()) }

					SegmentedControl(
						segments = segments,
						selectedSegment = selected,
						onSegmentSelected = { selected = it },
						colors = SegmentedControlDefaults.colors(
							thumbColor = if (selected == "On") Color(0xFF4CAF50) else Color(0xFFE57373)
						)
					)
				}
			}

			// Usage example code
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Usage Example",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = """
// Basic usage
val segments = listOf("A", "B", "C")
var selected by remember {
    mutableStateOf(segments.first())
}

SegmentedControl(
    segments = segments,
    selectedSegment = selected,
    onSegmentSelected = { selected = it }
)

// With custom colors
SegmentedControl(
    segments = segments,
    selectedSegment = selected,
    onSegmentSelected = { selected = it },
    colors = SegmentedControlDefaults.colors(
        thumbColor = Color.Blue,
        textSelectedColor = Color.White
    )
)

// With custom text style
SegmentedControl(
    segments = segments,
    selectedSegment = selected,
    onSegmentSelected = { selected = it },
    textStyle = SegmentedControlDefaults.textStyle(
        selectedFontSize = 18.sp
    )
)
                            """.trimIndent(),
						style = MaterialTheme.typography.bodySmall,
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		}
	}
}

private enum class ViewMode(val displayName: String, val icon: ImageVector) {
	LIST("List", Icons.AutoMirrored.Filled.ViewList),
	GRID("Grid", Icons.Default.GridView),
	MAP("Map", Icons.Default.Map)
}
