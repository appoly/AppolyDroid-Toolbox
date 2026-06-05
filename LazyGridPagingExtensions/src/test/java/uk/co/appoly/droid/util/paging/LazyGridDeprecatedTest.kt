@file:Suppress("DEPRECATION")

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
 * Coverage for the deprecated `lazyPagingItemsWithStates` LazyGrid overloads, which delegate to
 * the current `lazyPagingItemsStates` API. Invokes each of the 5 overloads against a populated
 * pager so the (inline) delegation bodies are exercised.
 */
@RunWith(AndroidJUnit4::class)
class LazyGridDeprecatedTest {

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
	fun `deprecated lazyPagingItemsWithStates overloads delegate against a populated pager`() {
		composeRule.setContent {
			val items = remember { pager().flow }.collectAsLazyPagingItems()
			LazyVerticalGrid(columns = GridCells.Fixed(2)) {
				// O1: emptyText + errorText + per-item content
				lazyPagingItemsWithStates(
					lazyPagingItems = items, emptyText = { "e" }, errorText = { "er" },
					itemContent = { BasicText("dep1-$it") }
				)
				// O2: emptyContent + errorText + per-item content
				lazyPagingItemsWithStates(
					lazyPagingItems = items, emptyContent = { item { BasicText("e") } },
					errorText = { "er" }, itemContent = { BasicText("dep2") }
				)
				// O3: errorContent + emptyContent + per-item content
				lazyPagingItemsWithStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemContent = { BasicText("dep3") }
				)
				// O4: emptyText + errorText + whole-list itemsContent
				lazyPagingItemsWithStates(
					lazyPagingItems = items, emptyText = { "e" }, errorText = { "er" },
					itemsContent = { item { BasicText("dep4") } }
				)
				// O5: errorContent + emptyContent + whole-list itemsContent
				lazyPagingItemsWithStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("e") } },
					emptyContent = { item { BasicText("e") } },
					itemsContent = { item { BasicText("dep5") } }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("dep1-Apple").fetchSemanticsNodes().isNotEmpty()
		}
		assertTrue(composeRule.onAllNodesWithText("dep1-Apple").fetchSemanticsNodes().isNotEmpty())
	}
}
