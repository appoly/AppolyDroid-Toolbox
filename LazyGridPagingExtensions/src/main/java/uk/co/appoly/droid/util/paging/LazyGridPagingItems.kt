package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey

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
 * 'requestScrollToItem' on the 'LazyGridState'.
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
 * Adds a list of items from a [LazyPagingItems] object with indexes.
 *
 * @param lazyPagingItems The [LazyPagingItems] object to use as the data source
 * @param key a factory of stable and unique keys representing the item. Using the same key
 * for multiple items in the list is not allowed. Type of the key should be saveable
 * via Bundle on Android. If null is passed the position in the list will represent the key.
 * When you specify the key the scroll position will be maintained based on the key, which
 * means if you add/remove items before the current visible item the item with the given key
 * will be kept as the first visible one. This can be overridden by calling
 * 'requestScrollToItem' on the 'LazyGridState'.
 * @param span a factory of the span for the item. The span of the item could be changed
 * dynamically based on the item data. If null is passed the span will be 1.
 * @param contentType a factory of the content types for the item. The item compositions of
 * the same type could be reused more efficiently. Note that null is a valid type and items of such
 * type will be considered compatible.
 * @param itemContent the content displayed by a single item, this provides the index and the item.
 * @param placeholderItemContent the content displayed by a single placeholder item, this provides the index.
 *
 * @see LazyGridScope.items
 */
inline fun <T : Any> LazyGridScope.lazyPagingItemsIndexed(
	lazyPagingItems: LazyPagingItems<T>,
	noinline key: ((index: Int, item: T) -> Any)? = null,
	noinline span: (LazyGridItemSpanScope.(item: T?) -> GridItemSpan)? = null,
	noinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
	crossinline placeholderItemContent: @Composable LazyGridItemScope.(index: Int) -> Unit = {},
	crossinline itemContent: @Composable LazyGridItemScope.(index: Int, item: T) -> Unit
) = items(
	count = lazyPagingItems.itemCount,
	key = if (key != null) {
		{ i ->
			lazyPagingItems.itemKey(key = { item -> key(i, item) }).invoke(i)
		}
	} else null,
	span = if (span != null) {
		{ span(lazyPagingItems.peek(it)) }
	} else null,
	contentType = { i -> lazyPagingItems.itemContentType { contentType(i, it) } },
) {
	val item = lazyPagingItems[it]
	if (item != null) {
		itemContent(it, item)
	} else {
		placeholderItemContent(it)
	}
}
