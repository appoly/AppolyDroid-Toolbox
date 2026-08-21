package uk.co.appoly.droid.s3upload.multipart.result

import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import kotlin.math.roundToLong

/**
 * Derives [MultipartUploadProgress.bytesPerSecond] and
 * [MultipartUploadProgress.estimatedTimeRemainingMs] from consecutive progress emissions.
 *
 * Progress itself is a pure function of the database rows, which carry no history, so a transfer
 * rate can only come from comparing successive samples. That makes this stateful and deliberately
 * **per-collector**: each subscriber owns a tracker, because two observers of the same upload
 * sample at their own pace and must not corrupt each other's baseline.
 *
 * Rates are smoothed exponentially — a raw delta between two samples swings wildly with normal
 * network jitter and produces an ETA that jumps around uselessly.
 *
 * Not thread-safe. Flow collection is sequential, which is the only context this is used in.
 */
internal class TransferRateTracker(
	private val now: () -> Long = System::currentTimeMillis
) {

	private class Sample(
		val bytes: Long,
		val atMs: Long,
		val smoothedBytesPerSecond: Double?
	)

	private val samples = mutableMapOf<String, Sample>()

	/**
	 * Returns [progress] with a transfer rate and ETA attached, if enough history exists to
	 * compute one honestly. Otherwise returns it untouched, leaving both fields null.
	 */
	fun track(progress: MultipartUploadProgress): MultipartUploadProgress {
		// Bytes only move while parts are in flight. Discarding the baseline on pause matters:
		// keeping it would measure the next sample across the whole paused gap and report a
		// near-zero rate with an absurd ETA the moment the upload resumes.
		if (progress.status != UploadSessionStatus.IN_PROGRESS) {
			samples.remove(progress.sessionId)
			return progress
		}

		val at = now()
		val previous = samples[progress.sessionId]
		if (previous == null) {
			samples[progress.sessionId] = Sample(progress.uploadedBytes, at, null)
			return progress
		}

		val deltaBytes = progress.uploadedBytes - previous.bytes
		if (deltaBytes < 0) {
			// A part being retried rewinds its byte count. Rebase instead of reporting a negative
			// rate, and go quiet until a fresh pair of samples exists.
			samples[progress.sessionId] = Sample(progress.uploadedBytes, at, null)
			return progress
		}

		val elapsedMs = at - previous.atMs
		if (elapsedMs < MIN_SAMPLE_INTERVAL_MS) {
			// Too short a window to divide by. Keep the *older* baseline rather than replacing it,
			// so the window widens on each emission and a rate is eventually produced even when
			// progressUpdateIntervalMs is set below the minimum sample interval.
			return progress.withRate(previous.smoothedBytesPerSecond)
		}

		val instantBytesPerSecond = deltaBytes * MILLIS_PER_SECOND / elapsedMs
		val smoothed = previous.smoothedBytesPerSecond
			?.let { SMOOTHING_FACTOR * instantBytesPerSecond + (1 - SMOOTHING_FACTOR) * it }
			?: instantBytesPerSecond

		samples[progress.sessionId] = Sample(progress.uploadedBytes, at, smoothed)
		return progress.withRate(smoothed)
	}

	/**
	 * Drops history for sessions no longer being observed, so tracking a long-lived list of
	 * uploads does not accumulate samples for every session that has ever completed.
	 */
	fun retainOnly(sessionIds: Set<String>) {
		samples.keys.retainAll(sessionIds)
	}

	private fun MultipartUploadProgress.withRate(bytesPerSecond: Double?): MultipartUploadProgress {
		// Below a byte per second there is nothing worth reporting, and the resulting ETA would be
		// long enough to be actively misleading.
		if (bytesPerSecond == null || bytesPerSecond < 1.0) return this

		val remainingBytes = (totalBytes - uploadedBytes).coerceAtLeast(0L)
		return copy(
			bytesPerSecond = bytesPerSecond.roundToLong(),
			estimatedTimeRemainingMs = (remainingBytes / bytesPerSecond * MILLIS_PER_SECOND).roundToLong()
		)
	}

	private companion object {
		const val MILLIS_PER_SECOND = 1000.0

		/** Shortest window worth dividing by, to keep a rate out of the noise. */
		const val MIN_SAMPLE_INTERVAL_MS = 250L

		/** Weight given to the newest sample; the rest carries over from the running estimate. */
		const val SMOOTHING_FACTOR = 0.3
	}
}
