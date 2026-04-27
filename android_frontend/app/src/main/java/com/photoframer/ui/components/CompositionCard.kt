package com.photoframer.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photoframer.data.api.CompositionResult
import com.photoframer.ui.theme.*

/**
 * 候选构图卡片
 *
 * 以图片为主，信息克制，尽量接近系统相册里的照片选择卡体验。
 */
@Composable
fun CompositionCard(
    composition: CompositionResult,
    bitmap: Bitmap?,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.012f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_scale"
    )

    Card(
        modifier = modifier
            .width(184.dp)
            .scale(scale)
            .then(
                if (isSelected) Modifier.border(
                    width = 1.25.dp,
                    color = PurplePrimary,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 10.dp else 3.dp
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(SurfaceDark),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "构图方案 ${composition.techniqueName}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = composition.techniqueName,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.68f)
                                )
                            )
                        )
                )

                SelectionChip(
                        text = when {
                            isSelected -> "已选中"
                            composition.isRecommended -> "推荐"
                            else -> "${composition.steps.size} 步"
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    isSelected = isSelected
                )

                if (bitmap != null) {
                    IconButton(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "保存",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = composition.techniqueName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = compactAestheticDesc(composition.aestheticDesc),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetaPill(text = "${composition.steps.size} 步")
                MetaPill(text = getStepSummary(composition))
            }
        }
    }
}

@Composable
private fun SelectionChip(
    text: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (isSelected) PurplePrimary.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.42f)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }

        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun getStepSummary(composition: CompositionResult): String {
    val firstAction = composition.steps.firstOrNull()?.actionType?.lowercase() ?: return "参考构图"
    return when (firstAction) {
        "shift" -> "先移动机位"
        "zoom" -> "先调整远近"
        "level" -> "先放平画面"
        "orbit", "raisecamera", "lowercamera", "step", "view-change" -> "先改变机位"
        else -> "按步骤调整"
    }
}

private fun compactAestheticDesc(text: String): String {
    val trimmed = text
        .replace("\n", " ")
        .replace("，", " ")
        .replace("。", " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    return trimmed.take(22)
}
