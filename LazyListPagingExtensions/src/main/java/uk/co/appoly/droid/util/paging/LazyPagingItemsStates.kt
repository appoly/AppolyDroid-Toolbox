package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyListScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param errorText The text displayed when an error occurs.
 * @param emptyText The text displayed when the list is empty and not loading.
 * @param retry The retry action to perform when an error occurs.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyListScope.lazyPagingItems
 */
@JvmName("lazyPagingItemsStatesWithEmptyText")
inline fun <T : Any> LazyListScope.lazyPagingItemsStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading" , contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyListScope.(PaddingValues) -> Unit)? = null,
	noinline emptyText: (@Composable () -> String)?,
	crossinline errorText: @Composable (LoadState.Error) -> String,
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable (LazyItemScope.(item: T) -> Unit),
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	refreshLoadingContent = refreshLoadingContent,
	errorContent = { key, error, paddingValues ->
		errorStateItem(
			key = "${key}_Paging_error",
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
				emptyText = emptyText,
				contentPadding = statesContentPadding
			)
		}
	} else null,
	itemKey = itemKey,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param errorText The text displayed when an error occurs.
 * @param retry The retry action to perform when an error occurs.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 * @see LazyListScope.lazyPagingItems
 */
@JvmName("lazyPagingItemsStatesWithEmptyContent")
inline fun <T : Any> LazyListScope.lazyPagingItemsStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyListScope.(PaddingValues) -> Unit)? = null,
	noinline emptyContent: (LazyListScope.(PaddingValues) -> Unit)?,
	crossinline errorText: @Composable (LoadState.Error) -> String,
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable (LazyItemScope.(item: T) -> Unit),
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	refreshLoadingContent = refreshLoadingContent,
	errorContent = { key, error, paddingValues ->
		errorStateItem(
			key = "${key}_Paging_error",
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
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param emptyText The text displayed when the list is empty and not loading.
 * @param errorContent The content displayed when an error occurs, this provides the key and the error, the should be used for
 * the [item][LazyListScope.item] key as there could show multiple errors for the prepend, append and refresh states.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyListScope.lazyPagingItems
 */
inline fun <T : Any> LazyListScope.lazyPagingItemsStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyListScope.(PaddingValues) -> Unit)? = null,
	noinline emptyText: (@Composable () -> String)?,
	crossinline errorContent: LazyListScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable (LazyItemScope.(item: T) -> Unit),
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
				emptyText = emptyText,
				contentPadding = statesContentPadding
			)
		}
	} else null,
	itemKey = itemKey,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyListScope.lazyPagingItems], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [itemContent] to it.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param errorContent The content displayed when an error occurs, this provides the key and the error, the should be used for
 * the [item][LazyListScope.item] key as there could show multiple errors for the prepend, append and refresh states.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param itemKey The key for the item, this should be unique for each item.
 * @param itemContentType The content type for the item, this should be unique for each item.
 * @param itemPlaceholderContent The content displayed by a single placeholder item.
 * @param itemContent The content displayed by a single item.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyListScope.lazyPagingItems
 */
inline fun <T : Any> LazyListScope.lazyPagingItemsStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem("paging_prepend_loading", contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem("paging_append_loading", contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyListScope.(PaddingValues) -> Unit)? = null,
	crossinline errorContent: LazyListScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
	noinline emptyContent: (LazyListScope.(PaddingValues) -> Unit)?,
	noinline itemKey: ((item: T) -> Any)? = null,
	noinline itemContentType: (item: T) -> Any? = { null },
	crossinline itemPlaceholderContent: @Composable (LazyItemScope.() -> Unit) = {},
	crossinline itemContent: @Composable (LazyItemScope.(item: T) -> Unit),
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
			contentType = itemContentType,
			placeholderItemContent = itemPlaceholderContent,
			itemContent = itemContent
		)
	},
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [loadingStateItem].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [loadingStateItem].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param errorText The text displayed when an error occurs.
 * @param emptyText The text displayed when the list is empty and not loading.
 * @param retry The retry action to perform when an error occurs.
 * @param itemsContent The content displayed for the items.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 */
inline fun <T : Any> LazyListScope.lazyPagingItemsStates(
	lazyPagingItems: LazyPagingItems<T>,
	usingPlaceholders: Boolean = false,
	crossinline prependLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_prepend_loading", contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem(key = "paging_append_loading", contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyListScope.(PaddingValues) -> Unit)? = null,
	crossinline emptyText: @Composable () -> String,
	crossinline errorText: @Composable (LoadState.Error) -> String,
	crossinline retry: () -> Unit = { lazyPagingItems.retry() },
	crossinline itemsContent: LazyListScope.(lazyPagingItems: LazyPagingItems<T>) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	refreshLoadingContent = refreshLoadingContent,
	errorContent = { key, error, paddingValues ->
		errorStateItem(
			key = "${key}_Paging_error",
			error = error,
			errorText = errorText,
			retry = retry,
			contentPadding = paddingValues
		)
	},
	emptyContent = {
		emptyStateItem(
			key = "paging_empty",
			emptyText = emptyText,
			contentPadding = statesContentPadding
		)
	},
	itemsContent = itemsContent,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source.
 * @param errorContent The content displayed when an error occurs, this provides the key and the error, the should be used for
 * the [item][LazyListScope.item] key as there could show multiple errors for the prepend, append and refresh states.
 * @param emptyContent The content displayed when the list is empty and not loading.
 * @param itemsContent The content displayed for the items.
 * @param prependLoadingContent The content displayed when the prepend is loading, this defaults to a [LoadingState].
 * @param appendLoadingContent The content displayed when the append or refresh is loading, this defaults to a [LoadingState].
 * @param refreshLoadingContent The content displayed when the refresh is loading, this defaults to null.
 * @param usingPlaceholders If using Placeholders, then the [prependLoadingContent] and [appendLoadingContent] will not be displayed.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 */
inline fun <T : Any> LazyListScope.lazyPagingItemsStates(
	lazyPagingItems: LazyPagingItems<T>,
	crossinline errorContent: LazyListScope.(key: PagingErrorType, error: LoadState.Error, PaddingValues) -> Unit,
	noinline emptyContent: (LazyListScope.(PaddingValues) -> Unit)?,
	crossinline itemsContent: LazyListScope.(lazyPagingItems: LazyPagingItems<T>) -> Unit,
	crossinline prependLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem("paging_prepend_loading", contentPadding = paddingValues)
	},
	crossinline appendLoadingContent: LazyListScope.(PaddingValues) -> Unit = { paddingValues ->
		loadingStateItem("paging_append_loading", contentPadding = paddingValues)
	},
	noinline refreshLoadingContent: (LazyListScope.(PaddingValues) -> Unit)? = null,
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
