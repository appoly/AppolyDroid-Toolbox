package uk.co.appoly.droid.compose.extensions

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.NotSerializableException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * A [Serializable] [MutableState] that persists and restores its value across process death.
 *
 * Voyager `Screen`s are Java-serialized to survive process death; a plain `mutableStateOf` is not
 * [Serializable], and the usual `@Transient` workaround restores as `null` (the JVM does not run
 * field initializers on deserialization), crashing on the next read. This holder implements
 * [MutableState] by delegating to a `@Transient` `mutableStateOf`, so it is a drop-in replacement
 * for `by`/`.value`/destructuring, while persisting and restoring the value itself.
 *
 * Use this for one-shot guards where a reset would re-fire an effect or lose real progress. For
 * ephemeral state that should reset on restore, use [TransientMutableState] instead.
 *
 * The value must be [Serializable] or `null`; this is enforced at construction and on assignment.
 * Main-thread access only.
 */
class SerializableMutableState<T>(initial: T) : MutableState<T>, Serializable {

	init {
		requireSerializableOrNull(initial)
	}

	@Transient
	private var delegate: MutableState<T> = mutableStateOf(initial)

	override var value: T
		get() = delegate.value
		set(value) {
			requireSerializableOrNull(value)
			delegate.value = value
		}

	override fun component1(): T = value
	override fun component2(): (T) -> Unit = { value = it }

	private fun writeObject(out: ObjectOutputStream) {
		out.defaultWriteObject()
		val current = delegate.value
		// Backstop: the setter/constructor already reject non-Serializable values, but guard here
		// too so a value slipped in by other means fails loudly rather than restoring as null.
		if (current != null && current !is Serializable) {
			throw NotSerializableException(
				"serializableMutableStateOf value is not Serializable: ${current::class.java.name}. " +
					"Use a Serializable type, or transientMutableStateOf if it need not survive process death.",
			)
		}
		out.writeObject(current as? Serializable)
	}

	private fun readObject(input: ObjectInputStream) {
		input.defaultReadObject()
		@Suppress("UNCHECKED_CAST")
		delegate = mutableStateOf(input.readObject() as T)
	}

	companion object {
		private const val serialVersionUID: Long = 1L

		private fun requireSerializableOrNull(value: Any?) {
			require(value == null || value is Serializable) {
				"serializableMutableStateOf value must be Serializable or null, was: " +
					"${value!!::class.java.name}. Use a Serializable type, or transientMutableStateOf " +
					"if it need not survive process death."
			}
		}
	}
}

/** Creates a [Serializable] [MutableState] that survives process death. */
fun <T> serializableMutableStateOf(initial: T): SerializableMutableState<T> = SerializableMutableState(initial)
