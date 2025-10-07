# PagingExtensions

Core utilities and extensions for Jetpack Paging 3 integration, providing the foundation for both LazyListPagingExtensions and LazyGridPagingExtensions modules.

## Features

- Type-safe LoadState extensions
- Error handling utilities
- Common UI state components (loading, error, empty)
- Customizable state representation through CompositionLocal providers

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions:1.0.34")
```

## Usage

### Extension Functions

The module provides extension functions to check LoadState types with Kotlin contracts for smart casting:

```kotlin
// Check if a LoadState is a loading state
if (loadState.isLoading()) {
    // loadState is automatically cast to LoadState.Loading
    showLoadingIndicator()
}

// Check if a LoadState is an error state
if (loadState.isError()) {
    // loadState is automatically cast to LoadState.Error
    showErrorMessage(loadState.error)
}
```

### Error Type Classification

The `PagingErrorType` enum helps you classify different types of paging errors:

```kotlin
when (errorType) {
    PagingErrorType.PREPEND -> showHeaderError(error)
    PagingErrorType.APPEND -> showFooterError(error)
    PagingErrorType.REFRESH -> showFullScreenError(error)
}
```

### Customizing UI Components

#### Loading State

You can customize the loading state appearance by providing your own implementation:

```kotlin
CompositionLocalProvider(
    LocalLoadingState provides object : LoadingStateProvider {
        @Composable
        override fun LoadingState(modifier: Modifier) {
            // Your custom loading UI implementation
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text("Loading...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
) {
    // Your composables that will use the custom loading state
    MyPagingList()
}
```

#### Error State

Provide custom error state handling:

```kotlin
CompositionLocalProvider(
    LocalErrorState provides object : ErrorStateProvider {
        @Composable
        override fun ErrorState(
            modifier: Modifier,
            text: @Composable () -> Unit,
            onRetry: (() -> Unit)?
        ) {
            // Your custom error UI implementation
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                text()
                if (onRetry != null) {
                    Button(onClick = onRetry) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
) {
    // Your composables that will use the custom error state
    MyPagingList()
}
```

#### Empty State

Customize the empty state appearance:

```kotlin
CompositionLocalProvider(
    LocalEmptyState provides object : EmptyStateTextProvider {
        @Composable
        override fun EmptyStateText(modifier: Modifier, text: String) {
            // Your custom empty state UI implementation
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
) {
    // Your composables that will use the custom empty state
    MyPagingList()
}
```

## Components

### EmptyStateTextProvider

Interface for providing custom empty state UI:

```kotlin
interface EmptyStateTextProvider {
    @Composable
    fun EmptyStateText(modifier: Modifier, text: String)
}
```

### ErrorStateProvider

Interface for providing custom error state UI:

```kotlin
interface ErrorStateProvider {
    @Composable
    fun ErrorState(modifier: Modifier, text: String, onRetry: (() -> Unit)?)

    @Composable
    fun ErrorState(modifier: Modifier, text: @Composable () -> Unit, onRetry: (() -> Unit)?)
}
```

### LoadingStateProvider

Interface for providing custom loading state UI:

```kotlin
interface LoadingStateProvider {
    @Composable
    fun LoadingState(modifier: Modifier)
}
```

## Integration with Other Modules

This module provides the foundation for:

- [LazyListPagingExtensions](../LazyListPagingExtensions/README.md) - For LazyColumn and LazyRow components
- [LazyGridPagingExtensions](../LazyGridPagingExtensions/README.md) - For LazyVerticalGrid and LazyHorizontalGrid components

## Dependencies

- Jetpack Compose
- [Jetpack Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)

