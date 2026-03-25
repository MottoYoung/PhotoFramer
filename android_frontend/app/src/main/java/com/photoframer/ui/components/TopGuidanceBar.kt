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
                    Icon(
                        imageVector = getActionIcon(step.actionType, step.direction),
                        contentDescription = step.actionType,
                        tint = PurplePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getPrimaryInstruction(step),
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

                if (step.actionType.equals("view-change", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = PurplePrimary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = getViewChangeHelperText(step.direction),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { validationResult?.progress ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(5.dp),
                        color = if (validationResult?.isCompleted == true) SuccessGreen else PurplePrimary,
                        trackColor = Color.White.copy(alpha = 0.14f)
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
    return when (actionType.lowercase()) {
        "shift" -> when (direction.lowercase()) {
            "left" -> Icons.AutoMirrored.Filled.ArrowBack
            "right" -> Icons.AutoMirrored.Filled.ArrowForward
            "up" -> Icons.Default.ArrowUpward
            "down" -> Icons.Default.ArrowDownward
            else -> Icons.Default.OpenWith
        }
        "zoom" -> when (direction.lowercase()) {
            "in" -> Icons.Default.ZoomIn
            "out" -> Icons.Default.ZoomOut
            else -> Icons.Default.Search
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

private fun getViewChangeHelperText(direction: String): String {
    return when (direction.lowercase()) {
        "high-angle" -> "抬高手机，从更高的角度看向主体"
        "low-angle" -> "压低手机，从更低的角度看向主体"
        "side-view-left" -> "围绕主体向左侧移动，切到侧视角"
        "side-view-right" -> "围绕主体向右侧移动，切到侧视角"
        else -> "围绕主体移动，逐步改变拍摄视角"
    }
}

private fun getPrimaryInstruction(step: CompositionStep): String {
    val actionType = step.actionType.lowercase()
    val direction = step.direction.lowercase()

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
        "zoom" -> when (direction) {
            "in" -> "放大一点"
            "out" -> "缩小一点"
            else -> "调整远近"
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
    return when (step.actionType.lowercase()) {
        "shift" -> when (direction) {
            "left" -> "把手机往左挪一点"
            "right" -> "把手机往右挪一点"
            "up" -> "把手机稍微抬高一点"
            "down" -> "把手机稍微压低一点"
            "rotate-cw", "cw" -> "手机稍微向右转，把画面放平"
            "rotate-ccw", "ccw" -> "手机稍微向左转，把画面放平"
            else -> "继续微调画面位置"
        }
        "zoom" -> when (direction) {
            "in" -> "放大一点，让主体更靠近"
            "out" -> "缩小一点，留出更多画面"
            else -> "微调远近，贴近参考图"
        }
        "view-change" -> getViewChangeHelperText(step.direction)
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
