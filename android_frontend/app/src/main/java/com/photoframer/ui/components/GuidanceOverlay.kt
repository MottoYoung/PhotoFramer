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
 * 在屏幕中心绘制指示箭头，使用 Compose 动画平滑圆环移动
 */
@Composable
fun GuidanceOverlay(
    step: CompositionStep?,
    validationResult: com.photoframer.vision.StepValidationResult? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_anim")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // UI 层补间动画：让圆环在 60fps 下平滑移动
    val isCompleted = validationResult?.isCompleted == true
    val isZoomStep = step?.actionType.equals("zoom", ignoreCase = true)
    val isViewChangeStep = step?.actionType.equals("view-change", ignoreCase = true)
    
    // 各步骤类型的 tx 语义不同：
    // Zoom: tx = visualScale（1.0=完美匹配），默认 1.0
    // View-change: tx = progress（0-1 完成进度），默认 0
    // Shift: tx/ty = 位移矢量，死区时归零
    val rawTx: Float
    val rawTy: Float
    when {
        isZoomStep -> {
            rawTx = if (isCompleted) 1.0f else (validationResult?.tx ?: 1.0f)
            rawTy = 0f
        }
        isViewChangeStep -> {
            rawTx = if (isCompleted) 1.0f else (validationResult?.tx ?: 0f)
            rawTy = 0f
        }
        else -> {
            val rawDistance = kotlin.math.sqrt(
                ((validationResult?.tx ?: 0f) * (validationResult?.tx ?: 0f) +
                 (validationResult?.ty ?: 0f) * (validationResult?.ty ?: 0f)).toDouble()
            ).toFloat()
            val inDeadZone = isCompleted || rawDistance < 25f  // 25 = SHIFT_THRESHOLD
            rawTx = if (inDeadZone) 0f else (validationResult?.tx ?: 0f)
            rawTy = if (inDeadZone) 0f else (validationResult?.ty ?: 0f)
        }
    }
    
    // 用 tween 而非 spring，确保动画时长固定且与文字防抖(500ms)节奏一致
    val animSpec: AnimationSpec<Float> = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    val animTx by animateFloatAsState(targetValue = rawTx, animationSpec = animSpec, label = "tx")
    val animTy by animateFloatAsState(targetValue = rawTy, animationSpec = animSpec, label = "ty")

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // ====== 尺寸（2x 放大，提升可见性） ======
        
        // 十字准星：中心空心圆 + 四根短线
        val crossHoleRadius = 12f
        val crossLineLength = 20f
        val crossGap = 6f
        val crossStroke = 3f
        
        // 目标圆环
        val ringRadius = 28f
        val ringStroke = 4f
        
        // 连线
        val lineStroke = 2.5f
        
        // 颜色 — 前景 + 黑色描边衬底（确保亮背景下可见）
        val color = if (isCompleted) com.photoframer.ui.theme.SuccessGreen else Color.White.copy(alpha = 0.85f)
        val shadow = Color.Black.copy(alpha = 0.5f)
        val shadowExtra = 3f  // 描边比前景宽多少

        // ======== 十字准星 ========
        // 衬底层（黑色，更粗）
        drawCircle(shadow, crossHoleRadius, Offset(centerX, centerY), style = Stroke(crossStroke + shadowExtra))
        val armStart = crossHoleRadius + crossGap
        val armEnd = armStart + crossLineLength
        drawLine(shadow, Offset(centerX + armStart, centerY), Offset(centerX + armEnd, centerY), strokeWidth = crossStroke + shadowExtra)
        drawLine(shadow, Offset(centerX - armStart, centerY), Offset(centerX - armEnd, centerY), strokeWidth = crossStroke + shadowExtra)
        drawLine(shadow, Offset(centerX, centerY + armStart), Offset(centerX, centerY + armEnd), strokeWidth = crossStroke + shadowExtra)
        drawLine(shadow, Offset(centerX, centerY - armStart), Offset(centerX, centerY - armEnd), strokeWidth = crossStroke + shadowExtra)
        // 前景层（白/绿色）
        drawCircle(color, crossHoleRadius, Offset(centerX, centerY), style = Stroke(crossStroke))
        drawLine(color, Offset(centerX + armStart, centerY), Offset(centerX + armEnd, centerY), strokeWidth = crossStroke)
        drawLine(color, Offset(centerX - armStart, centerY), Offset(centerX - armEnd, centerY), strokeWidth = crossStroke)
        drawLine(color, Offset(centerX, centerY + armStart), Offset(centerX, centerY + armEnd), strokeWidth = crossStroke)
        drawLine(color, Offset(centerX, centerY - armStart), Offset(centerX, centerY - armEnd), strokeWidth = crossStroke)

        // ======== 目标圆环 + 连线 (仅 Shift) ========
        if (step?.actionType.equals("shift", ignoreCase = true)) {
            val tx = animTx
            val ty = animTy
            
            val scaleFactor = size.width / 360f
            
            var targetX = centerX + tx * scaleFactor
            var targetY = centerY + ty * scaleFactor
            
            val margin = 40f
            targetX = targetX.coerceIn(margin, size.width - margin)
            targetY = targetY.coerceIn(margin, size.height - margin)

            val dist = kotlin.math.sqrt((targetX - centerX) * (targetX - centerX) + (targetY - centerY) * (targetY - centerY))
            
            // 连线
            val minDist = armEnd + ringRadius + 8f
            if (dist > minDist) {
                val dx = (targetX - centerX) / dist
                val dy = (targetY - centerY) / dist
                
                val lx1 = centerX + dx * (armEnd + 4f)
                val ly1 = centerY + dy * (armEnd + 4f)
                val lx2 = targetX - dx * (ringRadius + 4f)
                val ly2 = targetY - dy * (ringRadius + 4f)

                val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    intervals = floatArrayOf(10f, 8f), phase = 0f
                )
                // 衬底
                drawLine(shadow, Offset(lx1, ly1), Offset(lx2, ly2),
                    strokeWidth = lineStroke + shadowExtra,
                    pathEffect = dashEffect
                )
                // 前景
                drawLine(color.copy(alpha = 0.6f), Offset(lx1, ly1), Offset(lx2, ly2),
                    strokeWidth = lineStroke,
                    pathEffect = dashEffect
                )
            }
            
            // 目标圆环
            val r = if (isCompleted) ringRadius * pulseScale else ringRadius
            // 衬底
            drawCircle(shadow, r, Offset(targetX, targetY), style = Stroke(ringStroke + shadowExtra))
            // 前景
            drawCircle(color, r, Offset(targetX, targetY), style = Stroke(ringStroke))
        }

        // ======== 同心双圆环 (仅 Zoom) ========
        if (step?.actionType.equals("zoom", ignoreCase = true)) {
            // validationResult.tx 存放 visualScale（当前视觉缩放比，1.0=完美）
            val visualScale = animTx.coerceIn(0.3f, 3.0f)  // 安全范围
            
            // 内圈 = 目标参考圈（固定大小）
            val innerRadius = 40f
            // 外圈 = 当前状态圈（半径 = innerRadius * visualScale）
            // visualScale > 1 → 画面偏大 → 外圈比内圈大
            // visualScale < 1 → 画面偏小 → 外圈比内圈小
            val rawOuterRadius = innerRadius * visualScale
            val outerRadius = if (isCompleted) {
                innerRadius  // 完成时合一
            } else {
                rawOuterRadius * pulseScale  // 脉冲动画
            }
            
            val zoomRingStroke = 3f
            
            // 内圈（目标参考）— 白色虚线
            val innerDash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                intervals = floatArrayOf(8f, 6f), phase = 0f
            )
            // 衬底
            drawCircle(
                shadow, innerRadius, Offset(centerX, centerY),
                style = Stroke(zoomRingStroke + shadowExtra, pathEffect = innerDash)
            )
            // 前景
            drawCircle(
                color.copy(alpha = 0.5f), innerRadius, Offset(centerX, centerY),
                style = Stroke(zoomRingStroke, pathEffect = innerDash)
            )
            
            // 外圈（当前状态）— 实线
            // 衬底
            drawCircle(
                shadow, outerRadius, Offset(centerX, centerY),
                style = Stroke(zoomRingStroke + 1f + shadowExtra)
            )
            // 前景
            drawCircle(
                color, outerRadius, Offset(centerX, centerY),
                style = Stroke(zoomRingStroke + 1f)
            )
        }

        // ======== 进度弧环 (仅 View-change) ========
        if (step?.actionType.equals("view-change", ignoreCase = true)) {
            // validationResult.tx 存放 progress（0-1 完成进度）
            val progress = animTx.coerceIn(0f, 1f)
            
            val arcRadius = armEnd + 12f  // 准星外围
            val arcStroke = 4f
            val sweepAngle = progress * 360f  // 弧长 = 进度 × 360°
            
            // 底层轨道圈（灰色虚线，标识完整路径）
            val trackDash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                intervals = floatArrayOf(6f, 5f), phase = 0f
            )
            drawCircle(
                Color.White.copy(alpha = 0.15f), arcRadius, Offset(centerX, centerY),
                style = Stroke(arcStroke, pathEffect = trackDash)
            )
            
            if (sweepAngle > 0.5f) {
                // 进度弧线（从顶部 -90° 开始，顺时针）
                val arcRect = androidx.compose.ui.geometry.Rect(
                    centerX - arcRadius, centerY - arcRadius,
                    centerX + arcRadius, centerY + arcRadius
                )
                val finalSweep = if (isCompleted) 360f * pulseScale.coerceAtMost(1f) else sweepAngle
                
                // 衬底
                drawArc(
                    color = shadow,
                    startAngle = -90f,
                    sweepAngle = finalSweep,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        arcRect.left, arcRect.top
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        arcRect.width, arcRect.height
                    ),
                    style = Stroke(arcStroke + shadowExtra, cap = StrokeCap.Round)
                )
                // 前景
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = finalSweep,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        arcRect.left, arcRect.top
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        arcRect.width, arcRect.height
                    ),
                    style = Stroke(arcStroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}

