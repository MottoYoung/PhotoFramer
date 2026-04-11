package com.photoframer.ui.components

import androidx.camera.core.ImageCapture
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.*

/**
 * 闪光灯模式
 */
enum class FlashMode {
    OFF, ON, AUTO;
    
    fun toImageCaptureFlashMode(): Int = when (this) {
        OFF -> ImageCapture.FLASH_MODE_OFF
        ON -> ImageCapture.FLASH_MODE_ON
        AUTO -> ImageCapture.FLASH_MODE_AUTO
    }
    
    fun next(): FlashMode = when (this) {
        OFF -> ON
        ON -> AUTO
        AUTO -> OFF
    }
}

/**
 * 相机顶部功能栏 - 贴近系统相机的极简控制区
 * 
 * 布局: [闪光灯] [网格]  ...空间...  [画面内构图] [AI按钮]
 * 纯黑背景，独立区域
 */
@Composable
fun CameraTopBar(
    flashMode: FlashMode,
    onFlashModeChange: (FlashMode) -> Unit,
    gridEnabled: Boolean,
    onGridToggle: () -> Unit,
    showAnalysisButtons: Boolean = true,
    onInFrameClick: () -> Unit = {},
    onAiClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧功能按钮组
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 闪光灯
            TopBarIconButton(
                icon = when (flashMode) {
                    FlashMode.OFF -> Icons.Default.FlashOff
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                },
                contentDescription = "闪光灯",
                isActive = flashMode != FlashMode.OFF,
                onClick = { onFlashModeChange(flashMode.next()) }
            )
            
            // 网格线
            TopBarIconButton(
                icon = if (gridEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                contentDescription = "网格线",
                isActive = gridEnabled,
                onClick = onGridToggle
            )
            
        }
        
        // 右侧分析按钮
        if (showAnalysisButtons) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InFrameCompositionButton(onClick = onInFrameClick)
                AiAnalysisButton(onClick = onAiClick)
            }
        }
    }
}

/**
 * AI 分析按钮 - 更克制的系统风格胶囊按钮
 */
@Composable
private fun AiAnalysisButton(
    onClick: () -> Unit
) {
    AnalysisPillButton(
        icon = Icons.Default.AutoAwesome,
        label = "AI",
        onClick = onClick
    )
}

@Composable
private fun InFrameCompositionButton(
    onClick: () -> Unit
) {
    AnalysisPillButton(
        icon = Icons.Default.Crop,
        label = "画面内",
        onClick = onClick
    )
}

@Composable
private fun AnalysisPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        AiGradientStart.copy(alpha = 0.22f),
                        AiGradientEnd.copy(alpha = 0.14f)
                    )
                )
            )
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * 顶部栏图标按钮 - 仅图标，无文字
 */
@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (isActive) IconActiveYellow else Color.White,
        animationSpec = tween(200),
        label = "tint"
    )
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
    }
}
