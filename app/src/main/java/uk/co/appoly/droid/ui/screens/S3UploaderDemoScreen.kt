package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import uk.co.appoly.droid.s3upload.UploadResult
import uk.co.appoly.droid.ui.viewmodels.S3UploaderDemoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S3UploaderDemoScreen(navController: NavController) {
	val viewModel: S3UploaderDemoViewModel = viewModel()
	val uploadProgress by viewModel.uploadProgress.collectAsState()
	val uploadResult by viewModel.uploadResult.collectAsState()
	val isUploading by viewModel.isUploading.collectAsState()

	var fileName by remember { mutableStateOf("sample_image.jpg") }
	var fileSize by remember { mutableStateOf("2048000") } // 2MB default

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("S3 Uploader Demo") },
				navigationIcon = {
					IconButton(onClick = { navController.navigateUp() }) {
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
				text = "S3 Uploader Demo",
				style = MaterialTheme.typography.headlineMedium
			)

			Text(
				text = "Demonstrates file upload to AWS S3 with progress tracking and error handling",
				style = MaterialTheme.typography.bodyLarge
			)

			// File configuration
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "File Configuration",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					OutlinedTextField(
						value = fileName,
						onValueChange = { fileName = it },
						label = { Text("File Name") },
						modifier = Modifier.fillMaxWidth(),
						supportingText = {
							Text("Try names with 'error' or 'network' to simulate failures")
						}
					)

					OutlinedTextField(
						value = fileSize,
						onValueChange = { fileSize = it },
						label = { Text("File Size (bytes)") },
						modifier = Modifier.fillMaxWidth(),
						supportingText = {
							Text("Files > 10MB will fail. Try 10485760 for large file test")
						}
					)
				}
			}

			// Upload simulation
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Upload Simulation",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					// Progress indicator
					uploadProgress?.let { progress ->
						if (isUploading && progress in 0.0..1.0) {
							Column {
								Text("Upload Progress: ${(progress * 100).toInt()}%")
								LinearProgressIndicator(
									progress = { progress },
									modifier = Modifier.fillMaxWidth()
								)
							}
							Spacer(modifier = Modifier.height(8.dp))
						}
					}

					// Result display
					when (val result = uploadResult) {
						is UploadResult.Success -> {
							Column {
								Text("✅ Upload Successful!")
								Text("File path: ${result.filePath}")
							}
						}

						is UploadResult.Error -> {
							Column {
								Text("❌ Upload Failed")
								Text("Error: ${result.message}")
							}
						}

						null -> {
							if (isUploading) {
								Text("🔄 Uploading...")
							} else {
								Text("Ready to upload")
							}
						}
					}

					Spacer(modifier = Modifier.height(16.dp))

					// Action buttons
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Button(
							onClick = {
								val size = fileSize.toLongOrNull() ?: 2048000L
								viewModel.simulateUpload(fileName, size)
							},
							modifier = Modifier.weight(1f),
							enabled = !isUploading
						) {
							Text("Start Upload")
						}

						OutlinedButton(
							onClick = { viewModel.reset() },
							modifier = Modifier.weight(1f),
							enabled = !isUploading
						) {
							Text("Reset")
						}
					}
				}
			}

			// Test scenarios
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Test Scenarios",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						TestScenarioButton(
							title = "Successful Upload",
							description = "Normal file upload",
							onClick = { viewModel.simulateUpload("test_file.jpg", 1024000L) }
						)

						TestScenarioButton(
							title = "Large File Error",
							description = "File size > 10MB",
							onClick = { viewModel.simulateUpload("large_file.zip", 15 * 1024 * 1024L) }
						)

						TestScenarioButton(
							title = "API Error",
							description = "Filename contains 'error'",
							onClick = { viewModel.simulateUpload("test_error_file.jpg", 512000L) }
						)

						TestScenarioButton(
							title = "Network Error",
							description = "Filename contains 'network'",
							onClick = { viewModel.simulateUpload("network_issue_file.jpg", 768000L) }
						)
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
                        // Initialize S3Uploader
                        S3Uploader.initS3Uploader(
                            authTokenProvider = { "your-auth-token" },
                            loggingLevel = LoggingLevel.DEBUG
                        )
                        
                        // Upload file with progress tracking
                        val progressFlow = MutableStateFlow<Float>(0f)
                        val result = S3Uploader.uploadFileAsync(
                            file = File("path/to/file.jpg"),
                            getPresignedUrlAPI = "https://api.example.com/presigned-url",
                            progressFlow = progressFlow
                        ).await()
                        
                        // Handle result
                        when (result) {
                            is UploadResult.Success -> showSuccess(result.filePath)
                            is UploadResult.Error -> showError(result.message)
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

@Composable
private fun TestScenarioButton(
	title: String,
	description: String,
	onClick: () -> Unit
) {
	OutlinedButton(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
		contentPadding = PaddingValues(12.dp)
	) {
		Column(
			horizontalAlignment = Alignment.Start,
			modifier = Modifier.fillMaxWidth()
		) {
			Text(text = title, style = MaterialTheme.typography.titleSmall)
			Text(
				text = description,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}