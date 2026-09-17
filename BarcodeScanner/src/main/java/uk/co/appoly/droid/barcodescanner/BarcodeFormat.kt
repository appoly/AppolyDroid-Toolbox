package uk.co.appoly.droid.barcodescanner

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * The symbologies this toolbox can decode.
 *
 * This enum exists so that app code never has to import `com.google.mlkit.*`. Both
 * [OneShotBarcodeScanner] and the `BarcodeScanner-Camera` module report results as
 * [ScannedBarcode], which carries one of these instead of an ML Kit `Barcode.FORMAT_*` int.
 *
 * @property mlKitFormat the corresponding `Barcode.FORMAT_*` constant, used when building
 * scanner options.
 */
enum class BarcodeFormat(val mlKitFormat: Int) {
	Code128(Barcode.FORMAT_CODE_128),
	Code39(Barcode.FORMAT_CODE_39),
	Code93(Barcode.FORMAT_CODE_93),
	Codabar(Barcode.FORMAT_CODABAR),
	Ean13(Barcode.FORMAT_EAN_13),
	Ean8(Barcode.FORMAT_EAN_8),
	Itf(Barcode.FORMAT_ITF),
	UpcA(Barcode.FORMAT_UPC_A),
	UpcE(Barcode.FORMAT_UPC_E),
	QrCode(Barcode.FORMAT_QR_CODE),
	Pdf417(Barcode.FORMAT_PDF417),
	Aztec(Barcode.FORMAT_AZTEC),
	DataMatrix(Barcode.FORMAT_DATA_MATRIX),

	/**
	 * A code the detector read but could not classify, or a format added to ML Kit after this
	 * enum was written. [mlKitFormat] is `Barcode.FORMAT_UNKNOWN`, so passing `Unknown` in a
	 * format filter is meaningless — it is only ever a result value.
	 */
	Unknown(Barcode.FORMAT_UNKNOWN),
	;

	companion object {
		private val byMlKitFormat: Map<Int, BarcodeFormat> = entries.associateBy { it.mlKitFormat }

		/**
		 * Maps a `com.google.mlkit.vision.barcode.common.Barcode.FORMAT_*` int to its enum
		 * constant, falling back to [Unknown] for anything unrecognised.
		 */
		fun fromMlKit(format: Int): BarcodeFormat = byMlKitFormat[format] ?: Unknown
	}
}

/**
 * Ready-made [BarcodeFormat] sets for the `formats` parameter of the scanners.
 *
 * Narrowing the set is worth doing: the fewer symbologies the detector has to consider, the
 * faster and more reliably it locks onto the one you actually want.
 */
object BarcodeFormats {
	/** Every format this toolbox understands. The default for both scanners. */
	val All: Set<BarcodeFormat> = setOf(
		BarcodeFormat.Code128,
		BarcodeFormat.Code39,
		BarcodeFormat.Code93,
		BarcodeFormat.Codabar,
		BarcodeFormat.Ean13,
		BarcodeFormat.Ean8,
		BarcodeFormat.Itf,
		BarcodeFormat.UpcA,
		BarcodeFormat.UpcE,
		BarcodeFormat.QrCode,
		BarcodeFormat.Pdf417,
		BarcodeFormat.Aztec,
		BarcodeFormat.DataMatrix,
	)

	/** Linear symbologies — retail and logistics labels. */
	val OneDimensional: Set<BarcodeFormat> = setOf(
		BarcodeFormat.Code128,
		BarcodeFormat.Code39,
		BarcodeFormat.Code93,
		BarcodeFormat.Codabar,
		BarcodeFormat.Ean13,
		BarcodeFormat.Ean8,
		BarcodeFormat.Itf,
		BarcodeFormat.UpcA,
		BarcodeFormat.UpcE,
	)

	/** Matrix symbologies — QR and friends. */
	val TwoDimensional: Set<BarcodeFormat> = setOf(
		BarcodeFormat.QrCode,
		BarcodeFormat.Pdf417,
		BarcodeFormat.Aztec,
		BarcodeFormat.DataMatrix,
	)

	/** QR codes only — the narrowest and fastest common case. */
	val QrOnly: Set<BarcodeFormat> = setOf(BarcodeFormat.QrCode)
}

/**
 * Folds a set of formats into the `(first, vararg rest)` int pair that both ML Kit's
 * `BarcodeScannerOptions.Builder` and `GmsBarcodeScannerOptions.Builder` expect.
 *
 * An empty set, or one containing only [BarcodeFormat.Unknown], means "no useful filter" and
 * maps to `Barcode.FORMAT_ALL_FORMATS`.
 */
internal fun Set<BarcodeFormat>.toMlKitFormatArgs(): Pair<Int, IntArray> {
	val formats = filter { it != BarcodeFormat.Unknown }.map { it.mlKitFormat }
	if (formats.isEmpty()) return Barcode.FORMAT_ALL_FORMATS to IntArray(0)
	return formats.first() to formats.drop(1).toIntArray()
}
