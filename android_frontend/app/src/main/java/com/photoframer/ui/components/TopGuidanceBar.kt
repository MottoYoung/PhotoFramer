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
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
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
import com.photoframer.guidance.isViewpointActionType
import com.photoframer.guidance.normalizedActionType
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

                        if (step.actionType.isViewpointActionType()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = getViewChangeHelperText(step.actionType, step.direction),
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
    return when (actionType.normalizedActionType()) {
        "shift" -> "取景移动"
        "level" -> "水平校正"
        "zoom" -> "焦距校准"
        "orbit", "raisecamera", "lowercamera", "step", "view-change" -> "视角切换"
        else -> "构图引导"
    }
}

private fun getDirectionSubLabel(direction: String): String {
    return when (direction.lowercase()) {
        "left" -> "向画面左侧微调"
        "right" -> "向画面右侧微调"
        "up" -> "向更高机位调整"
        "down" -> "向更低机位调整"
        "cw" -> "顺时针微微转正"
        "ccw" -> "逆时针微微转正"
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
        "level" -> Icons.Default.Rotate90DegreesCw
        "zoom" -> when (direction.lowercase()) {
            "in" -> Icons.Default.ZoomIn
            "out" -> Icons.Default.ZoomOut
            else -> Icons.Default.Search
        }
        "orbit" -> if (direction.equals("left", ignoreCase = true)) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward
        "raisecamera" -> Icons.Default.ArrowUpward
        "lowercamera" -> Icons.Default.ArrowDownward
        "step" -> if (direction.equals("backward", ignoreCase = true)) Icons.Default.ZoomOut else Icons.Default.ZoomIn
        "view-change" -> Icons.Default.Rotate90DegreesCw
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
        "raisecamera" -> "抬高手机，让机位更高一些"
        "lowercamera" -> "压低手机，让机位更低一些"
        "orbit" -> if (direction.equals("left", ignoreCase = true)) {
            "围绕主体向左绕拍，切到侧视角"
        } else {
            "围绕主体向右绕拍，切到侧视角"
        }
        "step" -> if (direction.equals("backward", ignoreCase = true)) {
            "带着手机后退一点，拉开与主体距离"
        } else {
            "带着手机向前一步，靠近主体"
        }
        else -> when (direction.lowercase()) {
            "high-angle" -> "抬高手机，从更高的角度看向主体"
            "low-angle" -> "压低手机，从更低的角度看向主体"
            "side-view-left" -> "围绕主体向左侧移动，切到侧视角"
            "side-view-right" -> "围绕主体向右侧移动，切到侧视角"
            else -> "围绕主体移动，逐步改变拍摄视角"
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
            else -> "调整位置"
        }
        "level" -> when (direction) {
            "rotate-cw", "cw" -> "向右转正"
            "rotate-ccw", "ccw" -> "向左转正"
            else -> "转正画面"
        }
        "zoom" -> when (direction) {
            "in" -> "放大一点"
            "out" -> "缩小一点"
            else -> "调整远近"
        }
        "orbit" -> if (direction == "left") "向左绕拍" else "向右绕拍"
        "raisecamera" -> "抬高机位"
        "lowercamera" -> "压低机位"
        "step" -> if (direction == "backward") "后退一步" else "向前一步"
        "view-change" -> "改变视角"
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
            else -> "继续微调画面位置"
        }
        "level" -> when (direction) {
            "rotate-cw", "cw" -> "手机稍微向右转，把画面放平"
            "rotate-ccw", "ccw" -> "手机稍微向左转，把画面放平"
            else -> "继续把画面转正"
        }
        "zoom" -> when (direction) {
            "in" -> "放大一点，让主体更靠近"
            "out" -> "缩小一点，留出更多画面"
            else -> "微调远近，贴近参考图"
        }
        "orbit", "raisecamera", "lowercamera", "step", "view-change" -> getViewChangeHelperText(step.actionType, step.direction)
        else -> "继续按参考图调整"
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
        .replace("Shift", "移动")
        .replace("shift", "移动")
        .replace("Zoom", "变焦")
        .replace("zoom", "变焦")
        .trim()
}
