package uk.co.appoly.droid.util

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LoggingLevel
import uk.co.appoly.droid.DateHelperLog
import uk.co.appoly.droid.util.DateHelper.SERVER_PATTERN_DATE
import uk.co.appoly.droid.util.DateHelper.SERVER_PATTERN_FULL
import uk.co.appoly.droid.util.DateHelper.SERVER_PATTERN_FULL_OFFSET
import uk.co.appoly.droid.util.DateHelper.SERVER_PATTERN_SHORT
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Utility object for standardized date and time operations.
 *
 * Provides methods for parsing and formatting dates in consistent formats,
 * with built-in error handling and logging. Uses Java 8 Time API for
 * robust date and time operations.
 *
 * The helper uses three standard date formats:
 * - [SERVER_PATTERN_FULL]: ISO-8601 extended format with microseconds, e.g., "2023-12-01T10:38:29.000000Z"
 * - [SERVER_PATTERN_SHORT]: Simple date-time format without timezone, e.g., "2023-12-01 10:38:29"
 * - [SERVER_PATTERN_DATE]: Date-only format, e.g., "2023-12-01"
 */
object DateHelper {
	/**
	 * ISO-8601 extended format with microseconds, e.g., "2023-12-01T10:38:29.000000Z"
	 *
	 * **Deprecated.** The trailing `'Z'` is a *literal* character in the pattern, not the UTC
	 * offset designator. That makes this pattern ambiguous when paired with a zone-naive
	 * temporal like [LocalDateTime] — the formatter will emit whatever wall-clock digits the
	 * input carries and unconditionally append a `Z`, regardless of whether those digits are
	 * actually UTC.
	 *
	 * For honest formatter access use [SERVER_PATTERN_FULL_OFFSET]. For full type-safe server
	 * I/O use [formatServerTimestamp] / [parseServerInstant].
	 */
	@Deprecated(
		"Pattern's trailing 'Z' is a literal character, not the UTC offset designator — " +
			"it does not enforce UTC and will format any LocalDateTime regardless of zone. " +
			"Use SERVER_PATTERN_FULL_OFFSET for honest formatter access, " +
			"or formatServerTimestamp(Instant) / parseServerInstant for type-safe server I/O.",
		ReplaceWith("SERVER_PATTERN_FULL_OFFSET")
	)
	const val SERVER_PATTERN_FULL = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"

	/**
	 * ISO-8601 extended format with microseconds and a real offset designator,
	 * e.g., "2023-12-01T10:38:29.000000Z" for UTC or "2023-12-01T11:38:29.000000+01:00" otherwise.
	 *
	 * Unlike [SERVER_PATTERN_FULL], the trailing `XXX` is the genuine offset designator. This pattern
	 * refuses to format a bare [LocalDateTime] (throws `UnsupportedTemporalTypeException`) — that
	 * refusal is the feature: the formatter polices its own input.
	 *
	 * Useful for new code paths or when consuming servers may emit non-UTC offsets.
	 */
	const val SERVER_PATTERN_FULL_OFFSET = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX"

	/**
	 * ISO-8601 naive (zone-less) format with microseconds, e.g., "2023-12-01T10:38:29.000000".
	 *
	 * This is the honest format for genuinely zone-naive [LocalDateTime] values: no `Z`, no
	 * offset designator, no implicit zone claim. Used internally by [formatNaiveDateTime] and
	 * [parseNaiveDateTime].
	 */
	const val NAIVE_PATTERN_FULL = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"

	/**
	 * Simple date-time format without timezone, e.g., "2023-12-01 10:38:29"
	 */
	const val SERVER_PATTERN_SHORT = "yyyy-MM-dd HH:mm:ss"

	/**
	 * Date-only format, e.g., "2023-12-01"
	 */
	const val SERVER_PATTERN_DATE = "yyyy-MM-dd"

	/**
	 * Formatter for outbound server timestamps, pinned to UTC.
	 *
	 * Uses [SERVER_PATTERN_FULL_OFFSET] (real `XXX` offset designator), with the zone fixed to
	 * UTC so [Instant] inputs always render as `...Z`. Output is byte-identical to the legacy
	 * literal-`Z` pattern for any UTC moment, but the formatter is now structurally honest:
	 * the trailing `Z` is the genuine ISO-8601 UTC offset designator, not a literal character.
	 */
	private val serverFormatterUtc: DateTimeFormatter =
		DateTimeFormatter.ofPattern(SERVER_PATTERN_FULL_OFFSET).withZone(ZoneOffset.UTC)

	/**
	 * Parser for inbound server timestamps. Accepts any valid ISO-8601 offset (`Z`, `+00:00`,
	 * `+01:00`, etc.) — the `XXX` designator handles all of them. Used by [parseServerInstant].
	 */
	private val serverParserOffset: DateTimeFormatter =
		DateTimeFormatter.ofPattern(SERVER_PATTERN_FULL_OFFSET)

	/**
	 * Set the logger for this class
	 * @param logger [FlexiLog] the logger to use
	 * @param loggingLevel [LoggingLevel] the logging level to use
	 */
	fun setLogger(
		logger: FlexiLog,
		loggingLevel: LoggingLevel = LoggingLevel.NONE
	) {
		DateHelperLog.updateLogger(logger, loggingLevel)
	}

	/**
	 * Parses a string to a *zone-naive* [LocalDateTime].
	 *
	 * Honest about zone semantics: returns the parsed wall-clock digits as a [LocalDateTime]
	 * without attaching a zone. Use this only when you genuinely want a zone-naive value
	 * (date pickers, display labels, naive Room columns).
	 *
	 * For server I/O — where the wire `Z` is supposed to mean UTC — use [parseServerInstant]
	 * instead, which returns an [Instant] that carries UTC at the type level.
	 *
	 * Tolerant input: accepts the new [NAIVE_PATTERN_FULL] format (no zone marker), the
	 * legacy literal-`Z` format and any explicit ISO-8601 offset (via [SERVER_PATTERN_FULL_OFFSET]
	 * — the offset is dropped, returning the wall-clock digits at that offset), and the
	 * [SERVER_PATTERN_SHORT] format. This makes the parser strictly more lenient than its
	 * pre-1.4 behaviour: anything that used to parse, still parses to the same value.
	 *
	 * @param dateTime String representation of date-time to parse (e.g., "2023-12-01T10:38:29.000000")
	 * @return Parsed [LocalDateTime] or null if the input is null, blank, or cannot be parsed
	 */
	fun parseNaiveDateTime(dateTime: String?): LocalDateTime? {
		if (dateTime.isNullOrBlank()) return null

		// 1. New honest naive format (no zone marker)
		try {
			return LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern(NAIVE_PATTERN_FULL))
		} catch (e: Exception) {
			DateHelperLog.d(this, "parseNaiveDateTime: \"$dateTime\" not in NAIVE_PATTERN_FULL, trying SERVER_PATTERN_FULL_OFFSET", e)
		}

		// 2. Any explicit offset (legacy "Z", "+00:00", "+01:00", etc.) — offset stripped
		try {
			return OffsetDateTime
				.parse(dateTime, DateTimeFormatter.ofPattern(SERVER_PATTERN_FULL_OFFSET))
				.toLocalDateTime()
		} catch (e: Exception) {
			DateHelperLog.d(this, "parseNaiveDateTime: \"$dateTime\" not in SERVER_PATTERN_FULL_OFFSET, trying SERVER_PATTERN_SHORT", e)
		}

		// 3. Short format ("yyyy-MM-dd HH:mm:ss")
		return try {
			LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern(SERVER_PATTERN_SHORT))
		} catch (e: Exception) {
			DateHelperLog.e(this@DateHelper, "parseNaiveDateTime: failed to parse \"$dateTime\" with any known format", e)
			null
		}
	}

	/**
	 * Formats a *zone-naive* [LocalDateTime] using [NAIVE_PATTERN_FULL].
	 *
	 * The output (e.g. `2023-12-01T10:38:29.000000`) carries no zone marker — it is honest
	 * about being zone-naive. This is a deliberate behaviour change from the pre-1.4 path
	 * (which appended a literal `Z`); the new format is structurally truthful about not
	 * knowing the timezone.
	 *
	 * For server I/O, use [formatServerTimestamp] — it accepts an [Instant] / [ZonedDateTime]
	 * and pins the formatter to UTC, producing an honest `...Z` suffix that genuinely marks
	 * the digits as UTC.
	 *
	 * @param dateTime The [LocalDateTime] to format
	 * @return Formatted date-time string or null if the input is null
	 */
	fun formatNaiveDateTime(dateTime: LocalDateTime?): String? {
		return if (dateTime == null) {
			null
		} else {
			DateTimeFormatter.ofPattern(NAIVE_PATTERN_FULL).format(dateTime)
		}
	}

	/**
	 * Parses a string to [LocalDateTime] using the pre-1.4 fallback chain.
	 *
	 * Retains its original behaviour byte-for-byte: tries [SERVER_PATTERN_FULL] (literal `Z`)
	 * first, falls back to [SERVER_PATTERN_SHORT]. No `ReplaceWith` is offered because the
	 * right replacement depends on intent — server I/O wants [parseServerInstant], naive use
	 * wants [parseNaiveDateTime] (which now also accepts the legacy `...Z` format and is
	 * therefore a superset of this function for parsing, but yields output in a different
	 * shape downstream when round-tripped through [formatNaiveDateTime]).
	 *
	 * @param dateTime String representation of date-time to parse (e.g., "2023-12-01T10:38:29.000000Z")
	 * @return Parsed [LocalDateTime] or null if the input is null, blank, or cannot be parsed
	 */
	@Deprecated(
		"Ambiguous: does not enforce UTC and round-trips through a literal-'Z' format. " +
			"For server I/O, migrate the field to Instant and use parseServerInstant(text). " +
			"For genuinely zone-naive values, use parseNaiveDateTime(text) (note: round-trip " +
			"through formatNaiveDateTime emits no 'Z' suffix — wire format is no longer ambiguous)."
	)
	@Suppress("DEPRECATION") // legacy entry point: retains pre-1.4 byte-identical behaviour
	fun parseLocalDateTime(dateTime: String?): LocalDateTime? {
		if (dateTime.isNullOrBlank()) return null
		return try {
			LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern(SERVER_PATTERN_FULL))
		} catch (e: Exception) {
			DateHelperLog.d(this, "parseLocalDateTime: failed to parse \"$dateTime\" from SERVER_PATTERN_FULL, trying with SERVER_PATTERN_SHORT", e)
			try {
				LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern(SERVER_PATTERN_SHORT))
			} catch (e2: Exception) {
				DateHelperLog.e(this@DateHelper, "parseLocalDateTime: failed to parse \"$dateTime\" with SERVER_PATTERN_SHORT", e2)
				null
			}
		}
	}

	/**
	 * Formats a [LocalDateTime] to string using [SERVER_PATTERN_FULL] (literal-`Z`) format.
	 *
	 * Retains its original behaviour byte-for-byte: emits `2023-12-01T10:38:29.000000Z`
	 * regardless of input zone semantics. No `ReplaceWith` is offered because the new
	 * [formatNaiveDateTime] emits a *different* wire format (no `Z`); choosing between this
	 * and [formatServerTimestamp] is a deliberate decision, not a mechanical refactor.
	 *
	 * @param dateTime The [LocalDateTime] to format
	 * @return Formatted date-time string or null if the input is null
	 */
	@Deprecated(
		"Ambiguous: appends a literal 'Z' regardless of zone semantics. " +
			"For server I/O, migrate the field to Instant and use formatServerTimestamp(Instant). " +
			"For genuinely zone-naive values, use formatNaiveDateTime(dateTime) (note: emits no " +
			"'Z' suffix — this is a deliberate wire-format change for honesty)."
	)
	@Suppress("DEPRECATION") // legacy entry point: retains pre-1.4 byte-identical behaviour
	fun formatLocalDateTime(dateTime: LocalDateTime?): String? {
		return if (dateTime == null) {
			null
		} else {
			DateTimeFormatter.ofPattern(SERVER_PATTERN_FULL).format(dateTime)
		}
	}

	/**
	 * Parses a string to [LocalDate] using standard date format.
	 *
	 * This method first attempts to parse using [SERVER_PATTERN_DATE], and if that fails,
	 * tries to parse it as a date-time and extracts the date part. Parsing failures are
	 * logged with appropriate error messages.
	 *
	 * @param dateTime String representation of date to parse (e.g., "2023-12-01")
	 * @return Parsed [LocalDate] or null if the input is null, blank, or cannot be parsed
	 */
	fun parseLocalDate(dateTime: String?): LocalDate? {
		return if (dateTime.isNullOrBlank()) {
			null
		} else {
			//attempt to pars from pattern SERVER_PATTERN_DATE
			try {
				val it: LocalDate = LocalDate.parse(dateTime, DateTimeFormatter.ofPattern(SERVER_PATTERN_DATE))
				it
			} catch (e: Exception) {
				DateHelperLog.e(this@DateHelper, "parseLocalDate: failed to parse \"$dateTime\" with SERVER_PATTERN_DATE", e)
				null
			} ?: run {
				//attempt to parse as date-time
				parseNaiveDateTime(dateTime)?.toLocalDate()
			}
		}
	}

	/**
	 * Formats a [LocalDate] to string using [SERVER_PATTERN_DATE] format.
	 *
	 * @param date The [LocalDate] to format
	 * @return Formatted date string or null if the input is null
	 */
	fun formatLocalDate(date: LocalDate?): String? {
		return if (date == null) {
			null
		} else {
			DateTimeFormatter.ofPattern(SERVER_PATTERN_DATE).format(date)
		}
	}

	/**
	 * Extension function to format a [LocalDateTime] as a JSON string.
	 *
	 * Delegates to [formatNaiveDateTime] — does not enforce UTC. For server I/O,
	 * prefer [formatServerTimestamp] with an [Instant] / [ZonedDateTime].
	 *
	 * @return Formatted date-time string using [SERVER_PATTERN_FULL] or null if the receiver is null
	 */
	fun LocalDateTime?.toJsonString(): String? {
		return formatNaiveDateTime(this)
	}

	/**
	 * Extension function to parse a JSON date-time string to [LocalDateTime].
	 *
	 * Delegates to [parseNaiveDateTime] — does not attach UTC. For server I/O,
	 * prefer [parseServerInstant] which returns an [Instant] carrying UTC.
	 *
	 * @return Parsed [LocalDateTime] or null if the string is null, blank, or cannot be parsed
	 */
	fun String?.parseJsonDateTime(): LocalDateTime? {
		return parseNaiveDateTime(this)
	}

	/**
	 * Extension function to format a [LocalDate] as a JSON string.
	 *
	 * @return Formatted date string using [SERVER_PATTERN_DATE] or null if the receiver is null
	 */
	fun LocalDate?.toJsonString(): String? {
		return formatLocalDate(this)
	}

	/**
	 * Extension function to parse a JSON date string to [LocalDate].
	 *
	 * @return Parsed [LocalDate] or null if the string is null, blank, or cannot be parsed
	 */
	fun String?.parseJsonDate(): LocalDate? {
		return parseLocalDate(this)
	}

	/**
	 * Formats a [LocalDateTime] for use in filenames using a safe format.
	 *
	 * This format uses hyphens instead of colons for time values, making it
	 * safe for use in filenames across different operating systems.
	 *
	 * @return A string in the format "yyyy-MM-dd_HH-mm-ss.SSS"
	 */
	fun LocalDateTime.toFileString(): String {
		return DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSS").format(this)
	}

	/**
	 * Gets the current date and time in UTC timezone.
	 *
	 * @return Current [ZonedDateTime] in UTC timezone
	 */
	fun nowAsUTC(): ZonedDateTime {
		return ZonedDateTime.now().toUTC()
	}

	/**
	 * Formats an [Instant] for transmission to the server using [SERVER_PATTERN_FULL].
	 *
	 * Because [Instant] is UTC by definition and the formatter is pinned to UTC via
	 * [serverFormatterUtc], the emitted digits are *guaranteed* to be UTC wall-clock —
	 * the `Z` suffix accurately marks them as such, regardless of device timezone.
	 *
	 * This is the recommended entry point for any server I/O that previously used
	 * [formatLocalDateTime] with a manually UTC-converted [LocalDateTime].
	 *
	 * @param instant The [Instant] to format
	 * @return Formatted UTC date-time string, or null if the input is null
	 */
	fun formatServerTimestamp(instant: Instant?): String? =
		instant?.let { serverFormatterUtc.format(it) }

	/**
	 * Formats a [ZonedDateTime] for transmission to the server using [SERVER_PATTERN_FULL].
	 *
	 * The zone is collapsed to an [Instant] before formatting, so the emitted digits are UTC
	 * regardless of the input zone. Equivalent to `formatServerTimestamp(zoned?.toInstant())`.
	 *
	 * @param zoned The [ZonedDateTime] to format
	 * @return Formatted UTC date-time string, or null if the input is null
	 */
	fun formatServerTimestamp(zoned: ZonedDateTime?): String? =
		zoned?.toInstant()?.let { serverFormatterUtc.format(it) }

	/**
	 * Parses a server-emitted timestamp into an [Instant].
	 *
	 * The wire format follows [SERVER_PATTERN_FULL_OFFSET] — microsecond precision plus a real
	 * ISO-8601 offset designator. `Z`, `+00:00`, `+01:00`, `-05:00` are all valid input; the
	 * returned [Instant] represents the same moment regardless of which offset the server
	 * chose. Once parsed, the [Instant] carries UTC at the type level — it cannot be silently
	 * reinterpreted as device-local downstream.
	 *
	 * Backward compatibility: `Z` was previously the only accepted suffix; it remains so by
	 * construction. The parser is now strictly more lenient.
	 *
	 * @param text String representation of the server timestamp (e.g., "2023-12-01T10:38:29.000000Z")
	 * @return Parsed [Instant], or null if the input is null, blank, or cannot be parsed
	 */
	fun parseServerInstant(text: String?): Instant? {
		if (text.isNullOrBlank()) return null
		return try {
			OffsetDateTime.parse(text, serverParserOffset).toInstant()
		} catch (e: Exception) {
			DateHelperLog.e(this, "parseServerInstant: failed to parse \"$text\" with SERVER_PATTERN_FULL_OFFSET", e)
			null
		}
	}

	/**
	 * Parses a server-emitted timestamp into a [ZonedDateTime] in UTC.
	 *
	 * Convenience over [parseServerInstant] for the common case where the caller wants a
	 * [ZonedDateTime] rather than a raw [Instant]. The returned value **always carries the
	 * UTC zone** — regardless of whether the input used `Z`, `+00:00`, `+01:00`, `-05:00`,
	 * or any other ISO-8601 offset, the resulting `ZonedDateTime` represents the same
	 * moment expressed in UTC. To shift it into the device's local zone, chain
	 * [toDeviceZone] from `DateHelperExtensions`.
	 *
	 * Accepts the same input formats as [parseServerInstant]; the wire format is
	 * [SERVER_PATTERN_FULL_OFFSET].
	 *
	 * @param text String representation of the server timestamp (e.g., "2023-12-01T10:38:29.000000Z")
	 * @return Parsed [ZonedDateTime] in UTC, or null if the input is null, blank, or cannot be parsed
	 */
	fun parseServerZoneDateTime(text: String?): ZonedDateTime? {
		if (text.isNullOrBlank()) return null
		return try {
			OffsetDateTime
				.parse(text, serverParserOffset)
				.toInstant()
				.atZone(ZoneOffset.UTC)
		} catch (e: Exception) {
			DateHelperLog.e(this, "parseServerZoneDateTime: failed to parse \"$text\" with SERVER_PATTERN_FULL_OFFSET", e)
			null
		}
	}
}
