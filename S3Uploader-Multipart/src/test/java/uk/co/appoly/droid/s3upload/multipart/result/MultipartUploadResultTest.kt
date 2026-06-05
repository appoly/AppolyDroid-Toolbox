package uk.co.appoly.droid.s3upload.multipart.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [MultipartUploadResult] sealed result variants.
 */
class MultipartUploadResultTest {

	@Test
	fun `Success carries session, path and optional location`() {
		val result: MultipartUploadResult = MultipartUploadResult.Success("s1", "remote/a.bin", "https://x/a.bin")
		result as MultipartUploadResult.Success
		assertEquals("s1", result.sessionId)
		assertEquals("remote/a.bin", result.filePath)
		assertEquals("https://x/a.bin", result.location)
	}

	@Test
	fun `Error defaults to non-recoverable with no throwable`() {
		val error = MultipartUploadResult.Error("s1", "failed")
		assertFalse(error.isRecoverable)
		assertEquals(null, error.throwable)

		val recoverable = MultipartUploadResult.Error("s1", "net", RuntimeException(), isRecoverable = true)
		assertTrue(recoverable.isRecoverable)
	}

	@Test
	fun `Paused captures progress counters`() {
		val paused = MultipartUploadResult.Paused("s1", uploadedParts = 1, totalParts = 3, uploadedBytes = 100, totalBytes = 300)
		assertEquals(1, paused.uploadedParts)
		assertEquals(3, paused.totalParts)
		assertEquals(100L, paused.uploadedBytes)
		assertEquals(300L, paused.totalBytes)
	}

	@Test
	fun `Cancelled carries the session id`() {
		assertEquals("s1", MultipartUploadResult.Cancelled("s1").sessionId)
	}
}
