package uk.co.appoly.droid.nav3

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * A one-shot payload handed to one specific back-stack entry: the next step of a multi-step flow.
 *
 * A deep link to a clip, say, opens the detail screen. That screen loads its data and checks
 * permissions, then either stops or continues to the media screen, which opens the clip. Each
 * screen performs its own step and hands the next one on:
 *
 * ```kotlin
 * @Serializable data class OpenMedia(val clipId: String) : Nav3Continuation
 * @Serializable data class OpenClip(val clipId: String) : Nav3Continuation
 *
 * // A deep link lands DetailScreen with the first step addressed to it:
 * Nav3DeepLink(listOf(HomeTab, DetailScreen(id)), continuation = OpenMedia(clipId))
 *
 * // In DetailScreen.Content():
 * val step = rememberNav3Continuation<OpenMedia>()   // null unless addressed to THIS entry
 * LaunchedEffect(step, data) {
 *     val s = step ?: return@LaunchedEffect
 *     if (data == null) return@LaunchedEffect       // wait for the data to load
 *     if (!s.consume()) return@LaunchedEffect       // someone else already took it
 *     if (canShowMedia) navigator.push(MediaScreen(id), continuation = OpenClip(s.value.clipId))
 * }
 * ```
 *
 * Implementations must be `@Serializable`: pending continuations are saved with the navigator,
 * so they survive configuration change and process death. The module's consumer R8 rules keep the
 * serializers of every class implementing this interface.
 *
 * @see Nav3Continuations
 * @see rememberNav3Continuation
 * @see Serializable
 */
interface Nav3Continuation

/**
 * The pending [Nav3Continuation]s of one navigator, keyed by the back-stack entry each is
 * addressed to. Read it from [Nav3Navigator.continuations].
 *
 * - **Delivery** happens through [Nav3DeepLink.continuation] and
 *   [Nav3Navigator.push] with a `continuation`; there is no public way to address a key that
 *   isn't being navigated to.
 * - **Consumption** is from the entry itself: [rememberNav3Continuation] in composition, or
 *   [consume] from a ViewModel (pass the screen as the key).
 * - **Lifetime:** an entry's continuation is dropped when its key leaves the back stack, so a
 *   popped screen can't leave a request to fire later. A consumed continuation is gone for good,
 *   including after process-death restore.
 *
 * Equal keys share an entry (and its saved state, as everywhere in Navigation 3), so they share a
 * pending continuation too. The navigators' deep-link landing never puts an equal key on a stack
 * twice.
 *
 * Main thread only, like the back stack itself. Backed by snapshot state, so composition that
 * reads a pending continuation recomposes when it arrives or is consumed.
 */
class Nav3Continuations {

	private val pending = mutableStateMapOf<NavKey, Nav3Continuation>()

	/** The continuation pending for [key], or `null`. Doesn't consume it. */
	fun peek(key: NavKey): Nav3Continuation? = pending[key]

	/**
	 * The continuation pending for [key] if it is a [type], or `null`. Doesn't consume it; a
	 * payload of another type is left in place for whoever expects it.
	 */
	fun <T : Nav3Continuation> peek(key: NavKey, type: KClass<T>): T? =
		pending[key]?.let { if (type.isInstance(it)) type.java.cast(it) else null }

	/** Removes and returns the continuation pending for [key], or `null` if there is none. */
	@MainThread
	fun consume(key: NavKey): Nav3Continuation? = pending.remove(key)

	/**
	 * Removes and returns the continuation pending for [key] if it is a [type]. A payload of
	 * another type is left in place and `null` is returned.
	 */
	@MainThread
	fun <T : Nav3Continuation> consume(key: NavKey, type: KClass<T>): T? {
		val value = peek(key, type) ?: return null
		pending.remove(key)
		return value
	}

	/** Addresses [value] to [key], replacing anything already pending for it. */
	@MainThread
	internal fun put(key: NavKey, value: Nav3Continuation) {
		requireSerializable(value)
		pending[key] = value
	}

	/**
	 * Compare-and-clear: removes [key]'s continuation only if it is still [expected] (by
	 * identity). Returns whether it did, so two consumers can't both act on one payload.
	 */
	@MainThread
	internal fun consumeIf(key: NavKey, expected: Nav3Continuation): Boolean {
		if (pending[key] !== expected) return false
		pending.remove(key)
		return true
	}

	/** Drops every continuation whose key is no longer in [keys] (the current back stack). */
	@MainThread
	internal fun retainOnly(keys: Collection<NavKey>) {
		if (pending.isEmpty()) return
		val live = keys.toHashSet()
		pending.keys.filter { it !in live }.forEach { pending.remove(it) }
	}

	internal val pendingKeys: Set<NavKey> get() = pending.keys.toSet()

	internal fun toBundle(): Bundle = Bundle().apply {
		val entries = pending.entries.toList()
		putInt(KEY_COUNT, entries.size)
		entries.forEachIndexed { index, (key, value) ->
			putBundle("$KEY_KEY$index", encodeToSavedState(keySerializer, key))
			putBundle("$KEY_VALUE$index", encodeToSavedState(Nav3ContinuationSerializer, value))
		}
	}

	internal fun restoreFrom(bundle: Bundle) {
		pending.clear()
		for (index in 0 until bundle.getInt(KEY_COUNT, 0)) {
			// A continuation that can't be restored is dropped rather than crashing the restore:
			// delivery is at most once, so losing one is the safe failure.
			runCatching {
				val key = decodeFromSavedState(keySerializer, bundle.getBundle("$KEY_KEY$index")!!)
				val value = decodeFromSavedState(Nav3ContinuationSerializer, bundle.getBundle("$KEY_VALUE$index")!!)
				pending[key] = value
			}
		}
	}

	companion object {
		private const val KEY_COUNT = "n"
		private const val KEY_KEY = "k"
		private const val KEY_VALUE = "v"
		private val keySerializer = NavKeySerializer<NavKey>()

		/**
		 * [Saver] for keeping a [Nav3Continuations] in `rememberSaveable`, for a custom navigator
		 * that owns its own store. The built-in navigators already save theirs.
		 */
		fun saver(): Saver<Nav3Continuations, Bundle> = Saver(
			save = { it.toBundle() },
			restore = { bundle -> Nav3Continuations().also { it.restoreFrom(bundle) } },
		)

		/** Fails at the call site, not at save time, when a payload isn't `@Serializable`. */
		@OptIn(InternalSerializationApi::class)
		private fun requireSerializable(value: Nav3Continuation) {
			try {
				value::class.serializer()
			} catch (e: kotlinx.serialization.SerializationException) {
				throw IllegalArgumentException(
					"${value::class.qualifiedName} must be @Serializable to be used as a Nav3Continuation",
					e,
				)
			}
		}
	}
}

/**
 * Saves a [Nav3Continuation] as its class name plus its own serializer's output, the same
 * reflective approach Navigation 3's [NavKeySerializer] takes for keys.
 */
@OptIn(InternalSerializationApi::class)
internal object Nav3ContinuationSerializer : KSerializer<Nav3Continuation> {
	override val descriptor: SerialDescriptor =
		buildClassSerialDescriptor("uk.co.appoly.droid.nav3.Nav3Continuation") {
			element("type", serialDescriptor<String>())
			element("value", buildClassSerialDescriptor("Any"))
		}

	override fun serialize(encoder: Encoder, value: Nav3Continuation) {
		encoder.encodeStructure(descriptor) {
			encodeStringElement(descriptor, 0, value::class.java.name)
			@Suppress("UNCHECKED_CAST")
			val serializer = value::class.serializer() as KSerializer<Nav3Continuation>
			encodeSerializableElement(descriptor, 1, serializer, value)
		}
	}

	override fun deserialize(decoder: Decoder): Nav3Continuation =
		decoder.decodeStructure(descriptor) {
			val className = decodeStringElement(descriptor, decodeElementIndex(descriptor))
			val serializer = Class.forName(className).kotlin.serializer()
			decodeSerializableElement(descriptor, decodeElementIndex(descriptor), serializer) as Nav3Continuation
		}
}

/**
 * A continuation addressed to the current entry, from [rememberNav3Continuation].
 *
 * Act on [value] once, after calling [consume]: consume first so a step that is interrupted (a
 * cancelled coroutine, a recreated screen) is dropped instead of firing again.
 */
class Nav3PendingContinuation<out T : Nav3Continuation> internal constructor(
	val value: T,
	private val consumeAction: () -> Boolean,
) {
	/**
	 * Takes this continuation off the entry. Returns `false` when it was already consumed (or
	 * replaced) elsewhere, in which case don't act on [value].
	 */
	@MainThread
	fun consume(): Boolean = consumeAction()
}

/**
 * The [Nav3Screen] whose entry is being composed, provided by [nav3ScreenEntry]. A custom
 * `entryProvider` should provide it too, or [rememberNav3Continuation] can't tell which entry
 * it's in.
 */
val LocalNav3Screen = staticCompositionLocalOf<Nav3Screen?> { null }

/**
 * The [Nav3Continuation] of type [T] pending for **this** entry, or `null`.
 *
 * Returns `null` when nothing is pending for the entry, when the pending payload is of another
 * type (it is left in place), or outside a [Nav3ScreenHost] entry. Recomposes when a continuation
 * arrives or is consumed.
 *
 * @see Nav3Continuation for a worked example.
 */
@Composable
inline fun <reified T : Nav3Continuation> rememberNav3Continuation(): Nav3PendingContinuation<T>? =
	rememberNav3Continuation(T::class)

/** [rememberNav3Continuation] with an explicit [type], for callers without a reified type. */
@Composable
fun <T : Nav3Continuation> rememberNav3Continuation(type: KClass<T>): Nav3PendingContinuation<T>? {
	val screen = LocalNav3Screen.current ?: return null
	val store = LocalNav3Navigator.current?.continuations ?: return null
	val value = store.peek(screen, type) ?: return null
	return remember(store, screen, value) {
		Nav3PendingContinuation(value) { store.consumeIf(screen, value) }
	}
}

/**
 * Pushes [screen] with [continuation] addressed to it, so one step of a flow can hand the next
 * step to the screen it opens.
 */
@MainThread
fun Nav3Navigator.push(screen: Nav3Screen, continuation: Nav3Continuation) {
	continuations.put(screen, continuation)
	push(screen)
}

/** Reified [Nav3Continuations.peek]. */
inline fun <reified T : Nav3Continuation> Nav3Continuations.peekAs(key: NavKey): T? = peek(key, T::class)

/** Reified [Nav3Continuations.consume]. */
inline fun <reified T : Nav3Continuation> Nav3Continuations.consumeAs(key: NavKey): T? = consume(key, T::class)
