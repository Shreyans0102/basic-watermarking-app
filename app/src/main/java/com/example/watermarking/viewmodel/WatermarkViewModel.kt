package com.example.watermarking.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// State containers for UI states
data class ImageWatermarkState(
    val selectedImages: List<Uri> = emptyList(),
    val watermarkedBitmaps: List<Bitmap> = emptyList(),
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class VideoWatermarkState(
    val selectedVideo: Uri? = null,
    val watermarkedVideo: Uri? = null,
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

// Settings data class
data class WatermarkSettings(
    val text: String = "Deepak Babel",  // Changed default to "Deepak Babel"
    val textSize: Float = 100f,         // Changed default to 100f
    val opacity: Float = 0.7f,
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val showAdvancedOptions: Boolean = false
)

// Watermark position enum
enum class WatermarkPosition {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

class WatermarkViewModel(application: Application) : AndroidViewModel(application) {
    
    // Shared watermark settings
    var watermarkSettings by mutableStateOf(WatermarkSettings())
        private set
    
    // Image processing state
    private val _imageState = MutableStateFlow(ImageWatermarkState())
    val imageState: StateFlow<ImageWatermarkState> = _imageState.asStateFlow()
    
    // Video processing state
    private val _videoState = MutableStateFlow(VideoWatermarkState())
    val videoState: StateFlow<VideoWatermarkState> = _videoState.asStateFlow()
    
    // Update watermark text
    fun updateWatermarkText(text: String) {
        watermarkSettings = watermarkSettings.copy(text = text)
    }
    
    // Update text size
    fun updateWatermarkTextSize(size: Float) {
        watermarkSettings = watermarkSettings.copy(textSize = size)
    }
    
    // Update opacity
    fun updateWatermarkOpacity(opacity: Float) {
        watermarkSettings = watermarkSettings.copy(opacity = opacity)
    }
    
    // Update position
    fun updateWatermarkPosition(position: WatermarkPosition) {
        watermarkSettings = watermarkSettings.copy(position = position)
    }
    
    // Toggle advanced options
    fun toggleAdvancedOptions() {
        watermarkSettings = watermarkSettings.copy(
            showAdvancedOptions = !watermarkSettings.showAdvancedOptions
        )
    }
    
    // Set advanced options visibility
    fun setAdvancedOptionsVisibility(visible: Boolean) {
        watermarkSettings = watermarkSettings.copy(showAdvancedOptions = visible)
    }
    
    // Update selected images
    fun updateSelectedImages(uris: List<Uri>) {
        viewModelScope.launch {
            _imageState.value = _imageState.value.copy(
                selectedImages = uris,
                isProcessing = true,
                errorMessage = null
            )
            
            // Process images in background
            try {
                // This would call your ImageWatermarkProcessor methods
                // For now this is a placeholder
                withContext(Dispatchers.Default) {
                    // Processing would happen here
                }
                
                _imageState.value = _imageState.value.copy(
                    isProcessing = false,
                    successMessage = "${uris.size} images processed successfully"
                )
            } catch (e: Exception) {
                _imageState.value = _imageState.value.copy(
                    isProcessing = false,
                    errorMessage = "Error processing images: ${e.message}"
                )
            }
        }
    }
    
    // Clear success message after showing
    fun clearSuccessMessage() {
        _imageState.value = _imageState.value.copy(successMessage = null)
        _videoState.value = _videoState.value.copy(successMessage = null)
    }
    
    // Clear error message after showing
    fun clearErrorMessage() {
        _imageState.value = _imageState.value.copy(errorMessage = null)
        _videoState.value = _videoState.value.copy(errorMessage = null)
    }
}
