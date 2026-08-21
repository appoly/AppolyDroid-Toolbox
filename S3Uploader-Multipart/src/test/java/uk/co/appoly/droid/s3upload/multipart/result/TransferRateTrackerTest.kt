package uk.co.appoly.droid.s3upload.multipart.result

import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TransferRateTracker], driven by a fake clock so rates are exact rather than
 * dependent on how fast the test machine happens to run.
 */
class TransferRateTrackerTest {

	private var clock = 0L
	private val tracker = TransferRateTracker { clock }

	private fun progress(
		uploadedBytes: Long,
		sessionId: String = "s1",
		status: UploadSessionStatus = UploadSessionStatus.IN_PROGRESS,
		totalBytes: Long = TOTAL_BYTES
	) = MultipartUploadProgress
		.initial(sessionId = sessionId, fileName = "$sessionId.bin", totalBytes = totalBytes, totalParts = 2)
		.copy(uploadedBytes = uploadedBytes, status = status)

	@Test
	fun `the first sample cannot yield a rate`() {
		val tracked = tracker.track(progress(uploadedBytes = 1_000))

		assertNull(tracked.bytesPerSecond)
		assertNull(tracked.estimatedTimeRemainingMs)
	}

	@Test
	fun `a second sample yields a rate and an ETA`() {
		tracker.track(progress(uploadedBytes = 0))
		clock += 1_000
		val tracked = tracker.track(progress(uploadedBytes = 2_000))

		// 2000 bytes in 1s, with 8000 of 10000 left => 4s remaining.
		assertEquals(2_000L, tracked.bytesPerSecond)
		assertEquals(4_000L, tracked.estimatedTimeRemainingMs)
	}

	@Test
	fun `the rate is smoothed rather than tracking the latest sample exactly`() {
		tracker.track(progress(uploadedBytes = 0))
		clock += 1_000
		val steady = tracker.track(progress(uploadedBytes = 1_000))
		assertEquals(1_000L, steady.bytesPerSecond)

		// A sudden 4x burst must not be reported verbatim: raw deltas swing with network jitter
		// and would make the ETA jump around uselessly.
		clock += 1_000
		val burst = tracker.track(progress(uploadedBytes = 5_000))

		val reported = burst.bytesPerSecond!!
		assertTrue("expected smoothing between 1000 and 4000, got $reported", reported in 1_001..3_999)
	}

	@Test
	fun `a window shorter than the sample interval widens instead of being discarded`() {
		tracker.track(progress(uploadedBytes = 0))

		// Emissions closer together than the minimum sample interval yield nothing yet...
		clock += 100
		assertNull(tracker.track(progress(uploadedBytes = 100)).bytesPerSecond)
		clock += 100
		assertNull(tracker.track(progress(uploadedBytes = 200)).bytesPerSecond)

		// ...but the baseline is kept, so the window keeps widening and a rate does arrive.
		// This is what stops a small progressUpdateIntervalMs from suppressing rates forever.
		clock += 100
		val tracked = tracker.track(progress(uploadedBytes = 300))
		assertEquals("300 bytes over 300ms", 1_000L, tracked.bytesPerSecond)
	}

	@Test
	fun `a retrying part that rewinds its byte count reports no rate`() {
		tracker.track(progress(uploadedBytes = 0))
		clock += 1_000
		assertNotNull(tracker.track(progress(uploadedBytes = 3_000)).bytesPerSecond)

		// A part restarting drops the session's byte count. Reporting a negative rate, or an ETA
		// derived from one, would be worse than reporting nothing.
		clock += 1_000
		val rewound = tracker.track(progress(uploadedBytes = 1_000))
		assertNull(rewound.bytesPerSecond)
		assertNull(rewound.estimatedTimeRemainingMs)

		// It re-establishes from the new baseline.
		clock += 1_000
		assertEquals(1_500L, tracker.track(progress(uploadedBytes = 2_500)).bytesPerSecond)
	}

	@Test
	fun `a paused session reports no rate and does not spike on resume`() {
		tracker.track(progress(uploadedBytes = 1_000))
		clock += 1_000
		assertNotNull(tracker.track(progress(uploadedBytes = 3_000)).bytesPerSecond)

		val paused = tracker.track(progress(uploadedBytes = 3_000, status = UploadSessionStatus.PAUSED))
		assertNull(paused.bytesPerSecond)

		// Long pause. If the baseline had survived it, the next sample would be measured across the
		// whole idle gap and report a near-zero rate with an absurd ETA.
		clock += 600_000
		val resumed = tracker.track(progress(uploadedBytes = 3_000))
		assertNull("resuming must re-baseline, not measure across the pause", resumed.bytesPerSecond)

		clock += 1_000
		assertEquals(2_000L, tracker.track(progress(uploadedBytes = 5_000)).bytesPerSecond)
	}

	@Test
	fun `a constraint-violation pause is treated as paused too`() {
		tracker.track(progress(uploadedBytes = 1_000))
		clock += 1_000
		val tracked = tracker.track(
			progress(uploadedBytes = 3_000, status = UploadSessionStatus.PAUSED_CONSTRAINT_VIOLATION)
		)

		assertNull(tracked.bytesPerSecond)
	}

	@Test
	fun `a rate below one byte per second is not reported`() {
		tracker.track(progress(uploadedBytes = 0))
		// A stalled transfer would otherwise produce an ETA measured in years.
		clock += 10_000
		val tracked = tracker.track(progress(uploadedBytes = 1))

		assertNull(tracked.bytesPerSecond)
		assertNull(tracked.estimatedTimeRemainingMs)
	}

	@Test
	fun `ETA is zero once every byte is accounted for`() {
		tracker.track(progress(uploadedBytes = 0))
		clock += 1_000
		val tracked = tracker.track(progress(uploadedBytes = TOTAL_BYTES))

		assertEquals(0L, tracked.estimatedTimeRemainingMs)
	}

	@Test
	fun `sessions are tracked independently`() {
		tracker.track(progress(uploadedBytes = 0, sessionId = "a"))
		tracker.track(progress(uploadedBytes = 0, sessionId = "b"))
		clock += 1_000

		assertEquals(1_000L, tracker.track(progress(uploadedBytes = 1_000, sessionId = "a")).bytesPerSecond)
		assertEquals(5_000L, tracker.track(progress(uploadedBytes = 5_000, sessionId = "b")).bytesPerSecond)
	}

	@Test
	fun `retainOnly forgets sessions that are no longer observed`() {
		tracker.track(progress(uploadedBytes = 1_000, sessionId = "a"))
		clock += 1_000

		tracker.retainOnly(setOf("b"))

		// "a" is a first sample again, so no rate — proving its history was dropped.
		assertNull(tracker.track(progress(uploadedBytes = 2_000, sessionId = "a")).bytesPerSecond)
	}

	private companion object {
		const val TOTAL_BYTES = 10_000L
	}
}
