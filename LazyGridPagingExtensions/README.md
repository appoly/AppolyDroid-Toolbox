# LazyGridPagingExtensions

Extension functions for integrating Jetpack Paging 3 with Compose LazyVerticalGrid and LazyHorizontalGrid components.

## Features

- Easy-to-use extensions for LazyVerticalGrid and LazyHorizontalGrid
- Simplified state handling for loading, error and empty states
- Support for grid spans and spanning the full width when appropriate
- Automatic placeholder management
- Customizable UI through CompositionLocal providers

## Installation

```gradle.kts
// Requires the base PagingExtensions module
implementation("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions:1.0.33")
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:1.0.33")

// Make sure to include Jetpack Paging Compose
implementation("androidx.paging:paging-compose:3.3.6")
```

## Usage

### Basic Implementation

```kotlin
@Composable
fun ItemsGrid(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2)
    ) {
        lazyPagingItemsWithStates(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            errorText = { error -> error.localizedMessage ?: "An error occurred" }
        ) { item ->
            // Your grid item composable here
            ItemCard(item = item)
        }
    }
}
```

### Advanced Implementation with Custom Spans

```kotlin
@Composable
fun AdvancedItemsGrid(viewModel: AdvancedViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp)
    ) {
        lazyPagingItemsWithStates(
            lazyPagingItems = items,
            usingPlaceholders = true,  // Enable placeholders support
            emptyText = { "No items found" },
            errorText = { error -> error.localizedMessage ?: "An error occurred" },
            retry = { items.retry() },  // Custom retry action
            itemKey = { it.id },  // Custom key function
            // Custom span based on item properties
            itemSpan = { item ->
                if (item != null && item.isFullWidth) {
                    GridItemSpan(maxLineSpan)  // Full width for featured items
                } else {
                    GridItemSpan(1)  // Default span
                }
            },
            itemContentType = { it.type },  // Content type for recycling
            placeholderItemContent = {
                // Custom placeholder UI
                ItemPlaceholder()
            }
        ) { item ->
            // Item UI
            ItemCard(item = item)
        }
    }
}
```

### Custom Empty State with Custom Span

```kotlin
@Composable
fun CustomEmptyStateGrid(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3)
    ) {
        lazyPagingItemsWithStates(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            // Make empty state span all columns
            emptyTextSpan = { GridItemSpan(maxLineSpan) },
            errorText = { error -> error.localizedMessage ?: "An error occurred" },
            // Make error state span all columns
            errorTextSpan = { GridItemSpan(maxLineSpan) }
        ) { item ->
            ItemCard(item = item)
        }
    }
}
```

### Custom Loading, Error, and Empty States

```kotlin
@Composable
fun CustomStatesItemsGrid(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    // Provide custom UI providers
    CompositionLocalProvider(
        LocalLoadingState provides MyLoadingStateProvider(),
        LocalErrorState provides MyErrorStateProvider(),
        LocalEmptyState provides MyEmptyStateProvider()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2)
        ) {
            lazyPagingItemsWithStates(
                lazyPagingItems = items,
                emptyText = { "No items found" },
                errorText = { error -> error.localizedMessage ?: "An error occurred" }
            ) { item ->
                ItemCard(item = item)
            }
        }
    }
}
```

### Custom Error Handling

```kotlin
@Composable
fun ErrorHandlingGrid(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2)
    ) {
        lazyPagingItemsWithStates(
            lazyPagingItems = items,
            emptyContent = {
                item(
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Text(
                        text = "No items found",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            },
            errorContent = { errorType, error ->
                item(
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    when (errorType) {
                        PagingErrorType.REFRESH -> FullScreenError(error, items::retry)
                        PagingErrorType.APPEND -> AppendError(error, items::retry)
                        PagingErrorType.PREPEND -> PrependError(error, items::retry)
                    }
                }
            }
        ) { item ->
            ItemCard(item = item)
        }
    }
}
```

## Main Functions

### lazyPagingItems

Simplified way to add items from a LazyPagingItems instance to a LazyVerticalGrid/LazyHorizontalGrid.

```kotlin
fun <T : Any> LazyGridScope.lazyPagingItems(
    lazyPagingItems: LazyPagingItems<T>,
    key: ((item: T) -> Any)? = null,
    span: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
    contentType: (item: T) -> Any? = { null },
    placeholderItemContent: @Composable (LazyGridItemScope.() -> Unit) = {},
    itemContent: @Composable LazyGridItemScope.(item: T) -> Unit
)
```

### lazyPagingItemsWithStates

Complete solution that handles all states (loading, error, empty) and the items.

```kotlin
fun <T : Any> LazyGridScope.lazyPagingItemsWithStates(
    lazyPagingItems: LazyPagingItems<T>,
    usingPlaceholders: Boolean = false,
    errorText: @Composable (LoadState.Error) -> String,
    errorTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
    retry: () -> Unit = { lazyPagingItems.retry() },
    emptyText: @Composable () -> String,
    emptyTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
    itemKey: ((item: T) -> Any)? = null,
    itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
    itemContentType: (item: T) -> Any? = { null },
    placeholderItemContent: @Composable (LazyGridItemScope.() -> Unit) = {},
    itemContent: @Composable LazyGridItemScope.(item: T) -> Unit
)
```

## Helper Functions

### loadingStateItem

Adds a loading state item to the grid with customizable span.

```kotlin
fun LazyGridScope.loadingStateItem(
    key: Any,
    span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null
)
```

### errorStateItem

Adds an error state item to the grid with customizable span.

```kotlin
fun LazyGridScope.errorStateItem(
    key: Any,
    error: LoadState.Error,
    errorText: @Composable (LoadState.Error) -> String,
    retry: () -> Unit,
    span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null
)
```

### emptyStateItem

Adds an empty state item to the grid with customizable span.

```kotlin
fun LazyGridScope.emptyStateItem(
    key: Any,
    emptyText: @Composable () -> String,
    span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null
)
```

## Dependencies

- [PagingExtensions](../PagingExtensions/README.md) - Core paging utility module
- [Jetpack Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) - Android paging library
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Android UI toolkit

