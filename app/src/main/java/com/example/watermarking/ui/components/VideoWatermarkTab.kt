package com.example.watermarking.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.watermarking.utils.MediaSaver
import com.example.watermarking.utils.VideoCaptureContract
import com.example.watermarking.utils.VideoWatermarkProcessor
import com.example.watermarking.ui.components.shared.WatermarkControls
import android.content.pm.PackageManager // Add this import

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