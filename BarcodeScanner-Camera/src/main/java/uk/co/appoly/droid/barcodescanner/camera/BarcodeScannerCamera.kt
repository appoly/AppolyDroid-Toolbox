package uk.co.appoly.droid.barcodescanner.camera

import androidx.annotation.OptIn
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import uk.co.appoly.droid.barcodescanner.BarcodeFormat
import uk.co.appoly.droid.barcodescanner.BarcodeFormats
import uk.co.appoly.droid.barcodescanner.ScannedBarcode
import uk.co.appoly.droid.barcodescanner.toScannedBarcode
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Which camera the scanner binds to. */
enum class LensFacing(internal val selector: CameraSelector) {
	Back(CameraSelector.DEFAULT_BACK_CAMERA),
	Front(CameraSelector.DEFAULT_FRONT_CAMERA),
}

/**
 * A live camera preview that reports every barcode it decodes, for as long as it is composed.
 *
 * Camera use cases are bound to the current [LocalLifecycleOwner] while this composable is in the
 * composition and unbound when it leaves — including inside a `ModalBottomSheet`, whose dialog
 * inherits the host's lifecycle owner. [onBarcodeScanned] is always invoked on the main thread,
 * so touching ViewModel state from it is safe.
 *
 * Each camera frame is released back to CameraX only once the detector has finished with it,
 * which is what lets 1D formats (EAN, Code 128, ITF) decode as reliably as QR codes.
 *
 * **This composable does not request the `CAMERA` permission.** Check it before composing this;
 * every app's permission flow differs, so the module deliberately owns none of it. Composing
 * without the permission granted reports a bind failure through [onError].
 *
 * ```kotlin
 * BarcodeScannerCamera(
 *     modifier = Modifier.fillMaxSize(),
 *     formats = BarcodeFormats.OneDimensional,
 *     onError = { viewModel.onScannerFailed(it) },
 *     onBarcodeScanned = { viewModel.onCodeScanned(it) },
 * )
 * ```
 *
 * @param formats which symbologies to decode. Narrower is faster — see [BarcodeFormats].
 * @param lensFacing which camera to bind.
 * @param torchEnabled whether the torch is on. Silently ignored on a camera with no flash unit.
 * @param debounceWindow how long the same raw value is suppressed after being reported, per code.
 * Null disables it, which is what you want if you already de-duplicate downstream (keyed on
 * ViewModel state that outlives this composable, say).
 * @param overlay drawn on top of the preview, in the same [Box] — so `Modifier.align` is
 * available to it. Defaults to [DefaultScanFrame].
 * @param onError reports a camera that could not be opened or bound — no camera, permission not
 * granted, or another app holding it. The preview stays blank; recovery is the caller's call.
 * @param onBarcodeScanned invoked on the main thread, once per decoded barcode per analysed
 * frame, subject to [debounceWindow].
 */
@Composable
fun BarcodeScannerCamera(
	modifier: Modifier = Modifier,
	formats: Set<BarcodeFormat> = BarcodeFormats.All,
	lensFacing: LensFacing = LensFacing.Back,
	torchEnabled: Boolean = false,
	debounceWindow: Duration? = 2.5.seconds,
	overlay: @Composable BoxScope.() -> Unit = { DefaultScanFrame() },
	onError: (Throwable) -> Unit = {},
	onBarcodeScanned: (ScannedBarcode) -> Unit,
) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
	val currentOnError by rememberUpdatedState(onError)
	var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
	var camera by remember { mutableStateOf<Camera?>(null) }

	// Survives recomposition but is rebuilt whenever the window changes, so a caller toggling
	// debouncing does not carry stale suppressions across.
	val debouncer = remember(debounceWindow) { BarcodeDebouncer(debounceWindow) }

	LaunchedEffect(lifecycleOwner, formats, lensFacing) {
		surfaceRequest = null
		camera = null
		val scanner = BarcodeScanning.getClient(formats.toScannerOptions())
		// Single thread: STRATEGY_KEEP_ONLY_LATEST already drops frames under load, so a pool
		// would only buy concurrent decodes of frames we are about to discard anyway.
		val analysisExecutor = Executors.newSingleThreadExecutor()
		try {
			val preview = Preview.Builder()
				.build()
				.apply {
					setSurfaceProvider { request -> surfaceRequest = request }
				}
			val analysis = ImageAnalysis.Builder()
				.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
				.build()
				.apply {
					setAnalyzer(
						analysisExecutor,
						BarcodeAnalyzer(
							scanner = scanner,
							callbackExecutor = ContextCompat.getMainExecutor(context),
							onBarcodesDetected = { barcodes ->
								barcodes
									.mapNotNull { it.toScannedBarcode() }
									.filter(debouncer::shouldEmit)
									.forEach(currentOnBarcodeScanned)
							},
							onDetectionFailed = { currentOnError(it) },
						),
					)
				}

			// No camera, permission not granted, or another app holding it: report it rather
			// than crashing behind a blank preview.
			val cameraProvider = try {
				ProcessCameraProvider.awaitInstance(context)
			} catch (cancellation: CancellationException) {
				throw cancellation
			} catch (error: Exception) {
				currentOnError(error)
				null
			}
			if (cameraProvider != null) {
				// bindToLifecycle and unbind both assert they are on the main thread. In an app
				// the composition dispatches there anyway, but awaitInstance above resumes on a
				// CameraX executor, so the thread at this point depends on the ambient
				// dispatcher — which under a Compose test harness is not main. Pin it rather
				// than depend on it.
				withContext(Dispatchers.Main.immediate) {
					val bound = try {
						camera = cameraProvider.bindToLifecycle(
							lifecycleOwner,
							lensFacing.selector,
							preview,
							analysis,
						)
						true
					} catch (error: Exception) {
						currentOnError(error)
						false
					}
					// clearAnalyzer + unbind must happen before the scanner closes below, so that
					// no analyze() call can run against a closed detector.
					try {
						if (bound) awaitCancellation()
					} finally {
						camera = null
						analysis.clearAnalyzer()
						cameraProvider.unbind(preview, analysis)
					}
				}
			}
		} finally {
			// Queued on the analysis thread so it lands after any in-flight analyze() returns.
			analysisExecutor.execute { scanner.close() }
			analysisExecutor.shutdown()
		}
	}

	LaunchedEffect(camera, torchEnabled) {
		val control = camera?.cameraControl ?: return@LaunchedEffect
		if (camera?.cameraInfo?.hasFlashUnit() == true) {
			control.enableTorch(torchEnabled)
		}
	}

	Box(modifier = modifier) {
		surfaceRequest?.let { request ->
			CameraXViewfinder(
				modifier = Modifier.fillMaxSize(),
				surfaceRequest = request,
			)
		}
		overlay()
	}
}

/** Builds ML Kit detector options for [formats], skipping the filter when it would be a no-op. */
private fun Set<BarcodeFormat>.toScannerOptions(): BarcodeScannerOptions {
	val mlKitFormats = filter { it != BarcodeFormat.Unknown }.map { it.mlKitFormat }
	val builder = BarcodeScannerOptions.Builder()
	if (mlKitFormats.isEmpty()) {
		builder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
	} else {
		builder.setBarcodeFormats(mlKitFormats.first(), *mlKitFormats.drop(1).toIntArray())
	}
	return builder.build()
}

/**
 * Feeds each camera frame to ML Kit and releases it back to CameraX once detection completes.
 *
 * Holding the [ImageProxy] open until [BarcodeScanner.process] finishes is what lets ML Kit read
 * the frame's planes; closing it early makes every decode a race the detector usually loses, and
 * 1D formats are the ones that lose it. Both callbacks run on [callbackExecutor].
 */
private class BarcodeAnalyzer(
	private val scanner: BarcodeScanner,
	private val callbackExecutor: Executor,
	private val onBarcodesDetected: (List<Barcode>) -> Unit,
	private val onDetectionFailed: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer {

	@OptIn(ExperimentalGetImage::class)
	override fun analyze(imageProxy: ImageProxy) {
		val mediaImage = imageProxy.image
		if (mediaImage == null) {
			imageProxy.close()
			return
		}
		val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
		scanner.process(inputImage)
			.addOnSuccessListener(callbackExecutor) { barcodes ->
				if (barcodes.isNotEmpty()) onBarcodesDetected(barcodes)
			}
			.addOnFailureListener(callbackExecutor) { error ->
				onDetectionFailed(error)
			}
			.addOnCompleteListener(callbackExecutor) {
				imageProxy.close()
			}
	}
}
