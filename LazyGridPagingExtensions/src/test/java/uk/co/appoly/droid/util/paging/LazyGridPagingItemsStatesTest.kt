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
 * Compose-UI tests (Robolectric) for the LazyGrid [lazyPagingItemsStates], driving content,
 * empty and error states in a [LazyVerticalGrid].
 */
@RunWith(AndroidJUnit4::class)
class LazyGridPagingItemsStatesTest {

	@get:Rule
	val composeRule = createComposeRule()

	private fun pager(load: () -> PagingSource.LoadResult<Int, String>) =
		Pager(PagingConfig(pageSize = 20)) {
			object : PagingSource<Int, String>() {
				override suspend fun load(params: LoadParams<Int>) = load()
				override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
			}
		}

	private fun page(vararg values: String): PagingSource.LoadResult<Int, String> =
		PagingSource.LoadResult.Page(values.toList(), prevKey = null, nextKey = null)

	@Test
	fun `renders items on the content state`() {
		composeRule.setContent {
			val items = remember { pager { page("Red", "Blue") }.flow }.collectAsLazyPagingItems()
			LazyVerticalGrid(columns = GridCells.Fixed(2)) {
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "Empty" },
					errorText = { "Failed" },
					itemContent = { BasicText(it) }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("Red").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Red").assertIsDisplayed()
		composeRule.onNodeWithText("Blue").assertIsDisplayed()
	}

	@Test
	fun `renders the empty state when there are no items`() {
		composeRule.setContent {
			val items = remember { pager { page() }.flow }.collectAsLazyPagingItems()
			LazyVerticalGrid(columns = GridCells.Fixed(2)) {
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "Empty" },
					errorText = { "Failed" },
					itemContent = { BasicText(it) }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("Empty").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Empty").assertIsDisplayed()
	}

	@Test
	fun `renders the error state when the refresh fails`() {
		composeRule.setContent {
			val items = remember {
				pager { PagingSource.LoadResult.Error(RuntimeException("boom")) }.flow
			}.collectAsLazyPagingItems()
			LazyVerticalGrid(columns = GridCells.Fixed(2)) {
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "Empty" },
					errorText = { "Failed" },
					itemContent = { BasicText(it) }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("Failed").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Failed").assertIsDisplayed()
	}
}
