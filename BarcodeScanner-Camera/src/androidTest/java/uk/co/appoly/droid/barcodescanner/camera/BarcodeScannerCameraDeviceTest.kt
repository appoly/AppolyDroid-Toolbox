package uk.co.appoly.droid.barcodescanner.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * On-device smoke test for [BarcodeScannerCamera]'s bind/unbind lifecycle.
 *
 * **Not run in CI** — it needs a real camera, which no CI runner has. Run it before tagging a
 * release:
 *
 * ```
 * ./gradlew :BarcodeScanner-Camera:connectedDebugAndroidTest
 * ```
 *
 * What it proves: the composable binds its use cases without error, and tears them down cleanly
 * enough to be mounted and unmounted repeatedly. That is the regression surface that actually
 * bites here — closing the ML Kit detector while a frame is still in flight, or shutting the
 * analysis executor down underneath a queued task, both throw from the analysis thread rather
 * than returning an error, so they show up as a crashed test rather than an `onError` call.
 *
 * What it cannot prove: that no native camera resource leaked. CameraX exposes no API to observe
 * the use cases this composable owns, so a leak check would have to reach inside it. The
 * mount/unmount cycling below is the closest observable proxy.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeScannerCameraDeviceTest {

	@get:Rule
	val composeRule = createComposeRule()

	private val errors = CopyOnWriteArrayList<Throwable>()

	/**
	 * Deliberately asserted rather than granted with `GrantPermissionRule`: that rule opens a
	 * UiAutomation connection unconditionally, and on a device already holding one it dies with
	 * "UiAutomationService ... already registered" before the test body runs — even when the
	 * permission is already granted. The install grants CAMERA, so assert and fail loudly with an
	 * actionable message instead of depending on UiAutomation at all.
	 */
	@Before
	fun requireCameraPermission() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val granted = context.checkSelfPermission(Manifest.permission.CAMERA) ==
			PackageManager.PERMISSION_GRANTED
		assertTrue(
			"CAMERA not granted to the test package. Install with `adb install -g`, or run " +
				"`adb shell pm grant uk.co.appoly.droid.barcodescanner.camera.test " +
				"android.permission.CAMERA`.",
			granted,
		)
	}

	@Test
	fun bindsWithoutReportingAnError() {
		composeRule.setContent {
			BarcodeScannerCamera(
				modifier = Modifier.fillMaxSize(),
				onError = { errors.add(it) },
				onBarcodeScanned = {},
			)
		}

		composeRule.waitForIdle()
		// Binding is asynchronous (awaitInstance + bindToLifecycle), so give it real time before
		// concluding it succeeded.
		Thread.sleep(BIND_SETTLE_MS)
		composeRule.waitForIdle()

		assertNoErrors("binding the camera")
	}

	@Test
	fun survivesRepeatedMountAndUnmountCycles() {
		var mounted by mutableStateOf(true)

		composeRule.setContent {
			if (mounted) {
				BarcodeScannerCamera(
					modifier = Modifier.fillMaxSize(),
					onError = { errors.add(it) },
					onBarcodeScanned = {},
				)
			}
		}

		repeat(CYCLES) {
			composeRule.runOnIdle { mounted = true }
			Thread.sleep(BIND_SETTLE_MS)
			composeRule.runOnIdle { mounted = false }
			composeRule.waitForIdle()
			// Let the teardown's queued scanner.close() actually run on the analysis thread.
			Thread.sleep(TEARDOWN_SETTLE_MS)
		}

		assertNoErrors("cycling the camera $CYCLES times")
	}

	@Test
	fun theCameraIsUsableAgainAfterTheComposableLeaves() {
		var mounted by mutableStateOf(true)

		composeRule.setContent {
			if (mounted) {
				BarcodeScannerCamera(
					modifier = Modifier.fillMaxSize(),
					onError = { errors.add(it) },
					onBarcodeScanned = {},
				)
			}
		}
		composeRule.waitForIdle()
		Thread.sleep(BIND_SETTLE_MS)

		composeRule.runOnIdle { mounted = false }
		composeRule.waitForIdle()
		Thread.sleep(TEARDOWN_SETTLE_MS)

		// If the composable had left the camera bound to a dead lifecycle, the provider would
		// still report the back camera as unavailable to a fresh caller.
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val provider = ProcessCameraProvider.getInstance(context).get()
		assertTrue(
			"the back camera should be available again once the composable has left",
			provider.hasCamera(LensFacing.Back.selector),
		)
		assertNoErrors("unbinding the camera")
	}

	private fun assertNoErrors(whileDoing: String) {
		assertTrue(
			"onError fired while $whileDoing: ${errors.joinToString { it.toString() }}",
			errors.isEmpty(),
		)
	}

	private companion object {
		const val BIND_SETTLE_MS = 2_000L
		const val TEARDOWN_SETTLE_MS = 500L
		const val CYCLES = 5
	}
}
