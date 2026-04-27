package com.photoframer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photoframer.data.api.CompositionStep
import com.photoframer.guidance.isViewpointActionType
import com.photoframer.guidance.normalizedActionType
import com.photoframer.ui.theme.*

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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.75f))
            .statusBarsPadding()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 主指令区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 上一步 (小图标)
            IconButton(
                onClick = onPreviousStep,
                enabled = !isFirstStep,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "上一步",
                    tint = if (!isFirstStep) Color.White else Color.Gray
                )
            }

            // 中间指令内容
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val effectiveDirection = resolveEffectiveDirection(
                        step = step,
                        validationResult = validationResult
                    )
                    Icon(
                        imageVector = getActionIcon(step.actionType, effectiveDirection),
                        contentDescription = step.actionType,
                        tint = PurplePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getPrimaryInstruction(step, effectiveDirection),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 优先显示实时反馈，如果没有则显示静态指南
                val feedbackText = if (!validationResult?.feedbackText.isNullOrEmpty()) {
                    validationResult.feedbackText
                } else {
                    getSecondaryInstruction(step)
                }
                val feedbackColor = if (validationResult?.isCompleted == true) SuccessGreen else Color.White.copy(alpha = 0.8f)

                Text(
                    text = feedbackText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = feedbackColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                if (step.actionType.isViewpointActionType()) {
                    val subjectConfidence = (validationResult?.subjectConfidence ?: 0f).coerceIn(0f, 1f)
                    val hasSubject = validationResult?.hasSubject != false
                    val subjectColor = when {
                        validationResult?.isCompleted == true -> SuccessGreen
                        !hasSubject -> Color(0xFFFFB454)
                        subjectConfidence >= 0.72f -> SuccessGreen
                        subjectConfidence >= 0.42f -> Color(0xFFFFD166)
                        else -> Color(0xFFFF8A65)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = PurplePrimary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = getViewChangeHelperText(step.actionType, step.direction),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.72f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "机位接近",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                        Text(
                            text = if (hasSubject) "主体锁定" else "主体待找回",
                            style = MaterialTheme.typography.labelSmall,
                            color = subjectColor.copy(alpha = 0.92f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { validationResult?.progress ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(5.dp),
                        color = if (validationResult?.isCompleted == true) SuccessGreen else PurplePrimary,
                        trackColor = Color.White.copy(alpha = 0.14f)
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

            // 下一步 (小图标)
            IconButton(
                onClick = onNextStep,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "下一步",
                    tint = if (isLastStep) SuccessGreen else Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 步骤进度条
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .weight(1f)
                        .height(3.dp)
                        .background(
                            if (index == currentStepIndex) PurplePrimary
                            else if (index < currentStepIndex) SuccessGreen
                            else Color.Gray.copy(alpha = 0.5f),
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
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
