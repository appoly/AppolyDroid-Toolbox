package uk.co.appoly.droid.s3upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the [UploadResult] and [DirectUploadResult] sealed result types.
 */
class UploadResultTest {

	@Test
	fun `UploadResult Success carries the file path`() {
		val result: UploadResult = UploadResult.Success("images/a.jpg")
		assertEquals("images/a.jpg", (result as UploadResult.Success).filePath)
	}

	@Test
	fun `UploadResult Error carries message and optional throwable`() {
		val cause = IllegalStateException("x")
		val withCause = UploadResult.Error("failed", cause)
		assertEquals("failed", withCause.message)
		assertEquals(cause, withCause.throwable)

		val withoutCause = UploadResult.Error("failed")
		assertNull(withoutCause.throwable)
	}

	@Test
	fun `DirectUploadResult Success is a singleton`() {
		assertEquals(DirectUploadResult.Success, DirectUploadResult.Success)
	}

	@Test
	fun `DirectUploadResult Error carries message and optional throwable`() {
		val error = DirectUploadResult.Error("nope")
		assertEquals("nope", error.message)
		assertNull(error.throwable)
	}
}
