@file:Suppress("DEPRECATION")

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

// region Deprecated lazyPagingItemsWithStates -> lazyPagingItemsStates

/**
 * @deprecated Use [lazyPagingItemsStates] instead.
 */
@Deprecated(
	message = "Renamed to lazyPagingItemsStates for consistency with LazyListPagingExtensions.",
	replaceWith = ReplaceWith("lazyPagingItemsStates(lazyPagingItems, usingPlaceholders, prependLoadingContent, appendLoadingContent, emptyText = emptyText, errorText = errorText, errorTextSpan = errorTextSpan, retry = retry, emptyTextSpan = emptyTextSpan, itemKey = itemKey, itemSpan = itemSpan, itemContentType = itemContentType, itemPlaceholderContent = itemPlaceholderContent, itemContent = itemContent, statesContentPadding = statesContentPadding)")
)
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
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	emptyText = { emptyText() },
	errorText = errorText,
	errorTextSpan = errorTextSpan,
	retry = retry,
	emptyTextSpan = emptyTextSpan,
	itemKey = itemKey,
	itemSpan = itemSpan,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * @deprecated Use [lazyPagingItemsStates] instead.
 */
@Deprecated(
	message = "Renamed to lazyPagingItemsStates for consistency with LazyListPagingExtensions.",
	replaceWith = ReplaceWith("lazyPagingItemsStates(lazyPagingItems, usingPlaceholders, prependLoadingContent, appendLoadingContent, emptyContent = emptyContent, errorText = errorText, errorTextSpan = errorTextSpan, retry = retry, itemKey = itemKey, itemSpan = itemSpan, itemContentType = itemContentType, itemPlaceholderContent = itemPlaceholderContent, itemContent = itemContent, statesContentPadding = statesContentPadding)")
)
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
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	emptyContent = { emptyContent(it) },
	errorText = errorText,
	errorTextSpan = errorTextSpan,
	retry = retry,
	itemKey = itemKey,
	itemSpan = itemSpan,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * @deprecated Use [lazyPagingItemsStates] instead.
 */
@Deprecated(
	message = "Renamed to lazyPagingItemsStates for consistency with LazyListPagingExtensions.",
	replaceWith = ReplaceWith("lazyPagingItemsStates(lazyPagingItems, usingPlaceholders, prependLoadingContent, appendLoadingContent, errorContent = errorContent, emptyContent = emptyContent, itemKey = itemKey, itemSpan = itemSpan, itemContentType = itemContentType, itemPlaceholderContent = itemPlaceholderContent, itemContent = itemContent, statesContentPadding = statesContentPadding)")
)
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
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	errorContent = errorContent,
	emptyContent = { emptyContent(it) },
	itemKey = itemKey,
	itemSpan = itemSpan,
	itemContentType = itemContentType,
	itemPlaceholderContent = itemPlaceholderContent,
	itemContent = itemContent,
	statesContentPadding = statesContentPadding
)

/**
 * @deprecated Use [lazyPagingItemsStates] instead.
 */
@Deprecated(
	message = "Renamed to lazyPagingItemsStates for consistency with LazyListPagingExtensions.",
	replaceWith = ReplaceWith("lazyPagingItemsStates(lazyPagingItems, usingPlaceholders, prependLoadingContent, appendLoadingContent, emptyText = { emptyText() }, errorText = errorText, errorTextSpan = errorTextSpan, retry = retry, emptyTextSpan = emptyTextSpan, itemsContent = itemsContent, statesContentPadding = statesContentPadding)")
)
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
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	usingPlaceholders = usingPlaceholders,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	emptyText = { emptyText() },
	errorText = errorText,
	errorTextSpan = errorTextSpan,
	retry = retry,
	emptyTextSpan = emptyTextSpan,
	itemsContent = itemsContent,
	statesContentPadding = statesContentPadding
)

/**
 * @deprecated Use [lazyPagingItemsStates] instead.
 */
@Deprecated(
	message = "Renamed to lazyPagingItemsStates for consistency with LazyListPagingExtensions.",
	replaceWith = ReplaceWith("lazyPagingItemsStates(lazyPagingItems, errorContent = errorContent, emptyContent = emptyContent, itemsContent = itemsContent, prependLoadingContent = prependLoadingContent, appendLoadingContent = appendLoadingContent, usingPlaceholders = usingPlaceholders, statesContentPadding = statesContentPadding)")
)
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
) = lazyPagingItemsStates(
	lazyPagingItems = lazyPagingItems,
	errorContent = errorContent,
	emptyContent = { emptyContent(it) },
	itemsContent = itemsContent,
	prependLoadingContent = prependLoadingContent,
	appendLoadingContent = appendLoadingContent,
	usingPlaceholders = usingPlaceholders,
	statesContentPadding = statesContentPadding
)

// endregion
