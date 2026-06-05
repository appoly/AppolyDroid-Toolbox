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
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI tests (Robolectric) for the loading-state branch of [lazyPagingItemsStates]:
 * a never-completing [Pager] keeps refresh in the Loading state, so the supplied
 * refreshLoadingContent is rendered.
 */
@RunWith(AndroidJUnit4::class)
class LazyListPagingLoadingTest {

	@get:Rule
	val composeRule = createComposeRule()

	/** A pager whose load never returns -> refresh stays Loading. */
	private fun loadingPager() = Pager(PagingConfig(pageSize = 20)) {
		object : PagingSource<Int, String>() {
			override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> = awaitCancellation()
			override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
		}
	}

	@Test
	fun `lazyPagingItemsStates renders refresh-loading content while loading`() {
		composeRule.setContent {
			val items = remember { loadingPager().flow }.collectAsLazyPagingItems()
			LazyColumn {
				lazyPagingItemsStates(
					lazyPagingItems = items,
					emptyText = { "empty" },
					errorText = { "error" },
					refreshLoadingContent = { item { BasicText("REFRESH_LOADING") } },
					itemContent = { BasicText(it) }
				)
			}
		}
		composeRule.waitUntil(5_000) {
			composeRule.onAllNodesWithText("REFRESH_LOADING").fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText("REFRESH_LOADING").assertIsDisplayed()
	}
}
