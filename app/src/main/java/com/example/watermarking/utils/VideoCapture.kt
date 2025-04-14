package com.example.watermarking.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts

class TakePictureNoConfirmation : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return super.createIntent(context, input).apply {
            // This flag tells the camera to return immediately after capturing
            putExtra("android.intent.extra.QUICK_CAPTURE", true)
            // Some device manufacturers use different flags
            putExtra("quickCapture", true)
        }
    }
}

class VideoCaptureContract : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return super.createIntent(context, input).apply {
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30) // Limit to 30 seconds
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1) // High quality
        }
    }
}
