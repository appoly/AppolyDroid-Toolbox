package uk.co.appoly.droid.util

import com.duck.flexilogger.LogType
import com.duck.flexilogger.LoggingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for log-level and log-attribution behaviour of the parse helpers.
 *
 * Motivation: a recoverable fallback (e.g. `parseLocalDate` falling through to the
 * date-time parser for a `"1964-05-12T00:00:00.000000Z"` input) must not emit an
 * ERROR-level log — that pollutes consumer apps' Sentry/Crashlytics with spurious
 * stack traces for the happy path. Only the *terminal* failure (no format matched)
 * should log at ERROR, and the message must attribute the originating call site.
 *
 * These tests exercise [DateHelper.parseLocalDate] and [DateHelper.parseNaiveDateTime]
 * via a [RecordingTestLogger] and assert the level + tag attribution of the logs.
 */
class DateHelperLogAttributionTest {

	private lateinit var recorder: RecordingTestLogger

	@Before
	fun setUp() {
		recorder = RecordingTestLogger()
		// LoggingLevel.V — record everything (D, I, W, E)
		DateHelper.setLogger(recorder, LoggingLevel.V)
	}

	// region parseLocalDate

	@Test
	fun `parseLocalDate happy path with date-only input emits no logs`() {
		val result = DateHelper.parseLocalDate("1964-05-12")
		assertEquals(LocalDate.of(1964, 5, 12), result)
		assertTrue(
			"happy path should not log anything, got: ${recorder.entries}",
			recorder.entries.isEmpty()
		)
	}

	@Test
	fun `parseLocalDate recoverable fallback logs at DEBUG only no ERROR`() {
		// The original bug: this input would emit an ERROR log + stack trace despite
		// successfully parsing via the date-time fallback. Sentry/Crashlytics noise.
		val result = DateHelper.parseLocalDate("1964-05-12T00:00:00.000000Z")
		assertEquals(LocalDate.of(1964, 5, 12), result)

		val errorLogs = recorder.ofType(LogType.E)
		assertTrue(
			"recoverable fallback must not emit ERROR logs, got: $errorLogs",
			errorLogs.isEmpty()
		)
		// Some DEBUG log activity is expected and welcome — that's the audit trail.
		assertTrue(
			"DEBUG logs should be present for the audit trail",
			recorder.ofType(LogType.D).isNotEmpty()
		)
	}

	@Test
	fun `parseLocalDate terminal failure emits exactly one ERROR log attributed to parseLocalDate`() {
		val result = DateHelper.parseLocalDate("definitely not a date")
		assertEquals(null, result)

		val errorLogs = recorder.ofType(LogType.E)
		assertEquals(
			"terminal failure should emit exactly one ERROR log, got: $errorLogs",
			1,
			errorLogs.size
		)
		assertTrue(
			"ERROR log must attribute to parseLocalDate, got: ${errorLogs[0].msg}",
			errorLogs[0].msg.contains("parseLocalDate")
		)
		assertTrue(
			"ERROR log must include the input string for debuggability, got: ${errorLogs[0].msg}",
			errorLogs[0].msg.contains("definitely not a date")
		)
	}

	// endregion

	// region parseNaiveDateTime

	@Test
	fun `parseNaiveDateTime happy path emits no logs`() {
		val result = DateHelper.parseNaiveDateTime("2025-05-29T10:38:29.000000")
		assertEquals(java.time.LocalDateTime.of(2025, 5, 29, 10, 38, 29), result)
		assertTrue(recorder.entries.isEmpty())
	}

	@Test
	fun `parseNaiveDateTime terminal failure ERROR log attributes to parseNaiveDateTime not the internal worker`() {
		DateHelper.parseNaiveDateTime("definitely not a timestamp")

		val errorLogs = recorder.ofType(LogType.E)
		assertEquals(1, errorLogs.size)
		assertTrue(
			"public entry-point's tag should be 'parseNaiveDateTime', got: ${errorLogs[0].msg}",
			errorLogs[0].msg.startsWith("parseNaiveDateTime")
		)
	}

	// endregion
}
