package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Tests for the ISO-8601 tolerance fallbacks (1.6.1+): [DateHelper.parseServerInstant] and
 * [DateHelper.parseNaiveDateTime] accept ISO forms with **optional** fractional seconds, not just
 * the strict 6-digit-microsecond patterns.
 *
 * Motivating case: Laravel/Carbon `toIso8601String()` emits `2026-07-02T12:09:58+00:00` (no
 * fraction), which the strict [DateHelper.SERVER_PATTERN_FULL_OFFSET] rejects — pre-1.6.1 those
 * server timestamps silently parsed to null.
 */
class DateHelperIsoToleranceTest {

	@Before
	fun setUp() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	// region parseServerInstant — ISO offset forms without 6-digit fractions

	@Test
	fun `parseServerInstant accepts Carbon toIso8601String output`() {
		// The exact string a Laravel backend emitted in the field.
		val expected = LocalDateTime.of(2026, 7, 2, 12, 9, 58).toInstant(ZoneOffset.UTC)
		assertEquals(expected, DateHelper.parseServerInstant("2026-07-02T12:09:58+00:00"))
	}

	@Test
	fun `parseServerInstant accepts fraction-less Z suffix`() {
		val expected = LocalDateTime.of(2026, 7, 2, 12, 9, 58).toInstant(ZoneOffset.UTC)
		assertEquals(expected, DateHelper.parseServerInstant("2026-07-02T12:09:58Z"))
	}

	@Test
	fun `parseServerInstant accepts millisecond precision`() {
		val expected = LocalDateTime.of(2026, 7, 2, 12, 9, 58, 123_000_000).toInstant(ZoneOffset.UTC)
		assertEquals(expected, DateHelper.parseServerInstant("2026-07-02T12:09:58.123+00:00"))
	}

	@Test
	fun `parseServerInstant accepts fraction-less non-UTC offset`() {
		val expected = LocalDateTime.of(2026, 7, 2, 12, 9, 58).toInstant(ZoneOffset.ofHours(1))
		assertEquals(expected, DateHelper.parseServerInstant("2026-07-02T12:09:58+01:00"))
	}

	@Test
	fun `parseServerInstant still accepts the strict microsecond form`() {
		val expected = LocalDateTime.of(2026, 7, 2, 12, 9, 58, 123_456_000).toInstant(ZoneOffset.UTC)
		assertEquals(expected, DateHelper.parseServerInstant("2026-07-02T12:09:58.123456Z"))
	}

	@Test
	fun `parseServerInstant still rejects garbage`() {
		assertNull(DateHelper.parseServerInstant("not-a-date"))
	}

	// endregion

	// region parseNaiveDateTime — naive + offset ISO forms without 6-digit fractions

	@Test
	fun `parseNaiveDateTime accepts fraction-less naive ISO`() {
		assertEquals(
			LocalDateTime.of(2026, 7, 2, 12, 9, 58),
			DateHelper.parseNaiveDateTime("2026-07-02T12:09:58"),
		)
	}

	@Test
	fun `parseNaiveDateTime accepts millisecond naive ISO`() {
		assertEquals(
			LocalDateTime.of(2026, 7, 2, 12, 9, 58, 123_000_000),
			DateHelper.parseNaiveDateTime("2026-07-02T12:09:58.123"),
		)
	}

	@Test
	fun `parseNaiveDateTime accepts fraction-less offset form and drops the offset`() {
		assertEquals(
			LocalDateTime.of(2026, 7, 2, 12, 9, 58),
			DateHelper.parseNaiveDateTime("2026-07-02T12:09:58+01:00"),
		)
	}

	@Test
	fun `parseNaiveDateTime still accepts the strict naive microsecond form`() {
		assertEquals(
			LocalDateTime.of(2026, 7, 2, 12, 9, 58, 123_456_000),
			DateHelper.parseNaiveDateTime("2026-07-02T12:09:58.123456"),
		)
	}

	// endregion
}
