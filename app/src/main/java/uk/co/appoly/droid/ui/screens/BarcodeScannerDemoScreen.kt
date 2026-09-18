package uk.co.appoly.droid.ui.screens

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.barcodescanner.BarcodeFormats
import uk.co.appoly.droid.barcodescanner.OneShotBarcodeScanner
import uk.co.appoly.droid.barcodescanner.OneShotScanResult
import uk.co.appoly.droid.barcodescanner.ScannedBarcode
import androidx.compose.runtime.mutableIntStateOf
import kotlin.time.Duration.Companion.milliseconds
import uk.co.appoly.droid.barcodescanner.camera.ScanMode
import uk.co.appoly.droid.barcodescanner.camera.ScanPolicy
import uk.co.appoly.droid.barcodescanner.camera.ScanRegion
import uk.co.appoly.droid.ui.segmentedcontrol.SegmentedControl
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import uk.co.appoly.droid.barcodescanner.camera.DetectedBarcode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import uk.co.appoly.droid.barcodescanner.camera.AnimatedScanFrame
import uk.co.appoly.droid.barcodescanner.camera.LensFacing
import uk.co.appoly.droid.barcodescanner.camera.DefaultScanFrame
import uk.co.appoly.droid.barcodescanner.camera.ScannerOverlayScope
import uk.co.appoly.droid.barcodescanner.camera.BarcodeScannerCamera
import uk.co.appoly.droid.nav3.Nav3Screen

/**
 * Demonstrates both barcode modules side by side.
 *
 * "Scan once" goes through [OneShotBarcodeScanner] — Play services renders the UI, so there is no
 * permission to request here. "Scan continuously" opens a [ModalBottomSheet] hosting
 * [BarcodeScannerCamera], which is also the sheet case worth demonstrating: the dialog inherits
 * the host's lifecycle owner, so the camera binds and unbinds with the sheet.
 */
@Serializable
data object BarcodeScannerDemoScreen : Nav3Screen {
	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		val context = LocalContext.current
		val scope = rememberCoroutineScope()

		val oneShotScanner = remember { OneShotBarcodeScanner(context, formats = BarcodeFormats.All) }
		var oneShotResult by remember { mutableStateOf<String?>(null) }

		var showSheet by remember { mutableStateOf(false) }
		var torchEnabled by remember { mutableStateOf(false) }
		var cameraError by remember { mutableStateOf<String?>(null) }
		val scannedCodes = remember { mutableStateListOf<ScannedBarcode>() }

		var hasCameraPermission by remember {
			mutableStateOf(
				ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
					PackageManager.PERMISSION_GRANTED,
			)
		}
		val permissionLauncher = rememberLauncherForActivityResult(
			ActivityResultContracts.RequestPermission(),
		) { granted ->
			hasCameraPermission = granted
			if (granted) showSheet = true
		}

		// Pre-install the Play services scanner module so the first one-shot scan is instant
		// rather than a download spinner. Exactly what the README tells consumers to do.
		LaunchedEffect(oneShotScanner) {
			oneShotScanner.warmUp()
		}

		Scaffold(
			topBar = {
				TopAppBar(
					title = { Text("Barcode Scanner") },
				)
			},
		) { paddingValues ->
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(paddingValues)
					.padding(16.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text(
					text = "Two modules: BarcodeScanner hosts a single scan in Play services' own " +
						"UI (no CAMERA permission), BarcodeScanner-Camera runs a continuous " +
						"preview inside the app.",
					style = MaterialTheme.typography.bodyMedium,
				)

				HorizontalDivider()

				Text(
					text = "One-shot (BarcodeScanner)",
					style = MaterialTheme.typography.titleMedium,
				)

				Button(
					modifier = Modifier.fillMaxWidth(),
					onClick = {
						scope.launch {
							oneShotResult = when (val result = oneShotScanner.scan()) {
								is OneShotScanResult.Scanned ->
									"${result.barcode.format}: ${result.barcode.rawValue}"

								OneShotScanResult.Cancelled -> "Cancelled"
								is OneShotScanResult.Unavailable ->
									"Unavailable — no Play services scanner on this device " +
										"(${result.cause?.message ?: "no detail"})"

								is OneShotScanResult.Failed ->
									"Failed: ${result.cause.message ?: result.cause}"
							}
						}
					},
				) {
					Text("Scan once")
				}

				oneShotResult?.let { result ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Text(
							modifier = Modifier.padding(16.dp),
							text = result,
							style = MaterialTheme.typography.bodyMedium,
						)
					}
				}

				HorizontalDivider()

				Text(
					text = "Continuous (BarcodeScanner-Camera)",
					style = MaterialTheme.typography.titleMedium,
				)

				Button(
					modifier = Modifier.fillMaxWidth(),
					onClick = {
						cameraError = null
						if (hasCameraPermission) {
							showSheet = true
						} else {
							permissionLauncher.launch(Manifest.permission.CAMERA)
						}
					},
				) {
					Text("Scan continuously")
				}

				if (scannedCodes.isNotEmpty()) {
					OutlinedButton(
						modifier = Modifier.fillMaxWidth(),
						onClick = { scannedCodes.clear() },
					) {
						Text("Clear ${scannedCodes.size} scanned")
					}
				}

				cameraError?.let { error ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Text(
							modifier = Modifier.padding(16.dp),
							text = "Camera error: $error",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.error,
						)
					}
				}

				scannedCodes.forEach { barcode ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Column(modifier = Modifier.padding(16.dp)) {
							Text(
								text = barcode.format.name,
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
							Text(
								text = barcode.rawValue,
								style = MaterialTheme.typography.bodyMedium,
							)
							barcode.displayValue?.let { display ->
								Text(
									text = "display: $display",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
					}
				}
			}
		}

		if (showSheet) {
			val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
			ModalBottomSheet(
				sheetState = sheetState,
				// Material caps a sheet at 640dp on wide screens, which wastes most of a
				// landscape phone — and width is exactly what this layout wants.
				sheetMaxWidth = Dp.Unspecified,
				onDismissRequest = { showSheet = false },
			) {
				run {
					// Every ScanPolicy knob is driven live from here, so the sheet doubles as the
					// place to feel what each one does rather than reason about it.
					var mode by remember { mutableStateOf(ScanMode.Single) }
					var regionChoice by remember { mutableStateOf("Reticle") }
					var dwellMs by remember { mutableIntStateOf(500) }
					var paused by remember { mutableStateOf(false) }
					var overlayStyle by remember { mutableStateOf("Animated") }
					var haptics by remember { mutableStateOf(true) }
					var lens by remember { mutableStateOf(LensFacing.Back) }
					val hapticFeedback = LocalHapticFeedback.current
					var lastScan by remember { mutableStateOf<String?>(null) }

					val policy = remember(mode, regionChoice, dwellMs) {
						ScanPolicy(
							mode = mode,
							dwell = dwellMs.takeIf { it > 0 }?.milliseconds,
							region = when (regionChoice) {
								"Full" -> ScanRegion.Full
								"Visible" -> ScanRegion.Visible
								else -> ScanRegion.Reticle()
							},
						)
					}

					// Landscape is short on height and flush with width, so the knobs sit beside the
					// preview rather than above it — which also keeps the preview big enough to aim
					// with, instead of pushing it off the bottom of the sheet.
					val landscape = LocalConfiguration.current.orientation ==
						Configuration.ORIENTATION_LANDSCAPE

					val controls: @Composable ColumnScope.() -> Unit = {
						SegmentedControl(
							segments = listOf(ScanMode.Single, ScanMode.Multi),
							selectedSegment = mode,
							onSegmentSelected = { mode = it },
							segmentText = { it.name },
						)
						SegmentedControl(
							segments = listOf("Full", "Visible", "Reticle"),
							selectedSegment = regionChoice,
							onSegmentSelected = { regionChoice = it },
						)
						SegmentedControl(
							segments = listOf(LensFacing.Back, LensFacing.Front),
							selectedSegment = lens,
							onSegmentSelected = { lens = it },
							segmentText = { if (it == LensFacing.Back) "Back cam" else "Front cam" },
						)
						SegmentedControl(
							segments = listOf("Frame", "Animated", "Corners", "Bullseye"),
							selectedSegment = overlayStyle,
							onSegmentSelected = { overlayStyle = it },
						)
						SegmentedControl(
							segments = listOf(0, 250, 500, 1000),
							selectedSegment = dwellMs,
							onSegmentSelected = { dwellMs = it },
							segmentText = { if (it == 0) "no dwell" else "${it}ms" },
						)

						TorchToggleRow(
							modifier = Modifier.fillMaxWidth(),
							checked = torchEnabled,
							onCheckedChange = { torchEnabled = it },
						)
						TorchToggleRow(
							modifier = Modifier.fillMaxWidth(),
							label = "Haptic on scan",
							checked = haptics,
							onCheckedChange = { haptics = it },
						)
						TorchToggleRow(
							modifier = Modifier.fillMaxWidth(),
							label = "Pause scanning",
							checked = paused,
							onCheckedChange = { paused = it },
						)

						Text(
							text = lastScan?.let { "Last: $it" }
								?: "Hold a code inside the frame for ${dwellMs}ms",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.primary,
						)
						Text(
							text = "${scannedCodes.size} distinct code(s) scanned",
							style = MaterialTheme.typography.bodyMedium,
						)
					}
					val preview: @Composable (Modifier) -> Unit = { previewModifier ->
						Box(modifier = previewModifier) {
							BarcodeScannerCamera(
								modifier = Modifier.fillMaxSize(),
								lensFacing = lens,
								torchEnabled = torchEnabled,
								scanningEnabled = !paused,
								policy = policy,
								overlay = {
									when (overlayStyle) {
										"Frame" -> DefaultScanFrame()
										"Animated" -> AnimatedScanFrame()
										// Written here rather than in the library, to show the scope
										// gives a consumer everything needed to draw their own.
										"Corners" -> CornerBracketOverlay()
										else -> BullseyeOverlay()
									}
								},
								onError = { cameraError = it.message ?: it.toString() },
								onBarcodeScanned = { barcode ->
									// Feedback (haptic, sound etc.) lives here rather than in the module,
									// because only the app knows whether a scan was any *good*.
									// Here "already in the list" stands in for the real thing — a
									// code that is not on the manifest, or the wrong item — and gets
									// the reject signal.
									val isNew = scannedCodes.none { it.rawValue == barcode.rawValue }
									if (haptics) {
										hapticFeedback.performHapticFeedback(
											if (isNew) HapticFeedbackType.Confirm
											else HapticFeedbackType.Reject,
										)
									}
									lastScan = if (isNew) {
										scannedCodes.add(barcode)
										"${barcode.format}: ${barcode.rawValue}"
									} else {
										"Already scanned: ${barcode.rawValue}"
									}
								},
							)
						}

					}

					if (landscape) {
						Row(
							modifier = Modifier.fillMaxWidth().height(340.dp).padding(16.dp),
							horizontalArrangement = Arrangement.spacedBy(16.dp),
						) {
							Column(
								modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
								verticalArrangement = Arrangement.spacedBy(12.dp),
							) { controls() }
							preview(Modifier.weight(1f).fillMaxHeight())
						}
					} else {
						Column(
							modifier = Modifier.fillMaxWidth().padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp),
						) {
							controls()
							preview(Modifier.fillMaxWidth().height(360.dp))
						}
					}
				}
			}
		}
	}
}

@Composable
private fun TorchToggleRow(
	modifier: Modifier = Modifier,
	label: String = "Torch",
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
		)
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange,
		)
	}
}

/**
 * A hand-rolled overlay, built only from [ScannerOverlayScope]'s public surface.
 *
 * Exists to prove the point: a consumer needs nothing from the library beyond [regionRect] and
 * [detections] to draw something completely different — here, crosshairs on the acceptance region
 * and a filling ring on whatever the scanner is about to accept.
 */
@Composable
private fun ScannerOverlayScope.BullseyeOverlay() {
	Canvas(modifier = Modifier.fillMaxSize()) {
		val region = regionRect
		if (region.width <= 0f) return@Canvas

		// Crosshairs marking where the scanner is looking.
		val centre = region.center
		val arm = 24.dp.toPx()
		listOf(
			Offset(centre.x - arm, centre.y) to Offset(centre.x + arm, centre.y),
			Offset(centre.x, centre.y - arm) to Offset(centre.x, centre.y + arm),
		).forEach { (from, to) ->
			drawLine(Color.White.copy(alpha = 0.7f), from, to, strokeWidth = 2.dp.toPx())
		}

		detections.forEach { detection ->
			val box = detection.bounds
			val radius = maxOf(box.width, box.height) / 2f + 16.dp.toPx()
			drawCircle(
				color = Color.Cyan.copy(alpha = 0.35f),
				radius = radius,
                center = box.center,
				style = Stroke(width = 2.dp.toPx()),
			)
			// The ring fills as the code dwells — the same signal AnimatedScanFrame draws, just
			// shaped differently.
			drawArc(
				color = Color.Cyan,
				startAngle = -90f,
				sweepAngle = 360f * detection.dwellProgress,
				useCenter = false,
				topLeft = Offset(box.center.x - radius, box.center.y - radius),
				size = Size(radius * 2, radius * 2),
				style = Stroke(width = 4.dp.toPx()),
			)
		}
	}
}

/**
 * A second hand-rolled overlay, app-side, in the style of Google's hosted scanner.
 *
 * At rest, it marks the acceptance region with four **unconnected** corner brackets. As a barcode
 * dwells, the arms grow along each edge until they meet in the middle and the brackets close into a
 * complete frame — so [DetectedBarcode.dwellProgress] is legible as a shape rather than needing a
 * separate progress indicator.
 *
 * Built, like [BullseyeOverlay], from nothing but [ScannerOverlayScope.regionRect] and
 * [ScannerOverlayScope.detections]. Two overlays this different sharing one contract is the point:
 * the library ships an opinionated frame, and an app that wants its own look is not stuck with it.
 */
@Composable
private fun ScannerOverlayScope.CornerBracketOverlay(
	restingArm: Dp = 28.dp,
	strokeWidth: Dp = 4.dp,
	idleColor: Color = Color.White,
	trackingColor: Color = Color(0xFF4CAF50),
) {
	val tracked = detections.firstOrNull()
	val progress = tracked?.dwellProgress ?: 0f

	// The code's own corners, so the brackets sit on a tilted barcode rather than on the
	// axis-aligned box around it.
	val target = tracked?.corners?.takeIf { it.size == 4 }
		?: tracked?.bounds?.let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
		?: regionRect.let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }

	val spec = spring<Float>(stiffness = 420f, dampingRatio = 0.82f)
	val corners = target.mapIndexed { index, corner ->
		val x by animateFloatAsState(corner.x, spec, label = "bracketX$index")
		val y by animateFloatAsState(corner.y, spec, label = "bracketY$index")
		Offset(x, y)
	}
	val closure by animateFloatAsState(progress, label = "bracketClosure")
	val color by animateColorAsState(
		targetValue = if (tracked != null) trackingColor else idleColor,
		label = "bracketColor",
	)

	Canvas(modifier = Modifier.fillMaxSize()) {
		val base = restingArm.toPx()
		val stroke = strokeWidth.toPx()

		// Walk the quad's four edges. Each corner grows an arm along both edges it touches, from a
		// resting stub to half the edge — at which point the two arms meet and the brackets become
		// a continuous outline. Working along edges rather than in x/y keeps it correct at any
		// rotation.
		corners.forEachIndexed { index, corner ->
			val next = corners[(index + 1) % corners.size]
			val previous = corners[(index + corners.size - 1) % corners.size]
			listOf(next, previous).forEach { neighbour ->
				val edge = neighbour - corner
				val length = edge.getDistance()
				if (length <= 0f) return@forEach
				val arm = (base + ((length / 2f) - base).coerceAtLeast(0f) * closure)
					.coerceAtMost(length)
				drawLine(
					color = color,
					start = corner,
					end = corner + edge / length * arm,
					strokeWidth = stroke,
					cap = StrokeCap.Round,
				)
			}
		}
	}
}

