package com.photoframer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframer.ui.theme.*

/**
 * 分析按钮组件
 * 位于相机预览右上角
 */
@Composable
fun AnalysisButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(
                brush = if (enabled) {
                    Brush.linearGradient(listOf(GradientStart, GradientEnd))
                } else {
                    Brush.linearGradient(listOf(TextSecondary, TextSecondary))
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "分析构图",
            tint = TextPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 拍照按钮组件
 */
@Composable
fun CaptureButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        Brush.linearGradient(listOf(GradientStart, GradientEnd))
                    } else {
                        Brush.linearGradient(listOf(TextSecondary, TextSecondary))
                    }
                )
        )
    }
}
