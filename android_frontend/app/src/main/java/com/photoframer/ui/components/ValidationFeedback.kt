package com.photoframer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.*
import com.photoframer.vision.StepValidationResult

/**
 * 验证反馈组件（优化版）
 * 显示当前步骤的验证状态和进度
 */
@Composable
fun ValidationFeedback(
    result: StepValidationResult,
    stepCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 进度动画
    val animatedProgress by animateFloatAsState(
        targetValue = result.progress,
        animationSpec = tween(300),
        label = "progress"
    )
    
    // 完成时的缩放动画
    val scale by animateFloatAsState(
        targetValue = if (stepCompleted) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it }
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .scale(scale),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    stepCompleted || result.isCompleted -> SuccessGreen.copy(alpha = 0.15f)
                    result.progress > 0.7f -> WarningYellow.copy(alpha = 0.15f)
                    else -> SurfaceDark.copy(alpha = 0.95f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 完成图标或进度
                AnimatedContent(
                    targetState = stepCompleted || result.isCompleted,
                    transitionSpec = {
                        (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
                    },
                    label = "icon"
                ) { completed ->
                    if (completed) {
                        // 完成图标
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(SuccessGreen, SuccessGreen.copy(alpha = 0.7f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已完成",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        // 进度指示
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 圆形进度
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = when {
                                        result.progress > 0.7f -> WarningYellow
                                        result.progress > 0.3f -> BlueAccent
                                        else -> PurplePrimary
                                    },
                                    strokeWidth = 4.dp,
                                    trackColor = TextSecondary.copy(alpha = 0.2f)
                                )
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 反馈文本
                Text(
                    text = if (stepCompleted) "太棒了！" else result.feedbackText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        stepCompleted || result.isCompleted -> SuccessGreen
                        result.progress > 0.7f -> WarningYellow
                        else -> TextPrimary
                    },
                    textAlign = TextAlign.Center
                )
                
                // 匹配质量指示（当匹配质量较低时显示）
                if (result.matchQuality < 0.5f && !result.isCompleted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "正在识别场景...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 所有步骤完成的提示 — 精致半透明风格
 */
@Composable
fun AllStepsCompletedBanner(
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        onClick = onTakePhoto
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 绿色渐变对勾图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SuccessGreen, SuccessGreen.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "完成",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "构图就绪",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen
                )
                Text(
                    text = "按下快门拍摄",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
