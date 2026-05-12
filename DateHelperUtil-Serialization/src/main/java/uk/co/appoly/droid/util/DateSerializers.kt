package uk.co.appoly.droid.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import uk.co.appoly.droid.util.DateHelper.parseJsonDate
import uk.co.appoly.droid.util.DateHelper.toJsonString
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Typealias for a serializable [LocalDate] using [LocalDateSerializer].
 */
typealias SerializableLocalDate = @Serializable(with = LocalDateSerializer::class) LocalDate
/**
 * Typealias for a nullable serializable [LocalDate] using [NullableLocalDateSerializer].
 */
typealias NullableSerializableLocalDate = @Serializable(with = NullableLocalDateSerializer::class) LocalDate?

/**
 * Typealias for a serializable [LocalDateTime] using [DateTimeSerializer].
 */
typealias SerializableDateTime = @Serializable(with = DateTimeSerializer::class) LocalDateTime
/**
 * Typealias for a nullable serializable [LocalDateTime] using [NullableDateTimeSerializer].
 */
typealias NullableSerializableDateTime = @Serializable(with = NullableDateTimeSerializer::class) LocalDateTime?

/**
 * Typealias for a serializable [ZonedDateTime] using [ZonedDateTimeSerializer].
 */
typealias SerializableZonedDateTime = @Serializable(with = ZonedDateTimeSerializer::class) ZonedDateTime
/**
 * Typealias for a nullable serializable [ZonedDateTime] using [NullableZonedDateTimeSerializer].
 */
typealias NullableSerializableZonedDateTime = @Serializable(with = NullableZonedDateTimeSerializer::class) ZonedDateTime?

/**
 * Typealias for a serializable [Instant] using [InstantSerializer].
 */
typealias SerializableInstant = @Serializable(with = InstantSerializer::class) Instant
/**
 * Typealias for a nullable serializable [Instant] using [NullableInstantSerializer].
 */
typealias NullableSerializableInstant = @Serializable(with = NullableInstantSerializer::class) Instant?

/**
 * Serializer for nullable [LocalDate] instances using kotlinx.serialization.
 *
 * This serializer handles nullable [LocalDate] values and uses the standard date format
 * defined in [DateHelper] (yyyy-MM-dd).
 *
 * Usage:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = NullableLocalDateSerializer::class)
 *     val date: LocalDate?
 * )
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDate::class)
object NullableLocalDateSerializer : KSerializer<LocalDate?> {
	override fun serialize(encoder: Encoder, value: LocalDate?) {
		value.toJsonString()?.let {
			encoder.encodeString(it)
		}
	}

	override fun deserialize(decoder: Decoder): LocalDate? =
		decoder.decodeString().parseJsonDate()
}

/**
 * Serializer for non-nullable [LocalDate] instances using kotlinx.serialization.
 *
 * This serializer handles non-nullable [LocalDate] values and uses the standard date format
 * defined in [DateHelper] (yyyy-MM-dd).
 *
 * Usage:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = LocalDateSerializer::class)
 *     val date: LocalDate
 * )
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDate::class)
object LocalDateSerializer : KSerializer<LocalDate> {
	override fun serialize(encoder: Encoder, value: LocalDate) {
		value.toJsonString()?.let {
			encoder.encodeString(it)
		}
	}

	override fun deserialize(decoder: Decoder): LocalDate =
		decoder.decodeString().parseJsonDate()!!
}

/**
 * Serializer for nullable [LocalDateTime] instances using kotlinx.serialization.
 *
 * Wire format is the honest naive [DateHelper.NAIVE_PATTERN_FULL] (no zone marker, e.g.
 * "2025-05-29T10:38:29.000000"). Reads tolerate the legacy `...Z` form, any explicit
 * ISO-8601 offset, and the short `yyyy-MM-dd HH:mm:ss` format for backward compatibility.
 *
 * For server-emitted moments in time, prefer [InstantSerializer] / [NullableInstantSerializer] —
 * those preserve the literal-`Z` wire bytes for UTC moments.
 *
 * Usage:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = NullableDateTimeSerializer::class)
 *     val startTime: LocalDateTime?
 * )
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDateTime::class)
object NullableDateTimeSerializer : KSerializer<LocalDateTime?> {
	override fun serialize(encoder: Encoder, value: LocalDateTime?) {
		DateHelper.formatNaiveDateTime(value)?.let {
			encoder.encodeString(it)
		}
	}

	override fun deserialize(decoder: Decoder): LocalDateTime? =
		DateHelper.parseNaiveDateTime(decoder.decodeString())
}

/**
 * Serializer for non-nullable [LocalDateTime] instances using kotlinx.serialization.
 *
 * Wire format is the honest naive [DateHelper.NAIVE_PATTERN_FULL] (no zone marker, e.g.
 * "2025-05-29T10:38:29.000000"). Reads tolerate the legacy `...Z` form, any explicit
 * ISO-8601 offset, and the short `yyyy-MM-dd HH:mm:ss` format for backward compatibility.
 *
 * For server-emitted moments in time, prefer [InstantSerializer] — it preserves the
 * literal-`Z` wire bytes for UTC moments.
 *
 * Usage:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = DateTimeSerializer::class)
 *     val startTime: LocalDateTime
 * )
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDateTime::class)
object DateTimeSerializer : KSerializer<LocalDateTime> {
	override fun serialize(encoder: Encoder, value: LocalDateTime) {
		DateHelper.formatNaiveDateTime(value)?.let {
			encoder.encodeString(it)
		}
	}

	override fun deserialize(decoder: Decoder): LocalDateTime =
		DateHelper.parseNaiveDateTime(decoder.decodeString())!!
}

/**
 * Serializer for non-nullable [ZonedDateTime] instances using kotlinx.serialization.
 *
 * This serializer handles [ZonedDateTime] values by:
 * 1. Converting to UTC timezone before serialization
 * 2. Serializing as an ISO-8601 formatted string
 * 3. When deserializing, parsing the string and converting to the device's timezone
 *
 * Usage:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = ZonedDateTimeSerializer::class)
 *     val startTime: ZonedDateTime
 * )
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDateTime::class)
object ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
	override fun serialize(encoder: Encoder, value: ZonedDateTime) {
		DateHelper.formatServerTimestamp(value)?.let {
			encoder.encodeString(it)
		}
	}

	override fun deserialize(decoder: Decoder): ZonedDateTime =
		DateHelper.parseServerZoneDateTime(decoder.decodeString())!!.toDeviceZone()
}

/**
 * Serializer for nullable [ZonedDateTime] instances using kotlinx.serialization.
 *
 * This serializer handles nullable [ZonedDateTime] values by:
 * 1. Converting to UTC timezone before serialization (if not null)
 * 2. Serializing as an ISO-8601 formatted string
 * 3. When deserializing, parsing the string and converting to the device's timezone
 *
 * Usage:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = NullableZonedDateTimeSerializer::class)
 *     val reminderTime: ZonedDateTime?
 * )
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDateTime::class)
object NullableZonedDateTimeSerializer : KSerializer<ZonedDateTime?> {
	override fun serialize(encoder: Encoder, value: ZonedDateTime?) {
		DateHelper.formatServerTimestamp(value)?.let {
			encoder.encodeString(it)
		}
	}

	override fun deserialize(decoder: Decoder): ZonedDateTime? =
		DateHelper.parseServerInstant(decoder.decodeString())?.atZone(ZoneOffset.UTC)?.toDeviceZone()
}

/**
 * Serializer for non-nullable [Instant] instances using kotlinx.serialization.
 *
 * [Instant] is UTC by definition, so this serializer is unambiguous: the emitted digits are
 * guaranteed UTC wall-clock regardless of device timezone, and parsing returns an [Instant]
 * that carries the UTC information at the type level (it cannot be silently misinterpreted as
 * device-local downstream).
 *
 * Wire format is [DateHelper.SERVER_PATTERN_FULL_OFFSET] pinned to UTC, which renders as
 * "2023-12-01T10:38:29.000000Z" — byte-identical to the legacy literal-`Z` format for any
 * UTC moment, so any server already accepting the existing format keeps working.
 *
 * Usage:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = InstantSerializer::class)
 *     val timestamp: Instant
 * )
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Instant::class)
object InstantSerializer : KSerializer<Instant> {
	override fun serialize(encoder: Encoder, value: Instant) {
		DateHelper.formatServerTimestamp(value)?.let {
			encoder.encodeString(it)
		}
	}

	override fun deserialize(decoder: Decoder): Instant =
		DateHelper.parseServerInstant(decoder.decodeString())!!
}

/**
 * Serializer for nullable [Instant] instances using kotlinx.serialization.
 *
 * Same semantics as [InstantSerializer], but tolerates null values.
 *
 * Usage:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = NullableInstantSerializer::class)
 *     val timestamp: Instant?
 * )
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Instant::class)
object NullableInstantSerializer : KSerializer<Instant?> {
	override fun serialize(encoder: Encoder, value: Instant?) {
		DateHelper.formatServerTimestamp(value)?.let {
			encoder.encodeString(it)
		}
	}

	override fun deserialize(decoder: Decoder): Instant? =
		DateHelper.parseServerInstant(decoder.decodeString())
}