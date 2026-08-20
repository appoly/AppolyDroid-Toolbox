package uk.co.appoly.droid.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import uk.co.appoly.droid.util.DateHelper.parseJsonDate
import uk.co.appoly.droid.util.DateHelper.toJsonString
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Typealias for a serializable [LocalDate] using [LocalDateSerializer].
 *
 * Works for nullable properties too — write `SerializableLocalDate?` and kotlinx wraps the
 * serializer for you. See the note on [NullableSerializableLocalDate].
 */
typealias SerializableLocalDate = @Serializable(with = LocalDateSerializer::class) LocalDate

/**
 * Typealias for a nullable serializable [LocalDate] using [NullableLocalDateSerializer].
 */
@Suppress("DEPRECATION")
@Deprecated(
	message = "Redundant: `SerializableLocalDate?` behaves identically, because kotlinx wraps " +
		"LocalDateSerializer for nullable properties. See issue #106.",
)
typealias NullableSerializableLocalDate = @Serializable(with = NullableLocalDateSerializer::class) LocalDate?

/**
 * Typealias for a serializable [LocalDateTime] using [DateTimeSerializer].
 *
 * Works for nullable properties too — write `SerializableDateTime?` and kotlinx wraps the
 * serializer for you. See the note on [NullableSerializableDateTime].
 */
typealias SerializableDateTime = @Serializable(with = DateTimeSerializer::class) LocalDateTime

/**
 * Typealias for a nullable serializable [LocalDateTime] using [NullableDateTimeSerializer].
 */
@Suppress("DEPRECATION")
@Deprecated(
	message = "Redundant: `SerializableDateTime?` behaves identically, because kotlinx wraps " +
		"DateTimeSerializer for nullable properties. See issue #106.",
)
typealias NullableSerializableDateTime = @Serializable(with = NullableDateTimeSerializer::class) LocalDateTime?

/**
 * Typealias for a serializable [ZonedDateTime] using [ZonedDateTimeSerializer].
 *
 * Works for nullable properties too — write `SerializableZonedDateTime?` and kotlinx wraps the
 * serializer for you. See the note on [NullableSerializableZonedDateTime].
 */
typealias SerializableZonedDateTime = @Serializable(with = ZonedDateTimeSerializer::class) ZonedDateTime

/**
 * Typealias for a nullable serializable [ZonedDateTime] using [NullableZonedDateTimeSerializer].
 */
@Suppress("DEPRECATION")
@Deprecated(
	message = "Redundant: `SerializableZonedDateTime?` behaves identically, because kotlinx wraps " +
		"ZonedDateTimeSerializer for nullable properties. See issue #106.",
)
typealias NullableSerializableZonedDateTime = @Serializable(with = NullableZonedDateTimeSerializer::class) ZonedDateTime?

/**
 * Typealias for a serializable [Instant] using [InstantSerializer].
 *
 * Works for nullable properties too — write `SerializableInstant?` and kotlinx wraps the
 * serializer for you. See the note on [NullableSerializableInstant].
 */
typealias SerializableInstant = @Serializable(with = InstantSerializer::class) Instant

/**
 * Typealias for a nullable serializable [Instant] using [NullableInstantSerializer].
 */
@Suppress("DEPRECATION")
@Deprecated(
	message = "Redundant: `SerializableInstant?` behaves identically, because kotlinx wraps " +
		"InstantSerializer for nullable properties. See issue #106.",
)
typealias NullableSerializableInstant = @Serializable(with = NullableInstantSerializer::class) Instant?

/**
 * Serializer for non-nullable [LocalDate] instances using kotlinx.serialization.
 *
 * Uses the standard date format defined in [DateHelper] (yyyy-MM-dd).
 *
 * Also use this for **nullable** `LocalDate?` properties — kotlinx wraps it automatically:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = LocalDateSerializer::class)
 *     val date: LocalDate,
 *     @Serializable(with = LocalDateSerializer::class)
 *     val optionalDate: LocalDate?
 * )
 * ```
 */
object LocalDateSerializer : KSerializer<LocalDate> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("uk.co.appoly.droid.util.LocalDate", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: LocalDate) {
		encoder.encodeString(
			value.toJsonString()
				?: throw SerializationException("DateHelper failed to format LocalDate <$value>")
		)
	}

	override fun deserialize(decoder: Decoder): LocalDate =
		decoder.decodeString().parseJsonDate()!!
}

/**
 * Serializer for nullable [LocalDate] instances using kotlinx.serialization.
 *
 * Uses the standard date format defined in [DateHelper] (yyyy-MM-dd).
 */
@Deprecated(
	message = "Redundant: annotate the nullable property with LocalDateSerializer and let " +
		"kotlinx wrap it — the wire format is identical. See issue #106.",
	replaceWith = ReplaceWith("LocalDateSerializer"),
)
object NullableLocalDateSerializer : KSerializer<LocalDate?> {
	override val descriptor: SerialDescriptor = LocalDateSerializer.descriptor.nullable

	@OptIn(ExperimentalSerializationApi::class)
	override fun serialize(encoder: Encoder, value: LocalDate?) {
		val formatted = value?.toJsonString()
		if (formatted == null) encoder.encodeNull() else encoder.encodeString(formatted)
	}

	@OptIn(ExperimentalSerializationApi::class)
	override fun deserialize(decoder: Decoder): LocalDate? =
		if (decoder.decodeNotNullMark()) {
			decoder.decodeString().parseJsonDate()
		} else {
			decoder.decodeNull()
		}
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
 * Also use this for **nullable** `LocalDateTime?` properties — kotlinx wraps it automatically:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = DateTimeSerializer::class)
 *     val startTime: LocalDateTime,
 *     @Serializable(with = DateTimeSerializer::class)
 *     val endTime: LocalDateTime?
 * )
 * ```
 */
object DateTimeSerializer : KSerializer<LocalDateTime> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("uk.co.appoly.droid.util.LocalDateTime", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: LocalDateTime) {
		encoder.encodeString(
			DateHelper.formatNaiveDateTime(value)
				?: throw SerializationException("DateHelper failed to format LocalDateTime <$value>")
		)
	}

	override fun deserialize(decoder: Decoder): LocalDateTime =
		DateHelper.parseNaiveDateTime(decoder.decodeString())!!
}

/**
 * Serializer for nullable [LocalDateTime] instances using kotlinx.serialization.
 *
 * Same wire format and read tolerance as [DateTimeSerializer].
 */
@Deprecated(
	message = "Redundant: annotate the nullable property with DateTimeSerializer and let " +
		"kotlinx wrap it — the wire format is identical. See issue #106.",
	replaceWith = ReplaceWith("DateTimeSerializer"),
)
object NullableDateTimeSerializer : KSerializer<LocalDateTime?> {
	override val descriptor: SerialDescriptor = DateTimeSerializer.descriptor.nullable

	@OptIn(ExperimentalSerializationApi::class)
	override fun serialize(encoder: Encoder, value: LocalDateTime?) {
		val formatted = DateHelper.formatNaiveDateTime(value)
		if (formatted == null) encoder.encodeNull() else encoder.encodeString(formatted)
	}

	@OptIn(ExperimentalSerializationApi::class)
	override fun deserialize(decoder: Decoder): LocalDateTime? =
		if (decoder.decodeNotNullMark()) {
			DateHelper.parseNaiveDateTime(decoder.decodeString())
		} else {
			decoder.decodeNull()
		}
}

/**
 * Serializer for non-nullable [ZonedDateTime] instances using kotlinx.serialization.
 *
 * This serializer handles [ZonedDateTime] values by:
 * 1. Converting to UTC timezone before serialization
 * 2. Serializing as an ISO-8601 formatted string
 * 3. When deserializing, parsing the string and converting to the device's timezone
 *
 * Also use this for **nullable** `ZonedDateTime?` properties — kotlinx wraps it automatically:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = ZonedDateTimeSerializer::class)
 *     val startTime: ZonedDateTime,
 *     @Serializable(with = ZonedDateTimeSerializer::class)
 *     val reminderTime: ZonedDateTime?
 * )
 * ```
 */
object ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("uk.co.appoly.droid.util.ZonedDateTime", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: ZonedDateTime) {
		encoder.encodeString(
			DateHelper.formatServerTimestamp(value)
				?: throw SerializationException("DateHelper failed to format ZonedDateTime <$value>")
		)
	}

	override fun deserialize(decoder: Decoder): ZonedDateTime =
		DateHelper.parseServerZoneDateTime(decoder.decodeString())!!.toDeviceZone()
}

/**
 * Serializer for nullable [ZonedDateTime] instances using kotlinx.serialization.
 *
 * Same wire format and read tolerance as [ZonedDateTimeSerializer].
 */
@Deprecated(
	message = "Redundant: annotate the nullable property with ZonedDateTimeSerializer and let " +
		"kotlinx wrap it — the wire format is identical. See issue #106.",
	replaceWith = ReplaceWith("ZonedDateTimeSerializer"),
)
object NullableZonedDateTimeSerializer : KSerializer<ZonedDateTime?> {
	override val descriptor: SerialDescriptor = ZonedDateTimeSerializer.descriptor.nullable

	@OptIn(ExperimentalSerializationApi::class)
	override fun serialize(encoder: Encoder, value: ZonedDateTime?) {
		val formatted = DateHelper.formatServerTimestamp(value)
		if (formatted == null) encoder.encodeNull() else encoder.encodeString(formatted)
	}

	@OptIn(ExperimentalSerializationApi::class)
	override fun deserialize(decoder: Decoder): ZonedDateTime? =
		if (decoder.decodeNotNullMark()) {
			DateHelper.parseServerZoneDateTime(decoder.decodeString())?.toDeviceZone()
		} else {
			decoder.decodeNull()
		}
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
 * Also use this for **nullable** `Instant?` properties — kotlinx wraps it automatically:
 * ```kotlin
 * @Serializable
 * data class Event(
 *     val id: Int,
 *     @Serializable(with = InstantSerializer::class)
 *     val timestamp: Instant,
 *     @Serializable(with = InstantSerializer::class)
 *     val deletedAt: Instant?
 * )
 * ```
 */
object InstantSerializer : KSerializer<Instant> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("uk.co.appoly.droid.util.Instant", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: Instant) {
		encoder.encodeString(
			DateHelper.formatServerTimestamp(value)
				?: throw SerializationException("DateHelper failed to format Instant <$value>")
		)
	}

	override fun deserialize(decoder: Decoder): Instant =
		DateHelper.parseServerInstant(decoder.decodeString())!!
}

/**
 * Serializer for nullable [Instant] instances using kotlinx.serialization.
 *
 * Same semantics as [InstantSerializer], but tolerates null values.
 */
@Deprecated(
	message = "Redundant: annotate the nullable property with InstantSerializer and let " +
		"kotlinx wrap it — the wire format is identical. See issue #106.",
	replaceWith = ReplaceWith("InstantSerializer"),
)
object NullableInstantSerializer : KSerializer<Instant?> {
	override val descriptor: SerialDescriptor = InstantSerializer.descriptor.nullable

	@OptIn(ExperimentalSerializationApi::class)
	override fun serialize(encoder: Encoder, value: Instant?) {
		val formatted = DateHelper.formatServerTimestamp(value)
		if (formatted == null) encoder.encodeNull() else encoder.encodeString(formatted)
	}

	@OptIn(ExperimentalSerializationApi::class)
	override fun deserialize(decoder: Decoder): Instant? =
		if (decoder.decodeNotNullMark()) {
			DateHelper.parseServerInstant(decoder.decodeString())
		} else {
			decoder.decodeNull()
		}
}
