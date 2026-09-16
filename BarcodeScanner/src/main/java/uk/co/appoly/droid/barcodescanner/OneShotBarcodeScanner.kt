package uk.co.appoly.droid.barcodescanner

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

/**
 * Decides what a [CancellationException] out of a Play services `Task` actually means.
 *
 * Play services reports "the user backed out of the scanner" by *cancelling the Task*, which
 * `await()` surfaces as a [CancellationException] — an ordinary outcome that must be turned into a
 * result, not rethrown. Our own caller being cancelled arrives as the same exception type and must
 * propagate, or a cancelled screen would silently be treated as a user decision.
 *
 * [kotlinx.coroutines.ensureActive] throws only in the second case, so returning normally from
 * here means "the user cancelled".
 *
 * Extracted and internal so the distinction is unit-testable: getting it backwards makes
 * [OneShotScanResult.Cancelled] unreachable, which no compiler warns about.
 */
internal suspend fun awaitUserCancellation() {
	currentCoroutineContext().ensureActive()
}

/** The outcome of a single [OneShotBarcodeScanner.scan] call. */
sealed interface OneShotScanResult {
	/** The user scanned something. */
	data class Scanned(val barcode: ScannedBarcode) : OneShotScanResult

	/** The user backed out of the scanner UI without scanning. */
	data object Cancelled : OneShotScanResult

	/**
	 * Play services is missing, too old, or the scanner module could not be installed.
	 *
	 * Handle this branch: it is the everyday reality on Huawei devices and stripped ROMs, where
	 * the hosted scanner simply does not exist. Fall back to `BarcodeScanner-Camera`, or to
	 * manual entry.
	 */
	data class Unavailable(val cause: Throwable?) : OneShotScanResult

	/** The scan failed for any other reason. */
	data class Failed(val cause: Throwable) : OneShotScanResult
}

/**
 * A single barcode scan, rendered by Google Play services rather than by your app.
 *
 * Play services owns the camera, the preview and the permission prompt, so this costs you no
 * `CAMERA` permission in the manifest, no layout, and no CameraX on your classpath. The trade is
 * that it only exists where Play services does — see [OneShotScanResult.Unavailable] — and that
 * you get Google's UI, not yours. For in-app continuous scanning, use the `BarcodeScanner-Camera`
 * module instead.
 *
 * The instance is cheap and stateless; construct it wherever it is convenient.
 *
 * ```kotlin
 * val scanner = OneShotBarcodeScanner(context, formats = BarcodeFormats.QrOnly)
 *
 * when (val result = scanner.scan()) {
 *     is OneShotScanResult.Scanned -> onCode(result.barcode.rawValue)
 *     OneShotScanResult.Cancelled -> Unit
 *     is OneShotScanResult.Unavailable -> fallBackToManualEntry()
 *     is OneShotScanResult.Failed -> showError(result.cause)
 * }
 * ```
 *
 * @param context any context; the application context is retained internally.
 * @param formats which symbologies to look for. Narrower is faster — see [BarcodeFormats].
 * @param allowManualInput show Google's "enter the code by hand" affordance. Off by default.
 * @param autoZoom let the scanner zoom onto small or distant codes. On by default.
 */
class OneShotBarcodeScanner(
	context: Context,
	formats: Set<BarcodeFormat> = BarcodeFormats.All,
	allowManualInput: Boolean = false,
	autoZoom: Boolean = true,
) {
	private val appContext = context.applicationContext

	private val options: GmsBarcodeScannerOptions = GmsBarcodeScannerOptions.Builder()
		.apply {
			val (first, rest) = formats.toMlKitFormatArgs()
			setBarcodeFormats(first, *rest)
			if (allowManualInput) allowManualInput()
			if (autoZoom) enableAutoZoom()
		}
		.build()

	private val client get() = GmsBarcodeScanning.getClient(appContext, options)

	/**
	 * Pre-installs the Play services scanner module so the first [scan] opens immediately
	 * instead of sitting on a download spinner for several seconds.
	 *
	 * Call it from a screen the user reaches before they need to scan — app start, or the screen
	 * hosting the scan button. Safe to call repeatedly; it is a no-op once the module is present.
	 *
	 * @return true if the module is installed and ready, false if the install could not be done
	 * (no Play services, no network). A false here does not mean [scan] will fail — it will just
	 * be slower, or return [OneShotScanResult.Unavailable].
	 */
	suspend fun warmUp(): Boolean {
		val scannerClient = client
		return try {
			val moduleInstall = ModuleInstall.getClient(appContext)
			val availability = moduleInstall.areModulesAvailable(scannerClient).await()
			if (availability.areModulesAvailable()) {
				true
			} else {
				val request = ModuleInstallRequest.newBuilder()
					.addApi(scannerClient)
					.build()
				moduleInstall.installModules(request).await()
				true
			}
		} catch (cancellation: CancellationException) {
			// A cancelled install Task is a failed warm-up, not a reason to cancel whoever called
			// us. Only a genuinely cancelled caller propagates.
			awaitUserCancellation()
			false
		} catch (_: Exception) {
			false
		}
	}

	/**
	 * Opens the Play services scanner UI and suspends until the user scans, backs out, or it
	 * fails.
	 *
	 * Cancelling the calling coroutine does not close the scanner UI — Play services owns that
	 * activity. The result is simply discarded.
	 */
	suspend fun scan(): OneShotScanResult {
		val barcode: Barcode = try {
			client.startScan().await()
		} catch (cancellation: CancellationException) {
			awaitUserCancellation()
			return OneShotScanResult.Cancelled
		} catch (error: MlKitException) {
			return error.toScanResult()
		} catch (error: Exception) {
			return OneShotScanResult.Failed(error)
		}
		val scanned = barcode.toScannedBarcode()
			?: return OneShotScanResult.Failed(IllegalStateException("Scanner returned a barcode with no raw value"))
		return OneShotScanResult.Scanned(scanned)
	}

	private fun MlKitException.toScanResult(): OneShotScanResult = when (errorCode) {
		MlKitException.CODE_SCANNER_CANCELLED -> OneShotScanResult.Cancelled
		MlKitException.UNAVAILABLE,
		MlKitException.CODE_SCANNER_UNAVAILABLE,
		MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD,
			-> OneShotScanResult.Unavailable(this)

		else -> OneShotScanResult.Failed(this)
	}
}
