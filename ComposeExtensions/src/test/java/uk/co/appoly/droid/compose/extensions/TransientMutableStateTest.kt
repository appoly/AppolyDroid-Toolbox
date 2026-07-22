package uk.co.appoly.droid.compose.extensions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Unit tests for [TransientMutableState] / [transientMutableStateOf].
 */
class TransientMutableStateTest {

	@Suppress("UNCHECKED_CAST")
	private fun <T> roundTrip(state: TransientMutableState<T>): TransientMutableState<T> {
		val bytes = ByteArrayOutputStream().use { baos ->
			ObjectOutputStream(baos).use { it.writeObject(state) }
			baos.toByteArray()
		}
		return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as TransientMutableState<T> }
	}

	@Test
	fun `value resets to initial on restore`() {
		val state = transientMutableStateOf("init")
		state.value = "changed"
		assertEquals("changed", state.value)

		val restored = roundTrip(state)
		assertEquals("init", restored.value)
	}

	@Test
	fun `non-Serializable initial throws`() {
		assertThrows(IllegalArgumentException::class.java) {
			transientMutableStateOf<Any?>(Any())
		}
	}

	@Test
	fun `value get and set work`() {
		val state = transientMutableStateOf("a")
		assertEquals("a", state.value)
		state.value = "b"
		assertEquals("b", state.value)
	}

	@Test
	fun `by delegation works`() {
		var v by transientMutableStateOf("x")
		assertEquals("x", v)
		v = "y"
		assertEquals("y", v)
	}
}
