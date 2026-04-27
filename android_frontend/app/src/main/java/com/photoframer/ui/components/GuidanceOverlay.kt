package com.photoframer.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.photoframer.data.api.CompositionStep
import com.photoframer.ui.theme.BlueAccent
import com.photoframer.ui.theme.SuccessGreen
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 动态引导层
 * 在屏幕中心绘制更贴近相机辅助线风格的准星与目标圈
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
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val orbitPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_phase"
    )

    val isCompleted = validationResult?.isCompleted == true
    val isZoomStep = step?.actionType.equals("zoom", ignoreCase = true)
    val isViewChangeStep = step?.actionType.equals("view-change", ignoreCase = true)

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
            val rawDistance = sqrt(
                ((validationResult?.tx ?: 0f) * (validationResult?.tx ?: 0f) +
                    (validationResult?.ty ?: 0f) * (validationResult?.ty ?: 0f)).toDouble()
            ).toFloat()
            val inDeadZone = isCompleted || rawDistance < 25f
            rawTx = if (inDeadZone) 0f else (validationResult?.tx ?: 0f)
            rawTy = if (inDeadZone) 0f else (validationResult?.ty ?: 0f)
        }
    }

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
        val center = Offset(centerX, centerY)

        val accentColor = if (isCompleted) SuccessGreen else BlueAccent.copy(alpha = 0.96f)
        val neutralColor = Color.White.copy(alpha = 0.92f)
        val faintColor = Color.White.copy(alpha = 0.16f)
        val microGlow = accentColor.copy(alpha = 0.07f * pulseAlpha)
        val glowColor = accentColor.copy(alpha = 0.11f * pulseAlpha)
        val shadow = Color.Black.copy(alpha = 0.44f)

        drawCircle(
            color = microGlow,
            radius = 34f * pulseScale,
            center = center
        )
        drawCircle(
            color = glowColor,
            radius = 56f * pulseScale,
            center = center
        )

        drawReticle(
            center = center,
            accentColor = accentColor,
            neutralColor = neutralColor,
            faintColor = faintColor,
            shadowColor = shadow,
            pulseScale = pulseScale,
            orbitPhase = orbitPhase
        )

        if (step?.actionType.equals("shift", ignoreCase = true)) {
            val scaleFactor = size.width / 360f
            var targetX = centerX + animTx * scaleFactor
            var targetY = centerY + animTy * scaleFactor
            val margin = 44f

            targetX = targetX.coerceIn(margin, size.width - margin)
            targetY = targetY.coerceIn(margin, size.height - margin)

            val target = Offset(targetX, targetY)
            val distance = center.distanceTo(target)
            val ringRadius = if (isCompleted) 32f * pulseScale else 32f

            if (distance > 52f) {
                val dx = (targetX - centerX) / distance
                val dy = (targetY - centerY) / distance
                val start = Offset(centerX + dx * 31f, centerY + dy * 31f)
                val end = Offset(targetX - dx * (ringRadius + 10f), targetY - dy * (ringRadius + 10f))
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), orbitPhase / 10f)

                drawLine(
                    color = shadow,
                    start = start,
                    end = end,
                    strokeWidth = 5.6f,
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect
                )
                drawLine(
                    color = neutralColor.copy(alpha = 0.48f),
                    start = start,
                    end = end,
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect
                )
            }

            drawTargetRing(
                center = target,
                radius = ringRadius,
                accentColor = accentColor,
                neutralColor = neutralColor,
                shadowColor = shadow,
                pulseAlpha = pulseAlpha
            )
        }

        if (step?.actionType.equals("zoom", ignoreCase = true)) {
            val visualScale = animTx.coerceIn(0.3f, 3.0f)
            val innerRadius = 42f
            val outerRadius = if (isCompleted) innerRadius else innerRadius * visualScale * pulseScale
            val guideDash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), orbitPhase / 12f)

            drawCircle(
                color = shadow,
                radius = innerRadius,
                center = center,
                style = Stroke(width = 5f, pathEffect = guideDash)
            )
            drawCircle(
                color = faintColor,
                radius = innerRadius,
                center = center,
                style = Stroke(width = 2.2f, pathEffect = guideDash)
            )
            drawCircle(
                color = glowColor,
                radius = outerRadius + 9f,
                center = center
            )
            drawCircle(
                color = shadow,
                radius = outerRadius,
                center = center,
                style = Stroke(width = 6f)
            )
            drawCircle(
                color = accentColor,
                radius = outerRadius,
                center = center,
                style = Stroke(width = 2.8f)
            )

            drawOrbitDot(
                center = center,
                radius = outerRadius,
                angleDegrees = orbitPhase,
                color = accentColor
            )
        }

        if (step?.actionType.equals("view-change", ignoreCase = true)) {
            val progress = animTx.coerceIn(0f, 1f)
            val directionConfidence = animTy.coerceIn(0f, 1f)
            val arcRadius = 48f
            val arcStroke = 4.5f
            val sweepAngle = progress * 360f
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 7f), orbitPhase / 10f)

            drawCircle(
                color = faintColor,
                radius = arcRadius,
                center = center,
                style = Stroke(width = 2.2f, pathEffect = dash)
            )

            if (sweepAngle > 0.5f) {
                val rect = Rect(
                    left = centerX - arcRadius,
                    top = centerY - arcRadius,
                    right = centerX + arcRadius,
                    bottom = centerY + arcRadius
                )
                val finalSweep = if (isCompleted) 360f else sweepAngle

                drawArc(
                    color = shadow,
                    startAngle = -90f,
                    sweepAngle = finalSweep,
                    useCenter = false,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = arcStroke + 2.5f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = finalSweep,
                    useCenter = false,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = arcStroke, cap = StrokeCap.Round)
                )
                drawOrbitDot(
                    center = center,
                    radius = arcRadius,
                    angleDegrees = -90f + finalSweep,
                    color = accentColor
                )
            }
            val guideStrength = (0.35f + directionConfidence * 0.65f).coerceIn(0.35f, 1f)
            drawViewChangeSubjectAnchor(
                centerX = centerX,
                centerY = centerY,
                arcRadius = arcRadius + 24f,
                direction = step?.direction.orEmpty(),
                confidence = directionConfidence,
                color = accentColor,
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

private fun DrawScope.drawReticle(
    center: Offset,
    accentColor: Color,
    neutralColor: Color,
    faintColor: Color,
    shadowColor: Color,
    pulseScale: Float,
    orbitPhase: Float
) {
    val innerDotRadius = 3.5f
    val ringRadius = 14f
    val ringStroke = 2.2f
    val armStart = 21f
    val armEnd = 35f
    val armStroke = 3.2f
    val orbitalRadius = 23f * pulseScale
    val guideDash = PathEffect.dashPathEffect(floatArrayOf(18f, 20f), orbitPhase / 9f)

    drawCircle(
        color = shadowColor,
        radius = ringRadius,
        center = center,
        style = Stroke(width = ringStroke + 2.4f)
    )
    drawCircle(
        color = neutralColor,
        radius = ringRadius,
        center = center,
        style = Stroke(width = ringStroke)
    )
    drawCircle(
        color = faintColor,
        radius = orbitalRadius,
        center = center,
        style = Stroke(width = 1.2f, pathEffect = guideDash)
    )
    drawCircle(
        color = accentColor,
        radius = innerDotRadius,
        center = center
    )

    val segments = listOf(
        Offset(1f, 0f),
        Offset(-1f, 0f),
        Offset(0f, 1f),
        Offset(0f, -1f)
    )

    segments.forEach { direction ->
        val start = Offset(center.x + direction.x * armStart, center.y + direction.y * armStart)
        val end = Offset(center.x + direction.x * armEnd, center.y + direction.y * armEnd)
        drawLine(
            color = shadowColor,
            start = start,
            end = end,
            strokeWidth = armStroke + 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = neutralColor,
            start = start,
            end = end,
            strokeWidth = armStroke,
            cap = StrokeCap.Round
        )
    }

    drawOrbitDot(
        center = center,
        radius = orbitalRadius,
        angleDegrees = orbitPhase,
        color = accentColor
    )
}

private fun DrawScope.drawTargetRing(
    center: Offset,
    radius: Float,
    accentColor: Color,
    neutralColor: Color,
    shadowColor: Color,
    pulseAlpha: Float
) {
    drawCircle(
        color = accentColor.copy(alpha = 0.14f * pulseAlpha),
        radius = radius + 9f,
        center = center
    )
    drawCircle(
        color = shadowColor,
        radius = radius,
        center = center,
        style = Stroke(width = 6f)
    )
    drawCircle(
        color = accentColor,
        radius = radius,
        center = center,
        style = Stroke(width = 2.8f)
    )
    drawCircle(
        color = neutralColor.copy(alpha = 0.34f),
        radius = radius - 9f,
        center = center,
        style = Stroke(width = 1.4f)
    )

    val cornerLength = 10f
    val offset = radius + 8f
    val stroke = 2.4f
    val corners = listOf(
        Pair(-1f, -1f),
        Pair(1f, -1f),
        Pair(-1f, 1f),
        Pair(1f, 1f)
    )
    corners.forEach { (sx, sy) ->
        val px = center.x + sx * offset
        val py = center.y + sy * offset
        val horizontalStart = Offset(px, py)
        val horizontalEnd = Offset(px - sx * cornerLength, py)
        val verticalEnd = Offset(px, py - sy * cornerLength)
        drawLine(shadowColor, horizontalStart, horizontalEnd, strokeWidth = stroke + 1.8f, cap = StrokeCap.Round)
        drawLine(shadowColor, horizontalStart, verticalEnd, strokeWidth = stroke + 1.8f, cap = StrokeCap.Round)
        drawLine(accentColor, horizontalStart, horizontalEnd, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(accentColor, horizontalStart, verticalEnd, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawViewChangeDirectionHint(
    centerX: Float,
    centerY: Float,
    confidence: Float,
    hasTrackedSubject: Boolean,
    color: Color,
    shadow: Color
) {
    val inactiveColor = Color.White.copy(alpha = 0.2f)
    val activeAlpha = 0.42f + confidence * 0.5f
    val chevronSize = 17f
    val stroke = 3.6f

    fun drawChevron(cx: Float, cy: Float, dx: Float, dy: Float, active: Boolean) {
        val tint = if (active) color.copy(alpha = activeAlpha.coerceIn(0f, 1f)) else inactiveColor
        val path = Path().apply {
            moveTo(
                cx - dx * chevronSize - dy * chevronSize * 0.55f,
                cy - dy * chevronSize + dx * chevronSize * 0.55f
            )
            lineTo(cx, cy)
            lineTo(
                cx - dx * chevronSize + dy * chevronSize * 0.55f,
                cy - dy * chevronSize - dx * chevronSize * 0.55f
            )
        }
        drawPath(path = path, color = shadow, style = Stroke(width = stroke + 2f, cap = StrokeCap.Round))
        drawPath(path = path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
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

private fun DrawScope.drawOrbitDot(
    center: Offset,
    radius: Float,
    angleDegrees: Float,
    color: Color
) {
    val angle = Math.toRadians(angleDegrees.toDouble())
    val orbitCenter = Offset(
        x = center.x + cos(angle).toFloat() * radius,
        y = center.y + sin(angle).toFloat() * radius
    )
    drawCircle(
        color = color.copy(alpha = 0.24f),
        radius = 6f,
        center = orbitCenter
    )
    drawCircle(
        color = color,
        radius = 2.6f,
        center = orbitCenter
    )
}

private fun Offset.distanceTo(other: Offset): Float {
    return sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y))
}
