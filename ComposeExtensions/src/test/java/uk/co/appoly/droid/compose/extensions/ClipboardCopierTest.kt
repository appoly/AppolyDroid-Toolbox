package uk.co.appoly.droid.compose.extensions

import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Robolectric tests for [ClipboardCopier], the `copyX` extensions, and [rememberClipboardCopier].
 */
@RunWith(AndroidJUnit4::class)
class ClipboardCopierTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `copyPlainText builds plain-text ClipEntry and passes confirmation`() {
		var capturedEntry: ClipEntry? = null
		var capturedMsg: String? = null
		val copier = ClipboardCopier { entry, msg ->
			capturedEntry = entry
			capturedMsg = msg
		}

		copier.copyPlainText("body", label = "lbl", confirmationMessage = "Copied!")

		assertEquals("Copied!", capturedMsg)
		assertEquals("body", capturedEntry!!.clipData.getItemAt(0).text.toString())
	}

	@Test
	fun `copyHtmlText builds html ClipEntry and passes confirmation`() {
		var capturedEntry: ClipEntry? = null
		var capturedMsg: String? = null
		val copier = ClipboardCopier { entry, msg ->
			capturedEntry = entry
			capturedMsg = msg
		}

		copier.copyHtmlText(
			text = "plain",
			htmlText = "<b>bold</b>",
			label = "lbl",
			confirmationMessage = "Html!",
		)

		assertEquals("Html!", capturedMsg)
		val item = capturedEntry!!.clipData.getItemAt(0)
		assertEquals("plain", item.text.toString())
		assertEquals("<b>bold</b>", item.htmlText)
	}

	@Test
	fun `copyRawUri builds uri ClipEntry and passes confirmation`() {
		var capturedEntry: ClipEntry? = null
		var capturedMsg: String? = null
		val copier = ClipboardCopier { entry, msg ->
			capturedEntry = entry
			capturedMsg = msg
		}
		val uri = Uri.parse("https://example.com")

		copier.copyRawUri(uri, label = "link", confirmationMessage = "Uri!")

		assertEquals("Uri!", capturedMsg)
		assertEquals(uri, capturedEntry!!.clipData.getItemAt(0).uri)
	}

	@Test
	@Config(sdk = [32])
	fun `rememberClipboardCopier shows toast on sdk below TIRAMISU`() {
		ShadowToast.reset()
		composeRule.setContent {
			val copier = rememberClipboardCopier()
			LaunchedEffect(Unit) {
				copier.copyPlainText("body", label = "lbl", confirmationMessage = "Copied!")
			}
		}
		composeRule.waitForIdle()
		assertEquals("Copied!", ShadowToast.getTextOfLatestToast())
	}

	@Test
	@Config(sdk = [33])
	fun `rememberClipboardCopier suppresses toast on TIRAMISU`() {
		ShadowToast.reset()
		composeRule.setContent {
			val copier = rememberClipboardCopier()
			LaunchedEffect(Unit) {
				copier.copyPlainText("body", label = "lbl", confirmationMessage = "Copied!")
			}
		}
		composeRule.waitForIdle()
		assertNull(ShadowToast.getLatestToast())
	}

	@Test
	@Config(sdk = [32])
	fun `null confirmation shows no toast`() {
		ShadowToast.reset()
		composeRule.setContent {
			val copier = rememberClipboardCopier()
			LaunchedEffect(Unit) {
				copier.copyPlainText("body", label = "lbl", confirmationMessage = null)
			}
		}
		composeRule.waitForIdle()
		assertNull(ShadowToast.getLatestToast())
	}

	@Test
	@Config(sdk = [32])
	fun `confirmation toast fires only after setClipEntry returns`() {
		ShadowToast.reset()
		val gate = CompletableDeferred<Unit>()
		val fakeClipboard = object : Clipboard {
			override val nativeClipboard: NativeClipboard
				get() = error("unused")

			override suspend fun getClipEntry(): ClipEntry? = null

			override suspend fun setClipEntry(clipEntry: ClipEntry?) {
				gate.await()
			}
		}

		lateinit var copier: ClipboardCopier
		composeRule.setContent {
			CompositionLocalProvider(LocalClipboard provides fakeClipboard) {
				copier = rememberClipboardCopier()
			}
		}
		composeRule.runOnIdle {
			copier.copyPlainText("body", label = "lbl", confirmationMessage = "Copied!")
		}
		// setClipEntry is still suspended — toast must not have fired yet
		composeRule.runOnIdle {
			assertNull(ShadowToast.getLatestToast())
		}
		gate.complete(Unit)
		composeRule.waitForIdle()
		assertEquals("Copied!", ShadowToast.getTextOfLatestToast())
	}
}
