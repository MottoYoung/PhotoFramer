package com.photoframer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 流光特效覆盖层
 * 用于 "正在分析" 或 "构图完美" 的状态反馈
 */
@Composable
fun GlowOverlay(
    color: Color = Color.Cyan,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_anim")
    
    // 旋转动画
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_rotation"
    )
    
    // 呼吸动画
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val strokeWidth = with(LocalDensity.current) { 6.dp.toPx() }
    val cornerRadius = with(LocalDensity.current) { 24.dp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 创建扫描式渐变
        val gradient = Brush.sweepGradient(
            colors = listOf(
                color.copy(alpha = 0f),
                color.copy(alpha = alpha),
                color.copy(alpha = 0f)
            ),
            center = center
        )
        
        // 我们其实不需要旋转整个画布，而是让渐变旋转
        // 但 sweepGradient 是基于角度的，所以我们需要 rotate drawContext
        
        drawContext.canvas.save()
        drawContext.transform.rotate(rotation, Offset(center.x, center.y))
        
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(strokeWidth/2, strokeWidth/2),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokeWidth)
        )
        
        drawContext.canvas.restore()
    }
}
