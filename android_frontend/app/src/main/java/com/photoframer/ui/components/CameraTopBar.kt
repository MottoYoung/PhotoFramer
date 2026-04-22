package com.photoframer.ui.components

import androidx.camera.core.ImageCapture
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.AiGradientEnd
import com.photoframer.ui.theme.AiGradientStart
import com.photoframer.ui.theme.BlueAccent
import com.photoframer.ui.theme.IconActiveYellow
import com.photoframer.ui.theme.PurplePrimary

private const val LABEL_DEFAULT_CAMERA = "\u539f\u76f8\u673a"
private const val LABEL_CLASSIC = "\u7ecf\u5178"
private const val LABEL_FLASH = "\u95ea\u5149\u706f"
private const val LABEL_GRID = "\u7f51\u683c"
private const val LABEL_IN_FRAME = "\u753b\u9762\u5185"

private val ClassicSurface = Color(0xFFF8F7F4)
private val ClassicSurfaceRaised = Color(0xFFFFFEFC)
private val ClassicBorder = Color(0xFFD9D6D1)
private val ClassicSelectedGray = Color(0xFFE6E3DE)
private val ClassicTextPrimary = Color(0xFF2F2B28)
private val ClassicTextSecondary = Color(0xFF5B5650)
private val ClassicAiStart = Color(0xFFCFE3FF)
private val ClassicAiEnd = Color(0xFFBFD8FF)

enum class CameraEntryMode {
    NONE,
    IN_FRAME,
    AI
}

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

enum class CameraVisualStyle {
    DEFAULT,
    CLASSIC
}

@Composable
fun CameraTopBar(
    flashMode: FlashMode,
    onFlashModeChange: (FlashMode) -> Unit,
    gridEnabled: Boolean,
    onGridToggle: () -> Unit,
    visualStyle: CameraVisualStyle,
    onVisualStyleChange: (CameraVisualStyle) -> Unit,
    activeEntryMode: CameraEntryMode = CameraEntryMode.NONE,
    showInFrameButton: Boolean = true,
    onInFrameClick: () -> Unit = {},
    showAiButton: Boolean = true,
    onAiClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isClassic = visualStyle == CameraVisualStyle.CLASSIC
    val baseBackground = if (isClassic) Color.White else Color.Black
    val overlayBrush = if (isClassic) {
        Brush.verticalGradient(
            colors = listOf(
                ClassicSurfaceRaised,
                ClassicSurface
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black,
                Color(0xFF050505)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(baseBackground)
            .statusBarsPadding()
            .background(overlayBrush)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisualStyleSwitch(
                visualStyle = visualStyle,
                onVisualStyleChange = onVisualStyleChange
            )
            TopBarIconButton(
                icon = when (flashMode) {
                    FlashMode.OFF -> Icons.Default.FlashOff
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                },
                contentDescription = LABEL_FLASH,
                isActive = flashMode != FlashMode.OFF,
                onClick = { onFlashModeChange(flashMode.next()) },
                visualStyle = visualStyle
            )
            TopBarIconButton(
                icon = if (gridEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                contentDescription = LABEL_GRID,
                isActive = gridEnabled,
                onClick = onGridToggle,
                visualStyle = visualStyle
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showInFrameButton) {
                EntryModeButton(
                    icon = Icons.Default.CenterFocusStrong,
                    text = LABEL_IN_FRAME,
                    selected = activeEntryMode == CameraEntryMode.IN_FRAME,
                    onClick = onInFrameClick,
                    visualStyle = visualStyle
                )
            }

            if (showAiButton) {
                EntryModeButton(
                    icon = Icons.Default.AutoAwesome,
                    text = "AI",
                    selected = activeEntryMode == CameraEntryMode.AI,
                    onClick = onAiClick,
                    visualStyle = visualStyle,
                    useGradient = true
                )
            }
        }
    }
}

@Composable
private fun VisualStyleSwitch(
    visualStyle: CameraVisualStyle,
    onVisualStyleChange: (CameraVisualStyle) -> Unit
) {
    val containerColor = if (visualStyle == CameraVisualStyle.CLASSIC) {
        ClassicSurfaceRaised
    } else {
        Color(0xFF101010)
    }
    val borderColor = if (visualStyle == CameraVisualStyle.CLASSIC) {
        ClassicBorder
    } else {
        Color.White.copy(alpha = 0.16f)
    }

    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VisualStyleChip(
            text = LABEL_DEFAULT_CAMERA,
            selected = visualStyle == CameraVisualStyle.DEFAULT,
            containerStyle = visualStyle,
            onClick = { onVisualStyleChange(CameraVisualStyle.DEFAULT) }
        )
        VisualStyleChip(
            text = LABEL_CLASSIC,
            selected = visualStyle == CameraVisualStyle.CLASSIC,
            containerStyle = visualStyle,
            onClick = { onVisualStyleChange(CameraVisualStyle.CLASSIC) }
        )
    }
}

@Composable
private fun VisualStyleChip(
    text: String,
    selected: Boolean,
    containerStyle: CameraVisualStyle,
    onClick: () -> Unit
) {
    val isClassic = containerStyle == CameraVisualStyle.CLASSIC
    val backgroundColor = if (selected) {
        if (isClassic) ClassicSelectedGray else Color.White.copy(alpha = 0.92f)
    } else {
        Color.Transparent
    }
    val textColor = if (selected) {
        if (isClassic) ClassicTextPrimary else Color.Black
    } else {
        if (isClassic) ClassicTextSecondary else Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun EntryModeButton(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    visualStyle: CameraVisualStyle,
    useGradient: Boolean = false
) {
    val isClassic = visualStyle == CameraVisualStyle.CLASSIC
    val borderColor = when {
        selected -> BlueAccent.copy(alpha = 0.72f)
        useGradient -> BlueAccent.copy(alpha = if (isClassic) 0.36f else 0.55f)
        else -> if (isClassic) ClassicBorder else Color.White.copy(alpha = 0.16f)
    }
    val background = when {
        selected -> Brush.linearGradient(
            colors = listOf(
                BlueAccent.copy(alpha = if (isClassic) 0.22f else 0.42f),
                PurplePrimary.copy(alpha = if (isClassic) 0.12f else 0.24f)
            )
        )

        useGradient -> Brush.linearGradient(
            colors = listOf(
                if (isClassic) ClassicAiStart else AiGradientStart.copy(alpha = 0.58f),
                if (isClassic) ClassicAiEnd else AiGradientEnd.copy(alpha = 0.42f)
            )
        )

        isClassic -> Brush.linearGradient(
            colors = listOf(
                ClassicSurfaceRaised,
                ClassicSurface
            )
        )

        else -> Brush.linearGradient(
            colors = listOf(
                Color(0xFF171717),
                Color(0xFF111111)
            )
        )
    }

    val contentColor = if (isClassic) ClassicTextPrimary else Color.White

    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (useGradient) 15.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(if (useGradient) 15.dp else 18.dp)
        )
        Spacer(modifier = Modifier.width(if (useGradient) 4.dp else 6.dp))
        Text(
            text = text,
            color = contentColor,
            style = if (useGradient) {
                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    visualStyle: CameraVisualStyle
) {
    val isClassic = visualStyle == CameraVisualStyle.CLASSIC
    val tint by animateColorAsState(
        targetValue = if (isActive) IconActiveYellow else if (isClassic) ClassicTextPrimary else Color.White,
        animationSpec = tween(200),
        label = "tint"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isClassic) ClassicSurfaceRaised else Color(0xFF171717))
            .border(
                1.dp,
                if (isClassic) ClassicBorder else Color.White.copy(alpha = 0.16f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}
