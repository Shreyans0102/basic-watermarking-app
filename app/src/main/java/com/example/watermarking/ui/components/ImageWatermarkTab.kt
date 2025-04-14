@file:OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)

package com.example.watermarking.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.watermarking.ui.components.shared.EmptyStateView
import com.example.watermarking.ui.components.shared.WatermarkControls
import com.example.watermarking.utils.ImageWatermarkProcessor
import com.example.watermarking.utils.MediaSaver
import com.example.watermarking.utils.TakePictureNoConfirmation
import com.example.watermarking.viewmodel.WatermarkPosition
import com.example.watermarking.viewmodel.WatermarkViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageWatermarkTab(viewModel: WatermarkViewModel = viewModel()) {
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var watermarkedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val context = LocalContext.current
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val mediaSaver = remember { MediaSaver(context) }
    val watermarkSettings = viewModel.watermarkSettings
    
    // State variables for UI
    var isProcessing by remember { mutableStateOf(false) }
    var showGalleryPermissionDialog by remember { mutableStateOf(false) }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    
    fun createImageUri(): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Watermarked_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WatermarkApp")
            }
        }
        return try {
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create image file: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        TakePictureNoConfirmation()
    ) { success ->
        if (success) {
            isProcessing = true
            try {
                cameraImageUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bitmap ->
                            val watermarkedBitmap = ImageWatermarkProcessor.addTextWatermark(
                                bitmap, 
                                watermarkSettings.text, 
                                watermarkSettings.textSize
                            )
                            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                                watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                            }
                            
                            // Add to watermarked images
                            watermarkedBitmaps = watermarkedBitmaps + watermarkedBitmap
                            
                            Toast.makeText(context, "Photo captured and watermarked", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
            }
        } else {
            Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraImageUri = createImageUri()
            cameraImageUri?.let { uri -> takePhotoLauncher.launch(uri) }
        } else {
            showCameraPermissionDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            watermarkedBitmaps.forEach { bitmap ->
                mediaSaver.saveImageToGallery(bitmap)
            }
        } else {
            showGalleryPermissionDialog = true
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            selectedImageUris = uris
            
            try {
                val processed = uris.mapNotNull { uri ->
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let {
                            ImageWatermarkProcessor.addTextWatermark(
                                it, 
                                watermarkSettings.text, 
                                watermarkSettings.textSize
                            )
                        }
                    }
                }
                watermarkedBitmaps = processed
            } catch (e: Exception) {
                Toast.makeText(context, "Error processing images: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
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
                // Select images button
                ElevatedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Images")
                }
                
                // Camera button
                ElevatedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            cameraImageUri = createImageUri()
                            cameraImageUri?.let { uri -> takePhotoLauncher.launch(uri) }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Camera",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Take Photo")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section header for images
            if (watermarkedBitmaps.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Watermarked Images (${watermarkedBitmaps.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                                if (ContextCompat.checkSelfPermission(context, permission) == 
                                        PackageManager.PERMISSION_GRANTED) {
                                    watermarkedBitmaps.forEach { bitmap ->
                                        mediaSaver.saveImageToGallery(bitmap)
                                    }
                                    Toast.makeText(context, "All images saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    permissionLauncher.launch(permission)
                                }
                            } else {
                                watermarkedBitmaps.forEach { bitmap ->
                                    mediaSaver.saveImageToGallery(bitmap)
                                }
                                Toast.makeText(context, "All images saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save All")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        // Loading indicator
        item {
            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Processing images...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Show empty state if no images
        item {
            if (watermarkedBitmaps.isEmpty() && !isProcessing) {
                EmptyStateView(
                    icon = Icons.Outlined.Image,
                    title = "No Images Yet",
                    message = "Select images from your gallery or take a new photo to add a watermark",
                    actionText = "Select Images",
                    onAction = { imagePickerLauncher.launch("image/*") }
                )
            }
        }

        // Image grid (2 columns)
        if (watermarkedBitmaps.isNotEmpty()) {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((watermarkedBitmaps.size * 120).dp.coerceAtMost(500.dp)),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(watermarkedBitmaps) { bitmap ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement(),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Watermarked Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Image actions overlay
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    // Save button
                                    IconButton(
                                        onClick = {
                                            mediaSaver.saveImageToGallery(bitmap)
                                            Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                                shape = CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = "Save Image",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Permission denial dialogs
    if (showCameraPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDialog = false },
            title = { Text("Camera Permission Required") },
            text = { Text("Camera permission is needed to take photos. Please enable it in app settings.") },
            confirmButton = {
                TextButton(onClick = { showCameraPermissionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    if (showGalleryPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showGalleryPermissionDialog = false },
            title = { Text("Storage Permission Required") },
            text = { Text("Storage permission is needed to save images. Please enable it in app settings.") },
            confirmButton = {
                TextButton(onClick = { showGalleryPermissionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}