package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.nav3.LocalNav3Navigator
import uk.co.appoly.droid.nav3.Nav3Screen
import uk.co.appoly.droid.ui.UiState
import uk.co.appoly.droid.ui.isError
import uk.co.appoly.droid.ui.isLoading
import uk.co.appoly.droid.ui.isSuccess
import uk.co.appoly.droid.ui.viewmodels.UiStateDemoViewModel

@Serializable
data object UiStateDemoScreen : Nav3Screen {
	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		val navigator = LocalNav3Navigator.current

	val viewModel: UiStateDemoViewModel = viewModel()
	val uiState by viewModel.uiState.collectAsState()
	val multiOperationState by viewModel.multiOperationState.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("UI State Management Demo") },
				navigationIcon = {
					IconButton(onClick = { navigator?.pop() }) {
						Text("←")
					}
				}
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.padding(16.dp)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text(
				text = "UiState Demo",
				style = MaterialTheme.typography.headlineMedium
			)

			Text(
				text = "Demonstrates the UiState sealed class with smart casting extensions",
				style = MaterialTheme.typography.bodyLarge
			)

			// Single operation demo
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Single Operation State",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					// State display with smart casting
					when {
						uiState.isLoading() -> {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								CircularProgressIndicator(modifier = Modifier.size(20.dp))
								Text("Loading...")
							}
						}

						uiState.isError() -> {
							val errorState = uiState as UiState.Error
							Column {
								Text(
									text = "Error: ${errorState.message}",
									color = MaterialTheme.colorScheme.error
								)
								Button(onClick = { viewModel.resetState() }) {
									Text("Reset")
								}
							}
						}

						uiState.isSuccess() -> {
							Column {
								Text(
									text = "Success!",
									color = MaterialTheme.colorScheme.primary
								)
								Button(onClick = { viewModel.resetState() }) {
									Text("Reset")
								}
							}
						}

						else -> {
							Text("Idle - Click a button below to see state changes")
						}
					}

					Spacer(modifier = Modifier.height(16.dp))

					// Action buttons
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						OutlinedButton(
							onClick = { viewModel.simulateLoading() },
							modifier = Modifier.weight(1f)
						) {
							Text("Loading")
						}
						OutlinedButton(
							onClick = { viewModel.simulateSuccess() },
							modifier = Modifier.weight(1f)
						) {
							Text("Success")
						}
						OutlinedButton(
							onClick = { viewModel.simulateError() },
							modifier = Modifier.weight(1f)
						) {
							Text("Error")
						}
					}
				}
			}

			// Multi-operation demo
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Multi-Operation State",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "Track multiple operations with different keys",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					// Profile operation
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.fillMaxWidth()
					) {
						Text("Profile: ", modifier = Modifier.weight(1f))
						when {
							multiOperationState.isLoading() && multiOperationState.key == "profile" -> {
								Row(verticalAlignment = Alignment.CenterVertically) {
									CircularProgressIndicator(modifier = Modifier.size(16.dp))
									Text("Loading...", modifier = Modifier.padding(start = 4.dp))
								}
							}

							multiOperationState.isSuccess() && multiOperationState.key == "profile" -> {
								Text("✅ Loaded", color = MaterialTheme.colorScheme.primary)
							}

							else -> {
								Text("Idle")
							}
						}
					}

					// Posts operation
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.fillMaxWidth()
					) {
						Text("Posts: ", modifier = Modifier.weight(1f))
						when {
							multiOperationState.isLoading() && multiOperationState.key == "posts" -> {
								Row(verticalAlignment = Alignment.CenterVertically) {
									CircularProgressIndicator(modifier = Modifier.size(16.dp))
									Text("Loading...", modifier = Modifier.padding(start = 4.dp))
								}
							}

							multiOperationState.isError() && multiOperationState.key == "posts" -> {
								val errorState = multiOperationState as UiState.Error
								Text("❌ ${errorState.message}", color = MaterialTheme.colorScheme.error)
							}

							else -> {
								Text("Idle")
							}
						}
					}

					Spacer(modifier = Modifier.height(16.dp))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Button(
							onClick = { viewModel.loadUserProfile() },
							modifier = Modifier.weight(1f)
						) {
							Text("Load Profile")
						}
						Button(
							onClick = { viewModel.loadUserPosts() },
							modifier = Modifier.weight(1f)
						) {
							Text("Load Posts")
						}
						OutlinedButton(
							onClick = { viewModel.resetMultiState() },
							modifier = Modifier.weight(1f)
						) {
							Text("Reset")
						}
					}
				}
			}

			// Code example
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Usage Example",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = """
                        // Smart casting with extension functions
                        if (uiState.isError()) {
                            // uiState is automatically cast to UiState.Error
                            showError(uiState.message)
                        } else if (uiState.isLoading()) {
                            // uiState is automatically cast to UiState.Loading
                            showLoading()
                        }
                        
                        // Multi-operation tracking
                        UiState.Loading(key = "profile")
                        UiState.Success(key = "profile")
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
