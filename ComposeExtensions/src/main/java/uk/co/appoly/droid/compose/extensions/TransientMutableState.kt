package uk.co.appoly.droid.compose.extensions

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.Serializable

/**
 * A [Serializable] [MutableState] whose value resets to [initial] after a process-death restore,
 * rather than being persisted. Sibling of [SerializableMutableState]; for ephemeral presentation
 * or event state (sheet visibility, one-shot pulses, overlays) that should NOT survive process
 * death. Only [initial] is persisted (it must be [Serializable] or `null`).
 *
 * The backing delegate is created lazily and is not thread-safe; main-thread access only.
 */
class TransientMutableState<T>(private val initial: T) : MutableState<T>, Serializable {
	init {
		require(initial == null || initial is Serializable) {
			"transientMutableStateOf initial value must be Serializable or null, was: $initial"
		}
	}

	@Transient
	private var delegate: MutableState<T>? = null

	private fun delegate(): MutableState<T> =
		delegate ?: mutableStateOf(initial).also { delegate = it }

	override var value: T
		get() = delegate().value
		set(value) { delegate().value = value }

	override fun component1(): T = delegate().value
	override fun component2(): (T) -> Unit = { delegate().value = it }

	companion object { private const val serialVersionUID: Long = 1L }
}

/** Creates a [Serializable] [MutableState] that resets to [initial] on process-death restore. */
fun <T> transientMutableStateOf(initial: T): TransientMutableState<T> = TransientMutableState(initial)
