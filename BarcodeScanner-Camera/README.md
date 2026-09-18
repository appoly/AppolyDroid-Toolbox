# BarcodeScanner-Camera

Continuous in-app barcode scanning for Compose: a CameraX preview plus an ML Kit analyzer, wired
together so that 1D formats decode as reliably as QR codes.

Builds on [`BarcodeScanner`](../BarcodeScanner/README.md), which it exposes as `api` — adding this
module gives you the one-shot scanner for free.

## Features

- One `@Composable`; no `AndroidView`, no `PreviewView`
- Binds to the ambient lifecycle, so it works inside a `ModalBottomSheet` and unbinds on exit —
  and draws inline there, so the preview clips to the sheet instead of spilling behind it
- A dwell gate, so a code has to be held deliberately rather than glimpsed in passing
- A centre-of-frame acceptance region that the drawn reticle actually matches
- Single- or multi-code tracking, ranked nearest-the-centre first
- Callbacks marshalled to the main thread — touch ViewModel state directly
- Replaceable overlay — a static frame, an animated one that tracks the code, or your own
- Torch control
- Declares `CAMERA` and the ML Kit install-time model download in its own manifest

## Installation

```gradle.kts
implementation("uk.co.appoly.droid:barcodescanner-camera:1.10.0")
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

### Deciding what counts as a scan

ML Kit re-reports every barcode in view on every analysed frame — tens of times a second. Turning
that into "the user scanned this" is [`ScanPolicy`](src/main/java/uk/co/appoly/droid/barcodescanner/camera/ScanPolicy.kt):

```kotlin
BarcodeScannerCamera(
    policy = ScanPolicy(
        mode = ScanMode.Single,              // or Multi
        dwell = 500.milliseconds,            // hold it steady this long
        missTolerance = 750.milliseconds,    // absorb decode flicker
        debounceWindow = 2.5.seconds,        // absence needed before it can scan again
        region = ScanRegion.Reticle(),       // or Full / Visible
    ),
    onBarcodeScanned = ::onScanned,
)
```

The defaults are deliberately not "report everything immediately". A scanner that fires at whatever
drifts through the frame reads as broken to the person holding it — the usual complaint being that
it grabs a code they were not aiming at.

**One presentation is one result.** A held barcode reports once, however long it is held. To report
it again it has to be genuinely absent for `debounceWindow` first — not merely for that long since
it was last reported, which is a different and worse rule that re-fires a code you never put down.

**`Single` locks onto the code nearest the centre** and ignores the rest until it has gone. That is
the case that matters on a label carrying both a 1D tracking code and a QR: picking whichever the
detector happened to list first gets it wrong about half the time.

`ScanPolicy.Immediate` restores the old fire-on-sight behaviour if you want to do your own filtering.

### Where a barcode has to be

`ScanRegion` decides what counts, and the distinction is sharper than it looks: **the image the
analyser sees is wider than the preview the user sees.**

| | |
|---|---|
| `Full` | anything decodable, including barcodes off-screen. Rarely what you want |
| `Visible` | only what is actually on screen |
| `Reticle(widthFraction, aspectRatio)` | only inside the aiming frame — the default |

A barcode counts by the *centre* of its bounding box, so a code bigger than the reticle still scans
when aimed at properly.

Preview and analysis are bound through one CameraX `ViewPort`, which is what makes those two fields
of view agree — and what lets `DefaultScanFrame` draw the exact rectangle the analyser filters
against, so the box on screen and the region that accepts codes cannot drift apart.

**The `ViewPort` takes the preview's own shape**, measured from the bounds you give the composable
rather than assumed. That matters because the shared region is cropped twice on its way to the
screen — the camera crops to the `ViewPort`, and the viewfinder then scales that to *fill* the
bounds and centre-crops whatever overflows. Asking for a shape the layout does not have pays that
toll twice: a fixed 4:3 against a landscape preview left under a third of the frame on screen.
Matching the two means a preview of any shape gets the whole field of view the camera can give it.

What the camera offers is not infinitely divisible, so a little can still be cropped away.
`Visible` and `Reticle` are therefore measured against what is genuinely displayed rather than
against the shared region, which is what makes the first row of the table above literally true.

**Changing the preview's shape rebinds the camera**, which is briefly visible. Rotations and pane
resizes are meant to do that; a preview whose size is *animated* is not, so give it its final size
and animate something else, or accept a rebind each time the shape moves more than about 2%.

### Feedback on a scan

**The module plays nothing — no haptic, no sound.** Deliberately: it knows a barcode was *read*,
never whether it was the right one. Anything it played would have to fire before your callback
could disagree, so an app that validates would produce a confirm buzz followed by its own reject
buzz for a single scan.

Both belong in `onBarcodeScanned`, where the verdict is known:

```kotlin
val haptics = LocalHapticFeedback.current

BarcodeScannerCamera(
    onBarcodeScanned = { barcode ->
        when (viewModel.match(barcode.rawValue)) {
            is Matched -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                sounds.play(R.raw.scan_ok)
            }
            is NoMatch -> {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                sounds.play(R.raw.scan_bad)
            }
        }
    },
)
```

If you only want "I read something" and have no notion of a bad scan, that is one line in the same
place — the point is that it is your call, not ours.

Sound stays with you for its own reasons on top of that one: it needs an asset, an audio stream, a
silent-mode policy and usually a chosen sound to match whatever hardware scanners your users
already know. Four decisions a library should not be making on your behalf.

### Pausing without tearing down

`scanningEnabled = false` keeps the camera bound and the preview live but reports nothing — for
holding a result on screen without the scanner running underneath it. Removing the composable
instead unbinds the camera and flashes the preview on the way back.

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

Pass `overlay = {}` for a bare preview. `DefaultScanFrame()` draws the *resolved* acceptance
region from `ScannerOverlayScope.regionRect`, so what it shows is what the analyser filters
against. The overlay scope also carries the current `detections` with their bounds in preview
pixels and their dwell progress, for drawing something richer than a static box.

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
| `ScanPolicy` | What counts as a scan: dwell, tracking, region |
| `ScanMode` | `Single` / `Multi` |
| `ScanRegion` | `Full` / `Visible` / `Reticle(widthFraction, aspectRatio)` |
| `LensFacing` | `Back` / `Front` |
| `ScannerOverlayScope` | What an overlay can see: `regionRect`, `detections` |
| `DetectedBarcode` | One visible code: bounds, corners, dwell progress |
| `DefaultScanFrame` | The default overlay reticle; usable standalone |
| `AnimatedScanFrame` | A reticle that springs to the code and closes as the dwell fills |

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
