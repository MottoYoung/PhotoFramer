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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframer.data.api.CompositionStep
import com.photoframer.ui.theme.*

/**
 * 引导面板组件
 * 显示当前步骤和操作指令
 */
@Composable
fun GuidancePanel(
    step: CompositionStep,
    currentStepIndex: Int,
    totalSteps: Int,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    isFirstStep: Boolean,
    isLastStep: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(SurfaceDark.copy(alpha = 0.95f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 步骤指示器
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentStepIndex) 10.dp else 8.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (index == currentStepIndex) PurplePrimary
                            else if (index < currentStepIndex) SuccessGreen
                            else TextSecondary.copy(alpha = 0.3f)
                        )
                )
                if (index < totalSteps - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 步骤标题
        Text(
            text = "步骤 ${currentStepIndex + 1}/$totalSteps",
            fontSize = 12.sp,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 操作类型和方向
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = getActionIcon(step.actionType, step.direction),
                contentDescription = step.actionType,
                tint = PurplePrimary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = getActionText(step.actionType),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = step.direction,
                    fontSize = 14.sp,
                    color = BlueAccent
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 引导文本
        Text(
            text = step.guideText,
            fontSize = 14.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 步骤导航按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 上一步
            OutlinedButton(
                onClick = onPreviousStep,
                enabled = !isFirstStep,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PurpleSecondary,
                    disabledContentColor = TextSecondary.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "上一步",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("上一步")
            }
            
            // 下一步 / 完成
            Button(
                onClick = onNextStep,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLastStep) SuccessGreen else PurplePrimary
                )
            ) {
                Text(if (isLastStep) "拍照" else "下一步")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isLastStep) Icons.Default.CameraAlt else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = if (isLastStep) "拍照" else "下一步",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 根据操作类型和方向获取图标
 */
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
            else -> Icons.Default.Rotate90DegreesCw
        }
        else -> Icons.Default.TouchApp
    }
}

/**
 * 获取操作类型文本
 */
private fun getActionText(actionType: String): String {
    return when (actionType.lowercase()) {
        "shift" -> "移动相机"
        "zoom" -> "调整焦距"
        "view-change" -> "改变视角"
        else -> actionType
    }
}
