package uk.co.appoly.droid.barcodescanner

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the ML Kit boundary. `BarcodeFormat` exists so that app code never imports
 * `com.google.mlkit.*`, which only holds if the mapping in both directions is exact — a wrong
 * constant here mislabels every scan of that symbology, silently and at runtime.
 */
class BarcodeFormatTest {

	@Test
	fun `every format round-trips through its ML Kit constant`() {
		BarcodeFormat.entries.forEach { format ->
			assertEquals(
				"$format did not survive the round trip",
				format,
				BarcodeFormat.fromMlKit(format.mlKitFormat),
			)
		}
	}

	@Test
	fun `each format maps to a distinct ML Kit constant`() {
		val constants = BarcodeFormat.entries.map { it.mlKitFormat }
		assertEquals(
			"two formats share an ML Kit constant, so fromMlKit cannot be a bijection",
			constants.size,
			constants.toSet().size,
		)
	}

	@Test
	fun `known constants map to the expected formats`() {
		// Spot-checks against the ML Kit constants directly: the round-trip test above passes
		// even if a constant is wired to the wrong enum entry, as long as it is wired consistently.
		assertEquals(BarcodeFormat.Code128, BarcodeFormat.fromMlKit(Barcode.FORMAT_CODE_128))
		assertEquals(BarcodeFormat.Ean13, BarcodeFormat.fromMlKit(Barcode.FORMAT_EAN_13))
		assertEquals(BarcodeFormat.Ean8, BarcodeFormat.fromMlKit(Barcode.FORMAT_EAN_8))
		assertEquals(BarcodeFormat.QrCode, BarcodeFormat.fromMlKit(Barcode.FORMAT_QR_CODE))
		assertEquals(BarcodeFormat.Pdf417, BarcodeFormat.fromMlKit(Barcode.FORMAT_PDF417))
		assertEquals(BarcodeFormat.Aztec, BarcodeFormat.fromMlKit(Barcode.FORMAT_AZTEC))
		assertEquals(BarcodeFormat.DataMatrix, BarcodeFormat.fromMlKit(Barcode.FORMAT_DATA_MATRIX))
		assertEquals(BarcodeFormat.UpcA, BarcodeFormat.fromMlKit(Barcode.FORMAT_UPC_A))
		assertEquals(BarcodeFormat.UpcE, BarcodeFormat.fromMlKit(Barcode.FORMAT_UPC_E))
		assertEquals(BarcodeFormat.Itf, BarcodeFormat.fromMlKit(Barcode.FORMAT_ITF))
		assertEquals(BarcodeFormat.Codabar, BarcodeFormat.fromMlKit(Barcode.FORMAT_CODABAR))
		assertEquals(BarcodeFormat.Code39, BarcodeFormat.fromMlKit(Barcode.FORMAT_CODE_39))
		assertEquals(BarcodeFormat.Code93, BarcodeFormat.fromMlKit(Barcode.FORMAT_CODE_93))
	}

	@Test
	fun `an unrecognised constant maps to Unknown rather than throwing`() {
		// A format added to ML Kit after this enum was written must not crash a scan.
		assertEquals(BarcodeFormat.Unknown, BarcodeFormat.fromMlKit(Int.MAX_VALUE))
		assertEquals(BarcodeFormat.Unknown, BarcodeFormat.fromMlKit(Barcode.FORMAT_UNKNOWN))
	}

	@Test
	fun `All covers every format except Unknown`() {
		// Unknown is a result value, never a filter — including it would be meaningless.
		assertEquals(BarcodeFormat.entries.toSet() - BarcodeFormat.Unknown, BarcodeFormats.All)
		assertFalse("Unknown is a result value, not something to filter on", BarcodeFormat.Unknown in BarcodeFormats.All)
	}

	@Test
	fun `the dimensional sets partition All`() {
		assertEquals(BarcodeFormats.All, BarcodeFormats.OneDimensional + BarcodeFormats.TwoDimensional)
		assertTrue(
			"a format cannot be both 1D and 2D",
			(BarcodeFormats.OneDimensional intersect BarcodeFormats.TwoDimensional).isEmpty(),
		)
	}

	@Test
	fun `QrOnly is the single QR format and a subset of the 2D set`() {
		assertEquals(setOf(BarcodeFormat.QrCode), BarcodeFormats.QrOnly)
		assertTrue(BarcodeFormats.TwoDimensional.containsAll(BarcodeFormats.QrOnly))
	}

	@Test
	fun `a format set folds into first-plus-rest scanner options`() {
		val (first, rest) = setOf(BarcodeFormat.Ean13, BarcodeFormat.QrCode).toMlKitFormatArgs()
		assertEquals(
			setOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_QR_CODE),
			setOf(first) + rest.toSet(),
		)
	}

	@Test
	fun `a single-format set folds to that format with no rest`() {
		val (first, rest) = BarcodeFormats.QrOnly.toMlKitFormatArgs()
		assertEquals(Barcode.FORMAT_QR_CODE, first)
		assertEquals(0, rest.size)
	}

	@Test
	fun `an empty or Unknown-only set falls back to all formats`() {
		// Otherwise the scanner would be built with no formats at all and decode nothing —
		// a silent dead scanner is the worst possible failure here.
		val (emptyFirst, emptyRest) = emptySet<BarcodeFormat>().toMlKitFormatArgs()
		assertEquals(Barcode.FORMAT_ALL_FORMATS, emptyFirst)
		assertEquals(0, emptyRest.size)

		val (unknownFirst, unknownRest) = setOf(BarcodeFormat.Unknown).toMlKitFormatArgs()
		assertEquals(Barcode.FORMAT_ALL_FORMATS, unknownFirst)
		assertEquals(0, unknownRest.size)
	}

	@Test
	fun `Unknown is dropped from a set that also carries real formats`() {
		val (first, rest) = setOf(BarcodeFormat.Unknown, BarcodeFormat.QrCode).toMlKitFormatArgs()
		assertEquals(Barcode.FORMAT_QR_CODE, first)
		assertEquals(0, rest.size)
	}
}
