# Proposed: paging de-dupe + serialization-safe Compose state + clipboard copier

**Status:** TODO / proposed — not yet implemented.
**Origin:** WenWe Android (#1, #2) and Accelerate Android (#3), July 2026. The WenWe utilities were
written to fix production crashes (Sentry `WENWE-ANDROID-5H` and `WENWE-ANDROID-5G`); the clipboard
copier came out of the Accelerate migration off the deprecated `LocalClipboardManager`. All are
fully generic — nothing app-specific — so they belong in the toolbox. This doc captures them ready
to lift in.

Three independent additions:
1. `Flow<PagingData<T>>.distinctBy { }` → **PagingExtensions** module.
2. `SerializableMutableState` / `TransientMutableState` (+ factories) → **ComposeExtensions** module.
3. `ClipboardCopier` + `rememberClipboardCopier()` + `copyX` extensions → **ComposeExtensions** module.

Once released, update the originating apps to depend on the library versions and delete their local
copies (see [Migrate WenWe once released](#migrate-wenwe-once-released) and
[Migrate Accelerate once released](#migrate-accelerate-once-released)).

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

## 3. Clipboard copier — ComposeExtensions

**Module:** `:ComposeExtensions`  ·  **Package:** `uk.co.appoly.droid.compose.extensions`
(deps: compose runtime + compose ui + coroutines; all already on the module.)

### Why

`androidx.compose.ui.platform.LocalClipboardManager` is deprecated in favour of `LocalClipboard`,
whose `Clipboard.setClipEntry(…)` is a **suspend** function. That turns a one-line copy into a small
pile of plumbing every call site has to repeat correctly: grab `LocalClipboard` + a
`rememberCoroutineScope()`, `launch`, build a `ClipEntry`, and — because a "copied!" confirmation
should only fire once the write actually returns — order the toast after the await. There's also an
Android-13 wrinkle: API 33+ shows its own clipboard confirmation, so an app's own toast must be
gated to `< TIRAMISU` to avoid a double confirmation. This centralises all of it.

`ClipboardCopier` takes a raw `ClipEntry`, so it handles any payload (text, HTML, URIs, intents,
multi-item); the `copyX` extensions mirror the `ClipData.newX` factories for ergonomics. `label` is
kept **required** to match `ClipData.newX` exactly (it's read by clipboard managers / a11y even
though it isn't shown in the modern paste UI); `confirmationMessage` is the library's own addition
and defaults to `null` (no toast).

### Source (repackaged)

```kotlin
package uk.co.appoly.droid.compose.extensions

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.launch

/**
 * Copies a [ClipEntry] to the system clipboard and, on Android < 13, shows a confirmation Toast.
 * Android 13+ surfaces its own clipboard confirmation, so we suppress ours to avoid a double toast.
 *
 * Obtain one with [rememberClipboardCopier]. It centralises the [LocalClipboard] plumbing:
 * `setClipEntry` is a suspend function, so each copy runs on a remembered scope and the confirmation
 * only fires once the write has returned. The payload is a raw [ClipEntry], so it handles text, a
 * URI, multiple items, etc.; use [copyPlainText] for the common plain-text case.
 */
fun interface ClipboardCopier {
	/**
	 * @param clipEntry the payload to copy.
	 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
	 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
	 */
	fun copy(clipEntry: ClipEntry, confirmationMessage: String?)
}

/**
 * Copies plain [text] to the clipboard — the common case (wraps [ClipData.newPlainText]).
 *
 * @param text the text to copy.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyPlainText(
	text: CharSequence,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newPlainText(label, text).toClipEntry(), confirmationMessage)

/**
 * Copies styled [htmlText] to the clipboard, with [text] as the plain-text fallback for consumers
 * that can't render HTML (wraps [ClipData.newHtmlText]).
 *
 * @param text the plain-text representation.
 * @param htmlText the HTML-markup representation.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyHtmlText(
	text: CharSequence,
	htmlText: String,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newHtmlText(label, text, htmlText).toClipEntry(), confirmationMessage)

/**
 * Copies a raw [uri] to the clipboard without resolving it through a [ContentResolver]
 * (wraps [ClipData.newRawUri]). Use for URIs that aren't `content://` provider URIs — e.g. an
 * `http`/`https` link or a `mailto:` address; for content URIs use [copyUri] instead.
 *
 * @param uri the URI to copy verbatim.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyRawUri(
	uri: Uri,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newRawUri(label, uri).toClipEntry(), confirmationMessage)

/**
 * Copies a `content://` [uri] to the clipboard, querying its available MIME types from [resolver]
 * so pasting apps receive the right type (wraps [ClipData.newUri]). For plain web/mail URIs prefer
 * [copyRawUri].
 *
 * @param resolver resolves the URI's MIME types.
 * @param uri the content URI to copy.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyUri(
	resolver: ContentResolver,
	uri: Uri,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newUri(resolver, label, uri).toClipEntry(), confirmationMessage)

/**
 * Copies an [intent] to the clipboard (wraps [ClipData.newIntent]) — e.g. a launcher shortcut.
 *
 * @param intent the Intent to copy.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyIntent(
	intent: Intent,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newIntent(label, intent).toClipEntry(), confirmationMessage)

/**
 * Remembers a [ClipboardCopier] bound to the current [LocalClipboard] / [LocalContext] and a
 * composition-scoped coroutine scope. Call one of the `copyX` extensions (e.g. [copyPlainText]) on
 * the result to copy — see [ClipboardCopier] for the confirmation-Toast behaviour.
 */
@Composable
fun rememberClipboardCopier(): ClipboardCopier {
	val clipboard = LocalClipboard.current
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	return remember(clipboard, context, scope) {
		ClipboardCopier { clipEntry, confirmationMessage ->
			scope.launch {
				clipboard.setClipEntry(clipEntry)
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && confirmationMessage != null) {
					Toast.makeText(context, confirmationMessage, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}
}
```

### Tests to add

- `copyPlainText` builds a plain-text `ClipEntry` and calls `copy` with the given confirmation.
- Android < 13 shows the toast; API 33+ suppresses it (Robolectric `@Config(sdk = …)` on both sides
  of `TIRAMISU`).
- Confirmation fires only after `setClipEntry` returns (ordering), and not at all when `null`.
- Consider a Compose test that `rememberClipboardCopier()` copies via `LocalClipboard`.

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

---

## Migrate Accelerate once released

Once the clipboard copier (#3) ships in a toolbox release and Accelerate bumps to it:

1. Bump the AppolyDroid dependency in Accelerate.
2. Delete the local copy:
    - `app/src/main/java/uk/co/accelerate/ui/extensions/ClipboardCopy.kt`
3. Repoint imports:
    - `uk.co.accelerate.ui.extensions.{rememberClipboardCopier, copyPlainText, …}` →
      `uk.co.appoly.droid.compose.extensions.*`
4. Call sites (grep `rememberClipboardCopier` / `copyPlainText`): `KerbsideScreen` (perk promo-code
   redeem) and `WalletCodeScreen` (gift-card code copy).
5. Rebuild + tap-to-copy smoke test on an Android < 13 device/emulator (confirm the toast) and on
   API 33+ (confirm the single system confirmation, no double toast).
