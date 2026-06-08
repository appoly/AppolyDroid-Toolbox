package uk.co.appoly.droid.util

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip tests for the enum serializers ([EnumAsIntSerializer], [EnumAsStringSerializer] and
 * their nullable counterparts).
 */
class EnumSerializersTest {

	private enum class Color { RED, GREEN, BLUE }

	private val json = Json

	@Test
	fun `EnumAsIntSerializer round-trips by ordinal`() {
		val ser = EnumAsIntSerializer<Color>("Color", { it.ordinal }, { Color.entries[it] })
		val encoded = json.encodeToString(ser, Color.GREEN)
		assertEquals("1", encoded)
		assertEquals(Color.GREEN, json.decodeFromString(ser, encoded))
	}

	@Test
	fun `EnumAsStringSerializer round-trips by name`() {
		val ser = EnumAsStringSerializer<Color>("Color", { it.name }, { Color.valueOf(it) })
		val encoded = json.encodeToString(ser, Color.BLUE)
		assertEquals("\"BLUE\"", encoded)
		assertEquals(Color.BLUE, json.decodeFromString(ser, encoded))
	}

	@Test
	fun `NullableEnumAsIntSerializer encodes value and null and decodes a value`() {
		val ser = NullableEnumAsIntSerializer<Color>("Color", { it.ordinal }, { Color.entries.getOrNull(it) })
		assertEquals(Color.RED, json.decodeFromString(ser, json.encodeToString(ser, Color.RED)))
		assertEquals("null", json.encodeToString(ser, null))
	}

	@Test
	fun `NullableEnumAsStringSerializer encodes value and null and decodes a value`() {
		val ser = NullableEnumAsStringSerializer<Color>("Color", { it.name }, { runCatching { Color.valueOf(it) }.getOrNull() })
		assertEquals(Color.GREEN, json.decodeFromString(ser, json.encodeToString(ser, Color.GREEN)))
		assertEquals("null", json.encodeToString(ser, null))
	}
}
