package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.appoly.droid.ui.paging.LocalEmptyState

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
