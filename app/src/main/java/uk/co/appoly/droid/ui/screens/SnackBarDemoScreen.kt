package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.nav3.LocalNav3Navigator
import uk.co.appoly.droid.nav3.Nav3Screen
import kotlinx.coroutines.launch
import uk.co.appoly.droid.ui.snackbar.AppSnackBar
import uk.co.appoly.droid.ui.snackbar.AppSnackBarColors
import uk.co.appoly.droid.ui.snackbar.LocalAppSnackBarColors
import uk.co.appoly.droid.ui.snackbar.SnackBarType
import uk.co.appoly.droid.ui.snackbar.showSnackbar

@Serializable
data object SnackBarDemoScreen : Nav3Screen {
	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		val navigator = LocalNav3Navigator.current

	val snackbarHostState = remember { SnackbarHostState() }
	val scope = rememberCoroutineScope()

	// Custom colors for demonstration
	val customColors = AppSnackBarColors(
		info = Color(0xFFB421F3),     // Purple
		success = Color(0xFF45E0B3),  // Teal
		error = Color(0xFFF48836)     // Orange
	)

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("AppSnackBar Demo") },
				navigationIcon = {
					IconButton(onClick = { navigator?.pop() }) {
						Text("←")
					}
				}
			)
		},
		snackbarHost = {
			SnackbarHost(hostState = snackbarHostState) { snackbarData ->
				AppSnackBar(snackbarData = snackbarData)
			}
		}
	) { paddingValues ->
		CompositionLocalProvider(
			LocalAppSnackBarColors provides customColors
		) {
			Column(
				modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				Text(
					text = "AppSnackBar Demo",
					style = MaterialTheme.typography.headlineMedium
				)

				Text(
					text = "Demonstrates different snackbar types with custom colors and actions",
					style = MaterialTheme.typography.bodyLarge
				)

				// Basic snackbar types
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							text = "Basic Snackbar Types",
							style = MaterialTheme.typography.titleMedium
						)
						Spacer(modifier = Modifier.height(8.dp))

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							OutlinedButton(
								onClick = {
									scope.launch {
										snackbarHostState.showSnackbar(
											message = "This is an information message",
											type = SnackBarType.Info
										)
									}
								},
								modifier = Modifier.weight(1f)
							) {
								Text("Info")
							}

							OutlinedButton(
								onClick = {
									scope.launch {
										snackbarHostState.showSnackbar(
											message = "Operation completed successfully!",
											type = SnackBarType.Success
										)
									}
								},
								modifier = Modifier.weight(1f)
							) {
								Text("Success")
							}

							OutlinedButton(
								onClick = {
									scope.launch {
										snackbarHostState.showSnackbar(
											message = "An error occurred",
											type = SnackBarType.Error
										)
									}
								},
								modifier = Modifier.weight(1f)
							) {
								Text("Error")
							}
						}
					}
				}

				// Snackbar with actions
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							text = "Snackbar with Actions",
							style = MaterialTheme.typography.titleMedium
						)
						Spacer(modifier = Modifier.height(8.dp))

						var lastAction by remember { mutableStateOf<String?>(null) }

						Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
							Button(
								onClick = {
									scope.launch {
										val result = snackbarHostState.showSnackbar(
											message = "Item deleted from cart",
											actionLabel = "Undo",
											type = SnackBarType.Info
										)
										lastAction = when (result) {
											SnackbarResult.ActionPerformed -> "Undo clicked"
											SnackbarResult.Dismissed -> "Dismissed"
										}
									}
								},
								modifier = Modifier.fillMaxWidth()
							) {
								Text("Show Undo Action")
							}

							Button(
								onClick = {
									scope.launch {
										val result = snackbarHostState.showSnackbar(
											message = "Failed to save changes",
											actionLabel = "Retry",
											type = SnackBarType.Error
										)
										lastAction = when (result) {
											SnackbarResult.ActionPerformed -> "Retry clicked"
											SnackbarResult.Dismissed -> "Dismissed"
										}
									}
								},
								modifier = Modifier.fillMaxWidth()
							) {
								Text("Show Retry Action")
							}

							if (lastAction != null) {
								Text(
									text = "Last action: $lastAction",
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.primary
								)
							}
						}
					}
				}

				// Custom colors demonstration
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							text = "Custom Colors",
							style = MaterialTheme.typography.titleMedium
						)
						Text(
							text = "Using CompositionLocal to provide custom colors",
							style = MaterialTheme.typography.bodyMedium
						)
						Spacer(modifier = Modifier.height(8.dp))

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Text(
								text = "Info: Blue",
								modifier = Modifier.weight(1f),
								color = customColors.info
							)
							Text(
								text = "Success: Green",
								modifier = Modifier.weight(1f),
								color = customColors.success
							)
							Text(
								text = "Error: Red",
								modifier = Modifier.weight(1f),
								color = customColors.error
							)
						}

						Spacer(modifier = Modifier.height(8.dp))

						Button(
							onClick = {
								scope.launch {
									snackbarHostState.showSnackbar(
										message = "Custom colors applied!",
										type = SnackBarType.Success
									)
								}
							},
							modifier = Modifier.fillMaxWidth()
						) {
							Text("Test Custom Colors")
						}
					}
				}

				// Usage example
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(
							text = "Usage Example",
							style = MaterialTheme.typography.titleMedium
						)
						Spacer(modifier = Modifier.height(8.dp))
						Text(
							text = """
                            // Basic usage
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Operation successful!",
                                    type = SnackBarType.Success
                                )
                            }
                            
                            // With action
                            val result = snackbarHostState.showSnackbar(
                                message = "Item deleted",
                                actionLabel = "Undo",
                                type = SnackBarType.Info
                            )
                            
                            // Custom colors
                            CompositionLocalProvider(
                                LocalAppSnackBarColors provides customColors
                            ) {
                                // Your content
                            }
                            """.trimIndent(),
							style = MaterialTheme.typography.bodySmall,
							modifier = Modifier.fillMaxWidth()
						)
					}
				}
			}
		}
	}
}
}
