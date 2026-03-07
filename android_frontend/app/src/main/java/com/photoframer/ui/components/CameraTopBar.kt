package com.photoframer.ui.components

import androidx.camera.core.ImageCapture
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
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
 * 相机顶部功能栏 - Google 相机风格
 * 
 * 布局: [闪光灯] [网格] [设置]  ...空间...  [AI按钮]
 * 纯黑背景，独立区域
 */
@Composable
fun CameraTopBar(
    flashMode: FlashMode,
    onFlashModeChange: (FlashMode) -> Unit,
    gridEnabled: Boolean,
    onGridToggle: () -> Unit,
    onSettingsClick: () -> Unit = {},
    showAiButton: Boolean = true,
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
            
            // 设置
            TopBarIconButton(
                icon = Icons.Outlined.Tune,
                contentDescription = "设置",
                isActive = false,
                onClick = onSettingsClick
            )
        }
        
        // 右侧 AI 按钮
        if (showAiButton) {
            AiAnalysisButton(onClick = onAiClick)
        }
    }
}

/**
 * AI 分析按钮 - 蓝色渐变圆形
 */
@Composable
private fun AiAnalysisButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        AiGradientStart,
                        AiGradientEnd
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "AI 分析",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
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
