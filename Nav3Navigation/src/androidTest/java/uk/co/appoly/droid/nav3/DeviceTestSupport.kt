package uk.co.appoly.droid.nav3

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Synchronisation helpers for the on-device suite.
 *
 * `Instrumentation.waitForIdleSync()` alone is not enough here: it returns once the main looper
 * is idle, which can be *before* Compose has produced the frame that applies a snapshot mutation
 * and re-runs the affected composables. Asserting straight after a mutation is therefore racy —
 * it passes or fails depending on frame timing, which is exactly the kind of flake that gets an
 * on-device suite ignored and then deleted.
 *
 * Everything below polls to a deadline instead of assuming a frame has landed.
 */
private const val DEFAULT_TIMEOUT_MS = 5_000L
private const val POLL_INTERVAL_MS = 16L

/** Pumps the main looper once. */
internal fun idle() {
	InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}

/**
 * Polls [condition] against the running Activity until it holds, or fails with [message].
 *
 * @throws AssertionError if [condition] has not held by [timeoutMs].
 */
internal fun <A : androidx.activity.ComponentActivity> ActivityScenario<A>.waitUntil(
	message: String,
	timeoutMs: Long = DEFAULT_TIMEOUT_MS,
	condition: (A) -> Boolean,
) {
	val deadline = SystemClock.uptimeMillis() + timeoutMs
	while (true) {
		idle()
		var satisfied = false
		onActivity { satisfied = condition(it) }
		if (satisfied) return
		if (SystemClock.uptimeMillis() >= deadline) {
			throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $message")
		}
		SystemClock.sleep(POLL_INTERVAL_MS)
	}
}

/** Polls a condition that does not need the Activity (e.g. [DeviceProbes] state). */
internal fun waitUntil(
	message: String,
	timeoutMs: Long = DEFAULT_TIMEOUT_MS,
	condition: () -> Boolean,
) {
	val deadline = SystemClock.uptimeMillis() + timeoutMs
	while (true) {
		idle()
		if (condition()) return
		if (SystemClock.uptimeMillis() >= deadline) {
			throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $message")
		}
		SystemClock.sleep(POLL_INTERVAL_MS)
	}
}

/** The navigator the current composition is driving, once it exists. */
internal fun ActivityScenario<Nav3TestActivity>.awaitTabs(): TabsNav3Navigator {
	waitUntil("the composition to resolve a TabsNav3Navigator") { it.tabs != null }
	lateinit var tabs: TabsNav3Navigator
	onActivity { tabs = it.tabs!! }
	return tabs
}

/** The retention scope the current composition resolved, once it exists. */
internal fun ActivityScenario<Nav3TestActivity>.awaitRetentionScope(): Nav3RetentionScope {
	waitUntil("the composition to resolve a Nav3RetentionScope") { it.retentionScope != null }
	lateinit var scope: Nav3RetentionScope
	onActivity { scope = it.retentionScope!! }
	return scope
}

/**
 * Switches to [tab] and waits until the navigator reports it selected *and* its content has
 * recorded a probe.
 *
 * Waits on observable navigator state rather than a composition counter: during `recreate()` the
 * outgoing Activity's composition can run once more on its way out, so a bare "did the count go
 * up" signal fires for the wrong Activity.
 */
internal fun ActivityScenario<Nav3TestActivity>.switchTabAndAwait(
	tab: Nav3Screen,
	probeName: String,
) {
	onActivity { it.tabs!!.switchTab(tab) }
	waitUntil("the navigator to select $tab") { it.tabs!!.currentTab == tab }
	waitUntil("tab '$probeName' content to compose") { DeviceProbes.viewModelFor(probeName) != null }
}

/**
 * Recreates the Activity and waits until a **new** composition is in place, identified by the
 * navigator instance changing.
 *
 * `rememberTabsNav3Navigator` is `rememberSaveable`-backed, so the new Activity restores an equal
 * but distinct navigator — making instance identity an unambiguous "the new Activity has composed"
 * signal in a way that probe counts are not.
 *
 * @return the navigator belonging to the new composition.
 */
internal fun ActivityScenario<Nav3TestActivity>.recreateAndAwait(
	awaitTabContent: String? = null,
): TabsNav3Navigator {
	val previous = awaitTabs()
	recreate()
	waitUntil("the recreated Activity to establish a new composition") {
		it.tabs != null && it.tabs !== previous
	}
	val current = awaitTabs()
	if (awaitTabContent != null) {
		// The navigator is published from setContent, before the host has composed its entries —
		// so a fresh navigator alone does not mean the tab's content has re-rendered yet.
		waitUntil("tab '$awaitTabContent' to render under the recreated navigator") {
			DeviceProbes.navigatorFor(awaitTabContent) === current
		}
	}
	return current
}
