package com.photoframer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.AccentYellow
import com.photoframer.utils.CameraZoomPreset

data class CameraZoomUiState(
    val presets: List<CameraZoomPreset>,
    val selectedLensIndex: Int?,
    val displayedZoomText: String,
    val isRulerVisible: Boolean
)

@Composable
fun CameraZoomControls(
    state: CameraZoomUiState,
    onPresetClick: (Int) -> Unit,
    onRulerReveal: () -> Unit,
    onRulerDrag: (Float) -> Unit,
    onRulerDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.presets.isEmpty()) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isRulerVisible) {
            Text(
                text = state.displayedZoomText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ZoomRuler(
                onRulerReveal = onRulerReveal,
                onRulerDrag = onRulerDrag,
                onRulerDragEnd = onRulerDragEnd
            )
        } else {
            Row(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0xCC101010))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(21.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .pointerInput(state.presets, state.selectedLensIndex) {
                        detectHorizontalDragGestures(
                            onDragStart = { onRulerReveal() },
                            onHorizontalDrag = { _, dragAmount -> onRulerDrag(dragAmount) },
                            onDragEnd = onRulerDragEnd,
                            onDragCancel = onRulerDragEnd
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.presets.forEach { preset ->
                    val selected = preset.lensIndex == state.selectedLensIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Color(0xFF2A2A2A) else Color.Transparent)
                            .clickable {
                                if (selected) {
                                    onRulerReveal()
                                } else {
                                    onPresetClick(preset.lensIndex)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.label,
                            color = if (selected) AccentYellow else Color.White.copy(alpha = 0.86f),
                            style = if (selected) {
                                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomRuler(
    onRulerReveal: () -> Unit,
    onRulerDrag: (Float) -> Unit,
    onRulerDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xDD111111))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onRulerReveal() },
                    onHorizontalDrag = { _, dragAmount -> onRulerDrag(dragAmount) },
                    onDragEnd = onRulerDragEnd,
                    onDragCancel = onRulerDragEnd
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 18.dp)
        ) {
            val centerX = size.width / 2f
            val midY = size.height / 2f
            val tickCount = 25
            val spacing = size.width / (tickCount - 1)

            repeat(tickCount) { index ->
                val x = spacing * index
                val isMajor = index % 4 == 0
                val tickHeight = if (isMajor) size.height * 0.72f else size.height * 0.42f
                drawLine(
                    color = Color.White.copy(alpha = if (isMajor) 0.78f else 0.38f),
                    start = Offset(x, midY - tickHeight / 2f),
                    end = Offset(x, midY + tickHeight / 2f),
                    strokeWidth = if (isMajor) 3f else 2f
                )
            }

            drawLine(
                color = AccentYellow,
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = 4f
            )
        }
    }
}

@Composable
fun CameraFacingSwitchButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0xFF171717))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cameraswitch,
            contentDescription = "切换前后摄像头",
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(26.dp)
        )
    }
}
