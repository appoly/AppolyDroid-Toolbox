# LazyListPagingExtensions

Extension functions for easy integration of Jetpack Paging 3 with Compose LazyColumn and LazyRow components.

## Features

- Easy-to-use extensions for LazyColumn and LazyRow
- Simplified state handling for loading, error and empty states
- Automatic placeholder management
- Customizable UI through CompositionLocal providers
- Supports standard Jetpack Paging patterns
- Multiple function variants for different use cases

## Installation

```gradle.kts
// Requires the base PagingExtensions module
implementation("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions:1.0.34")
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:1.0.34")

// Make sure to include Jetpack Paging Compose
implementation("androidx.paging:paging-compose:3.3.6")
```

## Usage

### Basic Implementation

```kotlin
@Composable
fun ItemsList(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyColumn {
        lazyPagingItemsStates(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            errorText = { error -> error.localizedMessage ?: "An error occurred" }
        ) { item ->
            // Your item composable here
            ItemRow(item = item)
        }
    }
}
```

### Advanced Implementation with Custom Keys and Placeholders

```kotlin
@Composable
fun AdvancedItemsList(viewModel: AdvancedViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyColumn {
        lazyPagingItemsStates(
            lazyPagingItems = items,
            usingPlaceholders = true,  // Enable placeholders support
            emptyText = { "No items found" },
            errorText = { error -> error.localizedMessage ?: "An error occurred" },
            retry = { items.retry() },  // Custom retry action
            itemKey = { it.id },  // Custom key function
            itemContentType = { it.type },  // Content type for recycling
            itemPlaceholderContent = {
                // Custom placeholder UI
                ItemPlaceholder()
            }
        ) { item ->
            // Item UI
            ItemRow(item = item)
        }
    }
}
```

### Custom Loading, Error, and Empty States

```kotlin
@Composable
fun CustomStatesItemsList(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    // Provide custom UI providers
    CompositionLocalProvider(
        LocalLoadingState provides MyLoadingStateProvider(),
        LocalErrorState provides MyErrorStateProvider(),
        LocalEmptyState provides MyEmptyStateProvider()
    ) {
        LazyColumn {
            lazyPagingItemsStates(
                lazyPagingItems = items,
                emptyText = { "No items found" },
                errorText = { error -> error.localizedMessage ?: "An error occurred" }
            ) { item ->
                ItemRow(item = item)
            }
        }
    }
}
```

### Custom Error Handling

```kotlin
@Composable
fun ErrorHandlingList(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyColumn {
        lazyPagingItemsStates(
            lazyPagingItems = items,
            emptyContent = { paddingValues ->
                item(key = "empty") {
                    Text(
                        text = "No items found",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            },
            errorContent = { errorType, error, paddingValues ->
                item(key = "${errorType}_error") {
                    when (errorType) {
                        PagingErrorType.REFRESH -> FullScreenError(error, items::retry)
                        PagingErrorType.APPEND -> AppendError(error, items::retry)
                        PagingErrorType.PREPEND -> PrependError(error, items::retry)
                    }
                }
            }
        ) { item ->
            ItemRow(item = item)
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
fun ItemsWithNeighboursList(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyColumn {
        lazyPagingItemsStatesWithNeighbours(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            errorText = { error -> error.localizedMessage ?: "An error occurred" },
            itemKey = { it.id },  // Custom key function
            itemContentType = { it.type }  // Content type for recycling
        ) { previousItem, currentItem, nextItem, itemKey, itemContentType ->
            // Access to neighbouring items
            // eg yuo may want to add additional list Items based on a comparison of
            // the current item with the previous and/or next items like a date separator.
            if(currentItem.date != previousItem?.date) {
                item(
                    key = "date_separator_${currentItem.date}",
                    contentType = "date_separator"
                ) {
                    DateSeparator(currentItem.date)
                }
            }
            item (
                key = itemKey,// this is the value returned by itemKey function
                contentType = itemContentType// this is the value returned by itemContentType function
            ) {
                ItemRowWithContext(
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
fun IndexedItemsList(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyColumn {
        lazyPagingItemsIndexedStates(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            errorText = { error -> error.localizedMessage ?: "An error occurred" }
        ) { index, item ->
            // Access to item index
            ItemRowWithIndex(
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
fun CustomItemsList(viewModel: ItemsViewModel) {
    val items = viewModel.itemsFlow.collectAsLazyPagingItems()

    LazyColumn {
        lazyPagingItemsStates(
            lazyPagingItems = items,
            emptyText = { "No items found" },
            errorText = { error -> "Error: ${error.localizedMessage}" }
        ) { lazyPagingItems ->
            // Custom items rendering logic
            items(lazyPagingItems.itemCount) { index ->
                val item = lazyPagingItems[index]
                if (item != null) {
                    ItemRow(item = item)
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

Simplified way to add items from a LazyPagingItems instance to a LazyColumn/LazyRow.

```kotlin
fun <T : Any> LazyListScope.lazyPagingItems(
    lazyPagingItems: LazyPagingItems<T>,
    key: ((item: T) -> Any)? = null,
    contentType: (item: T) -> Any? = { null },
    placeholderItemContent: @Composable (LazyItemScope.() -> Unit) = {},
    itemContent: @Composable LazyItemScope.(item: T) -> Unit
)
```

### lazyPagingItemsStates

Complete solution that handles all states (loading, error, empty) and the items.

```kotlin
fun <T : Any> LazyListScope.lazyPagingItemsStates(
    lazyPagingItems: LazyPagingItems<T>,
    usingPlaceholders: Boolean = false,
    emptyText: (@Composable () -> String)?,
    errorText: @Composable (LoadState.Error) -> String,
    retry: () -> Unit = { lazyPagingItems.retry() },
    itemKey: ((item: T) -> Any)? = null,
    itemContentType: (item: T) -> Any? = { null },
    itemPlaceholderContent: @Composable (LazyItemScope.() -> Unit) = {},
    itemContent: @Composable LazyItemScope.(item: T) -> Unit,
    statesContentPadding: PaddingValues = PaddingValues(0.dp)
)
```

### lazyPagingItemsStates (with custom content)

Version that allows custom error and empty content handling:

```kotlin
fun <T : Any> LazyListScope.lazyPagingItemsStates(
    lazyPagingItems: LazyPagingItems<T>,
    usingPlaceholders: Boolean = false,
    errorContent: LazyListScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
    emptyContent: (LazyListScope.(PaddingValues) -> Unit)?,
    itemKey: ((item: T) -> Any)? = null,
    itemContentType: (item: T) -> Any? = { null },
    itemPlaceholderContent: @Composable (LazyItemScope.() -> Unit) = {},
    itemContent: @Composable LazyItemScope.(item: T) -> Unit,
    statesContentPadding: PaddingValues = PaddingValues(0.dp)
)
```

### lazyPagingItemsStates (with items content)

Version that provides complete control over items rendering:

```kotlin
fun <T : Any> LazyListScope.lazyPagingItemsStates(
    lazyPagingItems: LazyPagingItems<T>,
    usingPlaceholders: Boolean = false,
    emptyText: @Composable () -> String,
    errorText: @Composable (LoadState.Error) -> String,
    retry: () -> Unit = { lazyPagingItems.retry() },
    itemsContent: LazyListScope.(lazyPagingItems: LazyPagingItems<T>) -> Unit,
    statesContentPadding: PaddingValues = PaddingValues(0.dp)
)
```

### Additional Variants

- **lazyPagingItemsStatesWithNeighbours**: Access to previous and next items
- **lazyPagingItemsIndexedStates**: Access to item indices
- **lazyPagingItemsIndexedStatesWithNeighbours**: Access to both indices and neighbouring items
- **lazyPagingItemsWithNeighbours**: Basic neighbour access without state management

## Dependencies

- [PagingExtensions](../PagingExtensions/README.md) - Core paging utility module
- [Jetpack Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) - Android paging library
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Android UI toolkit

