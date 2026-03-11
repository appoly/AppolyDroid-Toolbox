# LazyGridPagingExtensions

Extension functions for integrating Jetpack Paging 3 with Compose LazyVerticalGrid and LazyHorizontalGrid components.

## Features

- Easy-to-use extensions for LazyVerticalGrid and LazyHorizontalGrid
- Simplified state handling for loading, error and empty states
- Support for grid spans and spanning the full width when appropriate
- Automatic placeholder management
- Customizable UI through CompositionLocal providers
- Multiple function variants for different use cases

## Installation

```gradle.kts
// Requires the base PagingExtensions module
implementation("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions:1.2.14")
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:1.2.14")

// Make sure to include Jetpack Paging Compose
implementation("androidx.paging:paging-compose:3.4.1")
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
        lazyPagingItemsStates(
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
        lazyPagingItemsStates(
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
            itemPlaceholderContent = {
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
            lazyPagingItemsStates(
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
        lazyPagingItemsStates(
            lazyPagingItems = items,
            emptyContent = { paddingValues ->
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
            errorContent = { errorType, error, paddingValues ->
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

### Using Items with Neighbours

For scenarios where you need access to previous and next items:
The Next and Previous items are accessed with the LazyPagingItems.peek(index: Int) function
so as not to trigger page load operations.

```kotlin
@Composable
fun ItemsWithNeighboursGrid(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2)
    ) {
        lazyPagingItemsStatesWithNeighbours(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            errorText = { error -> error.localizedMessage ?: "An error occurred" },
            itemKey = { it.id },  // Custom key function
            itemContentType = { it.type }  // Content type for recycling
        ) { previousItem, currentItem, nextItem, itemKey, itemContentType ->
            // Access to neighbouring items
            // eg you may want to add additional grid items based on a comparison of
            // the current item with the previous and/or next items like a category separator.
            if (currentItem.category != previousItem?.category) {
                item(
                    key = "category_separator_${currentItem.category}",
                    contentType = "category_separator",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    CategorySeparator(currentItem.category)
                }
            }
            item(
                key = itemKey,// this is the value returned by itemKey function
                contentType = itemContentType// this is the value returned by itemContentType function
            ) {
                ItemCard(
                    item = currentItem,
                    previousItem = previousItem,
                    nextItem = nextItem
                )
            }
        }
    }
}
```

### Using Indexed Items

For scenarios where you need access to the item index:

```kotlin
@Composable
fun IndexedItemsGrid(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2)
    ) {
        lazyPagingItemsIndexedStates(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            errorText = { error -> error.localizedMessage ?: "An error occurred" }
        ) { index, item ->
            // Access to item index
            ItemCardWithIndex(
                index = index,
                item = item
            )
        }
    }
}
```

### Custom Items Content

If you need complete control over how items are rendered:

```kotlin
@Composable
fun CustomItemsGrid(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2)
    ) {
        lazyPagingItemsStates(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            errorText = { error -> "Error: ${error.localizedMessage}" }
        ) { lazyPagingItems ->
            // Custom items rendering logic
            items(lazyPagingItems.itemCount) { index ->
                val item = lazyPagingItems[index]
                if (item != null) {
                    ItemCard(item = item)
                } else {
                    ItemPlaceholder()
                }
            }
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

### lazyPagingItemsStates

Complete solution that handles all states (loading, error, empty) and the items.

```kotlin
fun <T : Any> LazyGridScope.lazyPagingItemsStates(
    lazyPagingItems: LazyPagingItems<T>,
    usingPlaceholders: Boolean = false,
    emptyText: (@Composable () -> String)?,
    errorText: @Composable (LoadState.Error) -> String,
    errorTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
    retry: () -> Unit = { lazyPagingItems.retry() },
    emptyTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
    itemKey: ((item: T) -> Any)? = null,
    itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
    itemContentType: (item: T) -> Any? = { null },
    itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
    itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
    statesContentPadding: PaddingValues = PaddingValues(0.dp)
)
```

### lazyPagingItemsStates (with custom content)

Version that allows custom error and empty content handling:

```kotlin
fun <T : Any> LazyGridScope.lazyPagingItemsStates(
    lazyPagingItems: LazyPagingItems<T>,
    usingPlaceholders: Boolean = false,
    errorContent: LazyGridScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
    emptyContent: (LazyGridScope.(PaddingValues) -> Unit)?,
    itemKey: ((item: T) -> Any)? = null,
    itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
    itemContentType: (item: T) -> Any? = { null },
    itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
    itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
    statesContentPadding: PaddingValues = PaddingValues(0.dp)
)
```

### lazyPagingItemsStates (with items content)

Version that provides complete control over items rendering:

```kotlin
fun <T : Any> LazyGridScope.lazyPagingItemsStates(
    lazyPagingItems: LazyPagingItems<T>,
    usingPlaceholders: Boolean = false,
    emptyText: @Composable () -> String,
    errorText: @Composable (LoadState.Error) -> String,
    retry: () -> Unit = { lazyPagingItems.retry() },
    itemsContent: LazyGridScope.(lazyPagingItems: LazyPagingItems<T>) -> Unit,
    statesContentPadding: PaddingValues = PaddingValues(0.dp)
)
```

### Additional Variants

- **lazyPagingItemsIndexed**: Basic indexed items without state management
- **lazyPagingItemsStatesWithNeighbours**: Access to previous and next items
- **lazyPagingItemsIndexedStates**: Access to item indices
- **lazyPagingItemsIndexedStatesWithNeighbours**: Access to both indices and neighbouring items
- **lazyPagingItemsWithNeighbours**: Basic neighbour access without state management
- **lazyPagingItemsIndexedWithNeighbours**: Indexed neighbour access without state management

## Migration from `lazyPagingItemsWithStates`

The old `lazyPagingItemsWithStates` functions have been deprecated and renamed to `lazyPagingItemsStates` for consistency with the LazyListPagingExtensions module. The deprecated functions will continue to work but show a deprecation warning with a `ReplaceWith` suggestion.

Key changes:
- `lazyPagingItemsWithStates` → `lazyPagingItemsStates`
- `emptyText` and `emptyContent` are now nullable (pass `null` to skip the empty state)
- New `refreshLoadingContent` parameter for custom refresh loading UI

## Helper Functions

### loadingStateItem

Adds a loading state item to the grid with customizable span.

```kotlin
fun LazyGridScope.loadingStateItem(
    key: Any,
    span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
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
    span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
)
```

### emptyStateItem

Adds an empty state item to the grid with customizable span.

```kotlin
fun LazyGridScope.emptyStateItem(
    key: Any,
    emptyText: @Composable () -> String,
    span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
)
```

## Dependencies

- [PagingExtensions](../PagingExtensions/README.md) - Core paging utility module
- [Jetpack Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) - Android paging library
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Android UI toolkit
