# BarcodeScanner

Barcode scanning without the ML Kit imports: a shared result model, and a one-shot scanner backed
by the Google Play services code scanner — no camera permission, no CameraX, no bundled model.

For continuous in-app scanning with your own UI around it, add
[`BarcodeScanner-Camera`](../BarcodeScanner-Camera/README.md), which builds on this module.

## Features

- `ScannedBarcode` / `BarcodeFormat` — one result type shared by both scanning modules, so app
  code never imports `com.google.mlkit.*`
- `OneShotBarcodeScanner` — a single scan in Play services' own UI, as a `suspend fun`
- `warmUp()` to pre-install the scanner module, so the first scan is not a download spinner
- An explicit `Unavailable` result for devices with no (or outdated) Play services, instead of a
  silent failure
- Costs the consumer no `CAMERA` permission: Play services owns the camera and the prompt

## Installation

```gradle.kts
implementation("uk.co.appoly.droid:barcodescanner:1.10.0")
```

## Usage

### A single scan

```kotlin
class CheckInViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = OneShotBarcodeScanner(
        context = application,
        formats = BarcodeFormats.QrOnly,
    )

    fun onScanClicked() {
        viewModelScope.launch {
            when (val result = scanner.scan()) {
                is OneShotScanResult.Scanned -> checkIn(result.barcode.rawValue)
                OneShotScanResult.Cancelled -> Unit
                is OneShotScanResult.Unavailable -> showManualEntry()
                is OneShotScanResult.Failed -> showError(result.cause)
            }
        }
    }
}
```

### Warming up

The first `scan()` on a device that has never used the hosted scanner downloads a Play services
module, which can take several seconds. Call `warmUp()` from a screen the user reaches *before*
they need to scan, and they never see it:

```kotlin
LaunchedEffect(Unit) {
    scanner.warmUp()
}
```

It suspends until the module is genuinely installed, is safe to call repeatedly, and returns
`false` rather than throwing when the install cannot be done.

`warmUp()` is **purely an optimisation** — `scan()` performs the same check itself and waits if it
has to, so skipping it costs latency on the first scan, never correctness. Do not block your UI on
it: on a fresh device it needs a network and several seconds.

> Under the hood this is more than one call, because `installModules().await()` resolves when Play
> services *accepts* the request rather than when the download completes. Launching the scanner at
> that point hits a module that has not registered yet, and Play services fails the scan with a
> generic `INTERNAL` error. Completion is only observable via an `InstallStatusListener`, which is
> what both `warmUp()` and `scan()` wait on.

### Choosing formats

Narrowing the format set makes the detector faster and less prone to locking onto the wrong code:

```kotlin
BarcodeFormats.All              // every supported symbology (the default)
BarcodeFormats.OneDimensional   // Code128, Code39, Code93, Codabar, EAN-13/8, ITF, UPC-A/E
BarcodeFormats.TwoDimensional   // QR, PDF417, Aztec, Data Matrix
BarcodeFormats.QrOnly           // just QR
setOf(BarcodeFormat.Ean13, BarcodeFormat.UpcA)  // or roll your own
```

## Handling `Unavailable`

The hosted scanner lives in Play services, so it does not exist on Huawei devices, stripped ROMs,
or installs with a Play services too old to serve it. That is a real slice of real users, and the
sealed result makes it impossible to forget.

**`Unavailable` means the device genuinely cannot scan**, not "it did not work this time". The
distinction is enforced rather than assumed: the module asks Play services about its own
availability instead of inferring it from a scanner error code, because Play services reports the
same `CODE_SCANNER_UNAVAILABLE` for a device that can never scan *and* for a perfectly capable one
whose freshly installed scanner components have not been enabled yet. A first-run race, a missing
network or an update in progress all report `Failed`, so you will never tell a first-time user on a
good phone that their phone cannot do this.

The first scan on a fresh device also retries briefly while Play services finishes enabling the
scanner, so that race is usually invisible to you.

### "We ship through Play, so this can't happen"

It still can, and the branch is worth keeping. Shipping only through the Play Store rules out
*installing* on a device with no Play services — it does not rule out:

- **Play services disabled.** A user can disable it in system settings on an app that installed
  fine months earlier.
- **Play services too old.** `SERVICE_VERSION_UPDATE_REQUIRED` on a neglected or long-offline
  device is the single most likely way you will see this in the wild.
- **Enterprise or MDM distribution**, which bypasses Play entirely.

So treat `Unavailable` as rare rather than impossible. It needs a sane message and, ideally, a
manual-entry path — not a `TODO`.


```kotlin
is OneShotScanResult.Unavailable -> {
    // Fall back to BarcodeScanner-Camera, which needs only CameraX and the CAMERA permission,
    // or to typing the code in by hand.
}
```

## Migrating from an older scanner

One hazard worth knowing, found in a real migration. Older ML Kit and Mobile Vision code often
reads the display value defensively:

```kotlin
barcode.displayValue?.let { onSerial(it) }   // silently does nothing for most codes
```

`displayValue` is null whenever ML Kit has nothing better to offer than the raw contents, which is
the *common* case for plain serials and part numbers — so that line drops the scan entirely. The
scanner opens, decodes, closes, and no value ever arrives, with no error anywhere. Use:

```kotlin
val shown = barcode.displayValue ?: barcode.rawValue   // for display
val key   = barcode.rawValue                           // for matching your own data
```

`rawValue` is guaranteed non-blank, so it is always a safe fallback.

## Don't branch on error codes

`Failed` wraps whatever Play services threw, usually an `MlKitException`. Resist reading meaning
into its `errorCode`: Play services reports `INTERNAL` (13) for genuinely unrelated problems — a
scanner module that has not registered yet, a camera delivering no frames, and others. During this
module's first integration, two separate investigations were sent the wrong way by assuming that
code meant one specific thing.

`Unavailable` is the only result that carries a reliable meaning, so make product decisions there.
Treat `Failed` as "retry or tell the user", and log `cause` for diagnosis rather than switching on
it.

## API

| Type | Purpose |
|---|---|
| `ScannedBarcode` | `rawValue`, `format`, optional `displayValue` |
| `BarcodeFormat` | The symbology enum, plus `fromMlKit(Int)` and `mlKitFormat` |
| `BarcodeFormats` | `All`, `OneDimensional`, `TwoDimensional`, `QrOnly` |
| `OneShotBarcodeScanner` | `suspend fun warmUp(): Boolean`, `suspend fun scan(): OneShotScanResult` |
| `OneShotScanResult` | `Scanned` / `Cancelled` / `Unavailable` / `Failed` |
| `Barcode.toScannedBarcode()` | ML Kit → toolbox conversion; returns null for an empty payload |

## Notes

- `barcode-scanning-common` is an `api` dependency — it is ~50KB of format constants and
  interfaces, and carries no detection model.
- `scan()` does not close the scanner UI if the calling coroutine is cancelled; Play services owns
  that activity. The result is simply discarded.
