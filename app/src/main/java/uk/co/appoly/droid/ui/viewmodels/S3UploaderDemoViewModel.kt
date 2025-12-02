package uk.co.appoly.droid.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.appoly.droid.s3upload.UploadResult

class S3UploaderDemoViewModel : ViewModel() {

	private val _uploadProgress = MutableStateFlow<Float?>(null)
	val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

	private val _uploadResult = MutableStateFlow<UploadResult?>(null)
	val uploadResult: StateFlow<UploadResult?> = _uploadResult.asStateFlow()

	private val _isUploading = MutableStateFlow(false)
	val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

	// Mock upload simulation
	fun simulateUpload(fileName: String, fileSize: Long) {
		_isUploading.value = true
		_uploadProgress.value = 0f
		_uploadResult.value = null

		viewModelScope.launch {
			try {
				// Simulate upload progress
				val totalSteps = 100
				val delayPerStep = 50L // 50ms per percent

				for (step in 1..totalSteps) {
					delay(delayPerStep)
					_uploadProgress.value = step.toFloat() / totalSteps

					// Simulate occasional slowdowns
					if (step % 25 == 0) {
						delay(200)
					}
				}

				// Simulate different outcomes based on file size
				delay(500)

				when {
					fileSize > 10 * 1024 * 1024 -> { // > 10MB
						// Simulate failure for large files
						_uploadResult.value = UploadResult.Error("File too large (max 10MB)")
					}

					fileName.contains("error") -> {
						// Simulate API error
						_uploadResult.value = UploadResult.Error("Upload failed: Invalid credentials")
					}

					fileName.contains("network") -> {
						// Simulate network error
						_uploadResult.value = UploadResult.Error("Network connection lost")
					}

					else -> {
						// Successful upload
						val mockPath = "uploads/${System.currentTimeMillis()}/$fileName"
						_uploadResult.value = UploadResult.Success(mockPath)
					}
				}
			} catch (e: Exception) {
				_uploadResult.value = UploadResult.Error("Unexpected error: ${e.message}")
			} finally {
				_isUploading.value = false
				_uploadProgress.value = null
			}
		}
	}

	fun reset() {
		_uploadProgress.value = null
		_uploadResult.value = null
		_isUploading.value = false
	}
}