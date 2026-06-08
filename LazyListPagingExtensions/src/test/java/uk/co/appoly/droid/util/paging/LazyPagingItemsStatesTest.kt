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
 * Compose-UI tests (Robolectric) for [lazyPagingItemsStates], which drives the empty / error /
 * content states of a paged [LazyColumn] (and the shared emptyStateItem/errorStateItem helpers).
 * The default state composables fall back to the default Material theme, so no MaterialTheme
 * wrapper is needed (this module doesn't depend on material3).
 */
@RunWith(AndroidJUnit4::class)
class LazyPagingItemsStatesTest {

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
			val items = remember { pager { page("Apple", "Pear") }.flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "Nothing here" },
					errorText = { "Something went wrong" },
					itemContent = { BasicText(it) }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("Apple").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Apple").assertIsDisplayed()
		composeRule.onNodeWithText("Pear").assertIsDisplayed()
	}

	@Test
	fun `renders the empty state when there are no items`() {
		composeRule.setContent {
			val items = remember { pager { page() }.flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "Nothing here" },
					errorText = { "Something went wrong" },
					itemContent = { BasicText(it) }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("Nothing here").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
	}

	@Test
	fun `renders the error state when the refresh fails`() {
		composeRule.setContent {
			val items = remember {
				pager { PagingSource.LoadResult.Error(RuntimeException("boom")) }.flow
			}.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "Nothing here" },
					errorText = { "Something went wrong" },
					itemContent = { BasicText(it) }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("Something went wrong").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("Something went wrong").assertIsDisplayed()
	}
}
