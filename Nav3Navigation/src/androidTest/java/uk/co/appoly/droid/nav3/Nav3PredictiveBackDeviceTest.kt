package uk.co.appoly.droid.nav3

import androidx.activity.BackEventCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Predictive back, driven through a **real** [androidx.activity.OnBackPressedDispatcher].
 *
 * This is the part of the module no JVM test can reach. `predictivePopTransitionSpec` runs
 * *before* [TabsNav3Navigator.pop] commits, so it has to infer the slide direction rather than
 * read [TabsNav3Navigator.pendingTabSlide]; and back enablement is decided by whether
 * [TabsSceneStrategy] reported a non-empty `previousEntries`. Both are only exercised by a real
 * gesture reaching a real dispatcher.
 *
 * Gestures are dispatched programmatically (started → progressed → completed / cancelled) rather
 * than synthesised as touch events: the edge-swipe region is device- and gesture-mode-dependent,
 * and this suite is about the navigation contract, not the platform's swipe detection.
 */
@RunWith(AndroidJUnit4::class)
class Nav3PredictiveBackDeviceTest {

	private lateinit var scenario: ActivityScenario<Nav3TestActivity>

	@Before
	fun setUp() {
		DeviceProbes.reset()
		scenario = ActivityScenario.launch(Nav3TestActivity::class.java)
		scenario.awaitTabs()
		waitUntil("the start tab to compose") { DeviceProbes.compositionCount("Home") > 0 }
	}

	@After
	fun tearDown() {
		scenario.close()
		DeviceProbes.reset()
	}

	private fun backEvent(progress: Float) = BackEventCompat(
		touchX = 0f,
		touchY = 100f,
		progress = progress,
		swipeEdge = BackEventCompat.EDGE_LEFT,
	)

	/** Runs a full predictive-back gesture: started → progressed → completed. */
	private fun completeBackGesture() {
		scenario.onActivity { activity ->
			val dispatcher = activity.onBackPressedDispatcher
			dispatcher.dispatchOnBackStarted(backEvent(0f))
			dispatcher.dispatchOnBackProgressed(backEvent(0.3f))
			dispatcher.dispatchOnBackProgressed(backEvent(0.7f))
			dispatcher.onBackPressed()
		}
		idle()
	}

	/** Runs a predictive-back gesture the user abandons: started → progressed → cancelled. */
	private fun cancelBackGesture() {
		scenario.onActivity { activity ->
			val dispatcher = activity.onBackPressedDispatcher
			dispatcher.dispatchOnBackStarted(backEvent(0f))
			dispatcher.dispatchOnBackProgressed(backEvent(0.4f))
			dispatcher.dispatchOnBackCancelled()
		}
		idle()
	}

	@Test
	fun completedGestureDeeperInATabPopsThatTabOnly() {
		scenario.switchTabAndAwait(DeviceRoomsTab, "Rooms")
		scenario.onActivity {
			it.tabs!!.push(DeviceDetailScreen(1))
			it.tabs!!.push(DeviceDetailScreen(2))
		}
		scenario.waitUntil("the tab to reach depth 3") { it.tabs!!.currentTabDepth == 3 }

		completeBackGesture()

		scenario.waitUntil("the gesture to pop one screen") { it.tabs!!.currentTabDepth == 2 }
		scenario.onActivity { activity ->
			val tabs = activity.tabs!!
			assertEquals("should stay on the same tab", DeviceRoomsTab, tabs.currentTab)
			assertEquals(DeviceDetailScreen(1), tabs.lastItem)
		}
	}

	@Test
	fun completedGestureAtANonStartTabRootExitsThroughHome() {
		scenario.switchTabAndAwait(DeviceSettingsTab, "Settings")
		scenario.onActivity { activity ->
			assertTrue(activity.tabs!!.isAtCurrentTabRoot)
			assertFalse(activity.tabs!!.isOnStartTab)
		}

		completeBackGesture()

		scenario.waitUntil("exit-through-home to land on the start tab") {
			it.tabs!!.currentTab == DeviceHomeTab
		}
		assertFalse("Activity must not have finished", isFinishing())
	}

	@Test
	fun predictiveSpecInfersTheSameDirectionTheCommittedPopUses() {
		// The spec reads exitToStartTabSlide before the pop commits; pendingTabSlide is only set
		// after. If these disagree, the animation runs one way and settles the other.
		scenario.switchTabAndAwait(DeviceSettingsTab, "Settings")

		var inferred: TabSlide? = null
		scenario.onActivity { inferred = it.tabs!!.exitToStartTabSlide }

		completeBackGesture()

		scenario.waitUntil("the pop to commit") { it.tabs!!.currentTab == DeviceHomeTab }
		scenario.onActivity { activity ->
			assertEquals(
				"predictive direction must match the direction the committed pop recorded",
				inferred,
				activity.tabs!!.pendingTabSlide,
			)
		}
	}

	@Test
	fun cancelledGestureLeavesNavigationUntouched() {
		scenario.switchTabAndAwait(DeviceRoomsTab, "Rooms")
		scenario.onActivity { it.tabs!!.push(DeviceDetailScreen(7)) }
		scenario.waitUntil("the push to land") { it.tabs!!.currentTabDepth == 2 }

		cancelBackGesture()

		scenario.onActivity { activity ->
			val tabs = activity.tabs!!
			assertEquals("a cancelled gesture must not navigate", 2, tabs.currentTabDepth)
			assertEquals(DeviceRoomsTab, tabs.currentTab)
			assertEquals(DeviceDetailScreen(7), tabs.lastItem)
		}
	}

	@Test
	fun cancelledGestureAtANonStartTabRootDoesNotSwitchTabs() {
		scenario.switchTabAndAwait(DeviceSettingsTab, "Settings")

		cancelBackGesture()

		scenario.onActivity { activity ->
			assertEquals(
				"cancelling at a tab root must not run exit-through-home",
				DeviceSettingsTab,
				activity.tabs!!.currentTab,
			)
		}
		assertFalse(isFinishing())
	}

	@Test
	fun backIsDisabledAtTheStartTabRootEvenWithTabsRetainedBeneathIt() {
		// The contract is callback *enablement*, not what the platform does afterwards:
		// TabsSceneStrategy reports empty previousEntries at the start-tab root, so Nav3 leaves
		// its OnBackPressedCallback disabled and the system default (background the app) runs.
		// Retention puts other tabs' entries physically beneath home in the flattened back stack,
		// so this is the assertion that stops back walking into them.
		scenario.switchTabAndAwait(DeviceRoomsTab, "Rooms")
		scenario.switchTabAndAwait(DeviceHomeTab, "Home")

		scenario.onActivity { activity ->
			val tabs = activity.tabs!!
			assertTrue(tabs.isOnStartTab)
			assertTrue(tabs.isAtCurrentTabRoot)
			assertTrue("rooms should still be retained beneath home", tabs.backStack.size > 1)
			assertFalse("canPop must be false at the start-tab root", tabs.canPop)
		}

		waitUntil("Nav3 to disable its back callback at the start-tab root") {
			var enabled = true
			scenario.onActivity { enabled = it.onBackPressedDispatcher.hasEnabledCallbacks() }
			!enabled
		}
	}

	@Test
	fun backIsEnabledWhenThereIsSomewhereToGo() {
		// Counterpart to the above: without this, "disabled at the root" could pass simply because
		// the host never registers a callback at all.
		scenario.switchTabAndAwait(DeviceSettingsTab, "Settings")

		waitUntil("Nav3 to enable back at a non-start tab root (exit-through-home)") {
			var enabled = false
			scenario.onActivity { enabled = it.onBackPressedDispatcher.hasEnabledCallbacks() }
			enabled
		}

		scenario.onActivity { it.tabs!!.push(DeviceDetailScreen(3)) }
		scenario.waitUntil("the push to land") { it.tabs!!.currentTabDepth == 2 }

		waitUntil("back to stay enabled deeper in a tab") {
			var enabled = false
			scenario.onActivity { enabled = it.onBackPressedDispatcher.hasEnabledCallbacks() }
			enabled
		}
	}

	private fun isFinishing(): Boolean {
		var finishing = false
		try {
			scenario.onActivity { finishing = it.isFinishing || it.isDestroyed }
		} catch (_: Throwable) {
			// onActivity throws once the Activity is gone, which is itself the signal.
			finishing = true
		}
		return finishing
	}
}
