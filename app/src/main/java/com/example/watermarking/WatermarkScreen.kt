package com.example.watermarking

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import android.content.pm.PackageManager

enum class WatermarkPosition {
    TOP_LEFT, TOP_RIGHT, CENTER, BOTTOM_LEFT, BOTTOM_RIGHT
}


class ImageSaver(private val context: Context) {
    fun saveImageToGallery(bitmap: Bitmap): String? {
        val displayName = "Watermarked_${System.currentTimeMillis()}.jpg"
        val mimeType = "image/jpeg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WatermarkApp")
            }
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

fun addTextWatermark(
    bitmap: Bitmap,
    text: String,
    position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT
): Bitmap {
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)
    val padding = 20f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        alpha = 128
        textAlign = when (position) {
            WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> Paint.Align.RIGHT
            WatermarkPosition.CENTER -> Paint.Align.CENTER
            else -> Paint.Align.LEFT
        }
    }

    val (x, y) = when (position) {
        WatermarkPosition.TOP_LEFT -> Pair(padding, padding + paint.textSize)
        WatermarkPosition.TOP_RIGHT -> Pair(mutableBitmap.width - padding, padding + paint.textSize)
        WatermarkPosition.CENTER -> Pair(mutableBitmap.width / 2f, mutableBitmap.height / 2f)
        WatermarkPosition.BOTTOM_LEFT -> Pair(padding, mutableBitmap.height - padding)
        WatermarkPosition.BOTTOM_RIGHT -> Pair(mutableBitmap.width - padding, mutableBitmap.height - padding)
    }

    val textWidth = paint.measureText(text)
    val rect = when (position) {
        WatermarkPosition.TOP_LEFT -> RectF(
            x - padding,
            y - paint.textSize - padding,
            x + textWidth + padding,
            y + padding
        )
        WatermarkPosition.TOP_RIGHT -> RectF(
            x - textWidth - padding,
            y - paint.textSize - padding,
            x + padding,
            y + padding
        )
        WatermarkPosition.CENTER -> RectF(
            x - textWidth / 2 - padding,
            y - paint.textSize / 2 - padding,
            x + textWidth / 2 + padding,
            y + paint.textSize / 2 + padding
        )
        WatermarkPosition.BOTTOM_LEFT -> RectF(
            x - padding,
            y - paint.textSize - padding,
            x + textWidth + padding,
            y + padding
        )
        WatermarkPosition.BOTTOM_RIGHT -> RectF(
            x - textWidth - padding,
            y - paint.textSize - padding,
            x + padding,
            y + padding
        )
    }

    paint.color = Color.BLACK
    canvas.drawRoundRect(rect, 8f, 8f, paint)

    paint.color = Color.WHITE
    canvas.drawText(text, x, y, paint)

    return mutableBitmap
}
@Composable
fun WatermarkScreen() {
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var watermarkedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var watermarkText by remember { mutableStateOf("© Deepak Babel") }
    var selectedPosition by remember { mutableStateOf(WatermarkPosition.BOTTOM_RIGHT) }

    // Use MutableState objects directly
    val isLoading = remember { mutableStateOf(false) }
    val saveProgress = remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageSaver = remember { ImageSaver(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                saveImages(
                    context = context,
                    imageSaver = imageSaver,
                    watermarkedBitmaps = watermarkedBitmaps,
                    saveProgress = saveProgress,
                    isLoading = isLoading
                )
            }
        } else {
            Toast.makeText(context, "Permission required to save images", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        scope.launch {
            isLoading.value = true
            watermarkedBitmaps = uris.mapNotNull { uri ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { original ->
                            addTextWatermark(original, watermarkText, selectedPosition)
                        }
                    }
                }
            }
            isLoading.value = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Column {
                TextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark Text") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Watermark Position", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WatermarkPosition.values().forEach { position ->
                        FilterChip(
                            selected = selectedPosition == position,
                            onClick = { selectedPosition = position },
                            label = { Text(position.name.replace("_", " ")) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Images")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
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
            Column {
                if (watermarkedBitmaps.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                scope.launch {
                                    saveImages(
                                        context = context,
                                        imageSaver = imageSaver,
                                        watermarkedBitmaps = watermarkedBitmaps,
                                        saveProgress = saveProgress,
                                        isLoading = isLoading
                                    )
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save All (${watermarkedBitmaps.size}) Images")
                    }
                }

                if (isLoading.value) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Progress: ${(saveProgress.value * 100).toInt()}%")
                    }
                }
            }
        }
    }
}

private suspend fun saveImages(
    context: Context,
    imageSaver: ImageSaver,
    watermarkedBitmaps: List<Bitmap>,
    saveProgress: MutableState<Float>,
    isLoading: MutableState<Boolean>
) {
    withContext(Dispatchers.Main) {
        isLoading.value = true
    }

    val total = watermarkedBitmaps.size
    watermarkedBitmaps.forEachIndexed { index, bitmap ->
        withContext(Dispatchers.IO) {
            imageSaver.saveImageToGallery(bitmap)
        }
        saveProgress.value = (index + 1).toFloat() / total
    }

    withContext(Dispatchers.Main) {
        isLoading.value = false
        saveProgress.value = 0f
    }
}

