package com.photoframer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframer.data.api.AnalysisHeader
import com.photoframer.data.api.CompositionResult
import com.photoframer.ui.theme.PurplePrimary
import com.photoframer.ui.theme.TextPrimary
import com.photoframer.ui.theme.TextSecondary
import android.graphics.Bitmap

/**
 * 底部控制面板
 * 包含：Mode 切换, 快门, 候选列表, 确认/取消按钮
 */
@Composable
fun BottomControlPanel(
    mode: ControlMode,
    onCaptureClick: () -> Unit,
    onCancelClick: () -> Unit = {},
    onConfirmClick: () -> Unit = {}, // 实际上可能是下一步
    
    // Candidates Mode params
    analysisHeader: AnalysisHeader? = null,
    compositions: List<CompositionResult> = emptyList(),
    onCompositionSelected: (CompositionResult) -> Unit = {},
    getCompositionBitmap: (Int) -> Bitmap? = { null },
    onSaveComposition: (Int) -> Unit = {},
    
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        when (mode) {
            ControlMode.PREVIEW -> {
                // 标准预览模式：快门 + 模式切换
                // Spacer(modifier = Modifier.weight(1f)) // REMOVE: This causes infinite expansion issues
                
                ShutterButton(onClick = onCaptureClick)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                ModeSelector()
            }
            
            ControlMode.ANALYZING -> {
                // 分析中：显示 Loading (实际上 LoadingOverlay 覆盖全屏，这里可以留空或显示取消)
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("Analyzing...", color = Color.White)
                }
            }
            
            ControlMode.CANDIDATES -> {
                // 候选方案模式：显示标题 + 列表 + Rescan按钮
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 标题
                   if (analysisHeader != null) {
                        Text(
                            text = analysisHeader.analysis,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 2,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // 列表
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.height(140.dp)
                    ) {
                        items(compositions) { composition ->
                             CompositionCard(
                                composition = composition,
                                bitmap = getCompositionBitmap(composition.id),
                                onClick = { onCompositionSelected(composition) },
                                onSaveClick = { onSaveComposition(composition.id) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Rescan Button
                    Button(
                        onClick = onCancelClick, // Assuming cancel means back/rescan
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rescan")
                    }
                }
            }
            
            ControlMode.GUIDING -> {
                // 引导模式：左X，中快门，右确认(或无)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel / Back
                    IconButton(
                        onClick = onCancelClick,
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.DarkGray, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                    
                    // Shutter
                    ShutterButton(onClick = onCaptureClick)
                    
                    // Placeholder for balance or Next action
                    Box(modifier = Modifier.size(56.dp))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                ModeSelector() // Optional: keep modes visible? Usually hidden in guide mode.
            }
        }
    }
}

enum class ControlMode {
    PREVIEW, ANALYZING, CANDIDATES, GUIDING
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ModeSelector() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Portrait", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .background(Color.DarkGray, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Photo", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text("Night Sight", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
