package uk.co.appoly.droid.barcodescanner

import android.content.Context
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

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
	 * Pre-installs the Play services scanner module, suspending until it is genuinely ready, so
	 * that the first [scan] opens immediately instead of sitting on a download spinner for several
	 * seconds.
	 *
	 * Call it from a screen the user reaches before they need to scan — app start, or the screen
	 * hosting the scan button. Safe to call repeatedly; it returns straight away once the module
	 * is present. On a fresh device the first call can take several seconds and needs a network,
	 * so do not block your UI on it.
	 *
	 * Purely an optimisation: [scan] performs the same check itself, so skipping this costs
	 * latency on the first scan, never correctness.
	 *
	 * @return true if the module is installed and ready to use, false if the install could not be
	 * done (no Play services, no network, or the user cancelled it). A false here means [scan]
	 * will likely return [OneShotScanResult.Unavailable] until the situation changes.
	 */
	suspend fun warmUp(): Boolean = ensureModuleInstalled()

	/**
	 * Suspends until the Play services scanner module is installed, or the install reaches a
	 * terminal failure.
	 *
	 * The subtlety that makes this more than a one-liner: `installModules().await()` resolves when
	 * Play services *accepts* the request, not when the download finishes. Treating that as "ready"
	 * launches the scanner against a module that has not registered yet — Play services logs
	 * "No registered Chimera impl" and fails the scan with a generic `INTERNAL` error that is
	 * indistinguishable from a real scan failure. Completion is only observable through an
	 * [InstallStatusListener] on the request.
	 *
	 * There is no built-in timeout: a slow download is still a legitimate install. Callers that
	 * cannot wait should wrap the call in `withTimeout`, which cancels cleanly.
	 */
	private suspend fun ensureModuleInstalled(): Boolean {
		val scannerClient = client
		return try {
			val moduleInstall = ModuleInstall.getClient(appContext)
			if (moduleInstall.areModulesAvailable(scannerClient).await().areModulesAvailable()) {
				return true
			}
			suspendCancellableCoroutine { continuation ->
				// installModules' own callbacks and the listener race each other, and resuming a
				// continuation twice throws. First one through wins.
				val settled = AtomicBoolean(false)
				lateinit var listener: InstallStatusListener

				fun settle(installed: Boolean) {
					if (settled.compareAndSet(false, true)) {
						moduleInstall.unregisterListener(listener)
						continuation.resume(installed)
					}
				}

				listener = InstallStatusListener { update ->
					when (update.installState) {
						ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> settle(true)
						ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
						ModuleInstallStatusUpdate.InstallState.STATE_CANCELED,
							-> settle(false)
						// PENDING / DOWNLOADING / INSTALLING / DOWNLOAD_PAUSED: keep waiting.
					}
				}

				continuation.invokeOnCancellation {
					if (settled.compareAndSet(false, true)) {
						moduleInstall.unregisterListener(listener)
					}
				}

				moduleInstall.installModules(
					ModuleInstallRequest.newBuilder()
						.addApi(scannerClient)
						.setListener(listener)
						.build(),
				)
					.addOnSuccessListener { response ->
						// Nothing left to download means no listener callback will ever arrive,
						// so this is the only thing that can resume the continuation.
						if (response.areModulesAlreadyInstalled()) settle(true)
					}
					.addOnFailureListener { settle(false) }
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
		// Not merely an optimisation. Launching the scanner before the module has registered makes
		// Play services fail with a generic INTERNAL error, so [warmUp] only moves this cost
		// earlier — it is not the thing that makes scanning correct.
		if (!ensureModuleInstalled()) {
			return OneShotScanResult.Unavailable(
				IllegalStateException(
					"The Play services barcode scanner module is not installed and could not be " +
						"installed (no Play services, or no network).",
				),
			)
		}
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
