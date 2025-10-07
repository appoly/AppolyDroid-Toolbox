# AppSnackBar-UiState

Integration module that bridges the AppSnackBar and UiState modules, providing automatic Snackbar display based on UI state changes.

## Features

- Automatic mapping between UiState and SnackBarType
- Seamless integration with UiState for error handling
- Simple extension property for type conversion
- Consistent visual feedback for UI state changes

## Installation

```gradle.kts
// Requires both base modules
implementation("com.github.appoly.AppolyDroid-Toolbox:UiState:1.0.34")
implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar:1.0.34")
implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar-UiState:1.0.34")
```

## Usage

### Basic Integration

The module provides an extension property `snackBarType` that automatically converts a `UiState` to the appropriate `SnackBarType`:

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = viewModel.uiState.collectAsState().value
    val scope = rememberCoroutineScope()

    // Show snackbar when UI state changes to error
    LaunchedEffect(key1 = uiState) {
        if (uiState.isError()) {
            snackbarHostState.showSnackbar(
                message = uiState.message,
                type = uiState.snackBarType // Automatically uses SnackBarType.Error
            )
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                AppSnackBar(snackbarData = data)
            }
        }
    ) {
        // Your content
    }
}
```

### Complete Example with ViewModel

```kotlin
class MyViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle())
    val uiState = _uiState.asStateFlow()

    fun performAction() {
        _uiState.value = UiState.Loading()

        viewModelScope.launch {
            try {
                // Perform some operation
                val result = repository.doSomething()

                // Update UI state to success
                _uiState.value = UiState.Success()
            } catch (e: Exception) {
                // Update UI state to error with message
                _uiState.value = UiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle()
    }
}

@Composable
fun MyScreen(viewModel: MyViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = viewModel.uiState.collectAsState().value
    val scope = rememberCoroutineScope()

    // Show appropriate snackbars for different UI states
    LaunchedEffect(key1 = uiState) {
        when {
            uiState.isSuccess() -> {
                snackbarHostState.showSnackbar(
                    message = "Operation completed successfully",
                    type = uiState.snackBarType // Uses SnackBarType.Success
                )
                viewModel.resetState() // Reset state after showing success
            }
            uiState.isError() -> {
                snackbarHostState.showSnackbar(
                    message = uiState.message,
                    type = uiState.snackBarType // Uses SnackBarType.Error
                )
                viewModel.resetState() // Reset state after showing error
            }
        }
    }

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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { viewModel.performAction() },
                enabled = !uiState.isLoading() // Disable button during loading
            ) {
                Text("Perform Action")
            }

            // Show loading indicator
            if (uiState.isLoading()) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
```

### State Mapping

The module maps UiState types to SnackBarType as follows:

| UiState         | SnackBarType      | Default Color |
|-----------------|-------------------|---------------|
| UiState.Success | SnackBarType.Success | Green         |
| UiState.Error   | SnackBarType.Error   | Red           |
| UiState.Idle    | SnackBarType.Info    | Blue          |
| UiState.Loading | SnackBarType.Info    | Blue          |

## API Reference

### snackBarType

```kotlin
val UiState?.snackBarType: SnackBarType
```

An extension property that converts a UiState to the appropriate SnackBarType.

## Dependencies

- [UiState](../UiState/README.md) - For UI state management
- [AppSnackBar](../AppSnackBar/README.md) - For type-based snackbar styling

