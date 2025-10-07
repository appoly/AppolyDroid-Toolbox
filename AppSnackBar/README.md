# AppSnackBar

A customizable Jetpack Compose Snackbar implementation with support for different message types (info, success, error) and theming.

## Features

- Type-based styling for different message types (Info, Success, Error)
- Custom color theming support through CompositionLocal
- Extension function for SnackbarHostState for easy integration
- Fully compatible with Material 3 components
- Support for action buttons and dismiss actions

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar:1.0.34")
```

## Usage

### Basic Setup

```kotlin
@Composable
fun MyScreen() {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                // Use our custom AppSnackBar instead of default Snackbar
                AppSnackBar(snackbarData = snackbarData)
            }
        }
    ) {
        // Your content
        Button(onClick = {
            // Launch a coroutine to show the snackbar
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Operation successful!",
                    type = SnackBarType.Success
                )
            }
        }) {
            Text("Show Success Snackbar")
        }
    }
}
```

### Different Snackbar Types

```kotlin
// Show an info snackbar (blue)
scope.launch {
    snackbarHostState.showSnackbar(
        message = "This is an information message",
        type = SnackBarType.Info
    )
}

// Show a success snackbar (green)
scope.launch {
    snackbarHostState.showSnackbar(
        message = "Operation completed successfully",
        type = SnackBarType.Success
    )
}

// Show an error snackbar (red)
scope.launch {
    snackbarHostState.showSnackbar(
        message = "An error occurred",
        type = SnackBarType.Error
    )
}
```

### With Action Button

```kotlin
// Show snackbar with an action button
scope.launch {
    val result = snackbarHostState.showSnackbar(
        message = "Item deleted",
        actionLabel = "Undo",
        type = SnackBarType.Info
    )

    // Handle the action button click
    when (result) {
        SnackbarResult.ActionPerformed -> {
            // Undo the deletion
            viewModel.undoDelete()
        }
        SnackbarResult.Dismissed -> {
            // Snackbar was dismissed
        }
    }
}
```

### Custom Colors

You can customize the colors used for different snackbar types:

```kotlin
@Composable
fun MyApp() {
    // Define custom colors for snackbars
    val customSnackbarColors = AppSnackBarColors(
        info = Color(0xFF2196F3),     // Custom blue
        success = Color(0xFF4CAF50),  // Custom green
        error = Color(0xFFF44336)     // Custom red
    )

    // Provide the colors to all composables in the hierarchy
    CompositionLocalProvider(
        LocalAppSnackBarColors provides customSnackbarColors
    ) {
        MyAppContent()
    }
}
```

### Using with Scaffold in Material 3

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                AppSnackBar(snackbarData = data)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "This is an information message",
                            type = SnackBarType.Info
                        )
                    }
                }
            ) {
                Text("Show Info Snackbar")
            }

            Button(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Operation successful!",
                            type = SnackBarType.Success
                        )
                    }
                }
            ) {
                Text("Show Success Snackbar")
            }

            Button(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "An error occurred",
                            type = SnackBarType.Error
                        )
                    }
                }
            ) {
                Text("Show Error Snackbar")
            }
        }
    }
}
```

## API Reference

### AppSnackBar Component

```kotlin
@Composable
fun AppSnackBar(snackbarData: SnackbarData)
```

### SnackBarType

An enum class that defines the types of snackbars:

- `SnackBarType.Info` - For general information messages
- `SnackBarType.Success` - For successful operation notifications
- `SnackBarType.Error` - For error messages and warnings

### AppSnackBarColors

```kotlin
data class AppSnackBarColors(
    val info: Color,
    val success: Color,
    val error: Color
)
```

### showSnackbar Extension

```kotlin
suspend fun SnackbarHostState.showSnackbar(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration =
        if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
    type: SnackBarType = SnackBarType.Info
): SnackbarResult
```

## Integration with Other Modules

- [AppSnackBar-UiState](../AppSnackBar-UiState/README.md) - For automatic Snackbar display based on UI state
- [UiState](../UiState/README.md) - For standard UI state management

## Dependencies

- Jetpack Compose Material 3

