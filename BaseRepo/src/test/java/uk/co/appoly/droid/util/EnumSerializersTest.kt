package uk.co.appoly.droid.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the enum serializers ([EnumAsIntSerializer], [EnumAsStringSerializer] and
 * their nullable counterparts).
 *
 * The nullable pair is exercised on both paths deliberately — via `@Serializable(with = ...)` on a
 * property, and directly (bare value, bare `null`, and inside a `ListSerializer`). The direct
 * decode of a `null` token used to throw, because the serializers called `decodeString()` /
 * `decodeInt()` without checking the null mark; see issue #106 for the same defect in the date
 * serializers.
 */
class EnumSerializersTest {

	private enum class Color { RED, GREEN, BLUE }

	private val json = Json

	private val nullableIntSer =
		NullableEnumAsIntSerializer<Color>("Color", { it.ordinal }, { Color.entries.getOrNull(it) })

	private val nullableStringSer =
		NullableEnumAsStringSerializer<Color>("Color", { it.name }, { runCatching { Color.valueOf(it) }.getOrNull() })

	@Serializable
	private data class Holder(
		@Serializable(with = ColorIntSerializer::class) val byInt: Color?,
		@Serializable(with = ColorStringSerializer::class) val byName: Color?
	)

	private object ColorIntSerializer :
		NullableEnumAsIntSerializer<Color>("Color", { it.ordinal }, { Color.entries.getOrNull(it) })

	private object ColorStringSerializer :
		NullableEnumAsStringSerializer<Color>("Color", { it.name }, { runCatching { Color.valueOf(it) }.getOrNull() })

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
		assertEquals(Color.RED, json.decodeFromString(nullableIntSer, json.encodeToString(nullableIntSer, Color.RED)))
		assertEquals("null", json.encodeToString(nullableIntSer, null))
	}

	@Test
	fun `NullableEnumAsStringSerializer encodes value and null and decodes a value`() {
		assertEquals(Color.GREEN, json.decodeFromString(nullableStringSer, json.encodeToString(nullableStringSer, Color.GREEN)))
		assertEquals("null", json.encodeToString(nullableStringSer, null))
	}

	@Test
	fun `nullable enum serializers decode a bare null literal`() {
		assertNull(json.decodeFromString(nullableIntSer, "null"))
		assertNull(json.decodeFromString(nullableStringSer, "null"))
	}

	@Test
	fun `nullable enum serializers decode an unrecognised value as null`() {
		assertNull(json.decodeFromString(nullableIntSer, "99"))
		assertNull(json.decodeFromString(nullableStringSer, "\"MAGENTA\""))
		assertNull(json.decodeFromString(nullableStringSer, "\"\""))
	}

	@Test
	fun `nullable enum serializers round-trip nulls inside a list`() {
		val ints = listOf(Color.RED, null, Color.BLUE)
		val intList = ListSerializer(nullableIntSer)
		assertEquals(ints, json.decodeFromString(intList, json.encodeToString(intList, ints)))

		val names = listOf(null, Color.GREEN)
		val stringList = ListSerializer(nullableStringSer)
		assertEquals(names, json.decodeFromString(stringList, json.encodeToString(stringList, names)))
	}

	@Test
	fun `nullable enum serializers handle nulls and unknown values on the property path`() {
		val holder = Holder(Color.RED, Color.BLUE)
		assertEquals(holder, json.decodeFromString(Holder.serializer(), json.encodeToString(Holder.serializer(), holder)))

		val nulls = Holder(null, null)
		assertEquals("""{"byInt":null,"byName":null}""", json.encodeToString(Holder.serializer(), nulls))
		assertEquals(nulls, json.decodeFromString(Holder.serializer(), """{"byInt":null,"byName":null}"""))

		// Unrecognised wire values degrade to null rather than throwing — the whole point of these classes.
		val unknown = json.decodeFromString(Holder.serializer(), """{"byInt":99,"byName":"MAGENTA"}""")
		assertNull(unknown.byInt)
		assertNull(unknown.byName)
	}

	@Test
	fun `nullable enum serializers declare a nullable descriptor so kotlinx does not double-wrap`() {
		assertTrue(nullableIntSer.descriptor.isNullable)
		assertTrue(nullableStringSer.descriptor.isNullable)

		assertFalse(EnumAsIntSerializer<Color>("Color", { it.ordinal }, { Color.entries[it] }).descriptor.isNullable)
		assertFalse(EnumAsStringSerializer<Color>("Color", { it.name }, { Color.valueOf(it) }).descriptor.isNullable)
	}
}
