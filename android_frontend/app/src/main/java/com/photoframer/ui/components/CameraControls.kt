package com.photoframer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photoframer.data.api.CompositionResult
import com.photoframer.ui.theme.*

/**
 * 引导模式底部控制栏
 * 
 * 布局: [返回按钮] [快门按钮] [占位]
 */
@Composable
fun GuidingBottomBar(
    onCaptureClick: () -> Unit,
    onBackClick: () -> Unit,
    isCaptureEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(start = 48.dp, top = 12.dp, end = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：返回按钮
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ControlBackground)
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            
            // 中间：快门按钮
            GuidingShutterButton(
                enabled = isCaptureEnabled,
                onClick = onCaptureClick
            )
            
            // 右侧：占位
            Spacer(modifier = Modifier.size(52.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isCaptureEnabled) "构图已完成，可直接拍摄" else "按引导完成当前步骤后可拍摄",
            color = if (isCaptureEnabled) SuccessGreen else Color.White.copy(alpha = 0.64f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 引导模式快门按钮 - 带脉冲动画
 */
@Composable
private fun GuidingShutterButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(
                4.dp,
                if (enabled) Color.White else Color.White.copy(alpha = 0.28f),
                CircleShape
            )
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.22f))
        )
    }
}

/**
 * 候选方案底部面板
 */
@Composable
fun CandidatesBottomPanel(
    totalTechniques: Int,
    completedCount: Int,
    applicableCount: Int,
    totalTimeMs: Float,
    compositions: List<CompositionResult>,
    postCaptureHint: String?,
    onStartGuidance: (CompositionResult) -> Unit,
    getCompositionBitmap: (String) -> Bitmap?,
    onSaveComposition: (String) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTechnique by remember {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(compositions, selectedTechnique) {
        if (selectedTechnique == null || compositions.none { it.technique == selectedTechnique }) {
            selectedTechnique = compositions.firstOrNull()?.technique
        }
    }
    val selectedComposition = compositions.firstOrNull { it.technique == selectedTechnique }
        ?: compositions.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 统计信息
        if (totalTechniques > 0 || applicableCount > 0) {
            Text(
                text = "选择一个参考构图",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Text(
                text = buildCandidatesStatsText(
                    totalTechniques = totalTechniques,
                    completedCount = completedCount,
                    applicableCount = applicableCount,
                    totalTimeMs = totalTimeMs
                ),
                color = Color.White.copy(alpha = 0.46f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        if (!postCaptureHint.isNullOrBlank()) {
            Text(
                text = postCaptureHint,
                color = SuccessGreen,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            )
        }

        // 候选列表
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.height(190.dp)
        ) {
            items(compositions) { composition ->
                CompositionCard(
                    composition = composition,
                    bitmap = getCompositionBitmap(composition.technique),
                    isSelected = composition.technique == selectedComposition?.technique,
                    onClick = { selectedTechnique = composition.technique },
                    onSaveClick = { onSaveComposition(composition.technique) }
                )
            }
        }

        if (selectedComposition != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 14.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = selectedComposition.techniqueName,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = buildSelectionSummary(selectedComposition),
                        color = Color.White.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { onStartGuidance(selectedComposition) },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text(
                        text = "开始引导",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // 重新扫描按钮
        Button(
            onClick = onRescan,
            colors = ButtonDefaults.buttonColors(containerColor = ControlBackground),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.height(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh, 
                contentDescription = null, 
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "重新扫描",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun buildSelectionSummary(composition: CompositionResult): String {
    return "预计 ${composition.steps.size} 步完成"
}

private fun buildCandidatesStatsText(
    totalTechniques: Int,
    completedCount: Int,
    applicableCount: Int,
    totalTimeMs: Float
): String {
    val progressText = if (totalTechniques > 0) {
        "共有 $totalTechniques 个候选方案，目前已完成 ${completedCount.coerceAtMost(totalTechniques)} 个"
    } else {
        "正在生成候选方案"
    }

    val usableText = "可用 $applicableCount 个"
    if (totalTimeMs <= 0f) {
        return "$progressText  ·  $usableText"
    }
    return "$progressText  ·  $usableText  ·  ${totalTimeMs.toInt()}ms"
}
