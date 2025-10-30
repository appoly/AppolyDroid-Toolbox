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
 * This function uses [LazyListScope.lazyPagingItemsWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [item] to it.
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
 * @param item The content displayed by a single item, this provides the previous item, the current item, the next item, the item key and the item content type.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyListScope.lazyPagingItemsWithNeighbours
 */
@JvmName("lazyPagingItemsStatesWithNeighboursWithEmptyText")
inline fun <T : Any> LazyListScope.lazyPagingItemsStatesWithNeighbours(
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
	crossinline item: LazyListScope.(prevItem: T?, item: T, nextItem: T?, itemKey: Any?, itemContentType: Any?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStatesWithNeighbours(
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
	item = item,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyListScope.lazyPagingItemsWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [item] to it.
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
 * @param item The content displayed by a single item, this provides the previous item, the current item, the next item, the item key and the item content type.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyListScope.lazyPagingItemsWithNeighbours
 */
@JvmName("lazyPagingItemsStatesWithNeighboursWithEmptyContent")
inline fun <T : Any> LazyListScope.lazyPagingItemsStatesWithNeighbours(
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
	crossinline item: LazyListScope.(prevItem: T?, item: T, nextItem: T?, itemKey: Any?, itemContentType: Any?) -> Unit,
	statesContentPadding: PaddingValues = PaddingValues(0.dp)
) = lazyPagingItemsStatesWithNeighbours(
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
	item = item,
	statesContentPadding = statesContentPadding
)

/**
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyListScope.lazyPagingItemsWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [item] to it.
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
 * @param item The content displayed by a single item, this provides the previous item, the current item, the next item, the item key and the item content type.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyListScope.lazyPagingItemsWithNeighbours
 */
inline fun <T : Any> LazyListScope.lazyPagingItemsStatesWithNeighbours(
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
	crossinline item: LazyListScope.(prevItem: T?, item: T, nextItem: T?, itemKey: Any?, itemContentType: Any?) -> Unit,
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
 * Item and loading state management for [LazyPagingItems] within a [LazyListScope].
 *
 * This function will automatically handle the loading, error, empty states and the items.
 *
 * This function uses [LazyListScope.lazyPagingItemsWithNeighbours], passing the [itemKey], [itemContentType], [itemPlaceholderContent] and [item] to it.
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
 * @param item The content displayed by a single item, this provides the previous item, the current item, the next item, the item key and the item content type.
 * @param statesContentPadding The padding to apply around the loading and error states, this defaults to 0.dp.
 *
 * @see LazyListScope.lazyPagingItemsWithNeighbours
 */
inline fun <T : Any> LazyListScope.lazyPagingItemsStatesWithNeighbours(
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
	crossinline item: LazyListScope.(prevItem: T?, item: T, nextItem: T?, itemKey: Any?, itemContentType: Any?) -> Unit,
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