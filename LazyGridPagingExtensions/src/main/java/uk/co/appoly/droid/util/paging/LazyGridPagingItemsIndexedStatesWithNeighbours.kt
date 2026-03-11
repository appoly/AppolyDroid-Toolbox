package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItemsIndexedWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param errorText The text displayed when an error occurs.
 * @param errorTextSpan The span for the error text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param emptyText The text displayed when the list is empty and not loading.
 * @param emptyTextSpan The span for the empty text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param retry The retry action to perform when an error occurs.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item, this provides the previous item, the index, the item and the next item.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyGridScope.lazyPagingItemsIndexedWithNeighbours
 */
@JvmName("lazyPagingItemsIndexedStatesWithNeighboursWithEmptyText")
inline fun <T : Any> LazyGridScope.lazyPagingItemsIndexedStatesWithNeighbours(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyGridScope.(PaddingValues) -> Unit)? = null,
	noinline emptyText: (@Composable () -> String)?,
	crossinline errorText: @Composable (LoadState.Error) -> String,
	noinline errorTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	noinline emptyTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	noinline itemKey: ((index: Int, item: T) -> Any)? = null,
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (index: Int, item: T) -> Any? = { _, _ -> null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.(prevItem: T?, nextItem: T?) -> Unit) = { _, _ -> },
	crossinline itemContent: @Composable LazyGridItemScope.(prevItem: T?, index: Int, item: T, nextItem: T?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsIndexedStatesWithNeighbours(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	refreshLoadingContent = refreshLoadingContent,
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
	emptyContent = if (emptyText != null) {
		{
			emptyStateItem(
				key = "paging_empty",
				span = emptyTextSpan,
				emptyText = emptyText,
				contentPadding = statesContentPadding
			)
		}
	} else null,
	itemKey = itemKey,
	itemSpan = itemSpan,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItemsIndexedWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param errorText The text displayed when an error occurs.
 * @param errorTextSpan The span for the error text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param retry The retry action to perform when an error occurs.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item, this provides the previous item, the index, the item and the next item.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyGridScope.lazyPagingItemsIndexedWithNeighbours
 */
@JvmName("lazyPagingItemsIndexedStatesWithNeighboursWithEmptyContent")
inline fun <T : Any> LazyGridScope.lazyPagingItemsIndexedStatesWithNeighbours(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyGridScope.(PaddingValues) -> Unit)? = null,
	noinline emptyContent: (LazyGridScope.(PaddingValues) -> Unit)?,
	crossinline errorText: @Composable (LoadState.Error) -> String,
	noinline errorTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	noinline itemKey: ((index: Int, item: T) -> Any)? = null,
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (index: Int, item: T) -> Any? = { _, _ -> null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.(prevItem: T?, nextItem: T?) -> Unit) = { _, _ -> },
	crossinline itemContent: @Composable LazyGridItemScope.(prevItem: T?, index: Int, item: T, nextItem: T?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsIndexedStatesWithNeighbours(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	refreshLoadingContent = refreshLoadingContent,
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
	emptyContent = emptyContent,
	itemKey = itemKey,
	itemSpan = itemSpan,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItemsIndexedWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param errorContent The content displayed when an error occurs, this provides the key and the error, the should be used for
 * the [item][LazyGridScope.item] key as there could show multiple errors for the prepend, append and refresh states.
 * @param emptyText The text displayed when the list is empty and not loading.
 * @param emptyTextSpan The span for the empty text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item, this provides the previous item, the index, the item and the next item.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyGridScope.lazyPagingItemsIndexedWithNeighbours
 */
inline fun <T : Any> LazyGridScope.lazyPagingItemsIndexedStatesWithNeighbours(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyGridScope.(PaddingValues) -> Unit)? = null,
	noinline emptyText: (@Composable () -> String)?,
	crossinline errorContent: LazyGridScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
	noinline emptyTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	noinline itemKey: ((index: Int, item: T) -> Any)? = null,
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (index: Int, item: T) -> Any? = { _, _ -> null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.(prevItem: T?, nextItem: T?) -> Unit) = { _, _ -> },
	crossinline itemContent: @Composable LazyGridItemScope.(prevItem: T?, index: Int, item: T, nextItem: T?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsIndexedStatesWithNeighbours(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	refreshLoadingContent = refreshLoadingContent,
	errorContent = errorContent,
	emptyContent = if (emptyText != null) {
		{
			emptyStateItem(
				key = "paging_empty",
				span = emptyTextSpan,
				emptyText = emptyText,
				contentPadding = statesContentPadding
			)
		}
	} else null,
	itemKey = itemKey,
	itemSpan = itemSpan,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItemsIndexedWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param errorContent The content displayed when an error occurs, this provides the key and the error, the should be used for
 * the [item][LazyGridScope.item] key as there could show multiple errors for the prepend, append and refresh states.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item, this provides the previous item, the index, the item and the next item.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyGridScope.lazyPagingItemsIndexedWithNeighbours
 */
inline fun <T : Any> LazyGridScope.lazyPagingItemsIndexedStatesWithNeighbours(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyGridScope.(PaddingValues) -> Unit)? = null,
	crossinline errorContent: LazyGridScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
	noinline emptyContent: (LazyGridScope.(PaddingValues) -> Unit)?,
	noinline itemKey: ((index: Int, item: T) -> Any)? = null,
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (index: Int, item: T) -> Any? = { _, _ -> null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.(prevItem: T?, nextItem: T?) -> Unit) = { _, _ -> },
	crossinline itemContent: @Composable LazyGridItemScope.(prevItem: T?, index: Int, item: T, nextItem: T?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	refreshLoadingContent = refreshLoadingContent,
	errorContent = errorContent,
	emptyContent = emptyContent,
	itemsContent = {
		lazyPagingItemsIndexedWithNeighbours(
			lazyPagingItems = lazyPagingItems,
			key = itemKey,
			span = itemSpan,
			contentType = itemContentType,
			placeholderItemContent = itemPlaceholderContent,
			itemContent = itemContent
		)
	},
	statesContentPadding = statesContentPadding
)
