package uk.co.appoly.droid.ui.screens

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
				onDismissRequest = { showSheet = false },
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					TorchToggleRow(
						modifier = Modifier.fillMaxWidth(),
						checked = torchEnabled,
						onCheckedChange = { torchEnabled = it },
					)

					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(360.dp),
					) {
						BarcodeScannerCamera(
							modifier = Modifier.fillMaxSize(),
							torchEnabled = torchEnabled,
							onError = { cameraError = it.message ?: it.toString() },
							onBarcodeScanned = { barcode ->
								// The module's own 2.5s debounce stops a held code repeating;
								// this keeps the demo list to distinct values across the session.
								if (scannedCodes.none { it.rawValue == barcode.rawValue }) {
									scannedCodes.add(barcode)
								}
							},
						)
					}

					Text(
						text = "${scannedCodes.size} distinct code(s) scanned",
						style = MaterialTheme.typography.bodyMedium,
					)
				}
			}
		}
	}
}

@Composable
private fun TorchToggleRow(
	modifier: Modifier = Modifier,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			text = "Torch",
			style = MaterialTheme.typography.bodyMedium,
		)
		Switch(
			checked = checked,
			onCheckedChange = onCheckedChange,
		)
	}
}
