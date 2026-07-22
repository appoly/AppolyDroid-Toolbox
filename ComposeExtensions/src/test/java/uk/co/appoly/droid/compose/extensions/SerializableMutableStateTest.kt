package uk.co.appoly.droid.compose.extensions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Unit tests for [SerializableMutableState] / [serializableMutableStateOf].
 */
class SerializableMutableStateTest {

	@Suppress("UNCHECKED_CAST")
	private fun <T> roundTrip(state: SerializableMutableState<T>): SerializableMutableState<T> {
		val bytes = ByteArrayOutputStream().use { baos ->
			ObjectOutputStream(baos).use { it.writeObject(state) }
			baos.toByteArray()
		}
		return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as SerializableMutableState<T> }
	}

	@Test
	fun `round-trip preserves initial value`() {
		val restored = roundTrip(serializableMutableStateOf("hello"))
		assertEquals("hello", restored.value)
	}

	@Test
	fun `round-trip persists mutated value`() {
		val state = serializableMutableStateOf("hello")
		state.value = "world"
		val restored = roundTrip(state)
		assertEquals("world", restored.value)
	}

	@Test
	fun `round-trip preserves null value`() {
		val restored = roundTrip(serializableMutableStateOf<String?>(null))
		assertNull(restored.value)
	}

	@Test
	fun `constructing non-Serializable value throws`() {
		assertThrows(IllegalArgumentException::class.java) {
			serializableMutableStateOf<Any?>(Any())
		}
	}

	@Test
	fun `assigning non-Serializable value throws`() {
		val state = serializableMutableStateOf<Any?>(null)
		assertThrows(IllegalArgumentException::class.java) {
			state.value = Any()
		}
	}

	@Test
	fun `by delegation works`() {
		var v by serializableMutableStateOf("x")
		assertEquals("x", v)
		v = "y"
		assertEquals("y", v)
	}

	@Test
	fun `destructuring set routes through validating setter`() {
		val state = serializableMutableStateOf("x")
		val (_, set) = state
		set("z")
		assertEquals("z", state.value)
	}
}
