package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests for the less-exercised [DateHelper] helpers: JSON string round-trips, the file-safe
 * string, nowAsUTC, and the format/parse helpers' null handling.
 */
class DateHelperExtrasTest {

	@Before
	fun setUp() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	@Test
	fun `LocalDateTime JSON string round-trips`() = with(DateHelper) {
		val dt = LocalDateTime.of(2026, 6, 5, 14, 30, 15)
		val json = dt.toJsonString()
		assertEquals(dt, json.parseJsonDateTime())
	}

	@Test
	fun `LocalDate JSON string round-trips`() = with(DateHelper) {
		val date = LocalDate.of(2026, 6, 5)
		val json = date.toJsonString()
		assertEquals(date, json.parseJsonDate())
	}

	@Test
	fun `null JSON strings map to null`() = with(DateHelper) {
		assertNull((null as LocalDateTime?).toJsonString())
		assertNull((null as String?).parseJsonDateTime())
		assertNull((null as LocalDate?).toJsonString())
		assertNull((null as String?).parseJsonDate())
	}

	@Test
	fun `toFileString produces a non-blank filesystem-safe string`() = with(DateHelper) {
		val s = LocalDateTime.of(2026, 1, 2, 3, 4, 5).toFileString()
		assertTrue(s.isNotBlank())
		assertTrue("must not contain ':'", !s.contains(":"))
	}

	@Test
	fun `nowAsUTC is in the UTC zone`() {
		val now = DateHelper.nowAsUTC()
		assertEquals("Z", now.zone.id)
	}

	@Test
	fun `format helpers return null for null input`() {
		assertNull(DateHelper.formatLocalDate(null))
		assertNull(DateHelper.formatLocalDateTime(null))
		assertNull(DateHelper.formatNaiveDateTime(null))
	}

	@Test
	fun `format helpers round-trip through their parsers`() {
		val date = LocalDate.of(2026, 6, 5)
		assertEquals(date, DateHelper.parseLocalDate(DateHelper.formatLocalDate(date)))

		val dt = LocalDateTime.of(2026, 6, 5, 9, 8, 7)
		assertEquals(dt, DateHelper.parseLocalDateTime(DateHelper.formatLocalDateTime(dt)))
	}
}
