package uk.co.appoly.droid.util.paging

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [Flow][kotlinx.coroutines.flow.Flow]`<PagingData<T>>`.[distinctBy].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagingDataDistinctExtensionsTest {

	private data class Item(val id: Int, val name: String)

	@Before
	fun setUp() {
		Dispatchers.setMain(UnconfinedTestDispatcher())
	}

	@After
	fun tearDown() {
		Dispatchers.resetMain()
	}

	@Test
	fun `duplicates dropped, first kept, order preserved`() = runTest {
		val pagingData = PagingData.from(
			listOf(
				Item(1, "a"),
				Item(2, "b"),
				Item(1, "a-dup"),
				Item(3, "c"),
				Item(2, "b-dup"),
			),
		)

		val snapshot = flowOf(pagingData).distinctBy { it.id }.asSnapshot()

		assertEquals(listOf(1, 2, 3), snapshot.map { it.id })
		assertEquals("a", snapshot.first { it.id == 1 }.name)
		assertEquals("b", snapshot.first { it.id == 2 }.name)
	}

	@Test
	fun `clean data unchanged`() = runTest {
		val items = listOf(
			Item(1, "a"),
			Item(2, "b"),
			Item(3, "c"),
		)
		val snapshot = flowOf(PagingData.from(items)).distinctBy { it.id }.asSnapshot()

		assertEquals(items, snapshot)
	}

	@Test
	fun `seen set resets per generation`() = runTest {
		val dupData = listOf(Item(1, "a"), Item(1, "a-dup"), Item(2, "b"))
		// Present each transformed PagingData exactly once — presenting the same one twice would
		// dedupe the second presentation to empty because its closure-captured seen is already full.
		val generations = flowOf(PagingData.from(dupData), PagingData.from(dupData))
			.distinctBy { it.id }
			.toList()

		val snap1 = flowOf(generations[0]).asSnapshot()
		val snap2 = flowOf(generations[1]).asSnapshot()

		// Each generation de-dupes from scratch — if seen leaked, snap2 would be empty.
		assertEquals(listOf(1, 2), snap1.map { it.id })
		assertEquals(listOf(1, 2), snap2.map { it.id })
	}
}
