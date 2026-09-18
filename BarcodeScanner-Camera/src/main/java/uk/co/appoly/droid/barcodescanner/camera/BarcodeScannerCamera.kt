package uk.co.appoly.droid.barcodescanner.camera

import androidx.annotation.OptIn
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
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
 * A live camera preview that reports the barcodes the user deliberately aims at.
 *
 * Camera use cases bind to the current [LocalLifecycleOwner] while this composable is in the
 * composition and unbind when it leaves — including inside a `ModalBottomSheet`, whose dialog
 * inherits the host's lifecycle owner. [onBarcodeScanned] is always invoked on the main thread, so
 * touching ViewModel state from it is safe.
 *
 * Each camera frame is released back to CameraX only once the detector has finished with it, which
 * is what lets 1D formats (EAN, Code 128, ITF) decode as reliably as QR codes.
 *
 * **What counts as a scan is [policy]'s job**, and the defaults are deliberately not
 * "report everything immediately": a barcode must be held inside the aiming region for half a
 * second, and one presentation produces one result however long it is held. A scanner that fires
 * at whatever drifts through the frame reads as broken to the person holding it.
 *
 * **This composable does not request the `CAMERA` permission.** Check it before composing this;
 * every app's permission flow differs, so the module deliberately owns none of it. Composing
 * without the permission granted reports a bind failure through [onError].
 *
 * ```kotlin
 * BarcodeScannerCamera(
 *     modifier = Modifier.fillMaxSize(),
 *     formats = BarcodeFormats.OneDimensional,
 *     onError = viewModel::onScannerFailed,
 *     onBarcodeScanned = { viewModel.onCodeScanned(it) },
 * )
 * ```
 *
 * @param formats which symbologies to decode. Narrower is faster — see [BarcodeFormats].
 * @param lensFacing which camera to bind.
 * @param torchEnabled whether the torch is on. Silently ignored on a camera with no flash unit.
 * @param scanningEnabled whether results are reported. False keeps the camera bound and the
 * preview live but reports nothing — for holding a result on screen without the scanner running on
 * underneath it. Cheaper and far less jarring than removing the composable, which tears the camera
 * down and flashes the preview on the way back.
 * @param policy how long a barcode must be held, how many are tracked at once, and where in the
 * frame they count. See [ScanPolicy].
 * @param overlay drawn on top of the preview. Receives the resolved acceptance region and the
 * current detections, so it can show what is about to be accepted; see [ScannerOverlayScope].
 * @param onError reports a camera that could not be opened or bound — no camera, permission not
 * granted, or another app holding it. The preview stays blank; recovery is the caller's call.
 * @param onBarcodeScanned invoked on the main thread for each barcode that satisfies [policy].
 *
 * Feedback — haptics, sounds — is deliberately not a parameter here. This composable knows only
 * that a barcode was *read*, never whether it was the right one, so anything it played would have
 * to fire before your callback could disagree: a confirm buzz followed by your reject buzz, for
 * one scan. Play it in [onBarcodeScanned], where the verdict is known. The README shows the
 * pattern.
 */
@Composable
fun BarcodeScannerCamera(
	modifier: Modifier = Modifier,
	formats: Set<BarcodeFormat> = BarcodeFormats.All,
	lensFacing: LensFacing = LensFacing.Back,
	torchEnabled: Boolean = false,
	scanningEnabled: Boolean = true,
	policy: ScanPolicy = ScanPolicy.Default,
	overlay: @Composable ScannerOverlayScope.() -> Unit = { DefaultScanFrame() },
	onError: (Throwable) -> Unit = {},
	onBarcodeScanned: (ScannedBarcode) -> Unit,
) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
	val currentOnError by rememberUpdatedState(onError)
	var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
	var camera by remember { mutableStateOf<Camera?>(null) }

	var previewSize by remember { mutableStateOf(Size.Zero) }
	var detections by remember { mutableStateOf<List<DetectedBarcode>>(emptyList()) }
	val currentScanningEnabled by rememberUpdatedState(scanningEnabled)

	// Rebuilt only when the policy actually changes — which is why ScanPolicy implements equals by
	// hand. A policy constructed inline that did not compare equal would reset every dwell on
	// every recomposition and nothing would ever scan.
	val tracker = remember(policy) { BarcodeTracker(policy) }

	LaunchedEffect(lifecycleOwner, formats, lensFacing, policy) {
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
							region = policy.region,
							previewSize = { previewSize },
							callbackExecutor = ContextCompat.getMainExecutor(context),
							onFrameAnalysed = { ranked, crop ->
								// Every frame ticks the tracker, including empty ones: absence is
								// what expires a track, so skipping quiet frames would leave a
								// code "present" long after it had gone.
								val visible = ranked.mapNotNull { it.toScannedBarcode() }
								if (currentScanningEnabled) {
									tracker.accept(visible).forEach(currentOnBarcodeScanned)
								}
								detections = ranked.toDetections(
									tracker = tracker,
									crop = crop,
									previewSize = previewSize,
									mirrored = lensFacing == LensFacing.Front,
								)
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
						// Bound as a group with a ViewPort so preview and analysis share one field
						// of view. Without it the analyser sees a wider image than the preview
						// shows, ImageProxy.cropRect means nothing, and the scanner can read a
						// barcode that is not on screen at all.
						val group = UseCaseGroup.Builder()
							.setViewPort(ViewPort.Builder(android.util.Rational(4, 3), preview.targetRotation).build())
							.addUseCase(preview)
							.addUseCase(analysis)
							.build()
						camera = cameraProvider.bindToLifecycle(
							lifecycleOwner,
							lensFacing.selector,
							group,
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

	Box(
		modifier = modifier.onSizeChanged {
			previewSize = Size(it.width.toFloat(), it.height.toFloat())
		},
	) {
		surfaceRequest?.let { request ->
			// The viewfinder's default is a SurfaceView wherever the device supports one: cheaper
			// and lower-latency, but composited by the system outside the view hierarchy. In a
			// window of its own — a ModalBottomSheet, a Dialog — that surface is positioned against
			// the wrong window, so the preview spills outside its bounds and draws behind the sheet
			// rather than inside it. A TextureView draws inline and therefore clips, scrolls,
			// rounds and animates like anything else.
			//
			// Only in a dialog window, because a full-screen scanner is exactly where the cheaper
			// path is worth keeping. Two call sites rather than a nullable argument: passing no
			// mode is what leaves CameraX its own compatibility choice, which already downgrades to
			// a TextureView on legacy camera hardware.
			if (LocalView.current.parent is DialogWindowProvider) {
				CameraXViewfinder(
					modifier = Modifier.fillMaxSize(),
					surfaceRequest = request,
					implementationMode = ImplementationMode.EMBEDDED,
				)
			} else {
				CameraXViewfinder(
					modifier = Modifier.fillMaxSize(),
					surfaceRequest = request,
				)
			}
		}
		ScannerOverlayScopeImpl(
			boxScope = this,
			regionRect = ScanRegionResolver.inPreview(policy.region, previewSize),
			detections = detections,
		).overlay()
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
 * Feeds each camera frame to ML Kit, keeps only the barcodes inside the acceptance region, and
 * ranks them so the one nearest the centre comes first.
 *
 * Holding the [ImageProxy] open until [BarcodeScanner.process] finishes is what lets ML Kit read
 * the frame's planes; closing it early makes every decode a race the detector usually loses, and
 * 1D formats are the ones that lose it.
 *
 * [onFrameAnalysed] runs on [callbackExecutor] for **every** analysed frame, including ones with
 * nothing in them. That matters: the tracker expires a barcode by its absence, so a frame with no
 * detections is information, not a frame to skip.
 */
private class BarcodeAnalyzer(
	private val scanner: BarcodeScanner,
	private val region: ScanRegion,
	private val previewSize: () -> Size,
	private val callbackExecutor: Executor,
	private val onFrameAnalysed: (ranked: List<Barcode>, crop: android.graphics.Rect) -> Unit,
	private val onDetectionFailed: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer {

	@OptIn(ExperimentalGetImage::class)
	override fun analyze(imageProxy: ImageProxy) {
		val mediaImage = imageProxy.image
		if (mediaImage == null) {
			imageProxy.close()
			return
		}
		val rotation = imageProxy.imageInfo.rotationDegrees
		// ML Kit reports bounding boxes in the rotation-corrected space, so the region has to be
		// expressed there too — width and height swap on a portrait sensor.
		val upright = rotation == 90 || rotation == 270
		val width = if (upright) imageProxy.height else imageProxy.width
		val height = if (upright) imageProxy.width else imageProxy.height
		val crop = imageProxy.cropRect.rotatedInto(rotation, imageProxy.width, imageProxy.height)
		val imageRegion = ScanRegionResolver.inImage(region, crop, previewSize(), width, height)

		val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
		scanner.process(inputImage)
			.addOnSuccessListener(callbackExecutor) { barcodes ->
				val ranked = barcodes
					.filter { it.isWithin(imageRegion) }
					.sortedBy { it.distanceToCentreOf(imageRegion) }
				onFrameAnalysed(ranked, crop)
			}
			.addOnFailureListener(callbackExecutor) { error ->
				onDetectionFailed(error)
			}
			.addOnCompleteListener(callbackExecutor) {
				imageProxy.close()
			}
	}
}

/**
 * Maps ranked detections from analyser image space into preview pixels for an overlay to draw.
 *
 * Preview and analysis share a field of view because they are bound through one `ViewPort`, so a
 * point maps across by the ratio of their sizes and no further correction is needed.
 */
private fun List<Barcode>.toDetections(
	tracker: BarcodeTracker,
	crop: android.graphics.Rect,
	previewSize: Size,
	mirrored: Boolean,
): List<DetectedBarcode> {
	if (previewSize.width <= 0f || crop.width() <= 0 || crop.height() <= 0) return emptyList()
	return mapNotNull { barcode ->
		val box = barcode.boundingBox ?: return@mapNotNull null
		val scanned = barcode.toScannedBarcode() ?: return@mapNotNull null
		// Mirroring swaps which horizontal edge is "left", so build the Rect from the extremes
		// rather than assuming the mapped corners keep their names.
		val a = mapToPreview(box.left, box.top, crop, previewSize, mirrored)
		val b = mapToPreview(box.right, box.bottom, crop, previewSize, mirrored)
		val topLeft = androidx.compose.ui.geometry.Offset(minOf(a.x, b.x), minOf(a.y, b.y))
		val bottomRight = androidx.compose.ui.geometry.Offset(maxOf(a.x, b.x), maxOf(a.y, b.y))
		DetectedBarcode(
			barcode = scanned,
			bounds = Rect(topLeft, bottomRight),
			// cornerPoints follow the code's own rotation, unlike boundingBox which is always
			// axis-aligned — they are the only way an overlay can outline a tilted barcode.
			corners = barcode.cornerPoints
				?.map { mapToPreview(it.x, it.y, crop, previewSize, mirrored) }
				.orEmpty(),
			dwellProgress = tracker.dwellProgress(scanned.rawValue),
		)
	}
}
