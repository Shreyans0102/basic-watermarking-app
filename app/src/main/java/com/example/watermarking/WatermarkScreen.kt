package com.example.watermarking

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.watermarking.utils.ImageWatermarkProcessor
import com.example.watermarking.utils.MediaSaver
import com.example.watermarking.utils.VideoCaptureContract
import com.example.watermarking.utils.VideoWatermarkProcessor
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Custom camera contract that skips the confirmation screen
class TakePictureNoConfirmation : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return super.createIntent(context, input).apply {
            putExtra("android.intent.extra.QUICK_CAPTURE", true)
            putExtra("quickCapture", true)
        }
    }
}

// ImageSaver class for backward compatibility with existing code
// This can be removed in a later version when all code has been migrated to use MediaSaver
class ImageSaver(private val context: Context) {
    private val mediaSaver = MediaSaver(context)
    
    fun saveImageToGallery(bitmap: Bitmap): String? {
        return mediaSaver.saveImageToGallery(bitmap)
    }
}

fun addTextWatermark(bitmap: Bitmap, text: String, textSize: Float = 40f): Bitmap {
    return ImageWatermarkProcessor.addTextWatermark(bitmap, text, textSize)
}

// Main Composable function
@Composable
fun WatermarkScreen() {
    var currentTab by remember { mutableStateOf(0) }
    val tabs = listOf("Images", "Videos")
    
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = currentTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = currentTab == index,
                    onClick = { currentTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        when (currentTab) {
            0 -> ImageWatermarkTab()
            1 -> VideoWatermarkTab()
        }
    }
}

@Composable
fun ImageWatermarkTab() {
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var watermarkedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var watermarkText by remember { mutableStateOf("Deepak Babel") }
    var watermarkTextSize by remember { mutableStateOf(100f) }
    val context = LocalContext.current

    // Add this new state variable for camera URI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val mediaSaver = remember { MediaSaver(context) }
    
    // Helper function to create image URI with error handling
    fun createImageUri(): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Watermarked_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WatermarkApp")
            }
        }
        
        return try {
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create image file: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            null
        }
    }
    
    // New camera launcher
    val takePhotoLauncher = rememberLauncherForActivityResult(
        TakePictureNoConfirmation()
    ) { success ->
        if (success) {
            try {
                cameraImageUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bitmap ->
                            val watermarkedBitmap = ImageWatermarkProcessor.addTextWatermark(bitmap, watermarkText, watermarkTextSize)
                            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                                watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                            }
                            // Add to display list
                            watermarkedBitmaps = watermarkedBitmaps + watermarkedBitmap
                            Toast.makeText(context, "Photo captured and watermarked", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraImageUri = createImageUri()
                cameraImageUri?.let { uri -> 
                    takePhotoLauncher.launch(uri)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            watermarkedBitmaps.forEach { bitmap ->
                mediaSaver.saveImageToGallery(bitmap)
            }
        } else {
            Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // Image picker launcher (multiple selection)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedImageUris = uris
        watermarkedBitmaps = uris.mapNotNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let {
                    ImageWatermarkProcessor.addTextWatermark(it, watermarkText, watermarkTextSize)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            WatermarkControls(
                watermarkText = watermarkText,
                onWatermarkTextChange = { watermarkText = it },
                watermarkTextSize = watermarkTextSize,
                onWatermarkTextSizeChange = { watermarkTextSize = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Images")
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraImageUri = createImageUri()
                        cameraImageUri?.let { uri -> 
                            takePhotoLauncher.launch(uri)
                        }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Take Photo with Watermark")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        items(watermarkedBitmaps) { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Watermarked Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(vertical = 8.dp)
            )
        }

        item {
            if (watermarkedBitmaps.isNotEmpty()) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                            if (ContextCompat.checkSelfPermission(
                                    context, permission
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                watermarkedBitmaps.forEach { bitmap ->
                                    mediaSaver.saveImageToGallery(bitmap)
                                }
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        } else {
                            watermarkedBitmaps.forEach { bitmap ->
                                mediaSaver.saveImageToGallery(bitmap)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("Save All (${watermarkedBitmaps.size}) Images")
                }
            }
        }
    }
}

@Composable
fun VideoWatermarkTab() {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkText by remember { mutableStateOf("Deepak Babel") }
    var watermarkTextSize by remember { mutableStateOf(100f) }
    val context = LocalContext.current
    val mediaSaver = remember { MediaSaver(context) }
    val videoProcessor = remember { VideoWatermarkProcessor(context) }
    
    // Video recorder URI
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var processingError by remember { mutableStateOf<String?>(null) }
    
    // Add a processing state
    var isProcessing by remember { mutableStateOf(false) }
    
    // Helper function to create video URI with error handling
    fun createVideoUri(): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Watermarked_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/WatermarkApp")
            }
        }
        
        return try {
            context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create video file: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            null
        }
    }
    
    // Video recorder launcher
    val videoRecorderLauncher = rememberLauncherForActivityResult(
        VideoCaptureContract()
    ) { success ->
        if (success) {
            videoUri?.let { uri ->
                selectedVideoUri = uri
                // Process video
                Toast.makeText(context, "Processing video...", Toast.LENGTH_SHORT).show()
                isProcessing = true
                processingError = null
                
                Thread {
                    try {
                        val result = videoProcessor.addWatermarkToVideo(uri, watermarkText, watermarkTextSize)
                        (context as? ComponentActivity)?.runOnUiThread {
                            watermarkedVideoUri = result
                            isProcessing = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        (context as? ComponentActivity)?.runOnUiThread {
                            processingError = e.message
                            isProcessing = false
                            Toast.makeText(context, "Failed to process video: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
        } else {
            Toast.makeText(context, "Failed to record video", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Video camera and audio permissions
    val videoPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            videoUri = createVideoUri()
            videoUri?.let { uri ->
                videoRecorderLauncher.launch(uri)
            }
        } else {
            Toast.makeText(
                context,
                "Camera and audio permissions are required for video recording",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && watermarkedVideoUri != null) {
            watermarkedVideoUri?.let {
                mediaSaver.saveVideoToGallery(it)
            }
        } else {
            Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            // Process video in a separate thread to avoid UI freezes
            Toast.makeText(context, "Processing video...", Toast.LENGTH_SHORT).show()
            isProcessing = true
            processingError = null
            
            Thread {
                try {
                    val result = videoProcessor.addWatermarkToVideo(it, watermarkText, watermarkTextSize)
                    (context as? ComponentActivity)?.runOnUiThread {
                        watermarkedVideoUri = result
                        isProcessing = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    (context as? ComponentActivity)?.runOnUiThread {
                        processingError = e.message
                        isProcessing = false
                        Toast.makeText(context, "Failed to process video: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            WatermarkControls(
                watermarkText = watermarkText,
                onWatermarkTextChange = { watermarkText = it },
                watermarkTextSize = watermarkTextSize,
                onWatermarkTextSizeChange = { watermarkTextSize = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { videoPickerLauncher.launch("video/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Video")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    // Request camera and audio permissions
                    val permissions = arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                    
                    val allPermissionsGranted = permissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    
                    if (allPermissionsGranted) {
                        videoUri = createVideoUri()
                        videoUri?.let { uri ->
                            videoRecorderLauncher.launch(uri)
                        }
                    } else {
                        videoPermissionsLauncher.launch(permissions)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Record Video with Watermark")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            if (isProcessing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Processing video... This may take a while.")
                }
            }
            
            if (processingError != null) {
                Text(
                    "Error processing video: $processingError",
                    color = androidx.compose.ui.graphics.Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            watermarkedVideoUri?.let { uri ->
                val player = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(uri))
                        playWhenReady = false // Don't auto-play
                        prepare()
                    }
                }
                
                DisposableEffect(uri) { // Use uri as the key instead of Unit
                    onDispose {
                        player.release()
                    }
                }
                
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 8.dp),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                            if (ContextCompat.checkSelfPermission(
                                    context, permission
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                mediaSaver.saveVideoToGallery(uri)
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        } else {
                            mediaSaver.saveVideoToGallery(uri)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Watermarked Video")
                }
            }
        }
    }
}

@Composable
fun WatermarkControls(
    watermarkText: String,
    onWatermarkTextChange: (String) -> Unit,
    watermarkTextSize: Float,
    onWatermarkTextSizeChange: (Float) -> Unit
) {
    TextField(
        value = watermarkText,
        onValueChange = onWatermarkTextChange,
        label = { Text("Watermark Text") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        "Watermark Size: ${watermarkTextSize.toInt()}px",
        modifier = Modifier.padding(top = 8.dp)
    )

    Slider(
        value = watermarkTextSize,
        onValueChange = onWatermarkTextSizeChange,
        valueRange = 20f..100f,
        modifier = Modifier.fillMaxWidth()
    )
}
