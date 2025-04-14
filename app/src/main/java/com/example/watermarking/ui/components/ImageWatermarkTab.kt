package com.example.watermarking.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.watermarking.utils.ImageWatermarkProcessor
import com.example.watermarking.utils.MediaSaver
import com.example.watermarking.utils.TakePictureNoConfirmation
import com.example.watermarking.ui.components.shared.WatermarkControls

@Composable
fun ImageWatermarkTab() {
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var watermarkedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var watermarkText by remember { mutableStateOf("Watermark Text") }
    var watermarkTextSize by remember { mutableStateOf(100f) }
    val context = LocalContext.current

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val mediaSaver = remember { MediaSaver(context) }

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
            try {
                cameraImageUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bitmap ->
                            val watermarkedBitmap = ImageWatermarkProcessor.addTextWatermark(bitmap, watermarkText, watermarkTextSize)
                            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                                watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                            }
                            Toast.makeText(context, "Photo captured and watermarked", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
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
                        cameraImageUri?.let { uri -> takePhotoLauncher.launch(uri) }
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