package uk.co.appoly.droid.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import uk.co.appoly.droid.ui.viewmodels.MultipartUploadDemoViewModel

/**
 * Demo screen demonstrating the S3 multipart upload functionality.
 *
 * ## Purpose
 * This screen serves as both a test interface and a learning example for implementing
 * multipart uploads using the AppolyDroid S3Uploader library with the BaseRepo pattern.
 *
 * ## Features Demonstrated
 * 1. **Authentication Flow:** Login via BaseRepo repository pattern
 * 2. **File Selection:** Using Android's ActivityResultContracts for file picking
 * 3. **Upload Lifecycle:** Start, pause, resume, and cancel uploads
 * 4. **Progress Observation:** Real-time progress updates via StateFlow
 * 5. **Upload Recovery:** Recovering interrupted uploads after app restart
 * 6. **Debug Logging:** Visual log output for understanding the flow
 *
 * ## Screen Structure
 * ```
 * ┌─────────────────────────────────────┐
 * │          Top App Bar               │
 * ├─────────────────────────────────────┤
 * │       AuthSection                  │  <- Login/logout functionality
 * ├─────────────────────────────────────┤
 * │    FileSelectionSection            │  <- File picker
 * ├─────────────────────────────────────┤
 * │   UploadControlsSection            │  <- Start/pause/resume/cancel
 * ├─────────────────────────────────────┤
 * │      ProgressSection               │  <- Current upload progress
 * ├─────────────────────────────────────┤
 * │       ErrorCard                    │  <- Error display (if any)
 * ├─────────────────────────────────────┤
 * │    AllUploadsSection               │  <- List of all uploads
 * ├─────────────────────────────────────┤
 * │       LogSection                   │  <- Debug log output
 * └─────────────────────────────────────┘
 * ```
 *
 * ## Backend Server
 * This demo connects to a test server at `https://multipart-uploader.on-forge.com`.
 * See the server source at: [https://github.com/appoly/s3-uploader](https://github.com/appoly/s3-uploader)
 *
 * ## State Management
 * All UI state is managed by [MultipartUploadDemoViewModel] and observed via [StateFlow]:
 * - Authentication state (token, loading, errors)
 * - File selection state (URI, filename)
 * - Upload state (session ID, progress, errors)
 * - Debug logs
 *
 * @param navController Navigation controller for back navigation
 * @param viewModel ViewModel managing all screen state and operations
 *
 * @see MultipartUploadDemoViewModel
 * @see uk.co.appoly.droid.data.TestBackendRepository
 * @see uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultipartUploadDemoScreen(
    navController: NavController,
    viewModel: MultipartUploadDemoViewModel = viewModel()
) {
    // Collect all state from ViewModel
    val authToken by viewModel.authToken.collectAsState()
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val uploadError by viewModel.uploadError.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val allUploads by viewModel.allUploads.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()

    val context = LocalContext.current

    // File picker launcher using ActivityResultContracts
    // This is the modern approach for launching activities and receiving results
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Extract filename from content URI using ContentResolver
            val fileName = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "unknown_file"
            viewModel.setSelectedFile(it, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multipart Upload Test") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Authentication Section - Always visible
            AuthSection(
                authToken = authToken,
                email = email,
                password = password,
                isLoggingIn = isLoggingIn,
                loginError = loginError,
                onEmailChange = { viewModel.setEmail(it) },
                onPasswordChange = { viewModel.setPassword(it) },
                onLogin = { viewModel.login() }
            )

            // Authenticated content - Only visible after login
            if (authToken != null) {
                HorizontalDivider()

                // File Selection Section
                FileSelectionSection(
                    selectedFileName = selectedFileName,
                    onSelectFile = { filePickerLauncher.launch("*/*") }
                )

                // Upload Controls Section
                UploadControlsSection(
                    selectedFileName = selectedFileName,
                    currentSessionId = currentSessionId,
                    canPause = viewModel.canPause(),
                    canResume = viewModel.canResume(),
                    canCancel = viewModel.canCancel(),
                    canSimulateCrash = viewModel.canSimulateCrash(),
                    onStartUpload = { viewModel.startUpload() },
                    onPause = { viewModel.pauseUpload() },
                    onResume = { viewModel.resumeUpload() },
                    onCancel = { viewModel.cancelUpload() },
                    onRecover = { viewModel.recoverUploads() },
                    onClear = { viewModel.clearSelection() },
                    onSimulateCrash = { viewModel.simulateCrash() }
                )

                // Current Upload Progress Section
                uploadProgress?.let { progress ->
                    HorizontalDivider()
                    ProgressSection(progress = progress)
                }

                // Error Display Section
                uploadError?.let { error ->
                    ErrorCard(error = error)
                }

                // All Uploads List Section
                if (allUploads.isNotEmpty()) {
                    HorizontalDivider()
                    AllUploadsSection(
                        uploads = allUploads,
                        currentSessionId = currentSessionId,
                        onSelectUpload = { viewModel.selectUpload(it) }
                    )
                }

                // Debug Log Section
                HorizontalDivider()
                LogSection(
                    logs = logMessages,
                    onClear = { viewModel.clearLogs() }
                )
            }
        }
    }
}

/**
 * Authentication section displaying login state and controls.
 *
 * Shows either:
 * - Email/password input fields with login button (when not authenticated)
 * - Success indicator with token preview (when authenticated)
 *
 * @param authToken Current auth token (null if not logged in)
 * @param email Current email input value
 * @param password Current password input value
 * @param isLoggingIn Whether login is in progress
 * @param loginError Error message from failed login attempt
 * @param onEmailChange Callback when email input changes
 * @param onPasswordChange Callback when password input changes
 * @param onLogin Callback when login button is clicked
 */
@Composable
private fun AuthSection(
    authToken: String?,
    email: String,
    password: String,
    isLoggingIn: Boolean,
    loginError: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (authToken != null)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Authentication",
                style = MaterialTheme.typography.titleMedium
            )

            if (authToken != null) {
                // Authenticated state
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Logged in",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "Token: ${authToken.take(30)}...",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                // Unauthenticated state
                Text(
                    text = "Server: multipart-uploader.on-forge.com",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !isLoggingIn,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !isLoggingIn,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onLogin,
                    enabled = !isLoggingIn && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Login")
                }

                // Error display
                loginError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * File selection section with file picker button.
 *
 * Displays:
 * - Currently selected filename (if any)
 * - Button to launch system file picker
 *
 * @param selectedFileName Name of the selected file, or null if none selected
 * @param onSelectFile Callback to launch file picker
 */
@Composable
private fun FileSelectionSection(
    selectedFileName: String?,
    onSelectFile: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "File Selection",
                style = MaterialTheme.typography.titleMedium
            )

            if (selectedFileName != null) {
                Text(
                    text = "Selected: $selectedFileName",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedButton(
                onClick = onSelectFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedFileName != null) "Change File" else "Select File")
            }
        }
    }
}

/**
 * Upload controls section with action buttons.
 *
 * Provides controls for:
 * - Starting a new upload
 * - Pausing an active upload
 * - Resuming a paused/failed upload
 * - Cancelling an upload
 * - Recovering interrupted uploads
 * - Clearing selection
 *
 * Button states are controlled by the [canPause], [canResume], and [canCancel]
 * parameters from the ViewModel.
 *
 * @param selectedFileName Currently selected file name
 * @param currentSessionId Current upload session ID (null if no active upload)
 * @param canPause Whether the pause button should be enabled
 * @param canResume Whether the resume button should be enabled
 * @param canCancel Whether the cancel button should be enabled
 * @param onStartUpload Callback to start upload
 * @param onPause Callback to pause upload
 * @param onResume Callback to resume upload
 * @param onCancel Callback to cancel upload
 * @param onRecover Callback to recover interrupted uploads
 * @param onClear Callback to clear selection
 */
@Composable
private fun UploadControlsSection(
    selectedFileName: String?,
    currentSessionId: String?,
    canPause: Boolean,
    canResume: Boolean,
    canCancel: Boolean,
    canSimulateCrash: Boolean,
    onStartUpload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRecover: () -> Unit,
    onClear: () -> Unit,
    onSimulateCrash: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Upload Controls",
                style = MaterialTheme.typography.titleMedium
            )

            // Start and Pause buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartUpload,
                    enabled = selectedFileName != null && currentSessionId == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }

                OutlinedButton(
                    onClick = onPause,
                    enabled = canPause,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pause")
                }
            }

            // Resume and Cancel buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResume,
                    enabled = canResume,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume")
                }

                OutlinedButton(
                    onClick = onCancel,
                    enabled = canCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Recovery and Clear buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRecover,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Recover Uploads")
                }

                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Selection")
                }
            }

            // Simulate Crash button (for testing recovery)
            OutlinedButton(
                onClick = onSimulateCrash,
                enabled = canSimulateCrash,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Simulate Crash (Test Recovery)")
            }
        }
    }
}

/**
 * Progress section showing current upload status and progress.
 *
 * Displays:
 * - Upload status badge
 * - File name
 * - Progress bar
 * - Percentage and parts completed
 * - Session ID (truncated)
 * - Error message (if failed)
 *
 * Card color changes based on upload status:
 * - Green: COMPLETED
 * - Red: FAILED
 * - Yellow: PAUSED
 * - Gray: Other states
 *
 * @param progress Current upload progress information
 */
@Composable
private fun ProgressSection(progress: MultipartUploadProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (progress.status) {
                UploadSessionStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                UploadSessionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                UploadSessionStatus.PAUSED -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Upload",
                    style = MaterialTheme.typography.titleMedium
                )
                StatusChip(status = progress.status)
            }

            Text(
                text = progress.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            LinearProgressIndicator(
                progress = { progress.overallProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = progress.toProgressString(),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = progress.toPartsString(),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "Session: ${progress.sessionId.take(8)}...",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )

            // Show error message if upload failed
            progress.errorMessage?.let { error ->
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Status chip displaying the upload state with appropriate color.
 *
 * Maps [UploadSessionStatus] to human-readable labels and colors:
 * - PENDING: Orange
 * - IN_PROGRESS: Blue
 * - PAUSED: Yellow
 * - COMPLETING: Green
 * - COMPLETED: Green
 * - FAILED: Red
 * - ABORTED: Gray
 *
 * @param status Current upload status
 */
@Composable
private fun StatusChip(status: UploadSessionStatus) {
    val (backgroundColor, textColor, text) = when (status) {
        UploadSessionStatus.PENDING -> Triple(
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            "Pending"
        )
        UploadSessionStatus.IN_PROGRESS -> Triple(
            Color(0xFFE3F2FD),
            Color(0xFF1565C0),
            "Uploading"
        )
        UploadSessionStatus.PAUSED -> Triple(
            Color(0xFFFFF8E1),
            Color(0xFFF9A825),
            "Paused"
        )
        UploadSessionStatus.COMPLETING -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "Completing"
        )
        UploadSessionStatus.COMPLETED -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "Completed"
        )
        UploadSessionStatus.FAILED -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            "Failed"
        )
        UploadSessionStatus.ABORTED -> Triple(
            Color(0xFFECEFF1),
            Color(0xFF546E7A),
            "Aborted"
        )
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Error card displaying error messages.
 *
 * Shows a warning icon with the error message in an error-colored container.
 *
 * @param error Error message to display
 */
@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Section displaying all tracked uploads.
 *
 * Shows a list of all upload sessions with:
 * - File name
 * - Progress percentage
 * - Status chip
 *
 * Clicking an upload selects it for viewing/managing.
 * The currently selected upload is highlighted.
 *
 * @param uploads List of all upload progress entries
 * @param currentSessionId Currently selected session ID for highlighting
 * @param onSelectUpload Callback when an upload is clicked
 */
@Composable
private fun AllUploadsSection(
    uploads: List<MultipartUploadProgress>,
    currentSessionId: String?,
    onSelectUpload: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "All Uploads (${uploads.size})",
            style = MaterialTheme.typography.titleMedium
        )

        uploads.forEach { upload ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectUpload(upload.sessionId) },
                colors = CardDefaults.cardColors(
                    containerColor = if (upload.sessionId == currentSessionId)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = upload.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = upload.toProgressString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    StatusChip(status = upload.status)
                }
            }
        }
    }
}

/**
 * Debug log section showing timestamped log messages.
 *
 * Displays logs in a terminal-style dark container with:
 * - Green monospace text
 * - Auto-scroll to latest message
 * - Clear button to reset logs
 *
 * Useful for understanding the upload flow and debugging issues.
 *
 * @param logs List of timestamped log messages
 * @param onClear Callback to clear all logs
 */
@Composable
private fun LogSection(
    logs: List<String>,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Logs",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "No logs yet...",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    LazyColumn(state = listState) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                color = Color(0xFF00FF00),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
