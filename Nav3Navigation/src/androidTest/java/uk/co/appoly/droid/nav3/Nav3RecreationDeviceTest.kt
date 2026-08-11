package uk.co.appoly.droid.nav3

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Activity recreation: the claim [Nav3RetentionScope] exists to make.
 *
 * Its KDoc argues that being a `ViewModel` gives it "the only correct lifetime: it survives
 * configuration change (which is why the host cannot simply clear on disposal — the two are
 * indistinguishable in composition)". That is a statement about the real
 * `NonConfigurationInstance` handoff, which no JVM test exercises. If it were false, every
 * rotation would wipe tab state.
 *
 * `recreate()` also drives the saver through real `Bundle` parceling and real reflective `NavKey`
 * serialization, rather than the in-process round trip the unit tests perform.
 */
@RunWith(AndroidJUnit4::class)
class Nav3RecreationDeviceTest {

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

	@Test
	fun retentionScopeSurvivesRecreation() {
		val before = scenario.awaitRetentionScope()

		scenario.recreateAndAwait()

		assertSame(
			"the scope must survive configuration change — its whole justification for being a " +
				"ViewModel is that disposal and rotation are indistinguishable in composition",
			before,
			scenario.awaitRetentionScope(),
		)
	}

	@Test
	fun tabViewModelsSurviveRecreation() {
		scenario.switchTabAndAwait(DeviceRoomsTab, "Rooms")
		val roomsBefore = DeviceProbes.viewModelFor("Rooms")!!

		scenario.recreateAndAwait(awaitTabContent = "Rooms")

		assertSame(
			"a rotation must not recreate the tab's ViewModel",
			roomsBefore,
			DeviceProbes.viewModelFor("Rooms"),
		)
		assertFalse("nor clear it", roomsBefore.cleared)
	}

	@Test
	fun currentTabAndPerTabStacksRestoreAcrossRecreation() {
		scenario.switchTabAndAwait(DeviceRoomsTab, "Rooms")
		scenario.onActivity { it.tabs!!.push(DeviceDetailScreen(11)) }
		scenario.switchTabAndAwait(DeviceSettingsTab, "Settings")
		scenario.onActivity { it.tabs!!.push(DeviceDetailScreen(22)) }
		scenario.waitUntil("the second push to land") { it.tabs!!.currentTabDepth == 2 }

		scenario.recreateAndAwait()

		scenario.onActivity { activity ->
			val tabs = activity.tabs!!
			assertEquals("selected tab must restore", DeviceSettingsTab, tabs.currentTab)
			assertEquals(
				"current tab's stack must restore",
				listOf(DeviceSettingsTab, DeviceDetailScreen(22)),
				tabs.stackFor(DeviceSettingsTab),
			)
			assertEquals(
				"an inactive visited tab's stack must restore too",
				listOf(DeviceRoomsTab, DeviceDetailScreen(11)),
				tabs.stackFor(DeviceRoomsTab),
			)
			assertEquals(DeviceDetailScreen(22), tabs.lastItem)
		}
	}

	@Test
	fun saveableTabStateSurvivesRecreation() {
		scenario.switchTabAndAwait(DeviceRoomsTab, "Rooms")
		val counterBefore = DeviceProbes.counterFor("Rooms")!!
		val hostBefore = DeviceProbes.hostFor("Rooms")
		scenario.onActivity { counterBefore.value = 42 }
		scenario.waitUntil("the counter write to render") {
			DeviceProbes.counterFor("Rooms")!!.value == 42
		}

		scenario.recreateAndAwait(awaitTabContent = "Rooms")

		// Freshness guard: prove the reading below comes from the recreated Activity, so the
		// assertion cannot pass on a stale probe from the outgoing one.
		//
		// Deliberately NOT asserting the MutableState is a new instance. Measured behaviour on
		// device is that rememberSaveable hands back the *same* state object across recreate()
		// (same identityHashCode) even though the composition demonstrably re-ran under the new
		// Activity and the new navigator. That is a Compose-internals detail, not a contract of
		// this module, so pinning it would be asserting someone else's implementation.
		assertNotSame(
			"the recreated Activity should be a different instance",
			hostBefore,
			DeviceProbes.hostFor("Rooms"),
		)
		assertEquals(
			"per-tab rememberSaveable state must survive rotation",
			42,
			DeviceProbes.counterFor("Rooms")!!.value,
		)
	}

	@Test
	fun clearAfterRecreationStillTearsDownTheOnScreenTab() {
		// The generation-keyed provider must survive the handoff intact. If recreation re-parented
		// a fresh provider while the scope kept its old generation, clear() would go back to
		// leaving the on-screen tab's ViewModels alive — the defect this all exists to prevent.
		scenario.switchTabAndAwait(DeviceSettingsTab, "Settings")

		scenario.recreateAndAwait(awaitTabContent = "Settings")

		val onScreen = DeviceProbes.viewModelFor("Settings")!!
		assertFalse(onScreen.cleared)

		scenario.onActivity { it.retentionScope!!.clear() }
		waitUntil("clear() to reach the on-screen tab's ViewModel") { onScreen.cleared }

		assertTrue("clear() must still reach the on-screen tab after a rotation", onScreen.cleared)
	}

	@Test
	fun retentionResumesAfterRecreationAndClear() {
		val signedOut = DeviceProbes.viewModelFor("Home")!!
		scenario.onActivity { it.retentionScope!!.clear() }
		waitUntil("the first session's ViewModel to be torn down") { signedOut.cleared }

		scenario.recreateAndAwait(awaitTabContent = "Home")

		val afterRestart = DeviceProbes.viewModelFor("Home")!!
		assertFalse(afterRestart.cleared)

		scenario.switchTabAndAwait(DeviceRoomsTab, "Rooms")
		scenario.switchTabAndAwait(DeviceHomeTab, "Home")

		assertSame(
			"retention must work again for the new session after clear + rotation",
			afterRestart,
			DeviceProbes.viewModelFor("Home"),
		)
	}

	@Test
	fun aFreshLaunchDoesNotInheritThePreviousActivitysTabViewModels() {
		// Sanity check that the probes observe distinct instances rather than a leaked singleton,
		// which would make every assertSame above vacuous.
		val first = DeviceProbes.viewModelFor("Home")!!

		scenario.close()
		DeviceProbes.reset()
		scenario = ActivityScenario.launch(Nav3TestActivity::class.java)
		scenario.awaitTabs()
		waitUntil("the relaunched start tab to compose") {
			DeviceProbes.compositionCount("Home") > 0
		}

		assertNotSame(
			"a brand new Activity must get its own tab ViewModels",
			first,
			DeviceProbes.viewModelFor("Home"),
		)
	}
}
