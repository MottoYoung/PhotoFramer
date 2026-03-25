package com.photoframer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframer.R
import com.photoframer.ui.theme.TextPrimary

/**
 * Apple Intelligence 风格的流光色彩
 */
private val GlowColors = listOf(
    Color(0xFF9DC3FF),  // 冷白蓝
    Color(0xFF6EA7FF),  // 天蓝
    Color(0xFF4D8DFF),  // 主蓝
    Color(0xFF7DB9FF),  // 亮蓝
    Color(0xFFE7F1FF),  // 冷白
    Color(0xFF89B6FF),  // 柔和浅蓝
)

/**
 * 加载覆盖层 - Apple Intelligence 边框流光效果
 * 特点：光效从右上角沿边框扩散，扩散完成后开始流动旋转
 */
@Composable
fun LoadingOverlay(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp  // 预览框是矩形，无圆角
) {
    // ===== 进入动画：边框扩散 =====
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationStarted = true }
    
    // 扩散进度 (0 -> 1)：光效沿边框从一点扩散到全部
    val spreadProgress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "spread"
    )
    
    // 透明度淡入
    val alphaProgress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )
    
    // ===== 循环动画 =====
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    
    // 颜色旋转（扩散完成后才开始）
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // 呼吸效果
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // 只有扩散完成后才应用旋转
    val effectiveRotation = if (spreadProgress >= 0.95f) rotation else 0f
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alphaProgress),
        contentAlignment = Alignment.Center
    ) {
        // 半透明背景
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color.Black.copy(alpha = 0.4f))
        }
        
        // 流光边框效果 - 多层叠加，贴合边缘
        // 第1层：最外层大光晕（模糊）
        SpreadingGlowBorder(
            spreadProgress = spreadProgress,
            rotation = effectiveRotation,
            pulse = pulse,
            cornerRadius = cornerRadius,
            strokeWidth = 24.dp,
            alpha = 0.35f,
            modifier = Modifier
                .fillMaxSize()
                .blur(16.dp)
        )
        
        // 第2层：中等光晕
        SpreadingGlowBorder(
            spreadProgress = spreadProgress,
            rotation = effectiveRotation,
            pulse = pulse,
            cornerRadius = cornerRadius,
            strokeWidth = 14.dp,
            alpha = 0.5f,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp)
        )
        
        // 第3层：清晰边框
        SpreadingGlowBorder(
            spreadProgress = spreadProgress,
            rotation = effectiveRotation,
            pulse = pulse,
            cornerRadius = cornerRadius,
            strokeWidth = 5.dp,
            alpha = 0.9f,
            modifier = Modifier.fillMaxSize()
        )
        
        // 中心内容
        CenterContent(isVisible = spreadProgress > 0.5f)
    }
}

/**
 * 带扩散效果的流光边框
 * 光效从右上角开始，沿边框双向扩散
 */
@Composable
private fun SpreadingGlowBorder(
    spreadProgress: Float,
    rotation: Float,
    pulse: Float,
    cornerRadius: Dp,
    strokeWidth: Dp,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        
        val strokePx = strokeWidth.toPx()
        val w = size.width - strokePx
        val h = size.height - strokePx
        val halfStroke = strokePx / 2
        
        // 边框四个边的长度
        val topLen = w
        val rightLen = h
        val bottomLen = w
        val leftLen = h
        val perimeter = topLen + rightLen + bottomLen + leftLen
        
        // 可见长度
        val totalVisible = perimeter * spreadProgress
        val halfVisible = totalVisible / 2
        
        // 创建旋转的颜色渐变
        val colorStops = createRotatedColorStops(rotation, alpha * pulse)
        val brush = Brush.sweepGradient(
            colorStops = colorStops,
            center = Offset(size.width / 2, size.height / 2)
        )
        
        // 从右上角开始，双向扩散的路径
        // 路径1：从右上角顺时针 (向下 → 左 → 上)
        val path1 = Path().apply {
            // 起点：右上角
            moveTo(w + halfStroke, halfStroke)
            
            var remaining = halfVisible
            
            // 向下（右边）
            val rightDraw = minOf(remaining, rightLen)
            if (rightDraw > 0) {
                lineTo(w + halfStroke, halfStroke + rightDraw)
                remaining -= rightDraw
            }
            
            // 向左（底边）
            if (remaining > 0) {
                val bottomDraw = minOf(remaining, bottomLen)
                lineTo(w + halfStroke - bottomDraw, h + halfStroke)
                remaining -= bottomDraw
            }
            
            // 向上（左边）
            if (remaining > 0) {
                val leftDraw = minOf(remaining, leftLen)
                lineTo(halfStroke, h + halfStroke - leftDraw)
                remaining -= leftDraw
            }
            
            // 向右（顶边，到左上角方向）
            if (remaining > 0) {
                val topDraw = minOf(remaining, topLen)
                lineTo(halfStroke + topDraw, halfStroke)
            }
        }
        
        // 路径2：从右上角逆时针 (向左 → 下 → 右)
        val path2 = Path().apply {
            // 起点：右上角
            moveTo(w + halfStroke, halfStroke)
            
            var remaining = halfVisible
            
            // 向左（顶边）
            val topDraw = minOf(remaining, topLen)
            if (topDraw > 0) {
                lineTo(w + halfStroke - topDraw, halfStroke)
                remaining -= topDraw
            }
            
            // 向下（左边）
            if (remaining > 0) {
                val leftDraw = minOf(remaining, leftLen)
                lineTo(halfStroke, halfStroke + leftDraw)
                remaining -= leftDraw
            }
            
            // 向右（底边）
            if (remaining > 0) {
                val bottomDraw = minOf(remaining, bottomLen)
                lineTo(halfStroke + bottomDraw, h + halfStroke)
                remaining -= bottomDraw
            }
            
            // 向上（右边，到右下角方向）
            if (remaining > 0) {
                val rightDraw = minOf(remaining, rightLen)
                lineTo(w + halfStroke, h + halfStroke - rightDraw)
            }
        }
        
        // 绘制两条路径
        drawPath(
            path = path1,
            brush = brush,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        
        drawPath(
            path = path2,
            brush = brush,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

/**
 * 创建旋转的颜色停靠点
 */
private fun createRotatedColorStops(rotation: Float, alpha: Float): Array<Pair<Float, Color>> {
    val rotationFraction = (rotation % 360f) / 360f
    
    val stops = GlowColors.mapIndexed { index, color ->
        val basePos = index.toFloat() / GlowColors.size
        val rotatedPos = (basePos + rotationFraction) % 1f
        rotatedPos to color.copy(alpha = alpha)
    }.sortedBy { it.first }.toMutableList()
    
    // 确保渐变闭合
    if (stops.isNotEmpty()) {
        if (stops.first().first > 0.01f) {
            stops.add(0, 0f to stops.last().second)
        }
        if (stops.last().first < 0.99f) {
            stops.add(1f to stops.first().second)
        }
    }
    
    return stops.toTypedArray()
}

/**
 * 中心加载内容
 */
@Composable
private fun CenterContent(isVisible: Boolean) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "contentAlpha"
    )
    
    val contentScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "contentScale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .alpha(contentAlpha)
            .scale(contentScale)
    ) {
        Text(
            text = "✨",
            fontSize = 42.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.analyzing),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = "AI 正在分析构图...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary.copy(alpha = 0.5f)
        )
    }
}
