package uk.co.appoly.droid.data.repo.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.PageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GenericPagingSource] — the [GenericPagingSource.load] mapping of
 * [APIResult] to [PagingSource.LoadResult], and [GenericPagingSource.getRefreshKey].
 */
class GenericPagingSourceTest {

	private fun pageData(currentPage: Int, lastPage: Int) = PageData(
		data = listOf("item-$currentPage-a", "item-$currentPage-b"),
		currentPage = currentPage,
		lastPage = lastPage,
		perPage = 2,
		from = (currentPage - 1) * 2 + 1,
		to = currentPage * 2,
		total = lastPage * 2
	)

	@Test
	fun `load with null key fetches page 1 and maps to LoadResult Page`() = runTest {
		var requestedPage = -1
		var requestedPerPage = -1
		val source = GenericPagingSource<String>(
			fetchPage = { perPage, page ->
				requestedPerPage = perPage
				requestedPage = page
				APIResult.Success(pageData(currentPage = 1, lastPage = 3))
			},
			pageSize = 2
		)

		val result = source.load(
			PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false)
		)

		assertEquals(2, requestedPerPage)
		assertEquals(1, requestedPage)
		assertTrue(result is PagingSource.LoadResult.Page)
		val page = result as PagingSource.LoadResult.Page
		assertEquals(listOf("item-1-a", "item-1-b"), page.data)
		assertNull(page.prevKey)        // first page
		assertEquals(2, page.nextKey)   // has a next page
		assertEquals(0, page.itemsBefore)
	}

	@Test
	fun `load uses the supplied key as the page number`() = runTest {
		var requestedPage = -1
		val source = GenericPagingSource<String>(
			fetchPage = { _, page ->
				requestedPage = page
				APIResult.Success(pageData(currentPage = page, lastPage = 3))
			},
			pageSize = 2
		)

		val result = source.load(
			PagingSource.LoadParams.Append(key = 2, loadSize = 2, placeholdersEnabled = false)
		) as PagingSource.LoadResult.Page

		assertEquals(2, requestedPage)
		assertEquals(1, result.prevKey)
		assertEquals(3, result.nextKey)
	}

	@Test
	fun `load maps APIResult Error to LoadResult Error`() = runTest {
		val source = GenericPagingSource<String>(
			fetchPage = { _, _ -> APIResult.Error(500, "server exploded") },
			pageSize = 2
		)

		val result = source.load(
			PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false)
		)

		assertTrue(result is PagingSource.LoadResult.Error)
		val error = result as PagingSource.LoadResult.Error
		assertEquals("server exploded", error.throwable.message)
	}

	@Test
	fun `getRefreshKey returns null when there is no anchor position`() {
		val source = GenericPagingSource<String>(
			fetchPage = { _, _ -> APIResult.Success(pageData(1, 1)) },
			pageSize = 10
		)
		val state = PagingState<Int, String>(
			pages = emptyList(),
			anchorPosition = null,
			config = PagingConfig(pageSize = 10),
			leadingPlaceholderCount = 0
		)
		assertNull(source.getRefreshKey(state))
	}

	@Test
	fun `getRefreshKey with jumping support derives the page from the anchor position`() {
		val source = GenericPagingSource<String>(
			fetchPage = { _, _ -> APIResult.Success(pageData(1, 1)) },
			pageSize = 10,
			jumpingSupported = true
		)
		val state = PagingState<Int, String>(
			pages = emptyList(),
			anchorPosition = 25,
			config = PagingConfig(pageSize = 10),
			leadingPlaceholderCount = 0
		)
		// (25 / 10) + 1 = 3
		assertEquals(3, source.getRefreshKey(state))
	}
}
