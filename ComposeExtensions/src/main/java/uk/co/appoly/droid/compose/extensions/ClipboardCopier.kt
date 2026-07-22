package uk.co.appoly.droid.compose.extensions

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.launch

/**
 * Copies a [ClipEntry] to the system clipboard and, on Android < 13, shows a confirmation Toast.
 * Android 13+ surfaces its own clipboard confirmation, so we suppress ours to avoid a double toast.
 *
 * Obtain one with [rememberClipboardCopier]. It centralises the [LocalClipboard] plumbing:
 * `setClipEntry` is a suspend function, so each copy runs on a remembered scope and the confirmation
 * only fires once the write has returned. The payload is a raw [ClipEntry], so it handles text, a
 * URI, multiple items, etc.; use [copyPlainText] for the common plain-text case.
 */
fun interface ClipboardCopier {
	/**
	 * @param clipEntry the payload to copy.
	 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
	 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
	 */
	fun copy(clipEntry: ClipEntry, confirmationMessage: String?)
}

/**
 * Copies plain [text] to the clipboard — the common case (wraps [ClipData.newPlainText]).
 *
 * @param text the text to copy.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyPlainText(
	text: CharSequence,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newPlainText(label, text).toClipEntry(), confirmationMessage)

/**
 * Copies styled [htmlText] to the clipboard, with [text] as the plain-text fallback for consumers
 * that can't render HTML (wraps [ClipData.newHtmlText]).
 *
 * @param text the plain-text representation.
 * @param htmlText the HTML-markup representation.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyHtmlText(
	text: CharSequence,
	htmlText: String,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newHtmlText(label, text, htmlText).toClipEntry(), confirmationMessage)

/**
 * Copies a raw [uri] to the clipboard without resolving it through a [ContentResolver]
 * (wraps [ClipData.newRawUri]). Use for URIs that aren't `content://` provider URIs — e.g. an
 * `http`/`https` link or a `mailto:` address; for content URIs use [copyUri] instead.
 *
 * @param uri the URI to copy verbatim.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyRawUri(
	uri: Uri,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newRawUri(label, uri).toClipEntry(), confirmationMessage)

/**
 * Copies a `content://` [uri] to the clipboard, querying its available MIME types from [resolver]
 * so pasting apps receive the right type (wraps [ClipData.newUri]). For plain web/mail URIs prefer
 * [copyRawUri].
 *
 * @param resolver resolves the URI's MIME types.
 * @param uri the content URI to copy.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyUri(
	resolver: ContentResolver,
	uri: Uri,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newUri(resolver, label, uri).toClipEntry(), confirmationMessage)

/**
 * Copies an [intent] to the clipboard (wraps [ClipData.newIntent]) — e.g. a launcher shortcut.
 *
 * @param intent the Intent to copy.
 * @param label human-readable [ClipData] label (required, mirroring `ClipData.newX`); read by
 *   clipboard managers and accessibility services, though not shown in the modern paste UI.
 * @param confirmationMessage pre-Android-13 Toast text; pass `null` to skip it. Resolve it in
 *   composition (e.g. `stringResource`) so a configuration change re-reads it.
 */
fun ClipboardCopier.copyIntent(
	intent: Intent,
	label: CharSequence,
	confirmationMessage: String? = null,
) = copy(ClipData.newIntent(label, intent).toClipEntry(), confirmationMessage)

/**
 * Remembers a [ClipboardCopier] bound to the current [LocalClipboard] / [LocalContext] and a
 * composition-scoped coroutine scope. Call one of the `copyX` extensions (e.g. [copyPlainText]) on
 * the result to copy — see [ClipboardCopier] for the confirmation-Toast behaviour.
 */
@Composable
fun rememberClipboardCopier(): ClipboardCopier {
	val clipboard = LocalClipboard.current
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	return remember(clipboard, context, scope) {
		ClipboardCopier { clipEntry, confirmationMessage ->
			scope.launch {
				clipboard.setClipEntry(clipEntry)
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && confirmationMessage != null) {
					Toast.makeText(context, confirmationMessage, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}
}
