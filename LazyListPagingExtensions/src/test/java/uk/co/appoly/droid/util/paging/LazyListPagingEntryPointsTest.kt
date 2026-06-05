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
 * Compose-UI tests (Robolectric) covering the remaining LazyList paging entry points:
 * indexed-states, with-neighbours and indexed-states-with-neighbours variants.
 */
@RunWith(AndroidJUnit4::class)
class LazyListPagingEntryPointsTest {

	@get:Rule
	val composeRule = createComposeRule()

	private fun pagerOf(vararg values: String) = Pager(PagingConfig(pageSize = 20)) {
		object : PagingSource<Int, String>() {
			override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> =
				LoadResult.Page(values.toList(), prevKey = null, nextKey = null)

			override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
		}
	}

	private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
		composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
	}

	@Test
	fun `lazyPagingItemsIndexedStates renders indexed items`() {
		composeRule.setContent {
			val items = remember { pagerOf("A", "B").flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsIndexedStates(
					lazyPagingItems = items,
					emptyText = { "empty" },
					errorText = { "error" },
					itemContent = { index, item -> BasicText("$index-$item") }
				)
			}
		}
		waitForText("0-A")
		composeRule.onNodeWithText("1-B").assertIsDisplayed()
	}

	@Test
	fun `lazyPagingItemsStatesWithNeighbours renders items`() {
		composeRule.setContent {
			val items = remember { pagerOf("A", "B").flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsStatesWithNeighbours(
					lazyPagingItems = items,
					emptyText = { "empty" },
					errorText = { "error" },
					item = { _, current, _, key, contentType ->
						item(key = key, contentType = contentType) { BasicText("n-$current") }
					}
				)
			}
		}
		waitForText("n-A")
		composeRule.onNodeWithText("n-B").assertIsDisplayed()
	}

	@Test
	fun `lazyPagingItemsIndexedStatesWithNeighbours renders indexed items`() {
		composeRule.setContent {
			val items = remember { pagerOf("A", "B").flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsIndexedStatesWithNeighbours(
					lazyPagingItems = items,
					emptyText = { "empty" },
					errorText = { "error" },
					itemContent = { _, index, item, _ -> BasicText("i$index-$item") }
				)
			}
		}
		waitForText("i0-A")
		composeRule.onNodeWithText("i1-B").assertIsDisplayed()
	}

	@Test
	fun `lazyPagingItemsWithNeighbours renders items`() {
		composeRule.setContent {
			val items = remember { pagerOf("A", "B").flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsWithNeighbours(
					lazyPagingItems = items,
					item = { _, current, _, key, contentType ->
						item(key = key, contentType = contentType) { BasicText("w-$current") }
					}
				)
			}
		}
		waitForText("w-A")
		composeRule.onNodeWithText("w-B").assertIsDisplayed()
	}

	@Test
	fun `lazyPagingItemsIndexedWithNeighbours renders indexed items`() {
		composeRule.setContent {
			val items = remember { pagerOf("A", "B").flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsIndexedWithNeighbours(
					lazyPagingItems = items,
					itemContent = { _, index, item, _ -> BasicText("iw$index-$item") }
				)
			}
		}
		waitForText("iw0-A")
		composeRule.onNodeWithText("iw1-B").assertIsDisplayed()
	}
}
