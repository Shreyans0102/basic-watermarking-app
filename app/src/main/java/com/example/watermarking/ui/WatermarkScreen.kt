@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.watermarking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.watermarking.ui.components.ImageWatermarkTab
import com.example.watermarking.ui.components.VideoWatermarkTab

@Composable
fun WatermarkScreen() {
    var currentTab by remember { mutableStateOf(0) }
    val tabs = listOf("Images", "Videos")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watermark App") },
                colors = TopAppBarDefaults.topAppBarColors() // Updated method
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = currentTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (currentTab) {
                0 -> ImageWatermarkTab()
                1 -> VideoWatermarkTab()
            }
        }
    }
}
