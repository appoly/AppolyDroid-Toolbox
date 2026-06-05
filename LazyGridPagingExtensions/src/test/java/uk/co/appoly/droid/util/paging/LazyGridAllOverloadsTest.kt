package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
 * Exercises every overload permutation of the LazyGrid paging entry points (States,
 * IndexedStates, StatesWithNeighbours, IndexedStatesWithNeighbours) against a populated pager,
 * so each inline delegating body and default loading/span lambda is wired up. The primary
 * `lazyPagingItemsStates` text overload is already covered by [LazyGridPagingItemsStatesTest].
 */
@RunWith(AndroidJUnit4::class)
class LazyGridAllOverloadsTest {

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
	fun `every grid paging overload wires up against a populated pager`() {
		composeRule.setContent {
			val items = remember { pager().flow }.collectAsLazyPagingItems()
			LazyVerticalGrid(columns = GridCells.Fixed(2)) {
				// ---- lazyPagingItemsStates (O2..O6) ----
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyContent = { item { BasicText("e") } }, errorText = { "er" },
					itemContent = { BasicText("gs2-$it") }
				)
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "e" }, errorContent = { _, _, _ -> item { BasicText("e") } },
					itemContent = { BasicText("gs3-$it") }
				)
				lazyPagingItemsStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemContent = { BasicText("gs4-$it") }
				)
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "e" }, errorText = { "er" },
					itemsContent = { item { BasicText("gs5") } }
				)
				lazyPagingItemsStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemsContent = { item { BasicText("gs6") } }
				)

				// ---- lazyPagingItemsIndexedStates (4) ----
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items, emptyText = { "e" }, errorText = { "er" },
					itemContent = { _, v -> BasicText("gis1-$v") }
				)
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemContent = { _, _ -> BasicText("gis2") }
				)
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items, emptyText = { "e" }, errorText = { "er" },
					itemsContent = { item { BasicText("gis3") } }
				)
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemsContent = { item { BasicText("gis4") } }
				)

				// ---- lazyPagingItemsStatesWithNeighbours (4) ----
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items, emptyText = { "e" }, errorText = { "er" },
					item = { _, _, _, _, _ -> item { BasicText("gswn1") } }
				)
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items,
					emptyContent = { item { BasicText("e") } }, errorText = { "er" },
					item = { _, _, _, _, _ -> item { BasicText("gswn2") } }
				)
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items, emptyText = { "e" },
					errorContent = { _, _, _ -> item { BasicText("e") } },
					item = { _, _, _, _, _ -> item { BasicText("gswn3") } }
				)
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					item = { _, _, _, _, _ -> item { BasicText("gswn4") } }
				)

				// ---- lazyPagingItemsIndexedStatesWithNeighbours (4) ----
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items, emptyText = { "e" }, errorText = { "er" },
					itemContent = { _, _, _, _ -> BasicText("giswn1") }
				)
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items,
					emptyContent = { item { BasicText("e") } }, errorText = { "er" },
					itemContent = { _, _, _, _ -> BasicText("giswn2") }
				)
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items, emptyText = { "e" },
					errorContent = { _, _, _ -> item { BasicText("e") } },
					itemContent = { _, _, _, _ -> BasicText("giswn3") }
				)
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemContent = { _, _, _, _ -> BasicText("giswn4") }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("gs2-Apple").fetchSemanticsNodes().isNotEmpty()
		}
		assertTrue(composeRule.onAllNodesWithText("gs2-Apple").fetchSemanticsNodes().isNotEmpty())
	}
}
