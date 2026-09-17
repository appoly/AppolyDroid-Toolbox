package uk.co.appoly.droid.barcodescanner

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * One decoded barcode, in toolbox terms rather than ML Kit's.
 *
 * @property rawValue the barcode's contents exactly as encoded. Never blank — a barcode that
 * decodes to nothing is not reported at all.
 * @property format the symbology it was encoded in.
 * @property displayValue ML Kit's human-readable rendering, where it has one (it strips the
 * `WIFI:`/`tel:`-style scheme prefixes from structured QR payloads, for instance). Null when
 * ML Kit offers nothing better than [rawValue] — including when it would simply repeat it.
 *
 * Reach for `displayValue ?: rawValue` when showing a code to a user, and for [rawValue] alone
 * when matching against your own data. What you should not write is `displayValue?.let { … }`:
 * null is the common case, not the exceptional one, so that quietly does nothing for most codes.
 * A migrating app was found dropping every scanned serial that way — the scan succeeded, the
 * scanner closed, and no value ever appeared.
 */
data class ScannedBarcode(
	val rawValue: String,
	val format: BarcodeFormat,
	val displayValue: String? = null,
)

/**
 * Converts an ML Kit [Barcode] into a [ScannedBarcode], or null when it carries no usable
 * payload (`rawValue` absent or blank).
 *
 * Public because the `BarcodeScanner-Camera` module's analyzer needs it across the module
 * boundary; app code should not normally have an ML Kit [Barcode] in hand to convert.
 */
fun Barcode.toScannedBarcode(): ScannedBarcode? {
	val raw = rawValue?.takeIf { it.isNotBlank() } ?: return null
	return ScannedBarcode(
		rawValue = raw,
		format = BarcodeFormat.fromMlKit(format),
		displayValue = displayValue?.takeIf { it.isNotBlank() && it != raw },
	)
}
