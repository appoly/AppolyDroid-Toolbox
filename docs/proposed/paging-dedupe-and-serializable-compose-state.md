# Proposed: paging de-dupe + serialization-safe Compose state

**Status:** TODO / proposed — not yet implemented.
**Origin:** WenWe Android, July 2026. Both utilities were written to fix production crashes
(Sentry `WENWE-ANDROID-5H` and `WENWE-ANDROID-5G`) and are fully generic — nothing WenWe-specific —
so they belong in the toolbox. This doc captures them ready to lift in.

Two independent additions:
1. `Flow<PagingData<T>>.distinctBy { }` → **PagingExtensions** module.
2. `SerializableMutableState` / `TransientMutableState` (+ factories) → **ComposeExtensions** module.

Once released, update WenWe to depend on the library versions and delete its local copies (see
[Migrate WenWe once released](#migrate-wenwe-once-released)).

---

## 1. `Flow<PagingData<T>>.distinctBy` — PagingExtensions

**Module:** `:PagingExtensions`  ·  **Package:** `uk.co.appoly.droid.util.paging`
(alongside the existing `PagingExtensions.kt`; deps already include `androidx.paging`.)

### Why
Offset/page-number pagination over data that can change between page loads can return the **same
item id on more than one page**. With `itemKey = { it.id }` on a `LazyColumn`/`LazyRow`, the second
occurrence throws `IllegalArgumentException: Key "…" was already used` and crashes the screen. This
extension de-dupes the stream by an arbitrary key, keeping the first occurrence per `PagingData`
generation. (It's a client-side guard; the real fix is stable/keyset pagination server-side, but the
guard prevents a hard crash regardless.)

### Source (repackaged)
```kotlin
package uk.co.appoly.droid.util.paging

import androidx.paging.PagingData
import androidx.paging.filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * De-duplicates items in a paging stream by the key produced by [selector], keeping the first
 * occurrence of each key and dropping any later duplicates.
 *
 * Guards against a paged endpoint returning the same item on more than one page — common with
 * offset pagination over data that can change between page loads — which would otherwise crash a
 * `LazyColumn`/`LazyRow` with `IllegalArgumentException: Key "…" was already used` when that item's
 * key is used as the list `key`/`itemKey`.
 *
 * The `seen` set is scoped inside the [map] over each emitted [PagingData], so it resets naturally
 * on every refresh/invalidation. Only safe when the `Pager` does **not** set `maxSize` (no page
 * dropping): a dropped-then-reloaded page's items would otherwise be wrongly filtered as seen.
 */
fun <T : Any, K> Flow<PagingData<T>>.distinctBy(
	selector: (T) -> K,
): Flow<PagingData<T>> = map { pagingData ->
	val seen = mutableSetOf<K>()
	pagingData.filter { seen.add(selector(it)) }
}
```

### Tests to add
- Duplicate ids across a simulated multi-page `PagingData` → only first kept.
- Clean pages → unchanged, order preserved.
- Add to `PagingExtensionsTest.kt`.

---

## 2. Serialization-safe Compose `MutableState` — ComposeExtensions

**Module:** `:ComposeExtensions`  ·  **Package:** `uk.co.appoly.droid.compose.extensions`
(or a new `uk.co.appoly.droid.compose.state` sub-package; deps: compose runtime + stdlib only.)

### Why
Voyager `Screen`s implement `java.io.Serializable` and are Java-serialized to survive process death.
A plain `mutableStateOf(…)` isn't `Serializable`, so the common workaround is a `@Transient` field —
which returns **null** after a deserialization restore (the JVM doesn't run field initializers),
crashing on the next read with `NullPointerException: … State.getValue() on a null object reference`
(`WENWE-ANDROID-5G`). These two holders both implement `MutableState<T>` (so they're drop-in for
`by`/`.value`/destructuring) and are `Serializable`:

- **`SerializableMutableState`** — persists & restores the value. For one-shot guards where a reset
  would re-fire an effect (`firstOpen`, `shouldLaunchPicker`, `didInitialScroll`, `wenWeLoaded`).
- **`TransientMutableState`** — resets to `initial` on restore. For ephemeral presentation/event
  state (sheet visibility, "refresh now" pulses, overlays, deep-link/pending triggers, one-shot
  snackbars).

Rule of thumb: **persist if resetting would re-fire a one-shot effect or lose real progress;
otherwise reset.** For `derivedStateOf` on a Screen field (same null-after-restore bug, can't be
serialized), convert to a computed `get()` that recomputes from the now-restored sources.

### ⚠️ One adaptation needed for the library
`SerializableMutableState` below uses `BuildConfig.DEBUG` to fail-fast when handed a non-null,
non-`Serializable` value (which would silently restore as null and recreate the NPE). In a **published
library**, `BuildConfig.DEBUG` is always `false` in consumers, so the guard would never fire. Pick one:
- **Recommended:** just `throw` unconditionally on a non-null non-`Serializable` value (it's a
  programming error to pass one), dropping the `BuildConfig.DEBUG` gate; or
- gate on a library-level opt-in flag if you want it silenceable.

The version below is shown as-is from WenWe; adjust the `writeObject` guard per the above.

### Source (repackaged) — `SerializableMutableState`
```kotlin
package uk.co.appoly.droid.compose.extensions

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.NotSerializableException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * A [Serializable] [MutableState] that persists & restores its value across process death.
 * See the proposed-features doc / WENWE-ANDROID-5G for background. Implements [MutableState] by
 * delegating to a `@Transient` `mutableStateOf`, so it's a drop-in replacement.
 *
 * The value is persisted only when it is itself [Serializable] (or null); a non-serializable value
 * restores as null (safe only for a nullable T). Main-thread access only.
 */
class SerializableMutableState<T>(initial: T) : MutableState<T>, Serializable {

	@Transient
	private var delegate: MutableState<T> = mutableStateOf(initial)

	override var value: T
		get() = delegate.value
		set(value) { delegate.value = value }

	override fun component1(): T = delegate.value
	override fun component2(): (T) -> Unit = { delegate.value = it }

	private fun writeObject(out: ObjectOutputStream) {
		out.defaultWriteObject()
		val current = delegate.value
		// TODO(lib): BuildConfig.DEBUG is meaningless in a published lib — throw unconditionally
		// on a non-null non-Serializable value instead (see doc). WenWe original:
		//   if (BuildConfig.DEBUG && current != null && current !is Serializable) throw ...
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

	companion object { private const val serialVersionUID: Long = 1L }
}

/** Creates a [Serializable] [MutableState] that survives process death. */
fun <T> serializableMutableStateOf(initial: T): SerializableMutableState<T> = SerializableMutableState(initial)
```

### Source (repackaged) — `TransientMutableState`
```kotlin
package uk.co.appoly.droid.compose.extensions

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.Serializable

/**
 * A [Serializable] [MutableState] whose value resets to [initial] after a process-death restore,
 * rather than being persisted. Sibling of [SerializableMutableState]; for ephemeral state that
 * should NOT survive process death. Only [initial] is persisted (must be Serializable or null).
 * Main-thread access only.
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
```

### Tests to add
- Round-trip `SerializableMutableState` through `ObjectOutputStream`/`ObjectInputStream`: value
  preserved; non-null non-Serializable value → throws (per chosen guard); null value survives.
- Round-trip `TransientMutableState`: value resets to `initial`.
- Both: `.value` get/set and `by` delegation behave like `mutableStateOf`.

---

## Migrate WenWe once released

Once these ship in a toolbox release and WenWe bumps to it:

1. Bump the AppolyDroid dependency in WenWe.
2. Delete the local copies:
   - `app/src/main/java/uk/co/wenwe/util/PagingExt.kt`
   - `app/src/main/java/uk/co/wenwe/util/SerializableMutableState.kt`
   - `app/src/main/java/uk/co/wenwe/util/TransientMutableState.kt`
3. Repoint imports:
   - `uk.co.wenwe.util.distinctBy` → `uk.co.appoly.droid.util.paging.distinctBy`
   - `uk.co.wenwe.util.{serializable,transient}MutableStateOf` → `uk.co.appoly.droid.compose.extensions.*`
4. Call sites that use `distinctBy`: `WenWeContributorsScreenModel`, `ViewContributorsBottomSheet`
   (both currently reverted/optional — check current state). State delegates: ~19 Screen files
   swept in the WENWE-76 line (grep `serializableMutableStateOf` / `transientMutableStateOf`).
5. Rebuild + the same on-device process-death smoke test (Developer Options → "Don't keep
   activities", background/foreground a Screen, confirm no NPE).
