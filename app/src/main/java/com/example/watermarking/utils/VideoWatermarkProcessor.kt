package com.example.watermarking.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class VideoWatermarkProcessor(private val context: Context) {
    
    fun addWatermarkToVideo(videoUri: Uri, watermarkText: String, textSize: Float): Uri? {
        try {
            Log.d("VideoProcessor", "Starting video processing")
            
            // Create output file
            val outputFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "watermarked_${System.currentTimeMillis()}.mp4"
            )
            
            // Simple file copy for now
            // In a real implementation, we would use FFmpeg or similar to add watermark to video
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                    Log.d("VideoProcessor", "Video copied to ${outputFile.path}")
                }
            }
            
            // Return the output file URI
            return Uri.fromFile(outputFile)
            
        } catch (e: Exception) {
            Log.e("VideoProcessor", "Exception in video processing", e)
            e.printStackTrace()
            return null
        }
    }
    
    // This method isn't currently used for video watermarking,
    // but kept for future implementation when we add real video watermarking
    private fun createWatermarkBitmap(text: String, textSize: Float): Bitmap {
        // Create a bitmap for the watermark (transparent background with text)
        val paint = Paint().apply {
            setAntiAlias(true)
            setColor(Color.WHITE)
            setTextSize(textSize)
            setAlpha(180)  // Semi-transparent
            setTextAlign(Paint.Align.CENTER)
        }
        
        // Measure text to create appropriate size bitmap
        val textWidth = paint.measureText(text)
        val textHeight = paint.descent() - paint.ascent()
        
        // Create bitmap with some padding
        val padding = 20f
        val bitmap = Bitmap.createBitmap(
            (textWidth + padding * 2).toInt(),
            (textHeight + padding * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )
        
        // Create canvas and draw text
        val canvas = Canvas(bitmap)
        
        // Draw background rectangle first
        val bgPaint = Paint().apply {
            setColor(Color.BLACK)
            setAlpha(100)
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), bgPaint)
        
        // Draw text centered in the bitmap
        canvas.drawText(
            text,
            bitmap.width / 2f,
            (bitmap.height / 2f) - ((paint.descent() + paint.ascent()) / 2),
            paint
        )
        
        return bitmap
    }
}
