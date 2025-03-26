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

// Watermark generation function
//fun addTextWatermark(bitmap: Bitmap, text: String): Bitmap {
//    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//    val canvas = Canvas(mutableBitmap)
//
//    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        color = Color.WHITE
//        textSize = 40f
//        alpha = 128
//    }
//
//    val textWidth = paint.measureText(text)
//    val x = (mutableBitmap.width - textWidth) / 2f
//    val y = mutableBitmap.height / 2f
//
//    canvas.rotate(-45f, mutableBitmap.width / 2f, mutableBitmap.height / 2f)
//    canvas.drawText(text, x, y, paint)
//
//    return mutableBitmap
//}
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
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var watermarkText by remember { mutableStateOf("© Deepak Babel") }
    val context = LocalContext.current

    // Use mutableStateOf for ImageSaver to resolve remember issues
    val imageSaver = remember { mutableStateOf(ImageSaver(context)) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            watermarkedBitmap?.let { bitmap ->
                imageSaver.value.saveImageToGallery(bitmap)
            }
        } else {
            Toast.makeText(context, "Storage permission is required to save images", Toast.LENGTH_SHORT).show()
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            watermarkedBitmap = addTextWatermark(originalBitmap, watermarkText)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
            Text("Select Image")
        }

        watermarkedBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Watermarked Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))


            Button(onClick = {
                // For API < 29: Request permission
                // For API 29+: Directly save without permission
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        imageSaver.value.saveImageToGallery(bitmap)
                    } else {
                        permissionLauncher.launch(permission)
                    }
                } else {
                    // No permission needed for API 29+
                    imageSaver.value.saveImageToGallery(bitmap)
                }
            }) {
                Text("Save to Gallery")
            }
        }
    }
}
