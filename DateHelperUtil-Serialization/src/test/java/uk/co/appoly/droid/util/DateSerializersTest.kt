package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Round-trip tests for the [LocalDate], [LocalDateTime] and [ZonedDateTime] kotlinx serializers.
 *
 * Covers three paths deliberately:
 * 1. The **recommended** idiom — the non-null serializer on a nullable property, letting kotlinx
 *    wrap it in its own `NullableSerializer`.
 * 2. The **deprecated** `Nullable*` serializers on a nullable property, asserting byte-for-byte
 *    wire parity with (1) so the deprecation is a safe swap.
 * 3. **Direct use** of the `Nullable*` serializers — `encodeToString(serializer, null)` and
 *    inside a `ListSerializer` — which bypasses kotlinx's wrapper and therefore exercises the
 *    serializers' own null branches. This path was uncovered before issue #106.
 */
@Suppress("DEPRECATION")
class DateSerializersTest {

	@Serializable
	private data class LocalDateHolder(
		@Serializable(with = LocalDateSerializer::class) val date: LocalDate,
		@Serializable(with = LocalDateSerializer::class) val nullable: LocalDate?
	)

	@Serializable
	private data class LegacyLocalDateHolder(
		@Serializable(with = LocalDateSerializer::class) val date: LocalDate,
		@Serializable(with = NullableLocalDateSerializer::class) val nullable: LocalDate?
	)

	@Serializable
	private data class DateTimeHolder(
		@Serializable(with = DateTimeSerializer::class) val dt: LocalDateTime,
		@Serializable(with = DateTimeSerializer::class) val nullable: LocalDateTime?
	)

	@Serializable
	private data class LegacyDateTimeHolder(
		@Serializable(with = DateTimeSerializer::class) val dt: LocalDateTime,
		@Serializable(with = NullableDateTimeSerializer::class) val nullable: LocalDateTime?
	)

	@Serializable
	private data class ZonedHolder(
		@Serializable(with = ZonedDateTimeSerializer::class) val z: ZonedDateTime,
		@Serializable(with = ZonedDateTimeSerializer::class) val nullable: ZonedDateTime?
	)

	@Serializable
	private data class LegacyZonedHolder(
		@Serializable(with = ZonedDateTimeSerializer::class) val z: ZonedDateTime,
		@Serializable(with = NullableZonedDateTimeSerializer::class) val nullable: ZonedDateTime?
	)

	private val json = Json

	@Before
	fun silenceLogger() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	// region 1. Recommended idiom — non-null serializer on a nullable property

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

	// endregion

	// region 2. Deprecated Nullable* serializers are wire-identical on the property path

	@Test
	fun `deprecated NullableLocalDateSerializer is wire-identical to the wrapped LocalDateSerializer`() {
		val date = LocalDate.of(2026, 6, 5)
		val value = LocalDate.of(2026, 1, 2)
		assertEquals(
			json.encodeToString(LocalDateHolder.serializer(), LocalDateHolder(date, value)),
			json.encodeToString(LegacyLocalDateHolder.serializer(), LegacyLocalDateHolder(date, value))
		)
		assertEquals(
			json.encodeToString(LocalDateHolder.serializer(), LocalDateHolder(date, null)),
			json.encodeToString(LegacyLocalDateHolder.serializer(), LegacyLocalDateHolder(date, null))
		)
	}

	@Test
	fun `deprecated NullableDateTimeSerializer is wire-identical to the wrapped DateTimeSerializer`() {
		val dt = LocalDateTime.of(2026, 6, 5, 10, 38, 29)
		val value = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
		assertEquals(
			json.encodeToString(DateTimeHolder.serializer(), DateTimeHolder(dt, value)),
			json.encodeToString(LegacyDateTimeHolder.serializer(), LegacyDateTimeHolder(dt, value))
		)
		assertEquals(
			json.encodeToString(DateTimeHolder.serializer(), DateTimeHolder(dt, null)),
			json.encodeToString(LegacyDateTimeHolder.serializer(), LegacyDateTimeHolder(dt, null))
		)
	}

	@Test
	fun `deprecated NullableZonedDateTimeSerializer is wire-identical to the wrapped ZonedDateTimeSerializer`() {
		val z = ZonedDateTime.of(2026, 6, 5, 10, 38, 29, 0, ZoneOffset.UTC)
		assertEquals(
			json.encodeToString(ZonedHolder.serializer(), ZonedHolder(z, z)),
			json.encodeToString(LegacyZonedHolder.serializer(), LegacyZonedHolder(z, z))
		)
		assertEquals(
			json.encodeToString(ZonedHolder.serializer(), ZonedHolder(z, null)),
			json.encodeToString(LegacyZonedHolder.serializer(), LegacyZonedHolder(z, null))
		)
	}

	// endregion

	// region 3. Direct use — bypasses kotlinx's wrapper, exercises the serializers' own null branches

	@Test
	fun `Nullable serializers encode a bare null as the null literal`() {
		assertEquals("null", json.encodeToString(NullableLocalDateSerializer, null))
		assertEquals("null", json.encodeToString(NullableDateTimeSerializer, null))
		assertEquals("null", json.encodeToString(NullableZonedDateTimeSerializer, null))
		assertEquals("null", json.encodeToString(NullableInstantSerializer, null))
	}

	@Test
	fun `Nullable serializers decode a bare null literal`() {
		assertNull(json.decodeFromString(NullableLocalDateSerializer, "null"))
		assertNull(json.decodeFromString(NullableDateTimeSerializer, "null"))
		assertNull(json.decodeFromString(NullableZonedDateTimeSerializer, "null"))
		assertNull(json.decodeFromString(NullableInstantSerializer, "null"))
	}

	@Test
	fun `Nullable serializers round-trip a bare non-null value`() {
		val date = LocalDate.of(2026, 6, 5)
		assertEquals("\"2026-06-05\"", json.encodeToString(NullableLocalDateSerializer, date))
		assertEquals(date, json.decodeFromString(NullableLocalDateSerializer, "\"2026-06-05\""))

		val dt = LocalDateTime.of(2026, 6, 5, 10, 38, 29)
		assertEquals(dt, json.decodeFromString(NullableDateTimeSerializer, json.encodeToString(NullableDateTimeSerializer, dt)))

		val z = ZonedDateTime.of(2026, 6, 5, 10, 38, 29, 0, ZoneOffset.UTC)
		val decodedZ = json.decodeFromString(NullableZonedDateTimeSerializer, json.encodeToString(NullableZonedDateTimeSerializer, z))
		assertEquals(z.toInstant(), decodedZ?.toInstant())

		val instant = z.toInstant()
		assertEquals(instant, json.decodeFromString(NullableInstantSerializer, json.encodeToString(NullableInstantSerializer, instant)))
	}

	@Test
	fun `Nullable serializers round-trip nulls inside a list`() {
		val dates = listOf(LocalDate.of(2026, 6, 5), null, LocalDate.of(2026, 1, 2))
		val dateSerializer = ListSerializer(NullableLocalDateSerializer)
		assertEquals(dates, json.decodeFromString(dateSerializer, json.encodeToString(dateSerializer, dates)))

		val dateTimes = listOf(LocalDateTime.of(2026, 6, 5, 10, 38, 29), null)
		val dateTimeSerializer = ListSerializer(NullableDateTimeSerializer)
		assertEquals(dateTimes, json.decodeFromString(dateTimeSerializer, json.encodeToString(dateTimeSerializer, dateTimes)))

		val zoned = listOf(ZonedDateTime.of(2026, 6, 5, 10, 38, 29, 0, ZoneOffset.UTC), null)
		val zonedSerializer = ListSerializer(NullableZonedDateTimeSerializer)
		val decodedZoned = json.decodeFromString(zonedSerializer, json.encodeToString(zonedSerializer, zoned))
		assertEquals(zoned.map { it?.toInstant() }, decodedZoned.map { it?.toInstant() })

		val instants = listOf(ZonedDateTime.of(2026, 6, 5, 10, 38, 29, 0, ZoneOffset.UTC).toInstant(), null)
		val instantSerializer = ListSerializer(NullableInstantSerializer)
		assertEquals(instants, json.decodeFromString(instantSerializer, json.encodeToString(instantSerializer, instants)))
	}

	@Test
	fun `Nullable serializers declare a nullable descriptor so kotlinx does not double-wrap`() {
		assertTrue(NullableLocalDateSerializer.descriptor.isNullable)
		assertTrue(NullableDateTimeSerializer.descriptor.isNullable)
		assertTrue(NullableZonedDateTimeSerializer.descriptor.isNullable)
		assertTrue(NullableInstantSerializer.descriptor.isNullable)

		assertFalse(LocalDateSerializer.descriptor.isNullable)
		assertFalse(DateTimeSerializer.descriptor.isNullable)
		assertFalse(ZonedDateTimeSerializer.descriptor.isNullable)
		assertFalse(InstantSerializer.descriptor.isNullable)
	}

	@Test
	fun `both ZonedDateTime serializers agree on the parse path`() {
		// Issue #106 §3: the two used different DateHelper entry points. Assert they do not diverge,
		// across the strict-offset, no-fraction and Carbon-short wire forms.
		listOf(
			"\"2026-06-05T10:38:29.000000Z\"",
			"\"2026-06-05T10:38:29+00:00\"",
			"\"2026-06-05 10:38:29\""
		).forEach { wire ->
			assertEquals(
				wire,
				json.decodeFromString(ZonedDateTimeSerializer, wire).toInstant(),
				json.decodeFromString(NullableZonedDateTimeSerializer, wire)?.toInstant()
			)
		}
	}

	// endregion
}
