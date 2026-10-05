package uk.co.appoly.droid.nav3

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records what [rememberNav3Continuation] hands each entry, and consumes it once. */
internal object ContinuationLog {
	val received = mutableListOf<Pair<Int, String>>()
	val seen = mutableMapOf<Int, String?>()
}

@Serializable
internal data class StepScreen(val id: Int) : Nav3Screen {
	@Composable
	override fun Content() {
		val step = rememberNav3Continuation<OpenMedia>()
		ContinuationLog.seen[id] = step?.value?.clipId
		LaunchedEffect(step) {
			val s = step ?: return@LaunchedEffect
			if (s.consume()) ContinuationLog.received += id to s.value.clipId
		}
		Text("Step $id")
	}
}

/**
 * [rememberNav3Continuation] inside a real [Nav3ScreenHost]: entry addressing, one-shot
 * consumption, type filtering, pruning of direct list edits, and state restoration.
 */
@RunWith(AndroidJUnit4::class)
class Nav3ContinuationComposeTest {

	@get:Rule
	val composeRule = createComposeRule()

	private fun reset() {
		ContinuationLog.received.clear()
		ContinuationLog.seen.clear()
	}

	@Test
	fun `the addressed entry receives its continuation exactly once`() {
		reset()
		val backStack = NavBackStack<NavKey>(StepScreen(1))
		lateinit var navigator: Nav3Navigator
		composeRule.setContent {
			navigator = rememberBackStackNav3Navigator(backStack)
			Nav3ScreenHost(backStack = backStack, navigator = navigator, entryDecorators = emptyList())
		}

		composeRule.runOnIdle { navigator.push(StepScreen(2), continuation = OpenMedia("clip")) }
		composeRule.waitForIdle()

		assertEquals(listOf(2 to "clip"), ContinuationLog.received)
		assertNull("consumed, so recomposition sees nothing", ContinuationLog.seen[2])
		composeRule.runOnIdle { assertNull(navigator.continuations.peek(StepScreen(2))) }
	}

	@Test
	fun `an entry doesn't see a continuation addressed to another entry`() {
		reset()
		val backStack = NavBackStack<NavKey>(StepScreen(1))
		lateinit var navigator: Nav3Navigator
		composeRule.setContent {
			navigator = rememberBackStackNav3Navigator(backStack)
			Nav3ScreenHost(backStack = backStack, navigator = navigator, entryDecorators = emptyList())
		}

		// Address entry 9, which isn't on the stack being shown; entry 1 must not pick it up.
		composeRule.runOnIdle { navigator.continuations.put(StepScreen(9), OpenMedia("not yours")) }
		composeRule.waitForIdle()

		assertEquals(emptyList<Pair<Int, String>>(), ContinuationLog.received)
	}

	@Test
	fun `a payload of another type is left for whoever expects it`() {
		reset()
		val backStack = NavBackStack<NavKey>(StepScreen(1))
		lateinit var navigator: Nav3Navigator
		composeRule.setContent {
			navigator = rememberBackStackNav3Navigator(backStack)
			Nav3ScreenHost(backStack = backStack, navigator = navigator, entryDecorators = emptyList())
		}

		composeRule.runOnIdle { navigator.push(StepScreen(2), continuation = FocusComments) }
		composeRule.waitForIdle()

		assertEquals(emptyList<Pair<Int, String>>(), ContinuationLog.received)
		composeRule.runOnIdle { assertEquals(FocusComments, navigator.continuations.peek(StepScreen(2))) }
	}

	@Test
	fun `the host prunes when the back stack list is edited directly`() {
		reset()
		val backStack = NavBackStack<NavKey>(StepScreen(1))
		lateinit var navigator: Nav3Navigator
		composeRule.setContent {
			navigator = rememberBackStackNav3Navigator(backStack)
			Nav3ScreenHost(backStack = backStack, navigator = navigator, entryDecorators = emptyList())
		}
		// Deliver to an entry with a different type so it stays pending, then remove the entry
		// behind the navigator's back.
		composeRule.runOnIdle { navigator.push(StepScreen(2), continuation = FocusComments) }
		composeRule.waitForIdle()

		composeRule.runOnIdle { backStack.removeAt(backStack.lastIndex) }
		composeRule.waitForIdle()

		composeRule.runOnIdle { assertNull(navigator.continuations.peek(StepScreen(2))) }
	}

	@Test
	fun `a pending continuation survives state restoration`() {
		reset()
		val restorationTester = StateRestorationTester(composeRule)
		val backStack = NavBackStack<NavKey>(StepScreen(1), StepScreen(2))
		lateinit var navigator: Nav3Navigator
		restorationTester.setContent {
			navigator = rememberBackStackNav3Navigator(backStack)
			Nav3ScreenHost(backStack = backStack, navigator = navigator, entryDecorators = emptyList())
		}
		// A type entry 2 doesn't consume, so it is still pending when state is saved.
		composeRule.runOnIdle { navigator.continuations.put(StepScreen(2), FocusComments) }

		restorationTester.emulateSavedInstanceStateRestore()

		composeRule.runOnIdle { assertEquals(FocusComments, navigator.continuations.peek(StepScreen(2))) }
	}
}
