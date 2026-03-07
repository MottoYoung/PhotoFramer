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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(Color.Black.copy(alpha = 0.6f))
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
                        tint = Color.Yellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${getActionText(step.actionType)} ${getDirectionText(step.direction)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 优先显示实时反馈，如果没有则显示静态指南
                val feedbackText = if (!validationResult?.feedbackText.isNullOrEmpty()) validationResult.feedbackText else step.guideText
                val feedbackColor = if (validationResult?.isCompleted == true) SuccessGreen else Color.White.copy(alpha = 0.8f)

                Text(
                    text = feedbackText,
                    fontSize = 12.sp,
                    color = feedbackColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
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
                        .height(2.dp)
                        .background(
                            if (index == currentStepIndex) Color.Yellow
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
        "view-change" -> Icons.Default.Rotate90DegreesCw
        else -> Icons.Default.Info
    }
}

private fun getActionText(actionType: String): String {
    return when (actionType.lowercase()) {
        "shift" -> "移动"
        "zoom" -> "变焦"
        "view-change" -> "视角"
        else -> actionType
    }
}

private fun getDirectionText(direction: String): String {
    // 简单的英文转中文映射，实际项目中可以用资源文件
    return when (direction.lowercase()) {
        "left" -> "向左"
        "right" -> "向右"
        "up" -> "向上"
        "down" -> "向下"
        "forward" -> "向前"
        "backward" -> "向后"
        "in" -> "放大"
        "out" -> "缩小"
        else -> ""
    }
}
