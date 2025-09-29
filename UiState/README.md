# UiState

A standardized UI state management library for Android applications, providing consistent state representation for loading, success, and error scenarios.

## Features

- Simple sealed class design for UI state management
- Support for tracking multiple operations with key identifiers
- Null-safety with smart casting through Kotlin contracts
- Type-safe extension functions for state checking
- Lightweight with zero dependencies

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:UiState:1.0.33")
```

## Usage

### Basic UI State Management

```kotlin
class MyViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle())
    val uiState = _uiState.asStateFlow()

    fun loadData() {
        // Update state to Loading
        _uiState.value = UiState.Loading()

        viewModelScope.launch {
            try {
                val result = repository.fetchData()
                // Update state to Success
                _uiState.value = UiState.Success()
            } catch (e: Exception) {
                // Update state to Error with message
                _uiState.value = UiState.Error("Failed to load data: ${e.message}")
            }
        }
    }
}
```

### Using Smart Cast Extensions

The library includes extension functions that use Kotlin contracts to enable smart casting:

```kotlin
// In your Composable
val uiState = viewModel.uiState.collectAsState().value

// Smart casting with extension functions
if (uiState.isError()) {
    // uiState is automatically cast to UiState.Error
    Text("Error: ${uiState.message}")
} else if (uiState.isLoading()) {
    // uiState is automatically cast to UiState.Loading
    CircularProgressIndicator()
} else if (uiState.isSuccess()) {
    // uiState is automatically cast to UiState.Success
    Text("Data loaded successfully!")
}
```

### Tracking Multiple Operations

You can track multiple operations using the `key` property:

```kotlin
class MyViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle())
    val uiState = _uiState.asStateFlow()

    fun loadUserProfile(userId: String) {
        _uiState.value = UiState.Loading(key = "profile")
        // ...fetch profile...
    }

    fun loadUserPosts(userId: String) {
        _uiState.value = UiState.Loading(key = "posts")
        // ...fetch posts...
    }
}

// In your UI
val uiState = viewModel.uiState.collectAsState().value

// Check state for specific operation
when {
    uiState.isLoading() && uiState.key == "profile" -> {
        // Show profile loading UI
    }
    uiState.isLoading() && uiState.key == "posts" -> {
        // Show posts loading UI
    }
    uiState.isError() && uiState.key == "profile" -> {
        // Show profile error UI
    }
}
```

## Available States

| State | Description | Properties |
|-------|-------------|------------|
| `Idle` | Initial state before any operation | `key: Any?` |
| `Loading` | Operation in progress | `key: Any?` |
| `Success` | Operation completed successfully | `key: Any?` |
| `Error` | Operation failed | `message: String`, `key: Any?` |

## Extension Functions

| Function | Description | Smart Cast |
|----------|-------------|-----------|
| `isIdle()` | Checks if state is Idle | Yes, to UiState.Idle |
| `isLoading()` | Checks if state is Loading | Yes, to UiState.Loading |
| `isNotLoading()` | Checks if state is not Loading | Yes |
| `isSuccess()` | Checks if state is Success | Yes, to UiState.Success |
| `isError()` | Checks if state is Error | Yes, to UiState.Error |
| `isNotError()` | Checks if state is not Error | Yes |

## Integration with Other Modules

UiState works well with other Appoly modules:

- [AppSnackBar-UiState](../AppSnackBar-UiState/README.md) - For automatic Snackbar display based on UI state
- [BaseRepo](../BaseRepo/README.md) - For connecting repository results with UI state

