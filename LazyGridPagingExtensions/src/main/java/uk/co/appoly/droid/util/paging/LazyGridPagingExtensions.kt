/**
 * LazyGridPagingExtensions for Jetpack Compose
 *
 * This module provides extension functions for the LazyGridScope to work with Paging 3 library's LazyPagingItems.
 * It offers convenient utilities to handle common grid-based paging scenarios including:
 *
 * - Displaying paging items in a grid layout with proper key and content type handling
 * - Managing grid item spans to control layout width across columns
 * - Handling loading states (prepend, append, refresh)
 * - Displaying error states with retry functionality
 * - Showing empty state when no items are available
 *
 * The extensions are designed to work with composition locals defined in the UiState module
 * (LocalLoadingState, LocalErrorState, LocalEmptyState) for consistent UI presentation.
 */
package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import uk.co.appoly.droid.ui.paging.LocalEmptyState
import uk.co.appoly.droid.ui.paging.LocalErrorState
import uk.co.appoly.droid.ui.paging.LocalLoadingState


/**
 * Adds a list of items from a [LazyPagingItems] object.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source
 * @param key a factory of stable and unique keys representing the item. Using the same key
 * for multiple items in the list is not allowed. Type of the key should be saveable
 * via Bundle on Android. If null is passed the position in the list will represent the key.
 * When you specify the key the scroll position will be maintained based on the key, which
 * means if you add/remove items before the current visible item the item with the given key
 * will be kept as the first visible one. This can be overridden by calling
 * 'requestScrollToItem' on the 'LazyListState'.
 * @param span a factory of the span for the item. The span of the item could be changed
 * dynamically based on the item data. If null is passed the span will be 1.
 * @param contentType a factory of the content types for the item. The item compositions of
 * the same type could be reused more efficiently. Note that null is a valid type and items of such
 * type will be considered compatible.
 * @param itemContent the content displayed by a single item
 * @param placeholderItemContent the content displayed by a single placeholder item
 *
 * @see LazyGridScope.items
 */
inline fun <T : Any> LazyGridScope.lazyPagingItems(
	lazyPagingItems: LazyPagingItems<T>,
	noinline key: ((item: T) -> Any)? = null,
	noinline span: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline contentType: (item: T) -> Any? = { null },
	crossinline placeholderItemContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit
) = items(
	count = lazyPagingItems.itemCount,
	key = if (key != null) lazyPagingItems.itemKey { key(it) } else null,
	span = if (span != null) {
		{ span(lazyPagingItems.peek(it)) }
	} else null,
	contentType = lazyPagingItems.itemContentType { contentType(it) }
) {
	val item = lazyPagingItems[it]
	if (item != null) {
		itemContent(item)
	} else {
		placeholderItemContent()
	}
}

/**
 * Adds a loading state item to the grid.
 *
 * This item is displayed when the data is being loaded, typically used for prepend or append loading states.
 *
 * @param key The unique key for the loading state item.
 * @param span The span for the loading state item, defaults to spanning all columns.
 * @param contentPadding The padding applied to the loading state item.
 */
fun LazyGridScope.loadingStateItem(
	key: Any,
	span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null,
	contentPadding: PaddingValues = PaddingValues(0.dp)
) {
	item(
		key = key,
		contentType = key,
		span = span
	) {
		LocalLoadingState.current.LoadingState(
			modifier = Modifier
				.animateItem()
				.fillMaxWidth(),
			contentPadding = contentPadding
		)
	}
}

/**
 * Adds an error state item to the grid.
 *
 * This item is displayed when an error occurs while loading data, typically used for prepend or append errors.
 *
 * @param key The unique key for the error state item.
 * @param error The [LoadState.Error] object containing the error details.
 * @param errorText A composable function that provides the text to display for the error.
 * @param retry The action to retry loading data.
 * @param span The span for the error state item, defaults to spanning all columns.
 * @param contentPadding The padding applied to the error state item.
 */
inline fun LazyGridScope.errorStateItem(
	key: Any,
	error: LoadState.Error,
	crossinline errorText: @Composable (LoadState.Error) -> String,
	crossinline retry: () -> Unit,
	noinline span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null,
	contentPadding: PaddingValues = PaddingValues(0.dp)
) {
	item(
		key = "${key}_Paging_error",
		contentType = "${key}_Paging_error",
		span = span
	) {
		LocalErrorState.current.ErrorState(
			modifier = Modifier
				.animateItem()
				.fillMaxWidth(),
			text = errorText(error),
			onRetry = {
				retry()
			},
			contentPadding = contentPadding
		)
	}
}

/**
 * Adds an empty state item to the grid.
 *
 * This item is displayed when there are no items to show, typically used when the data source is empty.
 *
 * @param key The unique key for the empty state item.
 * @param emptyText A composable function that provides the text to display for the empty state.
 * @param span The span for the empty state item, defaults to spanning all columns.
 * @param contentPadding The padding applied to the empty state item.
 */
inline fun LazyGridScope.emptyStateItem(
	key: Any,
	crossinline emptyText: @Composable () -> String,
	noinline span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null,
	contentPadding: PaddingValues = PaddingValues(0.dp)
) {
	item(
		key = key,
		contentType = key,
		span = span
	) {
		LocalEmptyState.current.EmptyStateText(
			modifier = Modifier
				.animateItem()
				.fillMaxWidth(),
			text = emptyText(),
			contentPadding = contentPadding
		)
	}
}

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param errorText The text displayed when an error occurs.
 * @param errorTextSpan The span for the error text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param retry The retry action for the error.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 *
 * @see LazyGridScope.lazyPagingItems
 */
@JvmName("lazyPagingItemsWithStatesWithEmptyContent")
inline fun <T : Any> LazyGridScope.lazyPagingItemsWithStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline errorText: @Composable (LoadState.Error) -> String,
	noinline errorTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	crossinline emptyContent: LazyGridScope.(PaddingValues) -> Unit,
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsWithStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	emptyContent = emptyContent,
	errorContent = { key, error, paddingValues ->
		errorStateItem(
			key = key,
			span = errorTextSpan,
			error = error,
			errorText = errorText,
			retry = retry,
			contentPadding = paddingValues
		)
	},
	itemKey = itemKey,
	itemSpan = itemSpan,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param errorText The text displayed when an error occurs.
 * @param errorTextSpan The span for the error text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param retry The retry action for the error.
 * @param emptyText The text displayed when the list is empty and not loading.
 * @param emptyTextSpan The span for the empty text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 *
 * @see LazyGridScope.lazyPagingItems
 */
@JvmName("lazyPagingItemsWithStatesWithEmptyText")
inline fun <T : Any> LazyGridScope.lazyPagingItemsWithStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline errorText: @Composable (LoadState.Error) -> String,
	noinline errorTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	crossinline emptyText: @Composable () -> String,
	noinline emptyTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsWithStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	emptyContent = {
		emptyStateItem(
			key = "paging_empty",
			span = emptyTextSpan,
			emptyText = emptyText,
			contentPadding = statesContentPadding
		)
	},
	errorContent = { key, error, paddingValues ->
		errorStateItem(
			key = key,
			span = errorTextSpan,
			error = error,
			errorText = errorText,
			retry = retry,
			contentPadding = paddingValues
		)
	},
	itemKey = itemKey,
	itemSpan = itemSpan,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param errorContent The content displayed when an error occurs, this provides the key and the error, the should be used for
 * the [item][LazyGridScope.item] key as there could show multiple errors for the prepend, append and refresh states.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 *
 * @see LazyGridScope.lazyPagingItems
 */
inline fun <T : Any> LazyGridScope.lazyPagingItemsWithStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline errorContent: LazyGridScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
	crossinline emptyContent: LazyGridScope.(PaddingValues) -> Unit,
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsWithStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	errorContent = errorContent,
	emptyContent = emptyContent,
	itemsContent = {
		lazyPagingItems(
			lazyPagingItems = lazyPagingItems,
			key = itemKey,
			span = itemSpan,
			contentType = itemContentType,
			placeholderItemContent = itemPlaceholderContent,
			itemContent = itemContent
		)
	},
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param errorText The text displayed when an error occurs.
 * @param errorTextSpan The span for the error text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param emptyText The text displayed when the list is empty and not loading.
 * @param emptyTextSpan The span for the empty text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param itemsContent The content displayed for the items.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 */
@JvmName("lazyPagingItemsWithStatesWithEmptyTextAndItemsContent")
inline fun <T : Any> LazyGridScope.lazyPagingItemsWithStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline errorText: @Composable (LoadState.Error) -> String,
	noinline errorTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	crossinline emptyText: @Composable () -> String,
	noinline emptyTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	crossinline itemsContent: LazyGridScope.(lazyPagingItems: LazyPagingItems<T>) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsWithStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	emptyContent = {
		emptyStateItem(
			key = "paging_empty",
			span = emptyTextSpan,
			emptyText = emptyText,
			contentPadding = statesContentPadding
		)
	},
	errorContent = { key, error, paddingValues ->
		errorStateItem(
			key = key,
			span = errorTextSpan,
			error = error,
			errorText = errorText,
			retry = retry,
			contentPadding = paddingValues
		)
	},
	itemsContent = itemsContent,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param errorContent The content displayed when an error occurs, this provides the key and the error, the should be used for
 * the [item][LazyGridScope.item] key as there could show multiple errors for the prepend, append and refresh states.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param itemsContent The content displayed for the items.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 */
@JvmName("lazyPagingItemsWithStatesWithEmptyContentAndItemsContent")
inline fun <T : Any> LazyGridScope.lazyPagingItemsWithStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline errorContent: LazyGridScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
	crossinline emptyContent: LazyGridScope.(PaddingValues) -> Unit,
	crossinline itemsContent: LazyGridScope.(lazyPagingItems: LazyPagingItems<T>) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) {
	val prependState = lazyPagingItems.loadState.prepend
	val appendState = lazyPagingItems.loadState.append
	val refreshState = lazyPagingItems.loadState.refresh
	val loading = (listOf(prependState, appendState, refreshState).firstOrNull { it.isLoading() } as LoadState.Loading?) != null
	if (!loading && refreshState.isError()) {
		//show the refresh error first
		errorContent(PagingErrorType.REFRESH, refreshState, statesContentPadding)
		//then the items if any
		if (lazyPagingItems.itemCount > 0) {
			itemsContent(lazyPagingItems)
		}
	} else {
		if (!usingPlaceholders && prependState.isLoading()) {
			prependLoadingContent(statesContentPadding)
		} else if (prependState.isError()) {
			errorContent(PagingErrorType.PREPEND, prependState, statesContentPadding)
		}

		if (lazyPagingItems.itemCount == 0 && !loading) {
			emptyContent(statesContentPadding)
		} else {
			itemsContent(lazyPagingItems)
		}

		if (!usingPlaceholders && appendState.isLoading()) {
			appendLoadingContent(statesContentPadding)
		} else if (appendState.isError()) {
			errorContent(PagingErrorType.APPEND, appendState, statesContentPadding)
		}
	}
}
