package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Round-trip tests for the [LocalDate], [LocalDateTime] and [ZonedDateTime] kotlinx serializers
 * (nullable + non-null), driven through @Serializable holders so kotlinx gates the null mark.
 */
class DateSerializersTest {

	@Serializable
	private data class LocalDateHolder(
		@Serializable(with = LocalDateSerializer::class) val date: LocalDate,
		@Serializable(with = NullableLocalDateSerializer::class) val nullable: LocalDate?
	)

	@Serializable
	private data class DateTimeHolder(
		@Serializable(with = DateTimeSerializer::class) val dt: LocalDateTime,
		@Serializable(with = NullableDateTimeSerializer::class) val nullable: LocalDateTime?
	)

	@Serializable
	private data class ZonedHolder(
		@Serializable(with = ZonedDateTimeSerializer::class) val z: ZonedDateTime,
		@Serializable(with = NullableZonedDateTimeSerializer::class) val nullable: ZonedDateTime?
	)

	private val json = Json

	@Before
	fun silenceLogger() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	@Test
	fun `LocalDate serializers round-trip non-null and null`() {
		val holder = LocalDateHolder(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 1, 2))
		val decoded = json.decodeFromString(LocalDateHolder.serializer(), json.encodeToString(LocalDateHolder.serializer(), holder))
		assertEquals(holder, decoded)

		val nullHolder = LocalDateHolder(LocalDate.of(2026, 6, 5), null)
		val decodedNull = json.decodeFromString(LocalDateHolder.serializer(), json.encodeToString(LocalDateHolder.serializer(), nullHolder))
		assertNull(decodedNull.nullable)
	}

	@Test
	fun `LocalDateTime serializers round-trip non-null and null`() {
		val holder = DateTimeHolder(LocalDateTime.of(2026, 6, 5, 10, 38, 29), LocalDateTime.of(2026, 1, 2, 3, 4, 5))
		val decoded = json.decodeFromString(DateTimeHolder.serializer(), json.encodeToString(DateTimeHolder.serializer(), holder))
		assertEquals(holder, decoded)

		val nullHolder = DateTimeHolder(LocalDateTime.of(2026, 6, 5, 10, 38, 29), null)
		val decodedNull = json.decodeFromString(DateTimeHolder.serializer(), json.encodeToString(DateTimeHolder.serializer(), nullHolder))
		assertNull(decodedNull.nullable)
	}

	@Test
	fun `ZonedDateTime serializers round-trip stably and handle null`() {
		val z = ZonedDateTime.of(2026, 6, 5, 10, 38, 29, 0, ZoneOffset.UTC)
		val holder = ZonedHolder(z, z)
		val once = json.encodeToString(ZonedHolder.serializer(), holder)
		// Decode then re-encode must be byte-stable.
		val decoded = json.decodeFromString(ZonedHolder.serializer(), once)
		assertEquals(once, json.encodeToString(ZonedHolder.serializer(), decoded))

		val nullHolder = ZonedHolder(z, null)
		val decodedNull = json.decodeFromString(ZonedHolder.serializer(), json.encodeToString(ZonedHolder.serializer(), nullHolder))
		assertNull(decodedNull.nullable)
	}
}
