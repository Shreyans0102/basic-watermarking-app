package com.example.watermarking.ui.components.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WatermarkControls(
    watermarkText: String,
    onWatermarkTextChange: (String) -> Unit,
    watermarkTextSize: Float,
    onWatermarkTextSizeChange: (Float) -> Unit
) {
    TextField(
        value = watermarkText,
        onValueChange = onWatermarkTextChange,
        label = { Text("Watermark Text") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        "Watermark Size: ${watermarkTextSize.toInt()}px",
        modifier = Modifier.padding(top = 8.dp)
    )

    Slider(
        value = watermarkTextSize,
        onValueChange = onWatermarkTextSizeChange,
        valueRange = 20f..100f,
        modifier = Modifier.fillMaxWidth()
    )
}
