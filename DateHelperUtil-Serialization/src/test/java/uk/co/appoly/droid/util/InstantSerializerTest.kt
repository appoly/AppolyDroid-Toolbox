package uk.co.appoly.droid.util

import com.duck.flexilogger.LoggingLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Tests for the Phase 1 [Instant] kotlinx serializers and their typealiases.
 *
 * Verifies wire-format invariance (UTC moments render as "...Z", byte-identical to the
 * legacy format) and round-trip parity through [Json].
 */
class InstantSerializerTest {

	@Serializable
	private data class Wrapper(val value: SerializableInstant)

	@Serializable
	private data class NullableWrapper(val value: NullableSerializableInstant)

	private val json = Json

	@Before
	fun silenceLogger() {
		DateHelper.setLogger(SilentTestLogger, LoggingLevel.NONE)
	}

	@Test
	fun `InstantSerializer emits ISO-8601 UTC string`() {
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		val output = json.encodeToString(Wrapper.serializer(), Wrapper(instant))
		assertEquals("""{"value":"2025-05-29T10:38:29.000000Z"}""", output)
	}

	@Test
	fun `InstantSerializer preserves microsecond precision`() {
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29, 123_456_000)
			.toInstant(ZoneOffset.UTC)
		val output = json.encodeToString(Wrapper.serializer(), Wrapper(instant))
		assertEquals("""{"value":"2025-05-29T10:38:29.123456Z"}""", output)
	}

	@Test
	fun `InstantSerializer round-trip preserves the Instant`() {
		val original = LocalDateTime.of(2025, 1, 15, 9, 0, 0, 123_456_000)
			.toInstant(ZoneOffset.UTC)
		val encoded = json.encodeToString(Wrapper.serializer(), Wrapper(original))
		val decoded = json.decodeFromString(Wrapper.serializer(), encoded)
		assertEquals(original, decoded.value)
	}

	@Test
	fun `NullableInstantSerializer round-trips a non-null Instant`() {
		val original: Instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		val encoded = json.encodeToString(NullableWrapper.serializer(), NullableWrapper(original))
		val decoded = json.decodeFromString(NullableWrapper.serializer(), encoded)
		assertEquals(original, decoded.value)
	}

	@Test
	fun `NullableInstantSerializer encodes non-null with same wire format as InstantSerializer`() {
		val instant = LocalDateTime.of(2025, 5, 29, 10, 38, 29).toInstant(ZoneOffset.UTC)
		val nonNullEncoded = json.encodeToString(Wrapper.serializer(), Wrapper(instant))
		val nullableEncoded = json.encodeToString(NullableWrapper.serializer(), NullableWrapper(instant))
		assertEquals(nonNullEncoded, nullableEncoded)
	}
}
