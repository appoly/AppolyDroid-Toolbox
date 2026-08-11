package uk.co.appoly.droid.nav3

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime coverage for the tab-retention model, exercised through a **real** [Nav3TabsHost] /
 * `NavDisplay` composition rather than the navigator in isolation.
 *
 * The other tab tests assert the navigator's flattened `backStack` and the strategy's
 * `previousEntries` as data. Neither proves the point of the design: that keeping a visited tab's
 * key in the back stack actually preserves its `ViewModelStore` and `rememberSaveable` state
 * across a tab switch, and that [Nav3RetentionScope.clear] actually tears that down.
 *
 * The failure mode being guarded is the one documented on [Nav3RetentionScope]: tab roots are
 * stable content keys, so without a clearable parent a later user of the same Activity reattaches
 * to the previous user's ViewModels.
 */
@RunWith(AndroidJUnit4::class)
class Nav3TabsRetentionTest {

	@get:Rule
	val composeRule = createComposeRule()

	private val tabOrder = listOf(RetentionTabA, RetentionTabB, RetentionTabC)

	@Before
	fun setUp() {
		RetentionProbes.reset()
	}

	@After
	fun tearDown() {
		RetentionProbes.reset()
	}

	// --- Retention across tab switches -------------------------------------------------------

	@Test
	fun `tab ViewModel survives switching away and back`() {
		val retentionScope = Nav3RetentionScope()
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		val firstA = RetentionProbes.viewModelFor("A")
		assertNotNull("tab A should have composed and resolved a ViewModel", firstA)

		composeRule.runOnIdle { tabs.switchTab(RetentionTabB) }
		composeRule.waitForIdle()
		composeRule.runOnIdle { tabs.switchTab(RetentionTabA) }
		composeRule.waitForIdle()

		assertSame(
			"tab A's ViewModel must survive the round trip — that is the whole point of retention",
			firstA,
			RetentionProbes.viewModelFor("A"),
		)
		assertFalse("surviving ViewModel must not have been cleared", firstA!!.cleared)
	}

	@Test
	fun `tab rememberSaveable state survives switching away and back`() {
		val retentionScope = Nav3RetentionScope()
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		composeRule.runOnIdle { RetentionProbes.counterFor("A")!!.value = 7 }
		composeRule.waitForIdle()

		composeRule.runOnIdle { tabs.switchTab(RetentionTabC) }
		composeRule.waitForIdle()
		composeRule.runOnIdle { tabs.switchTab(RetentionTabA) }
		composeRule.waitForIdle()

		assertEquals(
			"per-tab rememberSaveable state must survive a tab switch",
			7,
			RetentionProbes.counterFor("A")!!.value,
		)
	}

	@Test
	fun `each tab gets its own ViewModel`() {
		val retentionScope = Nav3RetentionScope()
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		composeRule.runOnIdle { tabs.switchTab(RetentionTabB) }
		composeRule.waitForIdle()

		val a = RetentionProbes.viewModelFor("A")
		val b = RetentionProbes.viewModelFor("B")
		assertNotNull(a)
		assertNotNull(b)
		assertNotSame("per-entry ViewModelStores must not be shared across tabs", a, b)
	}

	@Test
	fun `only the current tab is composed`() {
		val retentionScope = Nav3RetentionScope()
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		composeRule.onNodeWithText("Tab A").assertIsDisplayed()
		composeRule.onNodeWithText("Tab B").assertDoesNotExist()

		composeRule.runOnIdle { tabs.switchTab(RetentionTabB) }
		composeRule.waitForIdle()

		composeRule.onNodeWithText("Tab B").assertIsDisplayed()
		composeRule.onNodeWithText("Tab A").assertDoesNotExist()
	}

	// --- Teardown: the security-relevant half -------------------------------------------------

	@Test
	fun `retentionScope clear tears down retained tab ViewModels`() {
		val retentionScope = Nav3RetentionScope()
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		// Visit a second tab so there is retained state on more than the start tab.
		composeRule.runOnIdle { tabs.switchTab(RetentionTabB) }
		composeRule.waitForIdle()

		val a = RetentionProbes.viewModelFor("A")!!
		val b = RetentionProbes.viewModelFor("B")!!
		assertFalse(a.cleared)
		assertFalse(b.cleared)

		// Sign-out.
		composeRule.runOnIdle { retentionScope.clear() }
		composeRule.waitForIdle()

		assertTrue("start tab's retained ViewModel must be cleared on sign-out", a.cleared)
		assertTrue("visited tab's retained ViewModel must be cleared on sign-out", b.cleared)
	}

	@Test
	fun `clear tears down the tab that is on screen at sign-out`() {
		// Regression guard. ViewModelStoreProvider's StateHolder.onCleared() deliberately skips any
		// entry with refCount > 0, and the composed entry always holds a live token — so clearing
		// the scope's store alone left the ViewModels of the screen the user was looking at alive.
		// Sign-out is normally triggered from a settings/account tab, i.e. exactly this case.
		val retentionScope = Nav3RetentionScope()
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		composeRule.runOnIdle { tabs.switchTab(RetentionTabC) }
		composeRule.waitForIdle()

		val onScreen = RetentionProbes.viewModelFor("C")!!
		assertFalse(onScreen.cleared)
		assertEquals("tab C should be the composed tab", RetentionTabC, tabs.currentTab)

		composeRule.runOnIdle { retentionScope.clear() }
		composeRule.waitForIdle()

		assertTrue(
			"the ViewModel of the tab visible at sign-out must be cleared, not just the hidden ones",
			onScreen.cleared,
		)
	}

	@Test
	fun `clearing the scope's own owner tears down the on-screen tab too`() {
		// The nesting a consumer gets when they hoist the scope into their own ViewModelStoreOwner
		// (a session-scoped owner cleared on sign-out) instead of calling clear() by hand. That
		// route reaches the scope through onCleared(), not clear() — it must be equivalent, or the
		// happy path silently loses the on-screen tab's teardown.
		val parentStore = ViewModelStore()
		val parentOwner = object : ViewModelStoreOwner {
			override val viewModelStore: ViewModelStore = parentStore
		}
		val retentionScope = ViewModelProvider(parentOwner)[Nav3RetentionScope::class.java]
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		composeRule.runOnIdle { tabs.switchTab(RetentionTabB) }
		composeRule.waitForIdle()

		val onScreen = RetentionProbes.viewModelFor("B")!!
		val hidden = RetentionProbes.viewModelFor("A")!!
		assertFalse(onScreen.cleared)
		assertFalse(hidden.cleared)

		// Sign-out via the owner, with no direct clear() call anywhere.
		composeRule.runOnIdle { parentStore.clear() }
		composeRule.waitForIdle()

		assertTrue("hidden tab should clear on the owner path", hidden.cleared)
		assertTrue(
			"on-screen tab must clear on the owner path too — onCleared must match clear()",
			onScreen.cleared,
		)
	}

	@Test
	fun `a second clear still tears down retained ViewModels`() {
		// The provider caches its StateHolder lazily from the parent store. A clear() that only
		// emptied the store would detach that holder permanently, so every later clear() would be
		// a no-op — retention would silently outlive every sign-out after the first.
		val retentionScope = Nav3RetentionScope()
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		composeRule.runOnIdle { retentionScope.clear() }
		composeRule.waitForIdle()

		val secondSession = RetentionProbes.viewModelFor("A")!!
		assertFalse("a fresh session's ViewModel must not start out cleared", secondSession.cleared)

		composeRule.runOnIdle { retentionScope.clear() }
		composeRule.waitForIdle()

		assertTrue("the second sign-out must tear down too", secondSession.cleared)
	}

	@Test
	fun `a new session does not reattach to the previous session's tab ViewModels`() {
		// The documented failure mode: stable tab-root content keys mean a second sign-in in the
		// same Activity would otherwise resolve the FIRST user's ViewModels.
		val retentionScope = Nav3RetentionScope()
		var session by mutableStateOf(0)

		composeRule.setContent {
			// Keyed on session so signing in again builds a fresh navigator, as a real app would.
			key(session) {
				val tabs = rememberTabsNav3Navigator(tabOrder)
				Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
			}
		}

		composeRule.waitForIdle()
		val firstUsersViewModel = RetentionProbes.viewModelFor("A")!!
		assertFalse(firstUsersViewModel.cleared)

		// Sign out, then sign back in as someone else.
		composeRule.runOnIdle {
			retentionScope.clear()
			session = 1
		}
		composeRule.waitForIdle()

		val secondUsersViewModel = RetentionProbes.viewModelFor("A")!!
		assertTrue("first user's ViewModel must have been cleared", firstUsersViewModel.cleared)
		assertNotSame(
			"second session must NOT reattach to the first session's ViewModel",
			firstUsersViewModel,
			secondUsersViewModel,
		)
		assertFalse(secondUsersViewModel.cleared)
	}

	@Test
	fun `scope survives sign-out and retains the next session's ViewModels`() {
		val retentionScope = Nav3RetentionScope()
		lateinit var tabs: TabsNav3Navigator

		composeRule.setContent {
			tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = retentionScope)
		}

		composeRule.waitForIdle()
		composeRule.runOnIdle { retentionScope.clear() }
		composeRule.waitForIdle()

		// Post-clear the scope must still be usable: retention resumes for the new session.
		val afterClear = RetentionProbes.viewModelFor("A")!!
		composeRule.runOnIdle { tabs.switchTab(RetentionTabB) }
		composeRule.waitForIdle()
		composeRule.runOnIdle { tabs.switchTab(RetentionTabA) }
		composeRule.waitForIdle()

		assertSame(
			"retention must still work after a clear — the scope is reusable, not spent",
			afterClear,
			RetentionProbes.viewModelFor("A"),
		)
	}

	@Test
	fun `rememberNav3RetentionScope resolves against the ambient owner`() {
		var scope: Nav3RetentionScope? = null

		composeRule.setContent {
			val resolved = rememberNav3RetentionScope()
			scope = resolved
			val tabs = rememberTabsNav3Navigator(tabOrder)
			Nav3TabsHost(tabsNavigator = tabs, retentionScope = resolved)
		}

		composeRule.waitForIdle()
		assertNotNull("scope should resolve from LocalViewModelStoreOwner", scope)
		assertNotNull(RetentionProbes.viewModelFor("A"))
	}
}

// --- Probe fixtures ---------------------------------------------------------------------------

/** Records whether [onCleared] ran, so teardown is observable from the test. */
internal class TabProbeViewModel : ViewModel() {
	var cleared: Boolean = false
		private set

	override fun onCleared() {
		cleared = true
		super.onCleared()
	}
}

/**
 * Collects the ViewModel and saveable state each probe tab resolved on its latest composition.
 * Top-level because [Nav3Screen] tab roots must be stable objects.
 */
internal object RetentionProbes {
	private val viewModels = mutableMapOf<String, TabProbeViewModel>()
	private val counters = mutableMapOf<String, MutableState<Int>>()

	fun record(tab: String, viewModel: TabProbeViewModel, counter: MutableState<Int>) {
		viewModels[tab] = viewModel
		counters[tab] = counter
	}

	fun viewModelFor(tab: String): TabProbeViewModel? = viewModels[tab]

	fun counterFor(tab: String): MutableState<Int>? = counters[tab]

	fun reset() {
		viewModels.clear()
		counters.clear()
	}
}

@Composable
private fun ProbeTabContent(tab: String) {
	val viewModel: TabProbeViewModel = viewModel()
	val counter = rememberSaveable { mutableStateOf(0) }
	RetentionProbes.record(tab, viewModel, counter)
	Text("Tab $tab")
}

@Serializable
internal data object RetentionTabA : Nav3Screen {
	@Composable
	override fun Content() = ProbeTabContent("A")
}

@Serializable
internal data object RetentionTabB : Nav3Screen {
	@Composable
	override fun Content() = ProbeTabContent("B")
}

@Serializable
internal data object RetentionTabC : Nav3Screen {
	@Composable
	override fun Content() = ProbeTabContent("C")
}
