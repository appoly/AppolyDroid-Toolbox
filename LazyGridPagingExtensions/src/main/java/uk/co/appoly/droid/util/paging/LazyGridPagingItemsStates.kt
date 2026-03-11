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
 * This function uses [LazyGridScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
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
@JvmName("lazyPagingItemsStatesWithEmptyText")
inline fun <T : Any> LazyGridScope.lazyPagingItemsStates(
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
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStates(
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
 * This function uses [LazyGridScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param errorText The text displayed when an error occurs.
 * @param errorTextSpan The span for the error text. Defaults to [GridItemSpan] with [maxLineSpan][LazyGridItemSpanScope.maxLineSpan] to span all columns.
 * @param retry The retry action for the error.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 *
 * @see LazyGridScope.lazyPagingItems
 */
@JvmName("lazyPagingItemsStatesWithEmptyContent")
inline fun <T : Any> LazyGridScope.lazyPagingItemsStates(
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
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStates(
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
 * This function uses [LazyGridScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
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
 * @param itemSpan The span for the item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 *
 * @see LazyGridScope.lazyPagingItems
 */
inline fun <T : Any> LazyGridScope.lazyPagingItemsStates(
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
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStates(
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
 * This function uses [LazyGridScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
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
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 *
 * @see LazyGridScope.lazyPagingItems
 */
inline fun <T : Any> LazyGridScope.lazyPagingItemsStates(
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
	noinline itemSpan: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyGridItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
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
		lazyPagingItems(
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

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyGridScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
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
 * @param retry The retry action for the error.
 * @param itemsContent The content displayed for the items.
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 */
@JvmName("lazyPagingItemsStatesWithEmptyTextAndItemsContent")
inline fun <T : Any> LazyGridScope.lazyPagingItemsStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyGridScope.(PaddingValues) -> Unit)? = null,
	crossinline emptyText: @Composable () -> String,
	crossinline errorText: @Composable (LoadState.Error) -> String,
	noinline errorTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	noinline emptyTextSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? = { GridItemSpan(maxLineSpan) },
	crossinline itemsContent: LazyGridScope.(lazyPagingItems: LazyPagingItems<T>) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStates(
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
	emptyContent = {
		emptyStateItem(
			key = "paging_empty",
			span = emptyTextSpan,
			emptyText = emptyText,
			contentPadding = statesContentPadding
		)
	},
	itemsContent = itemsContent,
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
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param statesContentPadding The content padding for the loading, error and empty states. Defaults to [PaddingValues(0.dp)].
 */
@JvmName("lazyPagingItemsStatesWithEmptyContentAndItemsContent")
inline fun <T : Any> LazyGridScope.lazyPagingItemsStates(
	lazyPagingItems: LazyPagingItems<T>,
	crossinline errorContent: LazyGridScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
	noinline emptyContent: (LazyGridScope.(PaddingValues) -> Unit)?,
	crossinline itemsContent: LazyGridScope.(lazyPagingItems: LazyPagingItems<T>) -> Unit,
	crossinline prependLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyGridScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", span = { GridItemSpan(maxLineSpan) }, contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyGridScope.(PaddingValues) -> Unit)? = null,
	usingPlaceholders: Boolean = false,
	statesContentPadding: PaddingValues = PaddingValues(0.dp),
) {
	val prependState = lazyPagingItems.loadState.prepend
	val appendState = lazyPagingItems.loadState.append
	val refreshState = lazyPagingItems.loadState.refresh
	val loading = (listOf(
		prependState,
		appendState,
		refreshState
	).firstOrNull { it.isLoading() } as LoadState.Loading?) != null
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

		if (refreshLoadingContent != null && refreshState.isLoading()) {
			refreshLoadingContent(statesContentPadding)
		}

		if (emptyContent != null && lazyPagingItems.itemCount == 0 && !loading) {
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
