package uk.co.appoly.droid.s3upload.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [firstNotNullOrBlank], which returns the first option that yields a non-null,
 * non-blank value, swallowing exceptions from individual options and falling back when none
 * succeed. (S3Uploader logging defaults to NONE, so the exception path logs nothing.)
 */
class ExtensionsTest {

	@Test
	fun `returns the first non-blank option`() {
		val result = firstNotNullOrBlank({ "first" }, { "second" }, fallback = "fb")
		assertEquals("first", result)
	}

	@Test
	fun `skips null and blank options`() {
		val result = firstNotNullOrBlank({ null }, { "  " }, { "third" }, fallback = "fb")
		assertEquals("third", result)
	}

	@Test
	fun `falls back when every option is null or blank`() {
		val result = firstNotNullOrBlank({ null }, { "" }, fallback = "fallback")
		assertEquals("fallback", result)
	}

	@Test
	fun `swallows an option that throws and continues to the next`() {
		val result = firstNotNullOrBlank(
			{ throw IllegalStateException("boom") },
			{ "recovered" },
			fallback = "fb"
		)
		assertEquals("recovered", result)
	}

	@Test
	fun `falls back when the only option throws`() {
		val result = firstNotNullOrBlank<String>(
			{ throw RuntimeException("boom") },
			fallback = "fb"
		)
		assertEquals("fb", result)
	}
}
