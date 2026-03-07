package com.photoframer.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(vertical = 24.dp, horizontal = 48.dp)
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
            GuidingShutterButton(onClick = onCaptureClick)
            
            // 右侧：占位
            Spacer(modifier = Modifier.size(52.dp))
        }
    }
}

/**
 * 引导模式快门按钮 - 带脉冲动画
 */
@Composable
private fun GuidingShutterButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "press"
    )
    
    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(pressScale)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * 候选方案底部面板
 */
@Composable
fun CandidatesBottomPanel(
    applicableCount: Int,
    totalTimeMs: Float,
    compositions: List<CompositionResult>,
    onCompositionSelected: (CompositionResult) -> Unit,
    getCompositionBitmap: (String) -> Bitmap?,
    onSaveComposition: (String) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 统计信息
        if (applicableCount > 0) {
            Text(
                text = "AI 推荐了 $applicableCount 种构图",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // 候选列表
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.height(220.dp)
        ) {
            items(compositions) { composition ->
                CompositionCard(
                    composition = composition,
                    bitmap = getCompositionBitmap(composition.technique),
                    onClick = { onCompositionSelected(composition) },
                    onSaveClick = { onSaveComposition(composition.technique) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
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
