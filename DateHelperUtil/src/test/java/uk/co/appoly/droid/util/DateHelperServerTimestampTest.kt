package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.UnsupportedTemporalTypeException
import java.util.TimeZone

/**
 * Tests for the Phase 1 server-timestamp API:
 * [DateHelper.formatServerTimestamp], [DateHelper.parseServerInstant],
 * and the [DateHelper.SERVER_PATTERN_FULL_OFFSET] pattern.
 *
 * Constraints under test:
 *  - Wire format uses `SERVER_PATTERN_FULL_OFFSET` internally — UTC moments render as
 *    `...Z`, byte-identical to the pre-1.4 legacy literal-`Z` output.
 *  - Output is independent of device timezone.
 *  - Round-trip parse-after-format yields the original [Instant].
 *  - The offset-aware pattern refuses bare [LocalDateTime].
 *  - Parser is strictly more lenient than pre-1.4: accepts `Z`, `+00:00`, `+01:00`, etc.
 */
class DateHelperServerTimestampTest {

	private lateinit var originalTimeZone: TimeZone

	@Before
	fun setUp() {
		originalTimeZone = TimeZone.getDefault()
		// Silence DateHelper logging so the drift-detection debug line in
		// parseServerInstant doesn't reach android.util.Log in JVM tests.
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	@After
	fun tearDown() {
		TimeZone.setDefault(originalTimeZone)
	}

	// region formatServerTimestamp(Instant?)

	@Test
	fun `formatServerTimestamp Instant null returns null`() {
		assertNull(DateHelper.formatServerTimestamp(null as Instant?))
	}

	@Test
	fun `formatServerTimestamp Instant emits UTC digits with literal Z`() {
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		assertEquals(
			"2025-05-29T10:38:29.000000Z",
			DateHelper.formatServerTimestamp(instant)
		)
	}

	@Test
	fun `formatServerTimestamp Instant preserves microsecond precision`() {
		// .SSSSSS in the pattern truncates to microseconds — the .000_000 nanos
		// suffix here should appear as 123456.
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29, 123_456_000)
			.toInstant(ZoneOffset.UTC)
		assertEquals(
			"2025-05-29T10:38:29.123456Z",
			DateHelper.formatServerTimestamp(instant)
		)
	}

	@Test
	fun `formatServerTimestamp Instant is independent of device timezone`() {
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		val expected = "2025-05-29T10:38:29.000000Z"

		TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
		assertEquals(expected, DateHelper.formatServerTimestamp(instant))

		TimeZone.setDefault(TimeZone.getTimeZone("Europe/London")) // BST in May
		assertEquals(expected, DateHelper.formatServerTimestamp(instant))

		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo")) // JST, no DST
		assertEquals(expected, DateHelper.formatServerTimestamp(instant))

		TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
		assertEquals(expected, DateHelper.formatServerTimestamp(instant))
	}

	// endregion

	// region formatServerTimestamp(ZonedDateTime?)

	@Test
	fun `formatServerTimestamp ZonedDateTime null returns null`() {
		assertNull(DateHelper.formatServerTimestamp(null as ZonedDateTime?))
	}

	@Test
	fun `formatServerTimestamp ZonedDateTime collapses non-UTC zone to UTC digits`() {
		// 11:38:29 +01:00 == 10:38:29 UTC
		val zoned = ZonedDateTime.of(
			LocalDateTime.of(2025, 5, 29, 11, 38, 29),
			ZoneOffset.ofHours(1)
		)
		assertEquals(
			"2025-05-29T10:38:29.000000Z",
			DateHelper.formatServerTimestamp(zoned)
		)
	}

	@Test
	fun `formatServerTimestamp ZonedDateTime UTC equals Instant of same moment`() {
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		val zoned = instant.atZone(ZoneOffset.UTC)
		assertEquals(
			DateHelper.formatServerTimestamp(instant),
			DateHelper.formatServerTimestamp(zoned)
		)
	}

	// endregion

	// region parseServerInstant(String?)

	@Test
	fun `parseServerInstant null returns null`() {
		assertNull(DateHelper.parseServerInstant(null))
	}

	@Test
	fun `parseServerInstant empty returns null`() {
		assertNull(DateHelper.parseServerInstant(""))
	}

	@Test
	fun `parseServerInstant blank returns null`() {
		assertNull(DateHelper.parseServerInstant("   "))
	}

	@Test
	fun `parseServerInstant malformed returns null`() {
		assertNull(DateHelper.parseServerInstant("not a timestamp"))
	}

	@Test
	fun `parseServerInstant returns Instant carrying UTC`() {
		val parsed = DateHelper.parseServerInstant("2025-05-29T10:38:29.000000Z")
		val expected = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		assertEquals(expected, parsed)
	}

	@Test
	fun `parseServerInstant accepts +0000 offset as UTC`() {
		// Equivalent to Z — the formatter's XXX designator handles both spellings.
		val parsed = DateHelper.parseServerInstant("2025-05-29T10:38:29.000000+00:00")
		val expected = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		assertEquals(expected, parsed)
	}

	@Test
	fun `parseServerInstant accepts non-UTC offset and returns the right Instant`() {
		// 11:38:29 +01:00 represents the same moment as 10:38:29 UTC.
		// The returned Instant should be the moment, not the wall-clock digits.
		val parsed = DateHelper.parseServerInstant("2025-05-29T11:38:29.000000+01:00")
		val expected = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		assertEquals(expected, parsed)
	}

	@Test
	fun `parseServerInstant accepts negative offsets`() {
		// 05:38:29 -05:00 represents the same moment as 10:38:29 UTC.
		val parsed = DateHelper.parseServerInstant("2025-05-29T05:38:29.000000-05:00")
		val expected = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		assertEquals(expected, parsed)
	}

	// endregion

	// region parseServerZoneDateTime(String?)

	@Test
	fun `parseServerZoneDateTime null returns null`() {
		assertNull(DateHelper.parseServerZoneDateTime(null))
	}

	@Test
	fun `parseServerZoneDateTime empty returns null`() {
		assertNull(DateHelper.parseServerZoneDateTime(""))
	}

	@Test
	fun `parseServerZoneDateTime blank returns null`() {
		assertNull(DateHelper.parseServerZoneDateTime("   "))
	}

	@Test
	fun `parseServerZoneDateTime malformed returns null`() {
		assertNull(DateHelper.parseServerZoneDateTime("not a timestamp"))
	}

	@Test
	fun `parseServerZoneDateTime returns ZonedDateTime in UTC for Z input`() {
		val parsed = DateHelper.parseServerZoneDateTime("2025-05-29T10:38:29.000000Z")
		val expected = LocalDateTime.of(2025, 5, 29, 10, 38, 29).atZone(ZoneOffset.UTC)
		assertEquals(expected, parsed)
		assertEquals(ZoneOffset.UTC, parsed!!.zone)
	}

	@Test
	fun `parseServerZoneDateTime returns ZonedDateTime in UTC for +0000 input`() {
		val parsed = DateHelper.parseServerZoneDateTime("2025-05-29T10:38:29.000000+00:00")
		val expected = LocalDateTime.of(2025, 5, 29, 10, 38, 29).atZone(ZoneOffset.UTC)
		assertEquals(expected, parsed)
		assertEquals(ZoneOffset.UTC, parsed!!.zone)
	}

	@Test
	fun `parseServerZoneDateTime collapses non-UTC offset to UTC`() {
		// 11:38:29 +01:00 represents the same moment as 10:38:29 UTC; the returned
		// ZonedDateTime carries the moment, expressed in UTC.
		val parsed = DateHelper.parseServerZoneDateTime("2025-05-29T11:38:29.000000+01:00")
		val expected = LocalDateTime.of(2025, 5, 29, 10, 38, 29).atZone(ZoneOffset.UTC)
		assertEquals(expected, parsed)
		assertEquals(ZoneOffset.UTC, parsed!!.zone)
	}

	@Test
	fun `parseServerZoneDateTime collapses negative offset to UTC`() {
		val parsed = DateHelper.parseServerZoneDateTime("2025-05-29T05:38:29.000000-05:00")
		val expected = LocalDateTime.of(2025, 5, 29, 10, 38, 29).atZone(ZoneOffset.UTC)
		assertEquals(expected, parsed)
		assertEquals(ZoneOffset.UTC, parsed!!.zone)
	}

	@Test
	fun `parseServerZoneDateTime always carries UTC zone regardless of device timezone`() {
		val text = "2025-05-29T10:38:29.000000Z"

		TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
		val bst = DateHelper.parseServerZoneDateTime(text)

		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
		val jst = DateHelper.parseServerZoneDateTime(text)

		assertEquals(ZoneOffset.UTC, bst!!.zone)
		assertEquals(ZoneOffset.UTC, jst!!.zone)
		assertEquals(bst, jst)
	}

	@Test
	fun `parseServerZoneDateTime equivalent to parseServerInstant atZone UTC`() {
		// Parity check: the helper is a thin convenience wrapper, output must match.
		val text = "2025-05-29T11:38:29.000000+01:00"
		val viaWrapper = DateHelper.parseServerZoneDateTime(text)
		val viaInstant = DateHelper.parseServerInstant(text)?.atZone(ZoneOffset.UTC)
		assertEquals(viaInstant, viaWrapper)
	}

	@Test
	fun `parseServerZoneDateTime round-trips through formatServerTimestamp`() {
		val original = LocalDateTime.of(2025, 6, 15, 14, 22, 51).atZone(ZoneOffset.UTC)
		val roundTripped = DateHelper.parseServerZoneDateTime(
			DateHelper.formatServerTimestamp(original)
		)
		assertEquals(original, roundTripped)
	}

	// endregion

	// region Round-trip parity

	@Test
	fun `round-trip parse-after-format yields original Instant`() {
		val original = LocalDateTime.of(2025, 1, 15, 9, 0, 0).toInstant(ZoneOffset.UTC)
		val roundTripped = DateHelper.parseServerInstant(
			DateHelper.formatServerTimestamp(original)
		)
		assertEquals(original, roundTripped)
	}

	@Test
	fun `round-trip preserves microsecond precision`() {
		val original = LocalDateTime.of(2025, 1, 15, 9, 0, 0, 123_456_000)
			.toInstant(ZoneOffset.UTC)
		val roundTripped = DateHelper.parseServerInstant(
			DateHelper.formatServerTimestamp(original)
		)
		assertEquals(original, roundTripped)
	}

	@Test
	fun `round-trip is timezone-independent`() {
		val original = LocalDateTime.of(2025, 6, 15, 14, 22, 51).toInstant(ZoneOffset.UTC)

		TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
		val roundTrippedBst = DateHelper.parseServerInstant(
			DateHelper.formatServerTimestamp(original)
		)

		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
		val roundTrippedJst = DateHelper.parseServerInstant(
			DateHelper.formatServerTimestamp(original)
		)

		assertEquals(original, roundTrippedBst)
		assertEquals(original, roundTrippedJst)
	}

	// endregion

	// region SERVER_PATTERN_FULL_OFFSET

	@Test
	fun `SERVER_PATTERN_FULL_OFFSET formats UTC Instant with Z`() {
		val formatter = DateTimeFormatter.ofPattern(DateHelper.SERVER_PATTERN_FULL_OFFSET)
			.withZone(ZoneOffset.UTC)
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		assertEquals("2025-05-29T10:38:29.000000Z", formatter.format(instant))
	}

	@Test
	fun `SERVER_PATTERN_FULL_OFFSET formats non-UTC zone with explicit offset`() {
		val formatter = DateTimeFormatter.ofPattern(DateHelper.SERVER_PATTERN_FULL_OFFSET)
		val zoned = ZonedDateTime.of(
			LocalDateTime.of(2025, 5, 29, 11, 38, 29),
			ZoneOffset.ofHours(1)
		)
		assertEquals("2025-05-29T11:38:29.000000+01:00", formatter.format(zoned))
	}

	@Test
	fun `SERVER_PATTERN_FULL_OFFSET refuses bare LocalDateTime`() {
		val formatter = DateTimeFormatter.ofPattern(DateHelper.SERVER_PATTERN_FULL_OFFSET)
		val ldt = LocalDateTime.of(2025, 5, 29, 10, 38, 29)
		try {
			formatter.format(ldt)
			fail("Expected UnsupportedTemporalTypeException for bare LocalDateTime")
		} catch (e: UnsupportedTemporalTypeException) {
			// expected — XXX requires a real offset, that's the feature
		}
	}

	// endregion
}
