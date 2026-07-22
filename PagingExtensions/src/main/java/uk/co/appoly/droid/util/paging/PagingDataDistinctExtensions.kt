package uk.co.appoly.droid.util.paging

import androidx.paging.PagingData
import androidx.paging.filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * De-duplicates items in a paging stream by the key produced by [selector], keeping the first
 * occurrence of each key and dropping any later duplicates.
 *
 * Guards against a paged endpoint returning the same item on more than one page — common with
 * offset pagination over data that can change between page loads — which would otherwise crash a
 * `LazyColumn`/`LazyRow` with `IllegalArgumentException: Key "…" was already used` when that item's
 * key is used as the list `key`/`itemKey`.
 *
 * The `seen` set is scoped inside the [map] over each emitted [PagingData], so it resets naturally
 * on every refresh/invalidation. Only safe when the `Pager` does **not** set `maxSize` (no page
 * dropping): a dropped-then-reloaded page's items would otherwise be wrongly filtered as seen.
 *
 * @param selector produces the de-duplication key for an item.
 */
fun <T : Any, K> Flow<PagingData<T>>.distinctBy(
	selector: (T) -> K,
): Flow<PagingData<T>> = map { pagingData ->
	val seen = mutableSetOf<K>()
	pagingData.filter { seen.add(selector(it)) }
}
