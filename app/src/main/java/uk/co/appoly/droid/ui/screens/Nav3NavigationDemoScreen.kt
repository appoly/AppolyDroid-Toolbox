package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.nav3.LocalNav3Navigator
import uk.co.appoly.droid.nav3.Nav3ResultReceiver
import uk.co.appoly.droid.nav3.Nav3Screen
import uk.co.appoly.droid.nav3.popUntilWithResult
import uk.co.appoly.droid.nav3.popWithResult

/**
 * Holds demo-only result state outside the `@Serializable` screen key (keys must not store
 * non-serializable body fields). Prefer a ViewModel in production apps.
 */
private object Nav3DemoResultHolder {
	var lastResult by mutableStateOf<String?>(null)
}

/**
 * Live demo of the Nav3Navigation module APIs used by the rest of this showcase app.
 *
 * Covers ambient [LocalNav3Navigator] push/pop, replace / replaceAll / popUntilRoot,
 * stack peek (`canPop` / `lastItem` / `items`), and [popWithResult] / [Nav3ResultReceiver].
 */
@Serializable
data object Nav3NavigationDemoScreen : Nav3Screen, Nav3ResultReceiver {

	override fun onResult(result: Any?) {
		Nav3DemoResultHolder.lastResult = result?.toString()
	}

	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		val navigator = LocalNav3Navigator.current
		val lastResult = Nav3DemoResultHolder.lastResult
		val canPop = navigator?.canPop == true
		val depth = navigator?.items?.size ?: 0
		val top = navigator?.lastItem?.let { it::class.simpleName } ?: "—"

		Scaffold(
			topBar = {
				TopAppBar(
					title = { Text("Nav3 Navigation") },
					navigationIcon = {
						IconButton(onClick = { navigator?.pop() }) {
							Text("←")
						}
					},
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
					text = "This showcase app is hosted on Nav3ScreenHost. Destinations are " +
						"@Serializable Nav3Screen objects; push/pop go through LocalNav3Navigator.",
					style = MaterialTheme.typography.bodyMedium,
				)

				Card(modifier = Modifier.fillMaxWidth()) {
					Column(
						modifier = Modifier.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Text("Stack peek", style = MaterialTheme.typography.titleMedium)
						Text("canPop: $canPop")
						Text("items.size: $depth")
						Text("lastItem: $top")
						Text("last result: ${lastResult ?: "none yet"}")
					}
				}

				Button(
					onClick = { navigator?.push(Nav3StackProbeScreen(label = "A")) },
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("push(StackProbe A)")
				}

				Button(
					onClick = {
						navigator?.push(
							Nav3StackProbeScreen(label = "B"),
							Nav3StackProbeScreen(label = "C"),
						)
					},
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("push(B, C) — multi-screen")
				}

				Button(
					onClick = { navigator?.push(Nav3PickerScreen) },
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("push(Picker) → popWithResult")
				}

				OutlinedButton(
					onClick = { navigator?.replace(Nav3StackProbeScreen(label = "Replaced")) },
					modifier = Modifier.fillMaxWidth(),
					enabled = canPop,
				) {
					Text("replace(top)")
				}

				OutlinedButton(
					onClick = { navigator?.popUntilRoot() },
					modifier = Modifier.fillMaxWidth(),
					enabled = canPop,
				) {
					Text("popUntilRoot()")
				}

				OutlinedButton(
					onClick = { navigator?.pop() },
					modifier = Modifier.fillMaxWidth(),
					enabled = canPop,
				) {
					Text("pop()")
				}
			}
		}
	}
}

@Serializable
data class Nav3StackProbeScreen(val label: String) : Nav3Screen {
	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		val navigator = LocalNav3Navigator.current
		val depth = navigator?.items?.size
		val canPop = navigator?.canPop

		Scaffold(
			topBar = {
				TopAppBar(
					title = { Text("Stack probe $label") },
					navigationIcon = {
						IconButton(onClick = { navigator?.pop() }) {
							Text("←")
						}
					},
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
					text = "Label: $label",
					style = MaterialTheme.typography.bodyLarge,
				)
				Text(text = "Depth: $depth")
				Text(text = "canPop: $canPop")

				Button(
					onClick = {
						val next = ((label.firstOrNull()?.code ?: 'A'.code) + 1).toChar().toString()
						navigator?.push(Nav3StackProbeScreen(label = next))
					},
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("push deeper")
				}

				Button(
					onClick = {
						navigator?.popUntilWithResult("from $label") { screen ->
							screen is Nav3NavigationDemoScreen
						}
					},
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("popUntilWithResult → demo root")
				}

				OutlinedButton(
					onClick = { navigator?.replaceAll(HomeScreen) },
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("replaceAll(HomeScreen)")
				}
			}
		}
	}
}

@Serializable
data object Nav3PickerScreen : Nav3Screen {
	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		val navigator = LocalNav3Navigator.current
		var choice by remember { mutableStateOf("Option 1") }

		Scaffold(
			topBar = {
				TopAppBar(
					title = { Text("Picker") },
					navigationIcon = {
						IconButton(onClick = { navigator?.pop() }) {
							Text("←")
						}
					},
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
					text = "Pick a value and popWithResult — delivered to the previous Nav3ResultReceiver.",
				)
				listOf("Option 1", "Option 2", "Option 3").forEach { option ->
					OutlinedButton(
						onClick = { choice = option },
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(if (choice == option) "✓ $option" else option)
					}
				}
				Button(
					onClick = { navigator?.popWithResult(choice) },
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("popWithResult(\"$choice\")")
				}
			}
		}
	}
}
