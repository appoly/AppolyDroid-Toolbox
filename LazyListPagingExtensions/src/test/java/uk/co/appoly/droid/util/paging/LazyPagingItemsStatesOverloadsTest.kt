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
 * Covers the remaining [lazyPagingItemsStates] overload permutations not already exercised by
 * [LazyPagingItemsStatesTest]: emptyContent vs emptyText × errorContent vs errorText, and the
 * whole-list `itemsContent` variants. Each overload is invoked once against a populated pager so
 * its (inline) delegating body and default loading lambdas are wired up.
 */
@RunWith(AndroidJUnit4::class)
class LazyPagingItemsStatesOverloadsTest {

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
	fun `every lazyPagingItemsStates overload wires up against a populated pager`() {
		composeRule.setContent {
			val items = remember { pager().flow }.collectAsLazyPagingItems()
			LazyColumn {
				// O2: emptyContent (lambda) + errorText + per-item content
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyContent = { item { BasicText("empty2") } },
					errorText = { "err2" },
					itemContent = { BasicText("o2-$it") }
				)
				// O3: emptyText + errorContent + per-item content
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "empty3" },
					errorContent = { _, _, _ -> item { BasicText("err3") } },
					itemContent = { BasicText("o3-$it") }
				)
				// O4: errorContent + emptyContent + per-item content (lowest per-item overload)
				lazyPagingItemsStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("err4") } },
					emptyContent = { item { BasicText("empty4") } },
					itemContent = { BasicText("o4-$it") }
				)
				// O5: emptyText + errorText + whole-list itemsContent
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "empty5" },
					errorText = { "err5" },
					itemsContent = { item { BasicText("o5-content") } }
				)
				// O6: errorContent + emptyContent + whole-list itemsContent (lowest impl)
				lazyPagingItemsStates(
					lazyPagingItems = items,
					errorContent = { _, _, _ -> item { BasicText("err6") } },
					emptyContent = { item { BasicText("empty6") } },
					itemsContent = { item { BasicText("o6-content") } }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("o2-Apple").fetchSemanticsNodes().isNotEmpty()
		}
		assertTrue(
			composeRule.onAllNodesWithText("o5-content").fetchSemanticsNodes().isNotEmpty()
		)
	}
}
