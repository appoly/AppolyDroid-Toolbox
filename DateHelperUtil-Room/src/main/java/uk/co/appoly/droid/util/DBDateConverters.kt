package uk.co.appoly.droid.util

import androidx.room.TypeConverter
import uk.co.appoly.droid.util.DateHelper.parseJsonDate
import uk.co.appoly.droid.util.DateHelper.toJsonString
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Room TypeConverters for Java 8 Time API date and time classes.
 *
 * This class provides bidirectional conversions between Java 8 time types
 * ([Instant], [LocalDateTime], [LocalDate], [ZonedDateTime]) and [String] for Room database
 * storage.
 *
 * All string representations use the standard formats defined in [DateHelper]:
 * - [Instant]: ISO-8601 format "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX" rendering as "...Z" for UTC
 *   (UTC, formatter-enforced)
 * - [LocalDateTime]: ISO-8601 naive format "yyyy-MM-dd'T'HH:mm:ss.SSSSSS" (no zone marker —
 *   reads also tolerate the legacy "...Z" form for backward compat with pre-1.4 data)
 * - [LocalDate]: Simple date format "yyyy-MM-dd"
 * - [ZonedDateTime]: Converted to UTC via [DateHelper.formatServerTimestamp]
 *
 * For new "moment in time" columns, prefer [Instant] over [LocalDateTime] — it carries UTC
 * at the type level so the converter cannot accidentally store device-local digits.
 *
 * Usage:
 * ```kotlin
 * @Database(entities = [YourEntity::class], version = 1)
 * @TypeConverters(DBDateConverters::class)
 * abstract class YourDatabase : RoomDatabase() {
 *     // ...
 * }
 * ```
 */
class DBDateConverters {
	/**
	 * Converts a [LocalDateTime] to its string representation for database storage.
	 *
	 * Uses [DateHelper.formatNaiveDateTime] which emits [DateHelper.NAIVE_PATTERN_FULL]
	 * (no zone marker, e.g. "2023-12-01T10:38:29.000000"). This is a deliberate change
	 * from the pre-1.4 literal-`Z` format — the new format is honest about being zone-naive.
	 *
	 * @param date The [LocalDateTime] to convert
	 * @return The string representation of the date-time, or null if the input is null
	 */
	@TypeConverter
	fun localDateTimeToJson(date: LocalDateTime?): String? = DateHelper.formatNaiveDateTime(date)

	/**
	 * Converts a string representation to [LocalDateTime] when reading from the database.
	 *
	 * Tolerant of multiple formats via [DateHelper.parseNaiveDateTime]: the new no-zone
	 * format, the legacy literal-`Z` format (so pre-1.4 rows keep reading), any explicit
	 * ISO-8601 offset, and the short `yyyy-MM-dd HH:mm:ss` format. The returned
	 * [LocalDateTime] is zone-naive.
	 *
	 * @param json The string representation to convert
	 * @return The parsed [LocalDateTime], or null if the input is null or invalid
	 */
	@TypeConverter
	fun jsonToLocalDateTime(json: String?): LocalDateTime? = DateHelper.parseNaiveDateTime(json)

	/**
	 * Converts a [LocalDate] to its string representation for database storage.
	 *
	 * Uses the [DateHelper.SERVER_PATTERN_DATE] format.
	 *
	 * @param date The [LocalDate] to convert
	 * @return The string representation of the date, or null if the input is null
	 */
	@TypeConverter
	fun localDateToJson(date: LocalDate?): String? = date.toJsonString()

	/**
	 * Converts a string representation to [LocalDate] when reading from the database.
	 *
	 * @param json The string representation to convert
	 * @return The parsed [LocalDate], or null if the input is null or invalid
	 */
	@TypeConverter
	fun jsonToLocalDate(json: String?): LocalDate? = json.parseJsonDate()

	/**
	 * Converts a [ZonedDateTime] to its string representation for database storage.
	 *
	 * Routed through [DateHelper.formatServerTimestamp], which collapses the input to an
	 * [Instant] and pins the formatter to UTC — the stored digits are UTC wall-clock
	 * regardless of the input zone or device zone.
	 *
	 * @param date The [ZonedDateTime] to convert
	 * @return The string representation of the date-time in UTC, or null if the input is null
	 */
	@TypeConverter
	fun zonedDateTimeToJson(date: ZonedDateTime?): String? = DateHelper.formatServerTimestamp(date)

	/**
	 * Converts a string representation to [ZonedDateTime] when reading from the database.
	 *
	 * The string is parsed via [DateHelper.parseServerInstant] (which honours the UTC contract
	 * at the type level), then adjusted to the device's timezone for downstream display.
	 *
	 * @param json The string representation to convert
	 * @return The parsed [ZonedDateTime] in the device's timezone, or null if the input is null or invalid
	 */
	@TypeConverter
	fun jsonToZonedDateTime(json: String?): ZonedDateTime? =
		DateHelper.parseServerInstant(json)
			?.atZone(ZoneOffset.UTC)
			?.toDeviceZone()

	/**
	 * Converts an [Instant] to its string representation for database storage.
	 *
	 * Stored as ISO-8601 UTC string using [DateHelper.SERVER_PATTERN_FULL_OFFSET] with the
	 * formatter pinned to UTC, so the trailing offset always renders as `Z`. Output is
	 * byte-identical to the legacy literal-`Z` format for any UTC moment — existing rows
	 * read back transparently. Because [Instant] is UTC by definition, the stored digits
	 * are guaranteed to be UTC wall-clock regardless of device timezone.
	 *
	 * @param instant The [Instant] to convert
	 * @return The string representation of the instant in UTC, or null if the input is null
	 */
	@TypeConverter
	fun instantToJson(instant: Instant?): String? = DateHelper.formatServerTimestamp(instant)

	/**
	 * Converts a string representation to [Instant] when reading from the database.
	 *
	 * The returned [Instant] carries UTC at the type level — it cannot be silently misinterpreted
	 * as device-local by downstream code.
	 *
	 * @param json The string representation to convert
	 * @return The parsed [Instant], or null if the input is null or invalid
	 */
	@TypeConverter
	fun jsonToInstant(json: String?): Instant? = DateHelper.parseServerInstant(json)
}
