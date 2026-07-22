# ComposeExtensions

Compose utilities for insets/IME padding, padding arithmetic, serialization-safe `MutableState`, and clipboard copying.

## Features

- Navigation-bar / IME padding helpers (`navigationBarsOrImePadding`, `navigationBarsOrNoneIfImePadding`)
- Keyboard visibility as state (`keyboardAsState`)
- `PaddingValues` addition operators and `hideWithIme` / `gradientTint` modifiers
- Serialization-safe `MutableState` holders for Voyager Screens (`serializableMutableStateOf`, `transientMutableStateOf`)
- Clipboard copier that wraps the suspend `LocalClipboard` API with optional pre-Android-13 toasts

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:ComposeExtensions:1.6.3")
```

## Usage

### Insets and IME padding

Pad content by whichever is larger of the navigation bars or the keyboard:

```kotlin
Column(Modifier.navigationBarsOrImePadding()) { /* … */ }
```

For bottom bars that should lose nav-bar padding when the keyboard is up:

```kotlin
BottomBar(Modifier.navigationBarsOrNoneIfImePadding())
```

Observe keyboard visibility:

```kotlin
val keyboardVisible by keyboardAsState()
```

### Padding helpers

Add two `PaddingValues` (or a `PaddingValues` and `WindowInsets`) together:

```kotlin
val combined = contentPadding + WindowInsets.navigationBars
```

### Serialization-safe Compose state

Voyager `Screen`s are Java-serialized across process death. A plain `mutableStateOf` is not
`Serializable`, and a `@Transient` field restores as `null` (JVM does not run field initializers),
crashing on the next read. Use these drop-in holders instead:

```kotlin
// Persist & restore — one-shot guards where a reset would re-fire an effect
var firstOpen by serializableMutableStateOf(true)

// Reset to initial on restore — ephemeral presentation/event state
var sheetVisible by transientMutableStateOf(false)
```

The value (or initial, for transient) must be `Serializable` or `null`; construction and assignment
fail fast with `IllegalArgumentException` otherwise.

### Clipboard copier

`LocalClipboardManager` is deprecated in favour of `LocalClipboard`, whose `setClipEntry` is suspend.
`rememberClipboardCopier` centralises the scope/await/toast plumbing (and suppresses the app toast on
API 33+ where the system already shows one):

```kotlin
@Composable
fun PromoCodeRow(code: String) {
    val copier = rememberClipboardCopier()
    TextButton(onClick = {
        copier.copyPlainText(
            text = code,
            label = "Promo code",
            confirmationMessage = "Copied!", // toast only on Android < 13
        )
    }) {
        Text("Copy")
    }
}
```

Also available: `copyHtmlText`, `copyRawUri`, `copyUri`, `copyIntent`, or pass a raw `ClipEntry` to
`ClipboardCopier.copy`.

## Dependencies

- Jetpack Compose
- kotlinx-coroutines-android
