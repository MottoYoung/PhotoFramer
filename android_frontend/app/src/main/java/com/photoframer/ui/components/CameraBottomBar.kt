package com.photoframer.ui.components

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.AccentYellow
import com.photoframer.ui.theme.ControlBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LABEL_PHOTO = "拍照"
private const val LABEL_RECENT_PHOTO = "最近照片"

private val ClassicPanel = Color(0xFFF8F7F4)
private val ClassicTextPrimary = Color(0xFF2F2B28)
private val ClassicBorder = Color(0xFFD9D6D1)
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
    zoomUiState: CameraZoomUiState,
    onZoomPresetClick: (Int) -> Unit,
    onZoomRulerReveal: () -> Unit,
    onZoomRulerDrag: (Float) -> Unit,
    onZoomRulerDragEnd: () -> Unit,
    onShutterClick: () -> Unit,
    lastPhotoThumbnail: Bitmap? = null,
    isGalleryAvailable: Boolean = false,
    onGalleryClick: (() -> Unit)? = null,
    canSwitchFacing: Boolean = false,
    onFacingSwitch: (() -> Unit)? = null,
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
        CameraZoomControls(
            state = zoomUiState,
            onPresetClick = onZoomPresetClick,
            onRulerReveal = onZoomRulerReveal,
            onRulerDrag = onZoomRulerDrag,
            onRulerDragEnd = onZoomRulerDragEnd,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Text(
            text = currentMode.displayName,
            color = titleColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 16.dp)
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
                visualStyle = visualStyle,
                onClick = onGalleryClick
            )

            ShutterButton(
                visualStyle = visualStyle,
                onClick = onShutterClick,
                onLongPressStart = onLongPressStart,
                onLongPressEnd = onLongPressEnd,
                burstCount = burstCount
            )

            CameraFacingSwitchButton(
                enabled = canSwitchFacing && onFacingSwitch != null,
                onClick = { onFacingSwitch?.invoke() }
            )
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
    val longPressFeedback =
        if (isClassic) ClassicShutterRing.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.30f)
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
    visualStyle: CameraVisualStyle,
    onClick: (() -> Unit)?
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
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled && onClick != null) {
                onClick?.invoke()
            },
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
