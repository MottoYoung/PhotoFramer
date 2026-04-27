package com.photoframer.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.AccentYellow
import com.photoframer.ui.theme.BlueAccent
import com.photoframer.ui.theme.ControlBackground
import com.photoframer.ui.theme.ControlSelectedBackground
import com.photoframer.ui.theme.PillBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LABEL_PHOTO = "\u62cd\u7167"
private const val LABEL_RECENT_PHOTO = "\u6700\u8fd1\u7167\u7247"
private const val LABEL_SWITCH_CAMERA = "\u5207\u6362\u6444\u50cf\u5934"

private val ClassicPanel = Color(0xFFF8F7F4)
private val ClassicPanelRaised = Color(0xFFFFFEFC)
private val ClassicBorder = Color(0xFFD9D6D1)
private val ClassicTextPrimary = Color(0xFF2F2B28)
private val ClassicTextSecondary = Color(0xFF5B5650)
private val ClassicZoomPill = Color(0xFFE3E1DE)
private val ClassicZoomSelected = Color(0xFFFFFEFC)
private val ClassicShutterOuter = Color(0xFFD9EEFF)
private val ClassicShutterGlow = Color(0xFFAED9FF)
private val ClassicShutterRing = Color(0xFF56AFFF)
private val ClassicShutterCoreStart = Color(0xFF7FD3FF)
private val ClassicShutterCoreEnd = Color(0xFF3F9DFF)

enum class CameraMode(val displayName: String) {
    PHOTO(LABEL_PHOTO)
}

@Composable
fun CameraBottomBar(
    currentMode: CameraMode,
    visualStyle: CameraVisualStyle,
    currentZoom: Float,
    onZoomChange: (Float) -> Unit,
    onShutterClick: () -> Unit,
    onCameraSwitch: () -> Unit,
    lastPhotoThumbnail: Bitmap? = null,
    isGalleryAvailable: Boolean = false,
    onLongPressStart: (() -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
    burstCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isClassic = visualStyle == CameraVisualStyle.CLASSIC
    val containerColor = if (isClassic) ClassicPanel else Color.Black
    val titleColor = if (isClassic) ClassicTextPrimary else AccentYellow

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZoomSelector(
            currentZoom = currentZoom,
            onZoomChange = onZoomChange,
            visualStyle = visualStyle,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = currentMode.displayName,
            color = titleColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryThumbnail(
                thumbnail = lastPhotoThumbnail,
                enabled = isGalleryAvailable,
                visualStyle = visualStyle
            )

            ShutterButton(
                visualStyle = visualStyle,
                onClick = onShutterClick,
                onLongPressStart = onLongPressStart,
                onLongPressEnd = onLongPressEnd,
                burstCount = burstCount
            )

            CameraSwitchButton(
                onClick = onCameraSwitch,
                visualStyle = visualStyle
            )
        }
    }
}

@Composable
fun ZoomSelector(
    currentZoom: Float,
    onZoomChange: (Float) -> Unit,
    visualStyle: CameraVisualStyle,
    modifier: Modifier = Modifier
) {
    val isClassic = visualStyle == CameraVisualStyle.CLASSIC
    val containerColor = if (isClassic) ClassicZoomPill else Color(0xFF111111)
    val containerBorderColor = if (isClassic) Color.Transparent else Color.White.copy(alpha = 0.06f)
    val selectedBackground = if (isClassic) ClassicZoomSelected else Color(0xFF2B2B2B)
    val selectedTextColor = if (isClassic) ClassicTextPrimary else AccentYellow
    val idleTextColor = if (isClassic) ClassicTextSecondary else Color.White.copy(alpha = 0.80f)
    val zoomOptions = listOf(1f, 2f)

    Row(
        modifier = modifier
            .height(44.dp)
            .width(124.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(containerColor)
            .border(1.dp, containerBorderColor, RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        zoomOptions.forEach { zoom ->
            val isSelected = kotlin.math.abs(currentZoom - zoom) < 0.3f ||
                (zoom == 1f && currentZoom >= 0.8f && currentZoom < 1.5f) ||
                (zoom == 2f && currentZoom >= 1.5f)

            Box(
                modifier = Modifier
                    .height(32.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) selectedBackground else Color.Transparent)
                    .clickable { onZoomChange(zoom) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSelected && kotlin.math.abs(currentZoom - zoom) > 0.05f) {
                        String.format("%.1f", currentZoom)
                    } else {
                        zoom.toInt().toString()
                    },
                    color = if (isSelected) selectedTextColor else idleTextColor,
                    style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    visualStyle: CameraVisualStyle,
    onClick: () -> Unit,
    onLongPressStart: (() -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
    burstCount: Int = 0
) {
    val isClassic = visualStyle == CameraVisualStyle.CLASSIC
    var isPressed by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed || isLongPressing) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "press"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "shutter_glow")
    val breathingGlow by infiniteTransition.animateFloat(
        initialValue = if (isClassic) 0.20f else 0.10f,
        targetValue = if (isClassic) 0.34f else 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_glow"
    )

    val ringColor = if (isClassic) ClassicShutterRing else Color.White
    val outerGlowColor = if (isClassic) ClassicShutterGlow else AccentYellow
    val longPressFeedback = if (isClassic) ClassicShutterRing.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.30f)
    val outerRingColor = if (isClassic) ClassicShutterOuter else Color(0x66D7C36A)
    val coreBrush = if (isClassic) {
        Brush.radialGradient(
            colors = listOf(
                ClassicShutterCoreStart,
                Color(0xFF63BFFF),
                ClassicShutterCoreEnd
            )
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Color.White,
                Color(0xFFF2F2F2)
            )
        )
    }

    Box(
        modifier = Modifier
            .size(82.dp)
            .scale(pressScale)
            .background(outerGlowColor.copy(alpha = breathingGlow), CircleShape)
            .padding(4.dp)
            .border(2.dp, outerRingColor, CircleShape)
            .padding(3.dp)
            .border(4.dp, ringColor, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (isLongPressing) longPressFeedback else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val job = scope.launch {
                            delay(500)
                            isLongPressing = true
                            onLongPressStart?.invoke()
                        }
                        awaitRelease()
                        job.cancel()
                        if (isLongPressing) {
                            isLongPressing = false
                            onLongPressEnd?.invoke()
                        } else {
                            onClick()
                        }
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(coreBrush)
        )

        if (burstCount > 0) {
            Text(
                text = burstCount.toString(),
                color = if (isClassic) Color.White else Color.Black,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun GalleryThumbnail(
    thumbnail: Bitmap?,
    enabled: Boolean,
    visualStyle: CameraVisualStyle
) {
    val isClassic = visualStyle == CameraVisualStyle.CLASSIC
    val backgroundColor = if (isClassic) Color(0xFFF0EEEB) else ControlBackground
    val borderColor = if (isClassic) ClassicBorder else Color.White.copy(alpha = 0.16f)
    val placeholderColor = if (isClassic) {
        Color.Black.copy(alpha = if (enabled) 0.18f else 0.10f)
    } else {
        Color.White.copy(alpha = if (enabled) 0.18f else 0.10f)
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = LABEL_RECENT_PHOTO,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .alpha(if (enabled) 1f else 0.72f),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(placeholderColor)
            )
        }
    }
}

@Composable
private fun CameraSwitchButton(
    onClick: () -> Unit,
    visualStyle: CameraVisualStyle
) {
    val isClassic = visualStyle == CameraVisualStyle.CLASSIC
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "rotation"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isClassic) Color(0xFF2A2825) else ControlBackground,
        animationSpec = tween(180),
        label = "switch_background"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable {
                rotationAngle += 180f
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cameraswitch,
            contentDescription = LABEL_SWITCH_CAMERA,
            tint = if (isClassic) ClassicPanelRaised else Color.White,
            modifier = Modifier
                .size(26.dp)
                .rotate(rotation)
        )
    }
}
