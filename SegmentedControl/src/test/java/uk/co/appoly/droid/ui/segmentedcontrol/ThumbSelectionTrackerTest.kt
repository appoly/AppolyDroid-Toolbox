package uk.co.appoly.droid.ui.segmentedcontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the snap-vs-animate decision for the thumb.
 *
 * The first implementation latched: the flag was set on selection and never cleared when the
 * selection went back to null, so only the very first selection ever snapped and every later
 * empty→selection slid the thumb in from wherever it had been parked — the exact travelling the
 * feature set out to remove. It survived a hand test on a device because that test cleared the
 * selection and stopped, one step short of the bug, and no UI test can see the difference between
 * snapping and animating.
 *
 * Hence unit tests on the decision itself.
 */
class ThumbSelectionTrackerTest {

	@Test
	fun `the first selection from empty snaps`() {
		val tracker = ThumbSelectionTracker(initialHasSelection = false)

		assertTrue(tracker.onSelectionChanged(hasSelection = true))
	}

	@Test
	fun `moving between two real selections animates`() {
		val tracker = ThumbSelectionTracker(initialHasSelection = true)

		assertFalse(tracker.onSelectionChanged(hasSelection = true))
	}

	@Test
	fun `every selection after a clear snaps, not just the first`() {
		// The regression. Each empty→selection must snap, however many times it happens.
		val tracker = ThumbSelectionTracker(initialHasSelection = false)

		repeat(5) { round ->
			assertTrue(
				"selection #${round + 1} out of an empty state should snap",
				tracker.onSelectionChanged(hasSelection = true),
			)
			tracker.onSelectionChanged(hasSelection = false)
		}
	}

	@Test
	fun `clearing the selection does not itself snap`() {
		val tracker = ThumbSelectionTracker(initialHasSelection = true)

		assertFalse(tracker.onSelectionChanged(hasSelection = false))
	}

	@Test
	fun `a control that starts with a selection animates its first change`() {
		// Not every control starts empty. One that opens with an answer should behave like an
		// ordinary segmented control from the outset.
		val tracker = ThumbSelectionTracker(initialHasSelection = true)

		assertFalse(tracker.onSelectionChanged(hasSelection = true))
		assertFalse(tracker.onSelectionChanged(hasSelection = true))
	}

	@Test
	fun `repeated empty updates keep the next selection snapping`() {
		// Recomposition can deliver the same empty state more than once; that must not be
		// mistaken for "there was a selection".
		val tracker = ThumbSelectionTracker(initialHasSelection = false)

		repeat(3) { tracker.onSelectionChanged(hasSelection = false) }

		assertTrue(tracker.onSelectionChanged(hasSelection = true))
	}

	@Test
	fun `hadSelection tracks the previous state rather than latching`() {
		val tracker = ThumbSelectionTracker(initialHasSelection = false)
		assertFalse(tracker.hadSelection)

		tracker.onSelectionChanged(hasSelection = true)
		assertTrue(tracker.hadSelection)

		tracker.onSelectionChanged(hasSelection = false)
		assertFalse("hadSelection latched instead of following the selection", tracker.hadSelection)
	}
}
