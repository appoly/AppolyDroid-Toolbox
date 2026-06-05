package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises every overload permutation of the indexed / neighbour LazyList paging entry points
 * (`lazyPagingItemsIndexedStates`, `lazyPagingItemsStatesWithNeighbours`,
 * `lazyPagingItemsIndexedStatesWithNeighbours`) against a populated pager, so each inline
 * delegating body and default loading lambda is wired up.
 */
@RunWith(AndroidJUnit4::class)
class LazyListAllOverloadsTest {

	@get:Rule
	val composeRule = createComposeRule()

	private fun pager() = Pager(PagingConfig(pageSize = 20)) {
		object : PagingSource<Int, String>() {
			override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> =
				LoadResult.Page(listOf("Apple", "Pear"), prevKey = null, nextKey = null)
			override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
		}
	}

	@Test
	fun `every indexed and neighbour overload wires up against a populated pager`() {
		composeRule.setContent {
			val items = remember { pager().flow }.collectAsLazyPagingItems()
			LazyColumn {
				// ---- lazyPagingItemsIndexedStates ----
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items,
					emptyText = { "e" }, errorText = { "er" },
					itemContent = { _, v -> BasicText("is1-$v") }
				)
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemContent = { _, _ -> BasicText("is2") }
				)
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items,
					emptyText = { "e" }, errorText = { "er" },
					itemsContent = { item { BasicText("is3") } }
				)
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemsContent = { item { BasicText("is4") } }
				)

				// ---- lazyPagingItemsStatesWithNeighbours ----
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items,
					emptyText = { "e" }, errorText = { "er" },
					item = { _, _, _, _, _ -> item { BasicText("swn1") } }
				)
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items,
					emptyContent = { item { BasicText("e") } }, errorText = { "er" },
					item = { _, _, _, _, _ -> item { BasicText("swn2") } }
				)
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items,
					emptyText = { "e" }, errorContent = { _, _, _ -> item { BasicText("e") } },
					item = { _, _, _, _, _ -> item { BasicText("swn3") } }
				)
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					item = { _, _, _, _, _ -> item { BasicText("swn4") } }
				)

				// ---- lazyPagingItemsIndexedStatesWithNeighbours ----
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items,
					emptyText = { "e" }, errorText = { "er" },
					itemContent = { _, _, _, _ -> BasicText("iswn1") }
				)
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items,
					emptyContent = { item { BasicText("e") } }, errorText = { "er" },
					itemContent = { _, _, _, _ -> BasicText("iswn2") }
				)
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items,
					emptyText = { "e" }, errorContent = { _, _, _ -> item { BasicText("e") } },
					itemContent = { _, _, _, _ -> BasicText("iswn3") }
				)
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemContent = { _, _, _, _ -> BasicText("iswn4") }
				)
			}
		}
		// Every overload's inline body runs while the LazyColumn content is evaluated; only the
		// first viewport's leaf composables are actually laid out, so we assert on the top item.
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("is1-Apple").fetchSemanticsNodes().isNotEmpty()
		}
		assertTrue(composeRule.onAllNodesWithText("is1-Apple").fetchSemanticsNodes().isNotEmpty())
	}
}
