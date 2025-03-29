package com.example.watermarking

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.OutputStream
import android.os.Build

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// ImageSaver class (make sure this is in the same file or imported)
class ImageSaver(private val context: Context) {
    fun saveImageToGallery(bitmap: Bitmap): String? {
        val displayName = "Watermarked_${System.currentTimeMillis()}.jpg"
        val mimeType = "image/jpeg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WatermarkApp")
        }

        val contentResolver = context.contentResolver
        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        return try {
            uri?.let {
                val outputStream: OutputStream? = contentResolver.openOutputStream(it)
                outputStream?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                }
                Toast.makeText(context, "Image saved to Gallery", Toast.LENGTH_SHORT).show()
                it.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
fun addTextWatermark(bitmap: Bitmap, text: String): Bitmap {
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        alpha = 128
        textAlign = Paint.Align.RIGHT  // Align text to the right
    }

    // Add padding from edges
    val padding = 20f

    // Calculate position
    val x = mutableBitmap.width - padding
    val y = mutableBitmap.height - padding
    // Add background rectangle
    val textWidth = paint.measureText(text)
    val rectPadding = 8f
    val rectLeft = x - textWidth - rectPadding
    val rectTop = y - paint.textSize - rectPadding
    val rectRight = x + rectPadding
    val rectBottom = y + rectPadding

    paint.color = Color.BLACK
    paint.alpha = 128
    canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, paint)

// Reset paint properties for text
    paint.color = Color.WHITE
    paint.alpha = 128

    canvas.drawText(text, x, y, paint)

    return mutableBitmap
}


// Main Composable function
@Composable
fun WatermarkScreen() {
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var watermarkedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var watermarkText by remember { mutableStateOf("© Deepak Babel") }
    val context = LocalContext.current

    // Add this new state variable for camera URI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val imageSaver = remember { mutableStateOf(ImageSaver(context)) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            watermarkedBitmaps.forEach { bitmap ->
                imageSaver.value.saveImageToGallery(bitmap)
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
                    addTextWatermark(it, watermarkText)
                }
            }
        }
    }
    // New camera launcher
    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bitmap ->
                        val watermarkedBitmap = addTextWatermark(bitmap, watermarkText)
                        // Add to the displayed images
                        watermarkedBitmaps = watermarkedBitmaps + watermarkedBitmap
                        // Save the watermarked version back to the URI
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                        }
                        Toast.makeText(context, "Photo captured and watermarked", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Helper function to create image URI
    fun createImageUri(): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Watermarked_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WatermarkApp")
        }
        return context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            TextField(
                value = watermarkText,
                onValueChange = { watermarkText = it },
                label = { Text("Watermark Text") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                imagePickerLauncher.launch("image/*")
            }) {
                Text("Select Images")  // Changed to plural
            }

            Spacer(modifier = Modifier.height(16.dp))
            // New camera button
            Button(
                onClick = {
                    cameraImageUri = createImageUri()
                    cameraImageUri?.let { uri ->
                        takePhotoLauncher.launch(uri)
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
                                    context,
                                    permission
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                watermarkedBitmaps.forEach { bitmap ->
                                    imageSaver.value.saveImageToGallery(bitmap)
                                }
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        } else {
                            watermarkedBitmaps.forEach { bitmap ->
                                imageSaver.value.saveImageToGallery(bitmap)
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
