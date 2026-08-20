package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
 * There are two families and the difference is **leniency**, not nullability:
 * - `XSerializer` is strict — an unparseable value throws [SerializationException].
 * - `NullableXSerializer` is lenient — an unparseable value, like a literal `null`, decodes to null.
 *
 * Coverage is split into four regions:
 * 1. The strict serializers, including on a nullable property (kotlinx wraps them).
 * 2. Wire parity between the two families **for null tokens and valid values** — they agree there.
 * 3. Direct use of the lenient serializers, bypassing kotlinx's wrapper, which exercises their own
 *    null branches. Uncovered before issue #106.
 * 4. The strict/lenient divergence on **unparseable** input. This is the case a parity test cannot
 *    see, and its absence is why 1.8.1 wrongly deprecated the lenient family as redundant.
 */
class DateSerializersTest {

	@Serializable
	private data class LocalDateHolder(
		@Serializable(with = LocalDateSerializer::class) val date: LocalDate,
		@Serializable(with = LocalDateSerializer::class) val nullable: LocalDate?
	)

	@Serializable
	private data class LenientLocalDateHolder(
		@Serializable(with = LocalDateSerializer::class) val date: LocalDate,
		@Serializable(with = NullableLocalDateSerializer::class) val nullable: LocalDate?
	)

	@Serializable
	private data class DateTimeHolder(
		@Serializable(with = DateTimeSerializer::class) val dt: LocalDateTime,
		@Serializable(with = DateTimeSerializer::class) val nullable: LocalDateTime?
	)

	@Serializable
	private data class LenientDateTimeHolder(
		@Serializable(with = DateTimeSerializer::class) val dt: LocalDateTime,
		@Serializable(with = NullableDateTimeSerializer::class) val nullable: LocalDateTime?
	)

	@Serializable
	private data class ZonedHolder(
		@Serializable(with = ZonedDateTimeSerializer::class) val z: ZonedDateTime,
		@Serializable(with = ZonedDateTimeSerializer::class) val nullable: ZonedDateTime?
	)

	@Serializable
	private data class LenientZonedHolder(
		@Serializable(with = ZonedDateTimeSerializer::class) val z: ZonedDateTime,
		@Serializable(with = NullableZonedDateTimeSerializer::class) val nullable: ZonedDateTime?
	)

	private val json = Json

	@Before
	fun silenceLogger() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	// region 1. Strict serializers, including on a nullable property

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

	// region 2. The two families agree on null tokens and valid values

	@Test
	fun `lenient LocalDate serializer matches the strict one for null and valid values`() {
		val date = LocalDate.of(2026, 6, 5)
		val value = LocalDate.of(2026, 1, 2)
		assertEquals(
			json.encodeToString(LocalDateHolder.serializer(), LocalDateHolder(date, value)),
			json.encodeToString(LenientLocalDateHolder.serializer(), LenientLocalDateHolder(date, value))
		)
		assertEquals(
			json.encodeToString(LocalDateHolder.serializer(), LocalDateHolder(date, null)),
			json.encodeToString(LenientLocalDateHolder.serializer(), LenientLocalDateHolder(date, null))
		)
	}

	@Test
	fun `lenient LocalDateTime serializer matches the strict one for null and valid values`() {
		val dt = LocalDateTime.of(2026, 6, 5, 10, 38, 29)
		val value = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
		assertEquals(
			json.encodeToString(DateTimeHolder.serializer(), DateTimeHolder(dt, value)),
			json.encodeToString(LenientDateTimeHolder.serializer(), LenientDateTimeHolder(dt, value))
		)
		assertEquals(
			json.encodeToString(DateTimeHolder.serializer(), DateTimeHolder(dt, null)),
			json.encodeToString(LenientDateTimeHolder.serializer(), LenientDateTimeHolder(dt, null))
		)
	}

	@Test
	fun `lenient ZonedDateTime serializer matches the strict one for null and valid values`() {
		val z = ZonedDateTime.of(2026, 6, 5, 10, 38, 29, 0, ZoneOffset.UTC)
		assertEquals(
			json.encodeToString(ZonedHolder.serializer(), ZonedHolder(z, z)),
			json.encodeToString(LenientZonedHolder.serializer(), LenientZonedHolder(z, z))
		)
		assertEquals(
			json.encodeToString(ZonedHolder.serializer(), ZonedHolder(z, null)),
			json.encodeToString(LenientZonedHolder.serializer(), LenientZonedHolder(z, null))
		)
	}

	// endregion

	// region 3. Direct use — bypasses kotlinx's wrapper, exercises the lenient null branches

	@Test
	fun `lenient serializers encode a bare null as the null literal`() {
		assertEquals("null", json.encodeToString(NullableLocalDateSerializer, null))
		assertEquals("null", json.encodeToString(NullableDateTimeSerializer, null))
		assertEquals("null", json.encodeToString(NullableZonedDateTimeSerializer, null))
		assertEquals("null", json.encodeToString(NullableInstantSerializer, null))
	}

	@Test
	fun `lenient serializers decode a bare null literal`() {
		assertNull(json.decodeFromString(NullableLocalDateSerializer, "null"))
		assertNull(json.decodeFromString(NullableDateTimeSerializer, "null"))
		assertNull(json.decodeFromString(NullableZonedDateTimeSerializer, "null"))
		assertNull(json.decodeFromString(NullableInstantSerializer, "null"))
	}

	@Test
	fun `lenient serializers round-trip a bare non-null value`() {
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
	fun `lenient serializers round-trip nulls inside a list`() {
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
	fun `lenient serializers declare a nullable descriptor so kotlinx does not double-wrap`() {
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

	// region 4. Strict vs lenient on UNPARSEABLE input — the divergence 1.8.1 missed

	@Test
	fun `lenient serializers decode an unparseable value to null`() {
		val garbage = "\"not-a-date\""
		assertNull(json.decodeFromString(NullableLocalDateSerializer, garbage))
		assertNull(json.decodeFromString(NullableDateTimeSerializer, garbage))
		assertNull(json.decodeFromString(NullableZonedDateTimeSerializer, garbage))
		assertNull(json.decodeFromString(NullableInstantSerializer, garbage))
	}

	@Test
	fun `strict serializers throw SerializationException on an unparseable value`() {
		val garbage = "\"not-a-date\""
		assertThrows(SerializationException::class.java) { json.decodeFromString(LocalDateSerializer, garbage) }
		assertThrows(SerializationException::class.java) { json.decodeFromString(DateTimeSerializer, garbage) }
		assertThrows(SerializationException::class.java) { json.decodeFromString(ZonedDateTimeSerializer, garbage) }
		assertThrows(SerializationException::class.java) { json.decodeFromString(InstantSerializer, garbage) }
	}

	@Test
	fun `on a property, lenient nulls the one field where strict fails the whole decode`() {
		// This is the case that makes the lenient family non-redundant: one malformed timestamp from
		// a backend nulls one field instead of losing the entire response.
		val wire = """{"z":"2026-06-05T10:38:29.000000Z","nullable":"garbage"}"""

		val lenient = json.decodeFromString(LenientZonedHolder.serializer(), wire)
		assertNull(lenient.nullable)
		assertEquals(
			ZonedDateTime.of(2026, 6, 5, 10, 38, 29, 0, ZoneOffset.UTC).toInstant(),
			lenient.z.toInstant()
		)

		assertThrows(SerializationException::class.java) {
			json.decodeFromString(ZonedHolder.serializer(), wire)
		}
	}

	@Test
	fun `an unparseable value in a list nulls one element under the lenient serializer`() {
		val wire = """["2026-06-05","garbage","2026-01-02"]"""
		assertEquals(
			listOf(LocalDate.of(2026, 6, 5), null, LocalDate.of(2026, 1, 2)),
			json.decodeFromString(ListSerializer(NullableLocalDateSerializer), wire)
		)
		assertThrows(SerializationException::class.java) {
			json.decodeFromString(ListSerializer(LocalDateSerializer), wire)
		}
	}

	// endregion
}
