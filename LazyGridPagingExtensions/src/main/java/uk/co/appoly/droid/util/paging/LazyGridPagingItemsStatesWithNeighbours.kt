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
 * This function uses [LazyGridScope.lazyPagingItemsWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [item] to it.
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
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param item The content displayed by a single item, this provides the previous item, the current item, the next item, the item key and the item content type.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyGridScope.lazyPagingItemsWithNeighbours
 */
@JvmName("lazyPagingItemsStatesWithNeighboursWithEmptyText")
inline fun <T : Any> LazyGridScope.lazyPagingItemsStatesWithNeighbours(
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
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline item: LazyGridScope.(prevItem: T?, item: T, nextItem: T?, itemKey: Any?, itemContentType: Any?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStatesWithNeighbours(
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
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	item = item,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItemsWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [item] to it.
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
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param item The content displayed by a single item, this provides the previous item, the current item, the next item, the item key and the item content type.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyGridScope.lazyPagingItemsWithNeighbours
 */
@JvmName("lazyPagingItemsStatesWithNeighboursWithEmptyContent")
inline fun <T : Any> LazyGridScope.lazyPagingItemsStatesWithNeighbours(
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
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline item: LazyGridScope.(prevItem: T?, item: T, nextItem: T?, itemKey: Any?, itemContentType: Any?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStatesWithNeighbours(
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
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	item = item,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItemsWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [item] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param emptyText The text displayed when the list is empty and not loading.
 * @param emptyTextSpan The span for the empty text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param errorContent The content displayed when an error occurs, this provides the key and the error, the should be used for
 * the [item][LazyGridScope.item] key as there could show multiple errors for the prepend, append and refresh states.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param item The content displayed by a single item, this provides the previous item, the current item, the next item, the item key and the item content type.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyGridScope.lazyPagingItemsWithNeighbours
 */
inline fun <T : Any> LazyGridScope.lazyPagingItemsStatesWithNeighbours(
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
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline item: LazyGridScope.(prevItem: T?, item: T, nextItem: T?, itemKey: Any?, itemContentType: Any?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStatesWithNeighbours(
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
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	item = item,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyGridScope.lazyPagingItemsWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [item] to it.
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
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param item The content displayed by a single item, this provides the previous item, the current item, the next item, the item key and the item content type.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyGridScope.lazyPagingItemsWithNeighbours
 */
inline fun <T : Any> LazyGridScope.lazyPagingItemsStatesWithNeighbours(
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
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline item: LazyGridScope.(prevItem: T?, item: T, nextItem: T?, itemKey: Any?, itemContentType: Any?) -> Unit,
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
		lazyPagingItemsWithNeighbours(
			lazyPagingItems = lazyPagingItems,
			key = itemKey,
			contentType = itemContentType,
			placeholderItemContent = itemPlaceholderContent,
			item = item
		)
	},
	statesContentPadding = statesContentPadding
)
