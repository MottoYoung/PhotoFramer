package com.photoframer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photoframer.data.api.CompositionStep

/**
 * 动态引导层
 * 在屏幕中心绘制指示箭头
 */
@Composable
fun GuidanceOverlay(
    step: CompositionStep,
    validationResult: com.photoframer.vision.StepValidationResult? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrow_anim")
    val offsetAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_offset"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // 只有 Shift 操作才绘制方向箭头
        if (step.actionType.equals("shift", ignoreCase = true)) {
            val path = Path()
            val arrowSize = 60.dp.toPx()
            val strokeWidth = 6.dp.toPx()
            
            // 根据方向绘制
            when (step.direction.lowercase()) {
                "left" -> {
                    // 向左指的箭头
                    val startX = centerX + arrowSize + offsetAnim
                    val endX = centerX - arrowSize + offsetAnim
                    
                    path.moveTo(startX, centerY)
                    path.lineTo(endX, centerY)
                    path.lineTo(endX + arrowSize/2, centerY - arrowSize/2)
                    path.moveTo(endX, centerY)
                    path.lineTo(endX + arrowSize/2, centerY + arrowSize/2)
                }
                "right" -> {
                    // 向右指的箭头
                    val startX = centerX - arrowSize - offsetAnim
                    val endX = centerX + arrowSize - offsetAnim
                    
                    path.moveTo(startX, centerY)
                    path.lineTo(endX, centerY)
                    path.lineTo(endX - arrowSize/2, centerY - arrowSize/2)
                    path.moveTo(endX, centerY)
                    path.lineTo(endX - arrowSize/2, centerY + arrowSize/2)
                }
                "up" -> {
                    // 向上指的箭头
                    val startY = centerY + arrowSize + offsetAnim
                    val endY = centerY - arrowSize + offsetAnim
                    
                    path.moveTo(centerX, startY)
                    path.lineTo(centerX, endY)
                    path.lineTo(centerX - arrowSize/2, endY + arrowSize/2)
                    path.moveTo(centerX, endY)
                    path.lineTo(centerX + arrowSize/2, endY + arrowSize/2)
                }
                "down" -> {
                    // 向下指的箭头
                    val startY = centerY - arrowSize - offsetAnim
                    val endY = centerY + arrowSize - offsetAnim
                    
                    path.moveTo(centerX, startY)
                    path.lineTo(centerX, endY)
                    path.lineTo(centerX - arrowSize/2, endY - arrowSize/2)
                    path.moveTo(centerX, endY)
                    path.lineTo(centerX + arrowSize/2, endY - arrowSize/2)
                }
            }
            
            
            val arrowColor = if (validationResult?.isCompleted == true) com.photoframer.ui.theme.SuccessGreen else Color.Yellow
            
            drawPath(
                path = path,
                color = arrowColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
