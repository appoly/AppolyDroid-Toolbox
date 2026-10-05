package uk.co.appoly.droid.nav3

import android.os.Bundle
import androidx.compose.runtime.saveable.SaverScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@Serializable
internal data class OpenMedia(val clipId: String) : Nav3Continuation

@Serializable
internal data object FocusComments : Nav3Continuation

/** Deliberately not @Serializable. */
internal data class NotSerializableStep(val x: Int) : Nav3Continuation

/**
 * [Nav3Continuations] as a store (typed peek/consume, compare-and-clear, pruning, saving), and
 * how each navigator delivers and prunes continuations.
 */
@RunWith(AndroidJUnit4::class)
class Nav3ContinuationsTest {

	private val saverScope = object : SaverScope {
		override fun canBeSaved(value: Any): Boolean = true
	}

	// --- the store ---

	@Test
	fun `peek leaves a continuation pending and consume takes it`() {
		val store = Nav3Continuations()
		store.put(DetailScreen(1), OpenMedia("c1"))

		assertEquals(OpenMedia("c1"), store.peek(DetailScreen(1)))
		assertEquals(OpenMedia("c1"), store.consume(DetailScreen(1)))
		assertNull("consumed means gone", store.peek(DetailScreen(1)))
	}

	@Test
	fun `typed consume leaves a payload of another type in place`() {
		val store = Nav3Continuations()
		store.put(DetailScreen(1), FocusComments)

		assertNull(store.consumeAs<OpenMedia>(DetailScreen(1)))
		assertEquals(FocusComments, store.peekAs<FocusComments>(DetailScreen(1)))
		assertEquals(FocusComments, store.consumeAs<FocusComments>(DetailScreen(1)))
	}

	@Test
	fun `consumeIf only clears the payload it was handed`() {
		val store = Nav3Continuations()
		val first = OpenMedia("a")
		store.put(DetailScreen(1), first)
		store.put(DetailScreen(1), OpenMedia("b")) // replaced before the first consumer acted

		assertFalse("a stale handle must not clear the newer payload", store.consumeIf(DetailScreen(1), first))
		assertEquals(OpenMedia("b"), store.peek(DetailScreen(1)))
	}

	@Test
	fun `two consumers of one payload can't both succeed`() {
		val store = Nav3Continuations()
		val step = OpenMedia("a")
		store.put(DetailScreen(1), step)

		assertTrue(store.consumeIf(DetailScreen(1), step))
		assertFalse(store.consumeIf(DetailScreen(1), step))
	}

	@Test
	fun `retainOnly drops continuations whose entry is gone`() {
		val store = Nav3Continuations()
		store.put(DetailScreen(1), OpenMedia("a"))
		store.put(DetailScreen(2), OpenMedia("b"))

		store.retainOnly(listOf(HomeScreen, DetailScreen(2)))

		assertEquals(setOf<NavKey>(DetailScreen(2)), store.pendingKeys)
	}

	@Test
	fun `a payload that isn't serializable is rejected where it is sent`() {
		assertThrows(IllegalArgumentException::class.java) {
			Nav3Continuations().put(DetailScreen(1), NotSerializableStep(1))
		}
	}

	@Test
	fun `the saver round-trips data class and data object payloads`() {
		val store = Nav3Continuations()
		store.put(DetailScreen(1), OpenMedia("clip-9"))
		store.put(SettingsScreen, FocusComments)

		val saver = Nav3Continuations.saver()
		val restored = saver.restore(with(saver) { saverScope.save(store) }!!)!!

		assertEquals(OpenMedia("clip-9"), restored.peek(DetailScreen(1)))
		assertEquals(FocusComments, restored.peek(SettingsScreen))
	}

	@Test
	fun `restoring drops an entry that can't be decoded instead of crashing`() {
		val good = Nav3Continuations().apply { put(DetailScreen(1), OpenMedia("ok")) }.toBundle()
		val broken = Bundle(good).apply {
			putInt("n", 2)
			putBundle("k1", good.getBundle("k0"))
			putBundle("v1", Bundle().apply { putString("type", "com.example.Gone") })
		}

		val restored = Nav3Continuations().apply { restoreFrom(broken) }

		assertEquals(OpenMedia("ok"), restored.peek(DetailScreen(1)))
		assertEquals(1, restored.pendingKeys.size)
	}

	// --- BackStackNav3Navigator ---

	@Test
	fun `a deep link addresses its continuation to the top screen`() {
		val navigator = BackStackNav3Navigator(NavBackStack(HomeScreen))
		navigator.navigateToDeepLink(
			Nav3DeepLink(listOf(HomeScreen, DetailScreen(1)), continuation = OpenMedia("c")),
		)

		assertEquals(OpenMedia("c"), navigator.continuations.peek(DetailScreen(1)))
	}

	@Test
	fun `an already open entry receives the continuation when surfaced`() {
		val navigator = BackStackNav3Navigator(NavBackStack(HomeScreen, DetailScreen(1), SettingsScreen))
		navigator.navigateToDeepLink(Nav3DeepLink(DetailScreen(1), continuation = OpenMedia("focus")))

		assertEquals(listOf(HomeScreen, DetailScreen(1)), navigator.items)
		assertEquals(OpenMedia("focus"), navigator.continuations.peek(DetailScreen(1)))
	}

	@Test
	fun `push with a continuation hands the next step to the pushed screen`() {
		val navigator = BackStackNav3Navigator(NavBackStack(HomeScreen))
		navigator.push(DetailScreen(5), continuation = OpenMedia("next"))

		assertEquals(DetailScreen(5), navigator.lastItem)
		assertEquals(OpenMedia("next"), navigator.continuations.peek(DetailScreen(5)))
	}

	@Test
	fun `popping an entry drops its continuation so it can't fire later`() {
		val navigator = BackStackNav3Navigator(NavBackStack(HomeScreen))
		navigator.push(DetailScreen(5), continuation = OpenMedia("next"))

		navigator.pop()
		navigator.push(DetailScreen(5)) // the same key coming back must not inherit it

		assertNull(navigator.continuations.peek(DetailScreen(5)))
	}

	@Test
	fun `every stack-changing operation prunes`() {
		val ops: List<Pair<String, (BackStackNav3Navigator) -> Unit>> = listOf(
			"replace" to { it.replace(SettingsScreen) },
			"replaceAll" to { it.replaceAll(SettingsScreen) },
			"popUpTo" to { it.popUpTo(HomeScreen) },
			"popUntilRoot" to { it.popUntilRoot() },
			"reconcile" to { it.navigateToDeepLink(Nav3DeepLink(listOf(HomeScreen, ListScreen), Nav3DeepLinkMode.Reconcile)) },
		)
		for ((name, op) in ops) {
			val navigator = BackStackNav3Navigator(NavBackStack(HomeScreen))
			navigator.push(DetailScreen(5), continuation = OpenMedia("x"))
			op(navigator)
			assertNull("$name left a continuation for a removed entry", navigator.continuations.peek(DetailScreen(5)))
		}
	}

	// --- TabsNav3Navigator ---

	private fun tabs() = TabsNav3Navigator(listOf(HomeScreen, ListScreen, SettingsScreen))

	@Test
	fun `a tab deep link addresses its continuation to the landed top`() {
		val tabs = tabs()
		tabs.navigateToDeepLink(Nav3DeepLink(DetailScreen(3), continuation = OpenMedia("c")))

		assertEquals(OpenMedia("c"), tabs.continuations.peek(DetailScreen(3)))
	}

	@Test
	fun `switching tabs keeps a continuation pending on the retained tab`() {
		val tabs = tabs()
		tabs.navigateToDeepLink(Nav3DeepLink(listOf(ListScreen, DetailScreen(3)), continuation = OpenMedia("c")))

		tabs.switchTab(HomeScreen)

		assertEquals(OpenMedia("c"), tabs.continuations.peek(DetailScreen(3)))
	}

	@Test
	fun `popping the entry in its tab drops the continuation`() {
		val tabs = tabs()
		tabs.navigateToDeepLink(Nav3DeepLink(listOf(ListScreen, DetailScreen(3)), continuation = OpenMedia("c")))

		tabs.pop()

		assertNull(tabs.continuations.peek(DetailScreen(3)))
	}

	@Test
	fun `the tabs saver keeps pending continuations`() {
		val tabs = tabs()
		tabs.navigateToDeepLink(Nav3DeepLink(listOf(ListScreen, DetailScreen(3)), continuation = OpenMedia("c")))

		val saver = TabsNav3Navigator.saver(tabs.tabOrder, tabs.startTab, parent = null)
		val restored = saver.restore(with(saver) { saverScope.save(tabs) }!!)!!

		assertEquals(listOf(ListScreen, DetailScreen(3)), restored.items)
		assertEquals(OpenMedia("c"), restored.continuations.peek(DetailScreen(3)))
	}

	// --- interface default ---

	@Test
	fun `the interface default delivers the continuation too`() {
		val inner = BackStackNav3Navigator(NavBackStack(HomeScreen))
		val wrapper = object : Nav3Navigator by inner {
			// Delegation would forward navigateToDeepLink too; restore the interface default.
			override fun navigateToDeepLink(link: Nav3DeepLink) = super.navigateToDeepLink(link)
		}

		wrapper.navigateToDeepLink(Nav3DeepLink(listOf(HomeScreen, DetailScreen(2)), continuation = FocusComments))

		assertEquals(listOf(HomeScreen, DetailScreen(2)), inner.items)
		assertEquals(FocusComments, inner.continuations.peek(DetailScreen(2)))
	}
}
