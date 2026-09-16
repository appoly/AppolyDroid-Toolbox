# BarcodeScanner-Camera

Continuous in-app barcode scanning for Compose: a CameraX preview plus an ML Kit analyzer, wired
together so that 1D formats decode as reliably as QR codes.

Builds on [`BarcodeScanner`](../BarcodeScanner/README.md), which it exposes as `api` — adding this
module gives you the one-shot scanner for free.

## Features

- One `@Composable`; no `AndroidView`, no `PreviewView`
- Binds to the ambient lifecycle, so it works inside a `ModalBottomSheet` and unbinds on exit
- Per-code debouncing, so a code held in frame fires once rather than forty times a second
- Callbacks marshalled to the main thread — touch ViewModel state directly
- Replaceable overlay, with a sensible default reticle
- Torch control
- Declares `CAMERA` and the ML Kit install-time model download in its own manifest

## Installation

```gradle.kts
implementation("uk.co.appoly.droid:barcodescanner-camera:1.10.0-beta01")
```

## Usage

```kotlin
@Composable
fun ScanSheet(viewModel: ScanViewModel) {
    BarcodeScannerCamera(
        modifier = Modifier.fillMaxSize(),
        formats = BarcodeFormats.OneDimensional,
        onError = viewModel::onScannerFailed,
        onBarcodeScanned = { barcode ->
            viewModel.onCodeScanned(barcode.rawValue, barcode.format)
        },
    )
}
```

### Permission

**This composable does not request the `CAMERA` permission.** The manifest declaration merges into
your app, but asking for it is yours to do — every app already has a permission flow and no two
are alike. Check before composing:

```kotlin
val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
    PackageManager.PERMISSION_GRANTED

if (granted) {
    BarcodeScannerCamera(onBarcodeScanned = ::onScanned)
} else {
    PermissionPrompt(onGrant = { launcher.launch(Manifest.permission.CAMERA) })
}
```

Composing it without the permission reports a bind failure through `onError` rather than crashing.

### Debouncing

ML Kit reports every barcode in frame on every analysed frame. `debounceWindow` (2.5s by default)
suppresses a repeat of the *same* raw value for that long — per code, so two labels in shot each
fire once rather than alternating every frame.

If you already de-duplicate against state that outlives the composable — a ViewModel keyed on
codes already collected, say — turn it off and do it yourself:

```kotlin
BarcodeScannerCamera(
    debounceWindow = null,
    onBarcodeScanned = viewModel::onCodeScanned,
)
```

### Custom overlay

The `overlay` lambda is scoped to the preview's `Box`, so `Modifier.align` is available:

```kotlin
BarcodeScannerCamera(
    overlay = {
        Text(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            text = "Point at the label on the box",
            color = Color.White,
        )
    },
    onBarcodeScanned = ::onScanned,
)
```

Pass `overlay = {}` for a bare preview. The default `DefaultScanFrame()` is decoration only — the
detector reads the whole frame, so a code outside the reticle still scans.

### Torch

```kotlin
var torchOn by remember { mutableStateOf(false) }

BarcodeScannerCamera(
    torchEnabled = torchOn,
    onBarcodeScanned = ::onScanned,
)
```

Silently ignored on a camera with no flash unit.

## API

| Type | Purpose |
|---|---|
| `BarcodeScannerCamera` | The scanning preview composable |
| `LensFacing` | `Back` / `Front` |
| `DefaultScanFrame` | The default overlay reticle; usable standalone |

Results arrive as `ScannedBarcode` from the `BarcodeScanner` module.

## Why this rather than a wrapper library

The common off-the-shelf wrappers close each camera frame *before* ML Kit has read it, so 1D
formats only decode by winning a thread race. This module holds the `ImageProxy` open until
`process()` completes and closes it in `addOnCompleteListener` — that single detail is most of the
reason it exists.

It also deliberately avoids `camera-view`, `camera-video` and `camera-mlkit-vision`:
`CameraXViewfinder` replaces `PreviewView`, and `MlKitAnalyzer` would drag in the other two to
replace a fifteen-line class.

## On-device test suite

The module ships a small instrumented suite covering the bind/unbind lifecycle. It is
**deliberately not run in CI** — it needs a real camera, which no CI runner has. Run it before
tagging a release:

```bash
./gradlew :BarcodeScanner-Camera:connectedDebugAndroidTest
```

It proves the composable binds without error and survives repeated mount/unmount cycles — the
regression surface that actually bites, since closing the detector while a frame is in flight
throws from the analysis thread rather than reporting through `onError`. Verified on a Pixel 9 Pro
Fold (Android 17) and a OnePlus 6T (Android 11).

The suite asserts the `CAMERA` grant rather than using `GrantPermissionRule`: that rule opens a
UiAutomation connection unconditionally and dies with "UiAutomationService ... already registered"
on a device that already holds one, even when the permission is granted. The install grants it; if
you see the assertion fire, the message tells you the `adb shell pm grant` to run.

## Notes

- The ML Kit model is served by Play services, not bundled — the module adds no multi-megabyte
  model to your APK. The manifest asks Play services to fetch it at install time.
- On a device with no Play services the detector is unavailable and `onError` fires; the camera
  preview itself still works.
