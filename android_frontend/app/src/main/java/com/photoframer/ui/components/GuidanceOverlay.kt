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
import androidx.compose.ui.graphics.drawscope.rotate
import com.photoframer.data.api.CompositionStep
import com.photoframer.guidance.isViewpointActionType
import com.photoframer.guidance.canonicalViewDirection
import com.photoframer.guidance.normalizedActionType
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
    val isShiftStep = step?.actionType.equals("shift", ignoreCase = true)
    val isZoomStep = step?.actionType.equals("zoom", ignoreCase = true)
    val isViewChangeStep = step?.actionType?.isViewpointActionType() == true
    val isLevelStep = step?.actionType.equals("level", ignoreCase = true)

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
            val deadZone = 25f
            val inDeadZone = isCompleted || rawDistance < deadZone
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

        if (isLevelStep) {
            drawLevelGuide(
                centerX = centerX,
                centerY = centerY,
                rotateRight = step?.direction.equals("cw", ignoreCase = true) ||
                    step?.direction.equals("rotate-cw", ignoreCase = true),
                rotationAngle = validationResult?.rotationAngle ?: 0f,
                progress = validationResult?.progress ?: 0f,
                pulseScale = pulseScale,
                isCompleted = isCompleted,
                color = accentColor,
                shadow = shadow
            )
        }

        if (isShiftStep) {
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
                val end = Offset(target.x - dx * (ringRadius + 10f), target.y - dy * (ringRadius + 10f))
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

        if (isViewChangeStep) {
            val progress = animViewChangeProgress.coerceIn(0f, 1f)
            val directionConfidence = animViewChangeDirectionConfidence.coerceIn(0f, 1f)
            val subjectConfidence = animViewChangeSubjectConfidence.coerceIn(0f, 1f)
            val actionType = step?.actionType.orEmpty()
            val subjectColor = when {
                isCompleted -> SuccessGreen
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
                direction = step?.direction.orEmpty(),
                progress = progress,
                guideStrength = guideStrength,
                pulseScale = pulseScale,
                isCompleted = isCompleted,
                color = accentColor,
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

private fun DrawScope.drawLevelGuide(
    centerX: Float,
    centerY: Float,
    rotateRight: Boolean,
    rotationAngle: Float,
    progress: Float,
    pulseScale: Float,
    isCompleted: Boolean,
    color: Color,
    shadow: Color
) {
    val guideRadius = 98f
    val activeColor = color.copy(alpha = 0.90f)
    val startAngle = if (rotateRight) -118f else 118f
    val sweep = if (rotateRight) 88f else -88f
    val activeSweep = if (isCompleted) sweep else sweep * (0.28f + progress.coerceIn(0f, 1f) * 0.72f)
    val trackRect = Rect(
        left = centerX - guideRadius,
        top = centerY - guideRadius - 8f,
        right = centerX + guideRadius,
        bottom = centerY + guideRadius - 8f
    )
    val currentTilt = if (isCompleted) 0f else rotationAngle.coerceIn(-18f, 18f)
    val targetPhoneCenter = Offset(centerX, centerY + 110f)
    val currentPhoneCenter = Offset(centerX, centerY + 34f)

    drawArc(
        color = Color.White.copy(alpha = 0.15f),
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = trackRect.topLeft,
        size = trackRect.size,
        style = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
    )
    drawArc(
        color = shadow,
        startAngle = startAngle,
        sweepAngle = activeSweep,
        useCenter = false,
        topLeft = trackRect.topLeft,
        size = trackRect.size,
        style = Stroke(width = 9f, cap = StrokeCap.Round)
    )
    drawArc(
        color = activeColor,
        startAngle = startAngle,
        sweepAngle = activeSweep,
        useCenter = false,
        topLeft = trackRect.topLeft,
        size = trackRect.size,
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )

    val arrowFractions = listOf(0.22f, 0.55f, 0.84f)
    arrowFractions.forEach { fraction ->
        val angle = startAngle + sweep * fraction
        val point = circlePoint(centerX, centerY - 8f, guideRadius, angle)
        val tangent = arcTangentDirection(angle, clockwise = rotateRight)
        drawArrowTip(
            tip = point,
            direction = tangent,
            size = 13f,
            color = activeColor,
            shadow = shadow
        )
    }

    drawGuidePhone(
        center = targetPhoneCenter,
        width = 44f,
        height = 72f,
        color = Color.White.copy(alpha = 0.30f),
        shadow = shadow,
        filled = false
    )
    drawGuidePhoneRotated(
        center = currentPhoneCenter,
        width = 50f,
        height = 82f,
        angleDegrees = currentTilt,
        color = if (isCompleted) SuccessGreen else activeColor,
        shadow = shadow
    )
    drawCircle(
        color = activeColor.copy(alpha = 0.10f),
        radius = 52f * if (isCompleted) pulseScale else 1f,
        center = currentPhoneCenter
    )

    val targetTip = circlePoint(centerX, centerY - 8f, guideRadius, startAngle + activeSweep)
    drawArrowTip(
        tip = targetTip,
        direction = arcTangentDirection(startAngle + activeSweep, clockwise = rotateRight),
        size = 16f * if (isCompleted) pulseScale else 1f,
        color = activeColor,
        shadow = shadow
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

    fun drawBracket(dx: Float, dy: Float) {
        val sx = centerX + dx * 14f
        val sy = centerY + dy * 14f
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
            moveRight = direction.canonicalViewDirection() == "right",
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
    val activeColor = color.copy(alpha = (0.58f + guideStrength * 0.3f).coerceIn(0.55f, 0.95f))
    val startY = if (moveUp) bottomY - 18f else topY + 18f
    val targetY = if (moveUp) topY + 24f else bottomY - 24f
    val markerY = if (isCompleted) targetY else lerpF(startY, targetY, progress)
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

    drawLine(Color.White.copy(alpha = 0.16f), Offset(centerX, topY), Offset(centerX, bottomY), strokeWidth = 5f, pathEffect = dash)
    drawLine(Color.White.copy(alpha = 0.12f), Offset(centerX - laneHalfWidth, topY), Offset(centerX - laneHalfWidth, bottomY), strokeWidth = 2f)
    drawLine(Color.White.copy(alpha = 0.12f), Offset(centerX + laneHalfWidth, topY), Offset(centerX + laneHalfWidth, bottomY), strokeWidth = 2f)

    drawLine(shadow, Offset(centerX, startY), Offset(centerX, markerY), strokeWidth = 8f, cap = StrokeCap.Round)
    drawLine(activeColor, Offset(centerX, startY), Offset(centerX, markerY), strokeWidth = 4.5f, cap = StrokeCap.Round)

    drawCircle(subjectColor.copy(alpha = 0.18f), 18f * if (isCompleted) pulseScale else 1f, Offset(centerX, targetY))
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
        color = if (isCompleted) SuccessGreen else activeColor,
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
    val rectSize = Size(radiusX * 2, radiusY * 2)
    val startAngle = if (moveRight) 180f else 0f
    val totalSweep = if (moveRight) 180f else -180f
    val activeSweep = if (isCompleted) totalSweep else totalSweep * progress
    val activeColor = color.copy(alpha = (0.58f + guideStrength * 0.28f).coerceIn(0.55f, 0.92f))
    val trackDash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

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
    val arrowFractions = listOf(0.18f, 0.46f, 0.74f)

    drawCircle(subjectColor.copy(alpha = 0.10f), 82f, Offset(centerX, centerY))
    drawGuidePhone(
        center = source,
        width = 36f,
        height = 58f,
        color = Color.White.copy(alpha = 0.28f),
        shadow = shadow,
        filled = false
    )
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
        color = if (isCompleted) SuccessGreen else activeColor,
        shadow = shadow
    )
    drawCircle(Color.White.copy(alpha = 0.10f), 8f, source)
    arrowFractions.forEach { fraction ->
        val angle = lerpF(sourceAngle, targetAngle, fraction)
        val point = ellipsePoint(centerX, centerY, radiusX, radiusY, angle)
        val tangent = ellipseTangentDirection(
            angleDegrees = angle,
            radiusX = radiusX,
            radiusY = radiusY,
            clockwise = moveRight
        )
        drawArrowTip(
            tip = point,
            direction = tangent,
            size = 13f * (0.9f + guideStrength * 0.25f),
            color = activeColor.copy(alpha = 0.88f),
            shadow = shadow
        )
    }

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
            size = Size(halfWidth * 2f, height),
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
        color = if (isCompleted) SuccessGreen else activeColor,
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
    val size = Size(width, height)

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

private fun DrawScope.drawGuidePhoneRotated(
    center: Offset,
    width: Float,
    height: Float,
    angleDegrees: Float,
    color: Color,
    shadow: Color
) {
    rotate(degrees = angleDegrees, pivot = center) {
        drawGuidePhone(
            center = center,
            width = width,
            height = height,
            color = color,
            shadow = shadow
        )
    }
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

private fun circlePoint(
    centerX: Float,
    centerY: Float,
    radius: Float,
    angleDegrees: Float
): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    return Offset(
        x = centerX + (kotlin.math.cos(radians) * radius).toFloat(),
        y = centerY + (kotlin.math.sin(radians) * radius).toFloat()
    )
}

private fun arcTangentDirection(angleDegrees: Float, clockwise: Boolean): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val rawDx = (-kotlin.math.sin(radians)).toFloat()
    val rawDy = (kotlin.math.cos(radians)).toFloat()
    val dx = if (clockwise) rawDx else -rawDx
    val dy = if (clockwise) rawDy else -rawDy
    val magnitude = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
    return Offset(dx / magnitude, dy / magnitude)
}

private fun ellipseTangentDirection(
    angleDegrees: Float,
    radiusX: Float,
    radiusY: Float,
    clockwise: Boolean
): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val rawDx = (-kotlin.math.sin(radians) * radiusX).toFloat()
    val rawDy = (kotlin.math.cos(radians) * radiusY).toFloat()
    val dx = if (clockwise) rawDx else -rawDx
    val dy = if (clockwise) rawDy else -rawDy
    val magnitude = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
    return Offset(dx / magnitude, dy / magnitude)
}

private fun lerpF(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

private fun Offset.distanceTo(other: Offset): Float {
    return sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y))
}
