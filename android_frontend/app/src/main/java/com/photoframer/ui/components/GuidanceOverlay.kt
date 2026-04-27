package com.photoframer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photoframer.data.api.CompositionStep
import com.photoframer.guidance.isViewpointActionType
import com.photoframer.guidance.normalizedActionType

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
    val isViewChangeStep = step?.actionType?.isViewpointActionType() == true
    
    // 各步骤类型的 tx/uiHint 语义不同：
    // Zoom: tx = visualScale（1.0=完美匹配），默认 1.0
    // View-change: uiHintX/uiHintY = 机位偏移提示，progress 走独立字段
    // Shift: tx/ty = 位移矢量，死区时归零
    val rawTx: Float
    val rawTy: Float
    when {
        isZoomStep -> {
            rawTx = if (isCompleted) 1.0f else (validationResult?.tx ?: 1.0f)
            rawTy = 0f
        }
        isViewChangeStep -> {
            rawTx = if (isCompleted) 0f else (validationResult?.uiHintX ?: 0f)
            rawTy = if (isCompleted) 0f else (validationResult?.uiHintY ?: 0f)
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
    val animViewChangeProgress by animateFloatAsState(
        targetValue = if (isCompleted) 1f else (validationResult?.progress ?: 0f),
        animationSpec = animSpec,
        label = "view_progress"
    )
    val animViewChangeDirectionConfidence by animateFloatAsState(
        targetValue = if (isCompleted) 1f else (validationResult?.directionConfidence ?: 0f),
        animationSpec = animSpec,
        label = "view_direction_confidence"
    )
    val animViewChangeSubjectConfidence by animateFloatAsState(
        targetValue = if (isCompleted) 1f else (validationResult?.subjectConfidence ?: 0f),
        animationSpec = animSpec,
        label = "view_subject_confidence"
    )
    val hasTrackedSubject = validationResult?.hasSubject != false
    val uiSpaceWidth = (validationResult?.uiSpaceWidth ?: 360f).coerceAtLeast(1f)
    val uiSpaceHeight = (validationResult?.uiSpaceHeight ?: uiSpaceWidth).coerceAtLeast(1f)

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
            
            val scaleFactorX = size.width / uiSpaceWidth
            val scaleFactorY = size.height / uiSpaceHeight
            
            var targetX = centerX + tx * scaleFactorX
            var targetY = centerY + ty * scaleFactorY
            
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

        // ======== 机位变化动作示意图 (仅 View-change) ========
        if (step?.actionType?.isViewpointActionType() == true) {
            val progress = animViewChangeProgress.coerceIn(0f, 1f)
            val directionConfidence = animViewChangeDirectionConfidence.coerceIn(0f, 1f)
            val subjectConfidence = animViewChangeSubjectConfidence.coerceIn(0f, 1f)
            val actionType = step?.actionType.orEmpty()
            val subjectColor = when {
                isCompleted -> com.photoframer.ui.theme.SuccessGreen
                !hasTrackedSubject -> Color(0xFFFFB454)
                subjectConfidence >= 0.72f -> Color(0xFF7BE495)
                subjectConfidence >= 0.42f -> Color(0xFFFFD166)
                else -> Color(0xFFFF8A65)
            }
            val guideStrength = (0.35f + directionConfidence * 0.65f).coerceIn(0.35f, 1f)
            drawViewChangeSubjectAnchor(
                centerX = centerX,
                centerY = centerY,
                confidence = subjectConfidence,
                hasTrackedSubject = hasTrackedSubject,
                color = subjectColor,
                shadow = shadow
            )
            drawViewChangeActionGuide(
                centerX = centerX,
                centerY = centerY,
                actionType = actionType,
                direction = step.direction,
                progress = progress,
                guideStrength = guideStrength,
                pulseScale = pulseScale,
                isCompleted = isCompleted,
                color = color,
                shadow = shadow,
                subjectColor = subjectColor
            )
        }
    }
}

private fun DrawScope.drawViewChangeSubjectAnchor(
    centerX: Float,
    centerY: Float,
    confidence: Float,
    hasTrackedSubject: Boolean,
    color: Color,
    shadow: Color
) {
    val outerRadius = 34f
    val innerRadius = 18f
    val bracketRadius = 48f
    val stroke = 3.5f
    val tint = color.copy(alpha = (0.42f + confidence * 0.5f).coerceIn(0.32f, 0.95f))

    drawCircle(shadow, outerRadius, Offset(centerX, centerY), style = Stroke(width = stroke + 2f))
    drawCircle(tint, outerRadius, Offset(centerX, centerY), style = Stroke(width = stroke))
    drawCircle(color.copy(alpha = if (hasTrackedSubject) 0.18f else 0.08f), innerRadius, Offset(centerX, centerY))

    val bracketLen = 15f
    val bracketGap = 14f
    fun drawBracket(dx: Float, dy: Float) {
        val sx = centerX + dx * bracketGap
        val sy = centerY + dy * bracketGap
        val ex = centerX + dx * bracketRadius
        val ey = centerY + dy * bracketRadius
        drawLine(shadow, Offset(sx, ey), Offset(ex, ey), strokeWidth = stroke + 2f, cap = StrokeCap.Round)
        drawLine(shadow, Offset(ex, sy), Offset(ex, ey), strokeWidth = stroke + 2f, cap = StrokeCap.Round)
        drawLine(tint, Offset(sx, ey), Offset(ex, ey), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(ex, sy), Offset(ex, ey), strokeWidth = stroke, cap = StrokeCap.Round)
    }
    drawBracket(-1f, -1f)
    drawBracket(1f, -1f)
    drawBracket(-1f, 1f)
    drawBracket(1f, 1f)

    if (!hasTrackedSubject) {
        val lost = 12f
        drawLine(shadow, Offset(centerX - lost, centerY - lost), Offset(centerX + lost, centerY + lost), strokeWidth = stroke + 2f)
        drawLine(shadow, Offset(centerX + lost, centerY - lost), Offset(centerX - lost, centerY + lost), strokeWidth = stroke + 2f)
        drawLine(color, Offset(centerX - lost, centerY - lost), Offset(centerX + lost, centerY + lost), strokeWidth = stroke)
        drawLine(color, Offset(centerX + lost, centerY - lost), Offset(centerX - lost, centerY + lost), strokeWidth = stroke)
    }
}

private fun DrawScope.drawViewChangeActionGuide(
    centerX: Float,
    centerY: Float,
    actionType: String,
    direction: String,
    progress: Float,
    guideStrength: Float,
    pulseScale: Float,
    isCompleted: Boolean,
    color: Color,
    shadow: Color,
    subjectColor: Color
) {
    when (actionType.normalizedActionType()) {
        "raisecamera", "lowercamera" -> drawElevationGuide(
            centerX = centerX,
            centerY = centerY,
            moveUp = actionType.normalizedActionType() == "raisecamera" || direction.equals("high-angle", ignoreCase = true),
            progress = progress,
            guideStrength = guideStrength,
            pulseScale = pulseScale,
            isCompleted = isCompleted,
            color = color,
            shadow = shadow,
            subjectColor = subjectColor
        )
        "orbit" -> drawOrbitGuide(
            centerX = centerX,
            centerY = centerY,
            moveRight = !direction.equals("left", ignoreCase = true),
            progress = progress,
            guideStrength = guideStrength,
            pulseScale = pulseScale,
            isCompleted = isCompleted,
            color = color,
            shadow = shadow,
            subjectColor = subjectColor
        )
        "step" -> drawStepGuide(
            centerX = centerX,
            centerY = centerY,
            moveForward = !direction.equals("backward", ignoreCase = true),
            progress = progress,
            guideStrength = guideStrength,
            pulseScale = pulseScale,
            isCompleted = isCompleted,
            color = color,
            shadow = shadow,
            subjectColor = subjectColor
        )
        else -> drawOrbitGuide(
            centerX = centerX,
            centerY = centerY,
            moveRight = true,
            progress = progress,
            guideStrength = guideStrength,
            pulseScale = pulseScale,
            isCompleted = isCompleted,
            color = color,
            shadow = shadow,
            subjectColor = subjectColor
        )
    }
}

private fun DrawScope.drawElevationGuide(
    centerX: Float,
    centerY: Float,
    moveUp: Boolean,
    progress: Float,
    guideStrength: Float,
    pulseScale: Float,
    isCompleted: Boolean,
    color: Color,
    shadow: Color,
    subjectColor: Color
) {
    val topY = centerY - 152f
    val bottomY = centerY + 152f
    val laneHalfWidth = 28f
    val trackColor = Color.White.copy(alpha = 0.16f)
    val activeColor = color.copy(alpha = (0.58f + guideStrength * 0.3f).coerceIn(0.55f, 0.95f))
    val startY = if (moveUp) bottomY - 18f else topY + 18f
    val targetY = if (moveUp) topY + 24f else bottomY - 24f
    val markerY = if (isCompleted) targetY else lerpF(startY, targetY, progress)
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

    drawLine(trackColor, Offset(centerX, topY), Offset(centerX, bottomY), strokeWidth = 5f, pathEffect = dash)
    drawLine(trackColor.copy(alpha = 0.12f), Offset(centerX - laneHalfWidth, topY), Offset(centerX - laneHalfWidth, bottomY), strokeWidth = 2f)
    drawLine(trackColor.copy(alpha = 0.12f), Offset(centerX + laneHalfWidth, topY), Offset(centerX + laneHalfWidth, bottomY), strokeWidth = 2f)

    drawLine(shadow, Offset(centerX, startY), Offset(centerX, markerY), strokeWidth = 8f, cap = StrokeCap.Round)
    drawLine(activeColor, Offset(centerX, startY), Offset(centerX, markerY), strokeWidth = 4.5f, cap = StrokeCap.Round)

    val targetGlow = 18f * if (isCompleted) pulseScale else 1f
    drawCircle(subjectColor.copy(alpha = 0.18f), targetGlow, Offset(centerX, targetY))
    drawGuidePhone(
        center = Offset(centerX, targetY),
        width = 44f,
        height = 72f,
        color = activeColor.copy(alpha = 0.45f),
        shadow = shadow,
        filled = false
    )
    drawGuidePhone(
        center = Offset(centerX, markerY),
        width = 48f,
        height = 78f,
        color = if (isCompleted) com.photoframer.ui.theme.SuccessGreen else activeColor,
        shadow = shadow
    )

    val arrowY = if (moveUp) targetY - 42f else targetY + 42f
    drawArrowTip(
        tip = Offset(centerX, arrowY),
        direction = if (moveUp) Offset(0f, -1f) else Offset(0f, 1f),
        size = 16f,
        color = activeColor,
        shadow = shadow
    )
}

private fun DrawScope.drawOrbitGuide(
    centerX: Float,
    centerY: Float,
    moveRight: Boolean,
    progress: Float,
    guideStrength: Float,
    pulseScale: Float,
    isCompleted: Boolean,
    color: Color,
    shadow: Color,
    subjectColor: Color
) {
    val radiusX = 122f
    val radiusY = 98f
    val rectTopLeft = Offset(centerX - radiusX, centerY - radiusY)
    val rectSize = androidx.compose.ui.geometry.Size(radiusX * 2, radiusY * 2)
    val startAngle = if (moveRight) 180f else 0f
    val totalSweep = if (moveRight) 180f else -180f
    val activeSweep = if (isCompleted) totalSweep else totalSweep * progress
    val activeColor = color.copy(alpha = (0.58f + guideStrength * 0.28f).coerceIn(0.55f, 0.92f))
    val trackDash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

    drawArc(
        color = Color.White.copy(alpha = 0.15f),
        startAngle = startAngle,
        sweepAngle = totalSweep,
        useCenter = false,
        topLeft = rectTopLeft,
        size = rectSize,
        style = Stroke(width = 5f, pathEffect = trackDash, cap = StrokeCap.Round)
    )
    drawArc(
        color = shadow,
        startAngle = startAngle,
        sweepAngle = activeSweep,
        useCenter = false,
        topLeft = rectTopLeft,
        size = rectSize,
        style = Stroke(width = 9f, cap = StrokeCap.Round)
    )
    drawArc(
        color = activeColor,
        startAngle = startAngle,
        sweepAngle = activeSweep,
        useCenter = false,
        topLeft = rectTopLeft,
        size = rectSize,
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )

    val sourceAngle = if (moveRight) 180f else 0f
    val targetAngle = if (moveRight) 360f else 180f
    val markerAngle = if (isCompleted) targetAngle else lerpF(sourceAngle, targetAngle, progress)
    val source = ellipsePoint(centerX, centerY, radiusX, radiusY, sourceAngle)
    val target = ellipsePoint(centerX, centerY, radiusX, radiusY, targetAngle)
    val marker = ellipsePoint(centerX, centerY, radiusX, radiusY, markerAngle)

    drawCircle(subjectColor.copy(alpha = 0.10f), 82f, Offset(centerX, centerY))
    drawGuidePhone(
        center = target,
        width = 42f,
        height = 68f,
        color = activeColor.copy(alpha = 0.45f),
        shadow = shadow,
        filled = false
    )
    drawGuidePhone(
        center = marker,
        width = 46f,
        height = 74f,
        color = if (isCompleted) com.photoframer.ui.theme.SuccessGreen else activeColor,
        shadow = shadow
    )
    drawCircle(Color.White.copy(alpha = 0.10f), 8f, source)

    val arrowDirection = if (moveRight) Offset(1f, 0f) else Offset(-1f, 0f)
    drawArrowTip(
        tip = Offset(target.x + arrowDirection.x * 28f, target.y),
        direction = arrowDirection,
        size = 16f * if (isCompleted) pulseScale else 1f,
        color = activeColor,
        shadow = shadow
    )
}

private fun DrawScope.drawStepGuide(
    centerX: Float,
    centerY: Float,
    moveForward: Boolean,
    progress: Float,
    guideStrength: Float,
    pulseScale: Float,
    isCompleted: Boolean,
    color: Color,
    shadow: Color,
    subjectColor: Color
) {
    val topY = centerY - 22f
    val bottomY = centerY + 166f
    val nearHalfWidth = 106f
    val farHalfWidth = 42f
    val activeColor = color.copy(alpha = (0.58f + guideStrength * 0.28f).coerceIn(0.55f, 0.92f))
    val startY = if (moveForward) bottomY - 10f else topY + 10f
    val targetY = if (moveForward) topY + 10f else bottomY - 10f
    val markerY = if (isCompleted) targetY else lerpF(startY, targetY, progress)

    drawLine(Color.White.copy(alpha = 0.16f), Offset(centerX - nearHalfWidth, bottomY), Offset(centerX - farHalfWidth, topY), strokeWidth = 4f)
    drawLine(Color.White.copy(alpha = 0.16f), Offset(centerX + nearHalfWidth, bottomY), Offset(centerX + farHalfWidth, topY), strokeWidth = 4f)
    drawLine(Color.White.copy(alpha = 0.10f), Offset(centerX, bottomY), Offset(centerX, topY), strokeWidth = 2f)

    val gateFractions = listOf(0f, 0.42f, 0.76f)
    gateFractions.forEach { fraction ->
        val y = lerpF(bottomY, topY, fraction)
        val halfWidth = lerpF(nearHalfWidth, farHalfWidth, fraction)
        val height = lerpF(82f, 36f, fraction)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(centerX - halfWidth, y - height / 2f),
            size = androidx.compose.ui.geometry.Size(halfWidth * 2f, height),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 3f)
        )
    }

    drawLine(shadow, Offset(centerX, startY), Offset(centerX, markerY), strokeWidth = 8f, cap = StrokeCap.Round)
    drawLine(activeColor, Offset(centerX, startY), Offset(centerX, markerY), strokeWidth = 4.5f, cap = StrokeCap.Round)

    val targetScale = if (moveForward) 0.85f else 1f
    drawGuidePhone(
        center = Offset(centerX, targetY),
        width = 44f * targetScale,
        height = 72f * targetScale,
        color = activeColor.copy(alpha = 0.45f),
        shadow = shadow,
        filled = false
    )
    drawGuidePhone(
        center = Offset(centerX, markerY),
        width = 48f * if (moveForward) lerpF(1f, 0.86f, progress) else lerpF(0.86f, 1f, progress),
        height = 78f * if (moveForward) lerpF(1f, 0.86f, progress) else lerpF(0.86f, 1f, progress),
        color = if (isCompleted) com.photoframer.ui.theme.SuccessGreen else activeColor,
        shadow = shadow
    )

    val arrowY = if (moveForward) topY - 32f else bottomY + 24f
    drawArrowTip(
        tip = Offset(centerX, arrowY),
        direction = if (moveForward) Offset(0f, -1f) else Offset(0f, 1f),
        size = 16f * if (isCompleted) pulseScale else 1f,
        color = activeColor,
        shadow = shadow
    )
    drawCircle(subjectColor.copy(alpha = 0.08f), 88f, Offset(centerX, centerY + 8f))
}

private fun DrawScope.drawGuidePhone(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
    shadow: Color,
    filled: Boolean = true
) {
    val radius = CornerRadius(width * 0.22f, width * 0.22f)
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
    val size = androidx.compose.ui.geometry.Size(width, height)

    if (filled) {
        drawRoundRect(
            color = color.copy(alpha = 0.12f),
            topLeft = topLeft,
            size = size,
            cornerRadius = radius
        )
    }

    drawRoundRect(
        color = shadow,
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Stroke(width = 5f)
    )
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Stroke(width = 3f)
    )
    drawCircle(shadow, 5f, Offset(center.x, topLeft.y + 10f))
    drawCircle(color, 3f, Offset(center.x, topLeft.y + 10f))
}

private fun DrawScope.drawArrowTip(
    tip: Offset,
    direction: Offset,
    size: Float,
    color: Color,
    shadow: Color
) {
    val path = Path().apply {
        moveTo(
            tip.x - direction.x * size - direction.y * size * 0.58f,
            tip.y - direction.y * size + direction.x * size * 0.58f
        )
        lineTo(tip.x, tip.y)
        lineTo(
            tip.x - direction.x * size + direction.y * size * 0.58f,
            tip.y - direction.y * size - direction.x * size * 0.58f
        )
    }
    drawPath(path = path, color = shadow, style = Stroke(width = 6f, cap = StrokeCap.Round))
    drawPath(path = path, color = color, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
}

private fun ellipsePoint(
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
    angleDegrees: Float
): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    return Offset(
        x = centerX + (kotlin.math.cos(radians) * radiusX).toFloat(),
        y = centerY + (kotlin.math.sin(radians) * radiusY).toFloat()
    )
}

private fun lerpF(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}
