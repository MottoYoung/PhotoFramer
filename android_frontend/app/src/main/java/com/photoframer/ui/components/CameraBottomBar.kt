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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.*

/**
 * 相机模式
 */
enum class CameraMode(val displayName: String) {
    PORTRAIT("人像"),
    PHOTO("拍照"),
    VIDEO("录像"),
    NIGHT("夜景")
}

/**
 * 相机底部控制栏 - 专业相机风格
 * 
 * 布局:
 * - 上层: 模式选择器 (可滑动)
 * - 下层: [相册缩略图] [快门按钮] [前后切换]
 * 
 * 注意：变焦控制已移至预览区悬浮显示
 */
@Composable
fun CameraBottomBar(
    currentMode: CameraMode,
    onModeChange: (CameraMode) -> Unit,
    onShutterClick: () -> Unit,
    onCameraSwitch: () -> Unit,
    onGalleryClick: () -> Unit,
    lastPhotoThumbnail: Bitmap? = null,
    isRecording: Boolean = false,
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
        // 1. 模式选择器
        ModeSelector(
            currentMode = currentMode,
            onModeChange = onModeChange,
            modifier = Modifier.padding(bottom = 28.dp)
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
                onClick = onGalleryClick
            )
            
            // 中间：快门按钮
            ShutterButton(
                isRecording = isRecording,
                isVideoMode = currentMode == CameraMode.VIDEO,
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
    val zoomOptions = listOf(0.6f, 1f, 2f)
    
    Row(
        modifier = modifier
            .background(PillBackground.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ControlSelectedBackground else Color.Transparent)
                    .clickable { onZoomChange(zoom) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSelected && kotlin.math.abs(currentZoom - zoom) > 0.05f) 
                        String.format("%.1f", currentZoom) 
                    else if (zoom < 1f) ".6" 
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
 * 模式选择器 - 可点击切换
 */
@Composable
private fun ModeSelector(
    currentMode: CameraMode,
    onModeChange: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = CameraMode.entries
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val isSelected = mode == currentMode
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onModeChange(mode) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) AccentYellow else Color.White.copy(alpha = 0.5f),
                    style = if (isSelected) MaterialTheme.typography.labelLarge
                            else MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
                )
                
                // 选中指示器
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AccentYellow else Color.Transparent)
                )
            }
        }
    }
}

/**
 * 快门按钮 - 专业设计
 * 
 * 拍照模式: 白色圆环 + 白色内圆
 * 录像模式: 白色圆环 + 红色内圆
 * 录像中: 白色圆环 + 红色方形(停止)
 */
@Composable
private fun ShutterButton(
    isRecording: Boolean,
    isVideoMode: Boolean,
    onClick: () -> Unit
) {
    // 录像时的脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 0.9f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
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
            .scale(if (isRecording) pulseScale else pressScale)
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
        // 内部填充
        if (isRecording) {
            // 录像中：显示红色方形停止按钮
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(RecordingRed)
            )
        } else if (isVideoMode) {
            // 录像模式：显示红色圆形
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(RecordingRed)
            )
        } else {
            // 拍照模式：显示白色圆形
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * 相册缩略图
 */
@Composable
private fun GalleryThumbnail(
    thumbnail: Bitmap?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ControlBackground)
            .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "最近照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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
