package com.example.watermarking.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object ImageWatermarkProcessor {
    fun addTextWatermark(bitmap: Bitmap, text: String, textSize: Float = 40f): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val paint = Paint().apply {
            setAntiAlias(true)
            setColor(Color.WHITE)
            setTextSize(textSize)
            setAlpha(128)
            setTextAlign(Paint.Align.RIGHT)
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

        paint.setColor(Color.BLACK)
        paint.setAlpha(128)
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, paint)

        // Reset paint properties for text
        paint.setColor(Color.WHITE)
        paint.setAlpha(128)

        canvas.drawText(text, x, y, paint)

        return mutableBitmap
    }
}

// Rename this class to avoid the conflict
// or comment out/remove if not needed
/*
class LegacyVideoWatermarkProcessor(private val context: Context) {
    // Original implementation or placeholder if not used
}
*/
