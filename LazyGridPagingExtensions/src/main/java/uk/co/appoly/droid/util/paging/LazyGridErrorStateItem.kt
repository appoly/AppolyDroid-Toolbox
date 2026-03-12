package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import uk.co.appoly.droid.ui.paging.LocalErrorState

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
