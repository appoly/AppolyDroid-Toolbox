package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import uk.co.appoly.droid.util.DateHelper.parseJsonDate
import uk.co.appoly.droid.util.DateHelper.parseJsonDateTime
import uk.co.appoly.droid.util.DateHelper.toJsonString
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Snapshot tests pinning the *current* zone-naive behaviour of the legacy helpers.
 *
 * These do not endorse the literal-`Z` quirk — they exist so any future refactor
 * (Phase 2 deprecation, eventual removal, internal restructuring) cannot
 * accidentally alter byte-output for callers who already adapted around it.
 *
 * If a test here starts failing, it means a backward-incompatible change has
 * been introduced and needs explicit consideration before merging.
 *
 * The `@Suppress("DEPRECATION")` is intentional — this test class deliberately
 * exercises the deprecated entry points to ensure their behaviour stays stable
 * for as long as they exist.
 */
@Suppress("DEPRECATION")
class DateHelperLegacyBehaviourTest {

	@Before
	fun silenceLogger() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	// region formatLocalDateTime / parseLocalDateTime

	@Test
	fun `formatLocalDateTime emits literal Z without zone conversion`() {
		// The formatter treats LocalDateTime digits as wall-clock and bolts a
		// literal Z onto the end — no UTC normalisation. Existing callers
		// relying on this either feed pre-converted UTC LDTs (correct usage)
		// or rely on the bug (incorrect usage). Either way, the output digits
		// must remain stable so existing apps don't see behaviour drift.
		val ldt = LocalDateTime.of(2025, 5, 29, 10, 38, 29)
		assertEquals("2025-05-29T10:38:29.000000Z", DateHelper.formatLocalDateTime(ldt))
	}

	@Test
	fun `formatLocalDateTime null returns null`() {
		assertNull(DateHelper.formatLocalDateTime(null))
	}

	@Test
	fun `parseLocalDateTime returns naive LocalDateTime carrying the wall-clock digits`() {
		// The literal-'Z' is matched against the literal Z in the input and
		// then discarded; the returned LocalDateTime carries the digits as-is.
		val parsed = DateHelper.parseLocalDateTime("2025-05-29T10:38:29.000000Z")
		assertEquals(LocalDateTime.of(2025, 5, 29, 10, 38, 29), parsed)
	}

	@Test
	fun `parseLocalDateTime falls back to SERVER_PATTERN_SHORT`() {
		val parsed = DateHelper.parseLocalDateTime("2025-05-29 10:38:29")
		assertEquals(LocalDateTime.of(2025, 5, 29, 10, 38, 29), parsed)
	}

	@Test
	fun `parseLocalDateTime null and blank return null`() {
		assertNull(DateHelper.parseLocalDateTime(null))
		assertNull(DateHelper.parseLocalDateTime(""))
		assertNull(DateHelper.parseLocalDateTime("   "))
	}

	@Test
	fun `parseLocalDateTime malformed returns null`() {
		assertNull(DateHelper.parseLocalDateTime("not a timestamp"))
	}

	@Test
	fun `LocalDateTime round-trip preserves the LocalDateTime`() {
		val original = LocalDateTime.of(2025, 5, 29, 10, 38, 29)
		val roundTripped = DateHelper.parseLocalDateTime(DateHelper.formatLocalDateTime(original))
		assertEquals(original, roundTripped)
	}

	// endregion

	// region formatLocalDate / parseLocalDate

	@Test
	fun `formatLocalDate emits ISO-8601 date`() {
		assertEquals("2025-05-29", DateHelper.formatLocalDate(LocalDate.of(2025, 5, 29)))
	}

	@Test
	fun `formatLocalDate null returns null`() {
		assertNull(DateHelper.formatLocalDate(null))
	}

	@Test
	fun `parseLocalDate parses YYYY-MM-DD format`() {
		assertEquals(LocalDate.of(2025, 5, 29), DateHelper.parseLocalDate("2025-05-29"))
	}

	@Test
	fun `parseLocalDate falls back to date-time parsing for full timestamp string`() {
		assertEquals(
			LocalDate.of(2025, 5, 29),
			DateHelper.parseLocalDate("2025-05-29T10:38:29.000000Z")
		)
	}

	@Test
	fun `parseLocalDate null and blank return null`() {
		assertNull(DateHelper.parseLocalDate(null))
		assertNull(DateHelper.parseLocalDate(""))
	}

	@Test
	fun `LocalDate round-trip preserves the date`() {
		val original = LocalDate.of(2025, 5, 29)
		val roundTripped = DateHelper.parseLocalDate(DateHelper.formatLocalDate(original))
		assertEquals(original, roundTripped)
	}

	// endregion

	// region Extension functions

	@Test
	fun `LocalDateTime toJsonString matches naive helper not deprecated function`() {
		// In 1.4.0+ the LocalDateTime.toJsonString() extension routes through
		// formatNaiveDateTime (no Z), not the deprecated formatLocalDateTime (with Z).
		// This test pins that contract.
		val ldt = LocalDateTime.of(2025, 5, 29, 10, 38, 29)
		assertEquals(DateHelper.formatNaiveDateTime(ldt), ldt.toJsonString())
		// And confirms the divergence from the deprecated path is real.
		assertNotEquals(DateHelper.formatLocalDateTime(ldt), ldt.toJsonString())
	}

	@Test
	fun `LocalDate toJsonString matches formatLocalDate`() {
		val date = LocalDate.of(2025, 5, 29)
		assertEquals(DateHelper.formatLocalDate(date), date.toJsonString())
	}

	@Test
	fun `String parseJsonDateTime parses legacy Z input the same as deprecated parseLocalDateTime`() {
		// parseNaiveDateTime is a strict superset of parseLocalDateTime for parsing,
		// so on inputs the deprecated parser accepts they return the same value.
		val text = "2025-05-29T10:38:29.000000Z"
		assertEquals(DateHelper.parseLocalDateTime(text), text.parseJsonDateTime())
	}

	@Test
	fun `String parseJsonDate matches parseLocalDate`() {
		val text = "2025-05-29"
		assertEquals(DateHelper.parseLocalDate(text), text.parseJsonDate())
	}

	// endregion

	// region Pattern constants (any change here is a wire-format change)

	@Test
	fun `SERVER_PATTERN_FULL is unchanged`() {
		assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", DateHelper.SERVER_PATTERN_FULL)
	}

	@Test
	fun `SERVER_PATTERN_SHORT is unchanged`() {
		assertEquals("yyyy-MM-dd HH:mm:ss", DateHelper.SERVER_PATTERN_SHORT)
	}

	@Test
	fun `SERVER_PATTERN_DATE is unchanged`() {
		assertEquals("yyyy-MM-dd", DateHelper.SERVER_PATTERN_DATE)
	}

	@Test
	fun `SERVER_PATTERN_FULL_OFFSET is unchanged`() {
		assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", DateHelper.SERVER_PATTERN_FULL_OFFSET)
	}

	// endregion
}
