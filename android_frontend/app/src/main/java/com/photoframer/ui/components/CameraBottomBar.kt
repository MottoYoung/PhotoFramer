package com.photoframer.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.*

/**
 * 保留枚举以兼容现有状态，但当前只暴露真正实现的拍照模式。
 */
enum class CameraMode(val displayName: String) {
    PHOTO("拍照")
}

/**
 * 相机底部控制栏 - 贴近系统相机的基础拍照控制
 * 
 * 布局:
 * - 上层: 当前模式标签（仅展示真实可用的拍照模式）
 * - 下层: [最近照片预览] [快门按钮] [前后切换]
 * 
 * 注意：变焦控制已移至预览区悬浮显示
 */
@Composable
fun CameraBottomBar(
    currentMode: CameraMode,
    onShutterClick: () -> Unit,
    onCameraSwitch: () -> Unit,
    lastPhotoThumbnail: Bitmap? = null,
    isGalleryAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(top = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = currentMode.displayName,
            color = AccentYellow,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // 2. 主控制行：相册 / 快门 / 切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：相册缩略图
            GalleryThumbnail(
                thumbnail = lastPhotoThumbnail,
                enabled = isGalleryAvailable
            )
            
            // 中间：快门按钮
            ShutterButton(
                onClick = onShutterClick
            )
            
            // 右侧：前后摄像头切换
            CameraSwitchButton(onClick = onCameraSwitch)
        }
    }
}

/**
 * 变焦选择器 - 药丸式设计（悬浮在预览区底部）
 */
@Composable
fun ZoomSelector(
    currentZoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomOptions = listOf(1f, 2f)
    
    Row(
        modifier = modifier
            .background(PillBackground.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        zoomOptions.forEach { zoom ->
            val isSelected = kotlin.math.abs(currentZoom - zoom) < 0.3f ||
                    (zoom == 1f && currentZoom >= 0.8f && currentZoom < 1.5f) ||
                    (zoom == 2f && currentZoom >= 1.5f)
            
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ControlSelectedBackground else Color.Transparent)
                    .clickable { onZoomChange(zoom) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSelected && kotlin.math.abs(currentZoom - zoom) > 0.05f) 
                        String.format("%.1f", currentZoom) 
                    else "${zoom.toInt()}",
                    color = if (isSelected) AccentYellow else Color.White.copy(alpha = 0.7f),
                    style = if (isSelected) MaterialTheme.typography.labelLarge
                            else MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 快门按钮 - 仅保留已实现的拍照语义
 */
@Composable
private fun ShutterButton(
    onClick: () -> Unit
) {
    // 点击动画
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "press"
    )
    
    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(pressScale)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * 相册缩略图
 */
@Composable
private fun GalleryThumbnail(
    thumbnail: Bitmap?,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ControlBackground)
            .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "最近照片",
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (enabled) 1f else 0.72f),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.1f))
            )
        }
    }
}

/**
 * 前后摄像头切换按钮
 */
@Composable
private fun CameraSwitchButton(onClick: () -> Unit) {
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "rotation"
    )
    
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(ControlBackground)
            .clickable { 
                rotationAngle += 180f
                onClick() 
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cameraswitch,
            contentDescription = "切换摄像头",
            tint = Color.White,
            modifier = Modifier
                .size(26.dp)
                .rotate(rotation)
        )
    }
}
