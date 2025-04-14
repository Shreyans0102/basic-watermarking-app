@file:OptIn(ExperimentalAnimationApi::class)

package com.example.watermarking.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.watermarking.ui.components.shared.EmptyStateView
import com.example.watermarking.ui.components.shared.WatermarkControls
import com.example.watermarking.utils.MediaSaver
import com.example.watermarking.utils.VideoCaptureContract
import com.example.watermarking.utils.VideoWatermarkProcessor
import com.example.watermarking.viewmodel.WatermarkViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Fix coroutine scope issue
private val uiScope = CoroutineScope(Dispatchers.Main)

@Composable
fun VideoWatermarkTab(viewModel: WatermarkViewModel = viewModel()) {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkedVideoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val mediaSaver = remember { MediaSaver(context) }
    val videoProcessor = remember { VideoWatermarkProcessor(context) }
    val watermarkSettings = viewModel.watermarkSettings
    
    // Video recorder URI
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var processingError by remember { mutableStateOf<String?>(null) }
    
    // Add processing states
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    
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
                processingProgress = 0f
                processingError = null
                
                // Simulate progress
                uiScope.launch {
                    for (i in 1..20) {
                        delay(300)
                        processingProgress = i / 20f
                    }
                }
                
                Thread {
                    try {
                        val result = videoProcessor.addWatermarkToVideo(
                            uri, 
                            watermarkSettings.text, 
                            watermarkSettings.textSize
                        )
                        (context as? ComponentActivity)?.runOnUiThread {
                            watermarkedVideoUri = result
                            isProcessing = false
                            processingProgress = 1f
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
            showPermissionDialog = true
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && watermarkedVideoUri != null) {
            watermarkedVideoUri?.let {
                mediaSaver.saveVideoToGallery(it)
                Toast.makeText(context, "Video saved to gallery", Toast.LENGTH_SHORT).show()
            }
        } else {
            showPermissionDialog = true
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
            processingProgress = 0f
            processingError = null
            
            // Simulate progress
            uiScope.launch {
                for (i in 1..20) {
                    delay(300)
                    processingProgress = i / 20f
                }
            }
            
            Thread {
                try {
                    val result = videoProcessor.addWatermarkToVideo(
                        it, 
                        watermarkSettings.text, 
                        watermarkSettings.textSize
                    )
                    (context as? ComponentActivity)?.runOnUiThread {
                        watermarkedVideoUri = result
                        isProcessing = false
                        processingProgress = 1f
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
                watermarkText = watermarkSettings.text,
                onWatermarkTextChange = viewModel::updateWatermarkText,
                watermarkTextSize = watermarkSettings.textSize,
                onWatermarkTextSizeChange = viewModel::updateWatermarkTextSize,
                watermarkOpacity = watermarkSettings.opacity,
                onWatermarkOpacityChange = viewModel::updateWatermarkOpacity,
                showAdvancedOptions = watermarkSettings.showAdvancedOptions,
                onShowAdvancedOptionsChange = viewModel::setAdvancedOptionsVisibility
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Select video button
                ElevatedButton(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Video Gallery",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Video")
                }
                
                // Record video button
                ElevatedButton(
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
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Record Video",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record Video")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Processing indicator
        item {
            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            progress = processingProgress
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "Processing video...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Please wait. This may take a while depending on video size.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = processingProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${(processingProgress * 100).toInt()}% complete",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
            
        // Error message
        item {
            AnimatedVisibility(
                visible = processingError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            "Error processing video: $processingError",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        // Empty state
        item {
            if (watermarkedVideoUri == null && selectedVideoUri == null && !isProcessing) {
                EmptyStateView(
                    icon = Icons.Outlined.Videocam,
                    title = "No Video Selected",
                    message = "Select a video from your gallery or record a new one to add a watermark",
                    actionText = "Select Video",
                    onAction = { videoPickerLauncher.launch("video/*") }
                )
            }
        }
            
        // Video player
        item {
            watermarkedVideoUri?.let { uri ->
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Watermarked Video",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val player = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(uri))
                        playWhenReady = false
                        prepare()
                        
                        addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(playing: Boolean) {
                                isPlaying = playing
                            }
                        })
                    }
                }
                
                DisposableEffect(uri) {
                    onDispose {
                        player.release()
                    }
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f/9f),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = true
                                }
                            }
                        )
                    }
                }
                
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
                                Toast.makeText(context, "Video saved to gallery", Toast.LENGTH_SHORT).show()
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        } else {
                            mediaSaver.saveVideoToGallery(uri)
                            Toast.makeText(context, "Video saved to gallery", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Watermarked Video")
                }
            }
        }
    }
    
    // Permission denial dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permissions Required") },
            text = { Text("Camera and audio permissions are required for recording videos. Please enable them in app settings.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}