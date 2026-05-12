package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Tests for the *naive* helpers [DateHelper.formatNaiveDateTime] and [DateHelper.parseNaiveDateTime].
 *
 * The format path is the **honest** spelling — output carries no zone marker. This is a deliberate
 * divergence from the deprecated `formatLocalDateTime` (which retains the literal-`Z` format
 * byte-identically for backward compat). The parse path is a strict superset of the deprecated
 * `parseLocalDateTime`: it tolerates the new no-Z format, the legacy literal-`Z` format, any
 * explicit ISO-8601 offset, and the short `yyyy-MM-dd HH:mm:ss` format.
 *
 * Tests cover:
 *  - Format output uses the honest [DateHelper.NAIVE_PATTERN_FULL] (no `Z`)
 *  - Format output **diverges** from the deprecated entry point (proves the wire-format change)
 *  - Parse tolerates legacy `Z`, new no-Z, arbitrary offsets, and short format
 *  - Parse parity with deprecated entry point on inputs the deprecated parser accepts
 *  - Round-trip through the naive helpers themselves
 */
@Suppress("DEPRECATION") // intentionally exercising the deprecated entry points for parity checks
class DateHelperNaiveAliasTest {

	@Before
	fun silenceLogger() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	// region formatNaiveDateTime

	@Test
	fun `formatNaiveDateTime null returns null`() {
		assertNull(DateHelper.formatNaiveDateTime(null))
	}

	@Test
	fun `formatNaiveDateTime emits NAIVE_PATTERN_FULL with no zone marker`() {
		val ldt = LocalDateTime.of(2025, 5, 29, 10, 38, 29)
		assertEquals("2025-05-29T10:38:29.000000", DateHelper.formatNaiveDateTime(ldt))
	}

	@Test
	fun `formatNaiveDateTime preserves microsecond precision`() {
		val ldt = LocalDateTime.of(2025, 5, 29, 10, 38, 29, 123_456_000)
		assertEquals("2025-05-29T10:38:29.123456", DateHelper.formatNaiveDateTime(ldt))
	}

	@Test
	fun `formatNaiveDateTime output never ends with Z`() {
		val ldt = LocalDateTime.of(2025, 5, 29, 10, 38, 29)
		val output = DateHelper.formatNaiveDateTime(ldt)
		assertFalse("naive output must not carry a zone marker", output!!.endsWith("Z"))
	}

	// endregion

	// region parseNaiveDateTime

	@Test
	fun `parseNaiveDateTime null returns null`() {
		assertNull(DateHelper.parseNaiveDateTime(null))
	}

	@Test
	fun `parseNaiveDateTime blank returns null`() {
		assertNull(DateHelper.parseNaiveDateTime("   "))
	}

	@Test
	fun `parseNaiveDateTime malformed returns null`() {
		assertNull(DateHelper.parseNaiveDateTime("not a timestamp"))
	}

	@Test
	fun `parseNaiveDateTime parses the new no-Z format`() {
		val parsed = DateHelper.parseNaiveDateTime("2025-05-29T10:38:29.000000")
		assertEquals(LocalDateTime.of(2025, 5, 29, 10, 38, 29), parsed)
	}

	@Test
	fun `parseNaiveDateTime accepts legacy literal-Z format`() {
		// Backward compat: data written by pre-1.4 must still parse.
		val parsed = DateHelper.parseNaiveDateTime("2025-05-29T10:38:29.000000Z")
		assertEquals(LocalDateTime.of(2025, 5, 29, 10, 38, 29), parsed)
	}

	@Test
	fun `parseNaiveDateTime accepts non-UTC offset and returns the wall-clock at that offset`() {
		// Naive contract: offset is dropped, digits returned as-is.
		val parsed = DateHelper.parseNaiveDateTime("2025-05-29T11:38:29.000000+01:00")
		assertEquals(LocalDateTime.of(2025, 5, 29, 11, 38, 29), parsed)
	}

	@Test
	fun `parseNaiveDateTime falls back to SERVER_PATTERN_SHORT`() {
		val parsed = DateHelper.parseNaiveDateTime("2025-05-29 10:38:29")
		assertEquals(LocalDateTime.of(2025, 5, 29, 10, 38, 29), parsed)
	}

	@Test
	fun `parseNaiveDateTime round-trip preserves the LocalDateTime`() {
		val original = LocalDateTime.of(2025, 5, 29, 10, 38, 29, 123_456_000)
		val roundTripped = DateHelper.parseNaiveDateTime(DateHelper.formatNaiveDateTime(original))
		assertEquals(original, roundTripped)
	}

	// endregion

	// region Divergence from deprecated entry points (deliberate wire-format change)

	@Test
	fun `formatNaiveDateTime diverges from deprecated formatLocalDateTime by dropping the Z`() {
		val ldt = LocalDateTime.of(2025, 5, 29, 10, 38, 29)
		val deprecated = DateHelper.formatLocalDateTime(ldt)
		val naive = DateHelper.formatNaiveDateTime(ldt)

		assertNotEquals(deprecated, naive)
		assertTrue("deprecated format retains literal-Z for backward compat", deprecated!!.endsWith("Z"))
		assertFalse("naive format is honest about being zone-less", naive!!.endsWith("Z"))
		// The naive output is the deprecated output minus the trailing Z.
		assertEquals(deprecated.dropLast(1), naive)
	}

	@Test
	fun `formatNaiveDateTime matches deprecated formatLocalDateTime for null`() {
		// Null behaviour stays aligned (both return null).
		assertEquals(
			DateHelper.formatLocalDateTime(null),
			DateHelper.formatNaiveDateTime(null)
		)
	}

	// endregion

	// region Parse parity (parseNaiveDateTime is a strict superset of parseLocalDateTime)

	@Test
	fun `parseNaiveDateTime matches deprecated parseLocalDateTime on legacy Z input`() {
		val text = "2025-05-29T10:38:29.000000Z"
		assertEquals(
			DateHelper.parseLocalDateTime(text),
			DateHelper.parseNaiveDateTime(text)
		)
	}

	@Test
	fun `parseNaiveDateTime matches deprecated parseLocalDateTime on short input`() {
		val text = "2025-05-29 10:38:29"
		assertEquals(
			DateHelper.parseLocalDateTime(text),
			DateHelper.parseNaiveDateTime(text)
		)
	}

	@Test
	fun `parseNaiveDateTime matches deprecated parseLocalDateTime for null and blank`() {
		assertEquals(DateHelper.parseLocalDateTime(null), DateHelper.parseNaiveDateTime(null))
		assertEquals(DateHelper.parseLocalDateTime(""), DateHelper.parseNaiveDateTime(""))
		assertEquals(DateHelper.parseLocalDateTime("   "), DateHelper.parseNaiveDateTime("   "))
	}

	@Test
	fun `parseNaiveDateTime matches deprecated parseLocalDateTime for malformed`() {
		val text = "definitely not a timestamp"
		assertEquals(
			DateHelper.parseLocalDateTime(text),
			DateHelper.parseNaiveDateTime(text)
		)
	}

	@Test
	fun `parseNaiveDateTime is more lenient than deprecated parseLocalDateTime for offset input`() {
		// The deprecated parser only knew SERVER_PATTERN_FULL (literal Z) and
		// SERVER_PATTERN_SHORT — explicit non-UTC offsets failed to parse and returned null.
		// The new naive parser uses SERVER_PATTERN_FULL_OFFSET as a fallback and accepts them.
		val text = "2025-05-29T11:38:29.000000+01:00"
		assertNull(DateHelper.parseLocalDateTime(text))
		assertEquals(LocalDateTime.of(2025, 5, 29, 11, 38, 29), DateHelper.parseNaiveDateTime(text))
	}

	// endregion

	// region NAIVE_PATTERN_FULL constant

	@Test
	fun `NAIVE_PATTERN_FULL is honest no-zone format`() {
		assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", DateHelper.NAIVE_PATTERN_FULL)
	}

	// endregion
}
