package uk.co.appoly.droid.data.remote.model.response

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the derived pagination properties on [PageData]
 * ([itemsBefore], [itemsAfter], [prevPage], [nextPage]).
 */
class PageDataTest {

	private fun page(currentPage: Int, lastPage: Int, from: Int, to: Int, total: Int) =
		PageData(
			data = emptyList<String>(),
			currentPage = currentPage,
			lastPage = lastPage,
			perPage = 20,
			from = from,
			to = to,
			total = total
		)

	@Test
	fun `first page of many`() {
		val p = page(currentPage = 1, lastPage = 5, from = 1, to = 20, total = 100)
		assertEquals(0, p.itemsBefore)
		assertEquals(80, p.itemsAfter)
		assertNull(p.prevPage)
		assertEquals(2, p.nextPage)
	}

	@Test
	fun `middle page`() {
		val p = page(currentPage = 3, lastPage = 5, from = 41, to = 60, total = 100)
		assertEquals(40, p.itemsBefore)
		assertEquals(40, p.itemsAfter)
		assertEquals(2, p.prevPage)
		assertEquals(4, p.nextPage)
	}

	@Test
	fun `last page`() {
		val p = page(currentPage = 5, lastPage = 5, from = 81, to = 100, total = 100)
		assertEquals(80, p.itemsBefore)
		assertEquals(0, p.itemsAfter)
		assertEquals(4, p.prevPage)
		assertNull(p.nextPage)
	}

	@Test
	fun `single page`() {
		val p = page(currentPage = 1, lastPage = 1, from = 1, to = 10, total = 10)
		assertEquals(0, p.itemsBefore)
		assertEquals(0, p.itemsAfter)
		assertNull(p.prevPage)
		assertNull(p.nextPage)
	}

	@Test
	fun `empty page with from zero clamps itemsBefore to zero`() {
		val p = page(currentPage = 1, lastPage = 1, from = 0, to = 0, total = 0)
		assertEquals(0, p.itemsBefore)
		assertEquals(0, p.itemsAfter)
	}
}
