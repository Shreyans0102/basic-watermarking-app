@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)

package com.example.watermarking.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.watermarking.R
import com.example.watermarking.ui.components.ImageWatermarkTab
import com.example.watermarking.ui.components.VideoWatermarkTab

data class TabItem(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val content: @Composable () -> Unit
)

@Composable
fun WatermarkScreen() {
    var currentTab by remember { mutableStateOf(0) }
    
    // Define tabs with icons
    val tabs = listOf(
        TabItem(
            title = "Images",
            icon = Icons.Default.Image,
            selectedIcon = Icons.Default.Image,
            content = { ImageWatermarkTab() }
        )
        //,
        //TabItem(
          //  title = "Videos",
          //  icon = Icons.Default.Videocam,
          //  selectedIcon = Icons.Default.Videocam,
          //  content = { VideoWatermarkTab() }
        //)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Watermark Studio",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Custom styled tab row
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { 
                            Icon(
                                imageVector = if (currentTab == index) tab.selectedIcon else tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        text = { 
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (currentTab == index) 
                                        FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Animate tab content changes
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) with
                    fadeOut(animationSpec = tween(300))
                }
            ) { tabIndex ->
                tabs[tabIndex].content()
            }
        }
    }
}