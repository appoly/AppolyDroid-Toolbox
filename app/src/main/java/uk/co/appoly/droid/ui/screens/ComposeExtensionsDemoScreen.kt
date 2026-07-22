package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.co.appoly.droid.compose.extensions.copyPlainText
import uk.co.appoly.droid.compose.extensions.rememberClipboardCopier
import uk.co.appoly.droid.compose.extensions.serializableMutableStateOf
import uk.co.appoly.droid.compose.extensions.transientMutableStateOf

/**
 * Demonstrates the ComposeExtensions module: the serialization-safe [serializableMutableStateOf] /
 * [transientMutableStateOf] state holders and the [rememberClipboardCopier] clipboard copier.
 *
 * Beyond the showcase, this screen exists so R8 retains `SerializableMutableState` /
 * `TransientMutableState` in the minified demo app, which is what lets `verifyConsumerKeepRules`
 * prove ComposeExtensions' consumer keep rules for their `writeObject`/`readObject` members fire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeExtensionsDemoScreen(navController: NavController) {
	// Persisted across process death — a real one-shot guard would use this.
	var tapCount by serializableMutableStateOf(0)
	// Ephemeral — resets to the initial value on a process-death restore.
	var lastCopied by transientMutableStateOf<String?>(null)
	val clipboardCopier = rememberClipboardCopier()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Compose Extensions") },
			)
		},
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				text = "serializableMutableStateOf survives process death; " +
					"transientMutableStateOf resets to its initial value on restore.",
				style = MaterialTheme.typography.bodyMedium,
			)

			Text(
				text = "Persisted tap count: $tapCount",
				style = MaterialTheme.typography.titleMedium,
			)

			Button(
				onClick = { tapCount++ },
				modifier = Modifier.fillMaxWidth(),
			) {
				Text("Increment persisted count")
			}

			Text(
				text = "Last copied (transient): ${lastCopied ?: "nothing yet"}",
				style = MaterialTheme.typography.bodyMedium,
			)

			Button(
				onClick = {
					val text = "Tap count is $tapCount"
					clipboardCopier.copyPlainText(
						text = text,
						label = "Tap count",
						confirmationMessage = "Copied!",
					)
					lastCopied = text
				},
				modifier = Modifier.fillMaxWidth(),
			) {
				Text("Copy tap count to clipboard")
			}

			Button(
				onClick = { navController.popBackStack() },
				modifier = Modifier.fillMaxWidth(),
			) {
				Text("Back")
			}
		}
	}
}
