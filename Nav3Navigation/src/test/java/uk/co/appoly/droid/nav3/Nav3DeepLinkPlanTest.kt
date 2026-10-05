package uk.co.appoly.droid.nav3

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure tests for [planDeepLinkStack] and [morphInto]: the stack rule every navigator's
 * [Nav3Navigator.navigateToDeepLink] shares.
 */
class Nav3DeepLinkPlanTest {

	private val root = HomeScreen
	private val a = ListScreen
	private val b = SettingsScreen
	private fun d(id: Int) = DetailScreen(id)

	private fun append(current: List<NavKey>, link: List<NavKey>) =
		planDeepLinkStack(current, link, Nav3DeepLinkMode.Append)

	private fun reconcile(current: List<NavKey>, link: List<NavKey>) =
		planDeepLinkStack(current, link, Nav3DeepLinkMode.Reconcile)

	// --- Append: the documented examples ---

	@Test
	fun `append pushes on top when only the root is shared`() {
		assertEquals(listOf(root, a, b, d(1)), append(listOf(root, a, b), listOf(root, d(1))))
	}

	@Test
	fun `append surfaces a screen that is already open`() {
		assertEquals(listOf(root, a, d(1)), append(listOf(root, a, d(1), b), listOf(root, d(1))))
	}

	@Test
	fun `append surfaces the deepest open screen of the link and pushes the rest`() {
		assertEquals(
			listOf(root, a, d(2)),
			append(listOf(root, a, d(9)), listOf(root, a, d(2))),
		)
	}

	// --- Append: edges ---

	@Test
	fun `append of just the shared root leaves the stack alone`() {
		assertEquals(listOf(root, a), append(listOf(root, a), listOf(root)))
	}

	@Test
	fun `append skips link screens below the one it surfaces`() {
		// d(1) is already open, so it is surfaced as-is; a is not inserted beneath it.
		assertEquals(listOf(root, d(1)), append(listOf(root, d(1), b), listOf(root, a, d(1))))
	}

	@Test
	fun `append pushes the whole link when its root is not on the stack`() {
		assertEquals(listOf(b, root, d(1)), append(listOf(b), listOf(root, d(1))))
	}

	@Test
	fun `append surfaces a link root that is open above the stack root`() {
		assertEquals(listOf(b, root, d(1)), append(listOf(b, root, a), listOf(root, d(1))))
	}

	@Test
	fun `append never leaves an equal key twice`() {
		val stacks = listOf(
			listOf(root, a, d(1), b),
			listOf(root, d(1), d(2), d(3)),
			listOf(root, b, a),
		)
		val links = listOf(listOf(root, d(1)), listOf(root, a, d(2)), listOf(root, b, d(3)))
		for (s in stacks) for (l in links) {
			val result = append(s, l)
			assertEquals("duplicate key landing $l on $s: $result", result.distinct(), result)
			assertEquals("$l on $s must end on the link's top", l.last(), result.last())
		}
	}

	// --- Reconcile ---

	@Test
	fun `reconcile makes the stack exactly the link`() {
		assertEquals(listOf(root, d(1)), reconcile(listOf(root, a, b), listOf(root, d(1))))
	}

	@Test
	fun `reconcile with a different root replaces everything`() {
		assertEquals(listOf(root, d(1)), reconcile(listOf(b, a), listOf(root, d(1))))
	}

	@Test
	fun `an empty current stack becomes the link in either mode`() {
		assertEquals(listOf(root, a), append(emptyList(), listOf(root, a)))
		assertEquals(listOf(root, a), reconcile(emptyList(), listOf(root, a)))
	}

	// --- morphInto ---

	@Test
	fun `morphInto keeps the common prefix entries in place`() {
		val kept = d(1)
		val stack = mutableListOf<NavKey>(root, kept, b)

		stack.morphInto(listOf(root, DetailScreen(1), a))

		assertEquals(listOf(root, d(1), a), stack)
		assertSame("the shared entry must not be replaced", kept, stack[1])
	}

	@Test
	fun `morphInto with no common prefix replaces the whole list`() {
		val stack = mutableListOf<NavKey>(b, a)
		stack.morphInto(listOf(root, d(1)))
		assertEquals(listOf(root, d(1)), stack)
	}

	// --- Nav3DeepLink ---

	@Test
	fun `a deep link needs at least one screen`() {
		assertThrows(IllegalArgumentException::class.java) { Nav3DeepLink(emptyList()) }
	}

	@Test
	fun `deep links compare by stack and mode`() {
		assertEquals(Nav3DeepLink(listOf(root, a)), Nav3DeepLink(listOf(root, a)))
		assertEquals(Nav3DeepLink(listOf(a)), Nav3DeepLink(a))
		assert(Nav3DeepLink(a) != Nav3DeepLink(a, Nav3DeepLinkMode.Reconcile))
	}
}
