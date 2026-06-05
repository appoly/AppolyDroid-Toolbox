package uk.co.appoly.droid.util.paging

import androidx.compose.foundation.lazy.LazyColumn
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
 * Compose-UI test (Robolectric) for the [LazyListScope.lazyPagingItems] /
 * [lazyPagingItemsIndexed] DSL helpers, driven by a real [Pager] of fixed data collected via
 * [collectAsLazyPagingItems].
 */
@RunWith(AndroidJUnit4::class)
class LazyPagingItemsTest {

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
	fun `lazyPagingItems renders each loaded item`() {
		composeRule.setContent {
			val items = remember { pagerOf("Alpha", "Beta", "Gamma").flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItems(items, key = { it }) { BasicText(it) }
			}
		}

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Alpha").assertIsDisplayed()
		composeRule.onNodeWithText("Beta").assertIsDisplayed()
		composeRule.onNodeWithText("Gamma").assertIsDisplayed()
	}

	@Test
	fun `lazyPagingItemsIndexed renders items with their index`() {
		composeRule.setContent {
			val items = remember { pagerOf("One", "Two").flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsIndexed(items, key = { i, _ -> i }) { index, item ->
					BasicText("$index:$item")
				}
			}
		}

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodesWithText("0:One").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("0:One").assertIsDisplayed()
		composeRule.onNodeWithText("1:Two").assertIsDisplayed()
	}
}
