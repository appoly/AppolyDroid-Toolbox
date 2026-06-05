package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI test (Robolectric) for the [LazyGridScope.lazyPagingItems] /
 * [lazyPagingItemsIndexed] DSL helpers, driven by a real [Pager] of fixed data.
 */
@RunWith(AndroidJUnit4::class)
class LazyGridPagingItemsTest {

	@get:Rule
	val composeRule = createComposeRule()

	private fun pagerOf(vararg values: String) = Pager(PagingConfig(pageSize = 20)) {
		object : PagingSource<Int, String>() {
			override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> =
				LoadResult.Page(data = values.toList(), prevKey = null, nextKey = null)

			override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
		}
	}

	@Test
	fun `lazyPagingItems renders each loaded item in a grid`() {
		composeRule.setContent {
			val items = remember { pagerOf("Red", "Green", "Blue").flow }.collectAsLazyPagingItems()
			LazyVerticalGrid(columns = GridCells.Fixed(2)) {
				lazyPagingItems(items, key = { it }) { BasicText(it) }
			}
		}

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodesWithText("Red").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Red").assertIsDisplayed()
		composeRule.onNodeWithText("Green").assertIsDisplayed()
		composeRule.onNodeWithText("Blue").assertIsDisplayed()
	}

	@Test
	fun `lazyPagingItemsIndexed renders items with their index in a grid`() {
		composeRule.setContent {
			val items = remember { pagerOf("A", "B").flow }.collectAsLazyPagingItems()
			LazyVerticalGrid(columns = GridCells.Fixed(2)) {
				lazyPagingItemsIndexed(items, key = { i, _ -> i }) { index, item ->
					BasicText("$index=$item")
				}
			}
		}

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodesWithText("0=A").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("0=A").assertIsDisplayed()
		composeRule.onNodeWithText("1=B").assertIsDisplayed()
	}
}
