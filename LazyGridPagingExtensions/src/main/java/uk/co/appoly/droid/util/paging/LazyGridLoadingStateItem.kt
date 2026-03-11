package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.appoly.droid.ui.paging.LocalLoadingState

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
