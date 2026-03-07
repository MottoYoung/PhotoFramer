package com.photoframer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.*

/**
 * 变焦药丸控件 - 浮动在预览区底部（备用，现已集成到底部栏）
 */
@Composable
fun ZoomPills(
    currentZoom: Float,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomOptions = listOf(0.6f, 1f, 2f)
    
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        zoomOptions.forEach { zoom ->
            val isSelected = kotlin.math.abs(currentZoom - zoom) < 0.3f ||
                    (zoom == 0.6f && currentZoom < 0.8f) ||
                    (zoom == 1f && currentZoom >= 0.8f && currentZoom < 1.5f) ||
                    (zoom == 2f && currentZoom >= 1.5f)
            
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onZoomChanged(zoom) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (zoom < 1f) ".6" else "${zoom.toInt()}",
                    color = if (isSelected) AccentYellow else Color.White,
                    style = if (isSelected) MaterialTheme.typography.labelLarge
                            else MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
