package com.photoframer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframer.R
import com.photoframer.ui.theme.TextPrimary

/**
 * Apple Intelligence 风格的发光颜色
 */
private val AppleIntelligenceColors = listOf(
    Color(0xFFBC82F3),  // 紫色
    Color(0xFFF5B9EA),  // 粉色
    Color(0xFF8D9FFF),  // 蓝紫色
    Color(0xFFAA6EEE),  // 深紫色
    Color(0xFFFF6778),  // 红色
    Color(0xFFFFBA71),  // 橙色
    Color(0xFFC686FF),  // 亮紫色
)

/**
 * Apple Intelligence 发光效果
 * 矩形固定，彩色光沿边缘流动（使用渐变旋转而非画布旋转）
 */
@Composable
fun AppleIntelligenceGlowEffect(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    strokeWidth: Dp = 6.dp,
    blurRadius: Dp = 8.dp
) {
    // 渐变偏移动画（0-1循环）
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientOffset"
    )
    
    // 脉冲动画
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(modifier = modifier) {
        // 底层：清晰边框
        GlowBorderLayer(
            gradientOffset = gradientOffset,
            strokeWidth = strokeWidth,
            cornerRadius = cornerRadius,
            alpha = 1f * pulse
        )
        
        // 上层：模糊发光
        GlowBorderLayer(
            gradientOffset = gradientOffset + 0.15f,
            strokeWidth = strokeWidth * 1.5f,
            cornerRadius = cornerRadius,
            alpha = 0.6f * pulse,
            modifier = Modifier.blur(blurRadius)
        )
        
        // 最上层：大范围光晕
        GlowBorderLayer(
            gradientOffset = gradientOffset - 0.1f,
            strokeWidth = strokeWidth * 2f,
            cornerRadius = cornerRadius,
            alpha = 0.3f,
            modifier = Modifier.blur(blurRadius * 2)
        )
    }
}

/**
 * 发光边框层 - 通过偏移颜色位置实现流动效果
 */
@Composable
private fun GlowBorderLayer(
    gradientOffset: Float,
    strokeWidth: Dp,
    cornerRadius: Dp,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val strokePx = strokeWidth.toPx()
        val radiusPx = cornerRadius.toPx()
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // 创建固定的圆角矩形路径
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = strokePx / 2,
                    top = strokePx / 2,
                    right = size.width - strokePx / 2,
                    bottom = size.height - strokePx / 2,
                    cornerRadius = CornerRadius(radiusPx)
                )
            )
        }
        
        // 通过偏移每个颜色的位置来实现流动效果
        // 关键：保持颜色位置间隔不变，只整体偏移
        val colorStops = AppleIntelligenceColors.mapIndexed { index, color ->
            val basePos = index.toFloat() / AppleIntelligenceColors.size
            // 加上偏移量，然后取模确保在0-1范围
            val pos = (basePos + gradientOffset) % 1f
            pos to color.copy(alpha = alpha)
        }.sortedBy { it.first }.toTypedArray()
        
        // 为了平滑过渡，在首尾添加额外的颜色停靠点
        val smoothColorStops = buildList {
            // 补齐0位置
            if (colorStops.first().first > 0.01f) {
                add(0f to colorStops.last().second)
            }
            addAll(colorStops.toList())
            // 补齐1位置
            if (colorStops.last().first < 0.99f) {
                add(1f to colorStops.first().second)
            }
        }.toTypedArray()
        
        // 创建扫描渐变
        val brush = Brush.sweepGradient(
            colorStops = smoothColorStops,
            center = Offset(centerX, centerY)
        )
        
        // 绘制路径（路径固定，渐变在动）
        drawPath(
            path = path,
            brush = brush,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

/**
 * 加载覆盖层（分析中状态）
 */
@Composable
fun LoadingOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        // Apple Intelligence 发光边框效果
        AppleIntelligenceGlowEffect(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            cornerRadius = 20.dp,
            strokeWidth = 5.dp,
            blurRadius = 10.dp
        )
        
        // 分析中文字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 发光圆形指示器
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                AppleIntelligenceGlowEffect(
                    modifier = Modifier.size(80.dp),
                    cornerRadius = 40.dp,
                    strokeWidth = 4.dp,
                    blurRadius = 6.dp
                )
                
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✨", fontSize = 24.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.analyzing),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "AI 正在分析构图...",
                fontSize = 14.sp,
                color = TextPrimary.copy(alpha = 0.7f)
            )
        }
    }
}
