package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Tests for the Phase 1 [java.time.Instant] Room TypeConverters added to
 * [DBDateConverters]. The converters are pure functions delegating to
 * [DateHelper.formatServerTimestamp] / [DateHelper.parseServerInstant] —
 * no Room database is required.
 */
class DBDateConvertersInstantTest {

	private val converters = DBDateConverters()

	@Before
	fun silenceLogger() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	@Test
	fun `instantToJson null returns null`() {
		assertNull(converters.instantToJson(null))
	}

	@Test
	fun `jsonToInstant null returns null`() {
		assertNull(converters.jsonToInstant(null))
	}

	@Test
	fun `jsonToInstant blank returns null`() {
		assertNull(converters.jsonToInstant(""))
	}

	@Test
	fun `instantToJson emits ISO-8601 UTC string`() {
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		assertEquals("2025-05-29T10:38:29.000000Z", converters.instantToJson(instant))
	}

	@Test
	fun `Instant round-trip preserves the original`() {
		val original = LocalDateTime.of(2025, 1, 15, 9, 0, 0, 123_456_000)
			.toInstant(ZoneOffset.UTC)
		val stored = converters.instantToJson(original)
		val restored = converters.jsonToInstant(stored)
		assertEquals(original, restored)
	}

	@Test
	fun `jsonToInstant matches DateHelper parseServerInstant`() {
		val text = "2025-05-29T10:38:29.000000Z"
		assertEquals(DateHelper.parseServerInstant(text), converters.jsonToInstant(text))
	}

	@Test
	fun `instantToJson matches DateHelper formatServerTimestamp`() {
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		assertEquals(DateHelper.formatServerTimestamp(instant), converters.instantToJson(instant))
	}
}
