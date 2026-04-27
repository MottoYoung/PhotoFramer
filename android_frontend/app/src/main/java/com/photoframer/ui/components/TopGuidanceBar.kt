package com.photoframer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photoframer.data.api.CompositionStep
import com.photoframer.ui.theme.BlueAccent
import com.photoframer.ui.theme.PurplePrimary
import com.photoframer.ui.theme.SuccessGreen

/**
 * 顶部引导栏组件
 * 显示简短指令和步骤导航
 */
@Composable
fun TopGuidanceBar(
    step: CompositionStep,
    currentStepIndex: Int,
    totalSteps: Int,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    isFirstStep: Boolean,
    isLastStep: Boolean,
    validationResult: com.photoframer.vision.StepValidationResult? = null,
    modifier: Modifier = Modifier
) {
    val effectiveDirection = resolveEffectiveDirection(
        step = step,
        validationResult = validationResult
    )
    val feedbackText = if (!validationResult?.feedbackText.isNullOrEmpty()) {
        validationResult.feedbackText
    } else {
        getSecondaryInstruction(step)
    }
    val highlightColor = if (validationResult?.isCompleted == true) SuccessGreen else BlueAccent
    val progress = ((currentStepIndex + 1).toFloat() / totalSteps.coerceAtLeast(1)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD8182130),
                            Color(0xC6101722),
                            Color(0xB80B111A)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.White.copy(alpha = 0.015f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GuidanceNavButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "上一步",
                        enabled = !isFirstStep,
                        onClick = onPreviousStep
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = getActionPillLabel(step.actionType),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = highlightColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "步骤 ${currentStepIndex + 1} / $totalSteps",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.68f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .border(1.dp, highlightColor.copy(alpha = 0.24f), CircleShape)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                highlightColor.copy(alpha = 0.18f),
                                                highlightColor.copy(alpha = 0.035f)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getActionIcon(step.actionType, effectiveDirection),
                                        contentDescription = step.actionType,
                                        tint = highlightColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = getPrimaryInstruction(step, effectiveDirection),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = getDirectionSubLabel(effectiveDirection),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.52f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = feedbackText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (validationResult?.isCompleted == true) SuccessGreen else Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )

                        if (step.actionType.equals("view-change", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = getViewChangeHelperText(step.direction),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.84f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    GuidanceNavButton(
                        icon = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "下一步",
                        enabled = true,
                        tint = if (isLastStep) SuccessGreen else Color.White,
                        onClick = onNextStep
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { subjectConfidence },
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(5.dp),
                        color = subjectColor,
                        trackColor = Color.White.copy(alpha = 0.10f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(totalSteps) { index ->
                    val segmentColor = when {
                        index == currentStepIndex -> highlightColor.copy(alpha = 0.92f)
                        index < currentStepIndex -> SuccessGreen.copy(alpha = 0.66f)
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(if (index == currentStepIndex) 1.2f else 1f)
                            .height(if (index == currentStepIndex) 4.dp else 3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(segmentColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidanceNavButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Surface(
        color = if (enabled) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.025f),
        shape = CircleShape
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else Color.White.copy(alpha = 0.28f)
            )
        }
    }
}

private fun getActionPillLabel(actionType: String): String {
    return when (actionType.lowercase()) {
        "shift" -> "取景移动"
        "zoom" -> "焦距校准"
        "view-change" -> "视角切换"
        else -> "构图引导"
    }
}

private fun getDirectionSubLabel(direction: String): String {
    return when (direction.lowercase()) {
        "left" -> "向画面左侧微调"
        "right" -> "向画面右侧微调"
        "up" -> "向更高机位调整"
        "down" -> "向更低机位调整"
        "in" -> "主体再靠近一点"
        "out" -> "给画面更多留白"
        "high-angle" -> "提升俯视感"
        "low-angle" -> "增强仰视感"
        "side-view-left" -> "切向左侧视角"
        "side-view-right" -> "切向右侧视角"
        else -> "跟随参考图继续微调"
    }
}

private fun getActionIcon(actionType: String, direction: String): ImageVector {
    return when (actionType.normalizedActionType()) {
        "shift" -> when (direction.lowercase()) {
            "left" -> Icons.AutoMirrored.Filled.ArrowBack
            "right" -> Icons.AutoMirrored.Filled.ArrowForward
            "up" -> Icons.Default.ArrowUpward
            "down" -> Icons.Default.ArrowDownward
            else -> Icons.Default.OpenWith
        }
        "level" -> Icons.Default.CropRotate
        "zoom" -> when (direction.lowercase()) {
            "in" -> Icons.Default.ZoomIn
            "out" -> Icons.Default.ZoomOut
            else -> Icons.Default.Search
        }
        "orbit" -> when (direction.lowercase()) {
            "left" -> Icons.AutoMirrored.Filled.ArrowBack
            "right" -> Icons.AutoMirrored.Filled.ArrowForward
            else -> Icons.Default.Cached
        }
        "raisecamera" -> Icons.Default.ArrowUpward
        "lowercamera" -> Icons.Default.ArrowDownward
        "step" -> when (direction.lowercase()) {
            "backward" -> Icons.Default.ZoomOutMap
            else -> Icons.Default.ZoomInMap
        }
        "view-change" -> when (direction.lowercase()) {
            "high-angle" -> Icons.Default.ArrowUpward
            "low-angle" -> Icons.Default.ArrowDownward
            "side-view-left" -> Icons.AutoMirrored.Filled.ArrowBack
            "side-view-right" -> Icons.AutoMirrored.Filled.ArrowForward
            else -> Icons.Default.Rotate90DegreesCw
        }
        else -> Icons.Default.Info
    }
}

private fun resolveEffectiveDirection(
    step: CompositionStep,
    validationResult: com.photoframer.vision.StepValidationResult?
): String {
    val originalDirection = step.direction
    if (!step.actionType.equals("zoom", ignoreCase = true)) {
        return originalDirection
    }

    val feedback = validationResult?.feedbackText?.lowercase().orEmpty()
    return when {
        "缩小" in feedback || "缩回" in feedback -> "out"
        "放大" in feedback -> "in"
        else -> originalDirection
    }
}

private fun getViewChangeHelperText(actionType: String, direction: String): String {
    return when (actionType.normalizedActionType()) {
        "orbit" -> if (direction.equals("right", ignoreCase = true)) {
            "让手机沿着主体右侧绕过去，切到新的侧面"
        } else {
            "让手机沿着主体左侧绕过去，切到新的侧面"
        }
        "raisecamera" -> "整台手机一起抬高，不要只掰手腕"
        "lowercamera" -> "整台手机一起放低，不要只压手腕"
        "step" -> if (direction.equals("backward", ignoreCase = true)) {
            "连人带手机一起后退，给主体留出环境"
        } else {
            "连人带手机一起靠近，让主体更有存在感"
        }
        else -> when (direction.lowercase()) {
            "high-angle" -> "抬高手机，从更高的位置看向主体"
            "low-angle" -> "压低手机，从更低的位置看向主体"
            "side-view-left" -> "沿着主体左侧绕过去，切到侧视角"
            "side-view-right" -> "沿着主体右侧绕过去，切到侧视角"
            else -> "按中间轨迹移动手机，逐步换到新视角"
        }
    }
}

private fun getPrimaryInstruction(step: CompositionStep, effectiveDirection: String = step.direction): String {
    val actionType = step.actionType.normalizedActionType()
    val direction = effectiveDirection.lowercase()

    return when (actionType) {
        "shift" -> when (direction) {
            "left" -> "向左移动"
            "right" -> "向右移动"
            "up" -> "向上移动"
            "down" -> "向下移动"
            "rotate-cw", "cw" -> "向右转正"
            "rotate-ccw", "ccw" -> "向左转正"
            else -> "调整位置"
        }
        "level" -> when (direction) {
            "cw", "rotate-cw" -> "向右转正"
            "ccw", "rotate-ccw" -> "向左转正"
            else -> "放平画面"
        }
        "zoom" -> when (direction) {
            "in" -> "放大一点"
            "out" -> "缩小一点"
            else -> "调整远近"
        }
        "orbit" -> when (direction) {
            "left" -> "向左绕拍"
            "right" -> "向右绕拍"
            else -> "环绕主体"
        }
        "raisecamera" -> "抬高机位"
        "lowercamera" -> "压低机位"
        "step" -> when (direction) {
            "backward" -> "后退一点"
            else -> "靠近一点"
        }
        "view-change" -> when (direction) {
            "high-angle" -> "抬高机位"
            "low-angle" -> "压低机位"
            "side-view-left" -> "向左绕拍"
            "side-view-right" -> "向右绕拍"
            else -> "改变视角"
        }
        else -> "继续调整"
    }
}

private fun getSecondaryInstruction(step: CompositionStep): String {
    val normalizedGuide = normalizeGuideText(step.guideText)
    if (normalizedGuide.isNotBlank()) {
        return normalizedGuide
    }

    val direction = step.direction.lowercase()
    return when (step.actionType.normalizedActionType()) {
        "shift" -> when (direction) {
            "left" -> "把手机往左挪一点"
            "right" -> "把手机往右挪一点"
            "up" -> "把手机稍微抬高一点"
            "down" -> "把手机稍微压低一点"
            "rotate-cw", "cw" -> "手机稍微向右转，把画面放平"
            "rotate-ccw", "ccw" -> "手机稍微向左转，把画面放平"
            else -> "继续微调画面位置"
        }
        "level" -> when (direction) {
            "rotate-cw", "cw" -> "手机稍微向右转，把画面放平"
            "rotate-ccw", "ccw" -> "手机稍微向左转，把画面放平"
            else -> "微调手机角度，把画面放平"
        }
        "zoom" -> when (direction) {
            "in" -> "放大一点，让主体更靠近"
            "out" -> "缩小一点，留出更多画面"
            else -> "微调远近，贴近参考图"
        }
        else -> if (step.actionType.isViewpointActionType()) {
            getViewChangeHelperText(step.actionType, step.direction)
        } else {
            "继续按参考图调整"
        }
    }
}

private fun normalizeGuideText(text: String): String {
    return text
        .replace("Rotate-CCW", "向左转")
        .replace("Rotate-CW", "向右转")
        .replace("rotate-ccw", "向左转")
        .replace("rotate-cw", "向右转")
        .replace("CCW", "向左转")
        .replace("CW", "向右转")
        .replace("View-change", "改变视角")
        .replace("view-change", "改变视角")
        .replace("RaiseCamera", "抬高机位")
        .replace("LowerCamera", "压低机位")
        .replace("Orbit", "绕拍")
        .replace("Step", "前后移动")
        .replace("Level", "放平画面")
        .replace("Shift", "移动")
        .replace("shift", "移动")
        .replace("Zoom", "变焦")
        .replace("zoom", "变焦")
        .trim()
}
