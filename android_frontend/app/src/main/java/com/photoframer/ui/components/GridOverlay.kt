package com.photoframer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * 3x3 网格线叠加层
 * 用于辅助三分法构图
 */
@Composable
fun GridOverlay(
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White.copy(alpha = 0.5f),
    lineWidth: Float = 1.5f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // 垂直线（将画面分为三列）
        val verticalSpacing = width / 3f
        for (i in 1..2) {
            drawLine(
                color = lineColor,
                start = Offset(verticalSpacing * i, 0f),
                end = Offset(verticalSpacing * i, height),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
        }
        
        // 水平线（将画面分为三行）
        val horizontalSpacing = height / 3f
        for (i in 1..2) {
            drawLine(
                color = lineColor,
                start = Offset(0f, horizontalSpacing * i),
                end = Offset(width, horizontalSpacing * i),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 高级网格线叠加层
 * 支持多种网格类型
 */
enum class GridType {
    NONE,           // 无网格
    RULE_OF_THIRDS, // 三分法（3x3）
    GOLDEN_RATIO,   // 黄金分割
    SQUARE,         // 正方形网格（4x4）
    DIAGONAL        // 对角线
}

@Composable
fun AdvancedGridOverlay(
    gridType: GridType,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White.copy(alpha = 0.4f),
    lineWidth: Float = 1f
) {
    when (gridType) {
        GridType.NONE -> { /* 不显示任何网格 */ }
        GridType.RULE_OF_THIRDS -> {
            GridOverlay(
                modifier = modifier,
                lineColor = lineColor,
                lineWidth = lineWidth
            )
        }
        GridType.GOLDEN_RATIO -> {
            Canvas(modifier = modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val phi = 0.618f
                
                // 黄金分割线
                drawLine(
                    color = lineColor,
                    start = Offset(width * phi, 0f),
                    end = Offset(width * phi, height),
                    strokeWidth = lineWidth
                )
                drawLine(
                    color = lineColor,
                    start = Offset(width * (1 - phi), 0f),
                    end = Offset(width * (1 - phi), height),
                    strokeWidth = lineWidth
                )
                drawLine(
                    color = lineColor,
                    start = Offset(0f, height * phi),
                    end = Offset(width, height * phi),
                    strokeWidth = lineWidth
                )
                drawLine(
                    color = lineColor,
                    start = Offset(0f, height * (1 - phi)),
                    end = Offset(width, height * (1 - phi)),
                    strokeWidth = lineWidth
                )
            }
        }
        GridType.SQUARE -> {
            Canvas(modifier = modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val spacing = width / 4f
                
                for (i in 1..3) {
                    drawLine(
                        color = lineColor,
                        start = Offset(spacing * i, 0f),
                        end = Offset(spacing * i, height),
                        strokeWidth = lineWidth
                    )
                }
                val hSpacing = height / 4f
                for (i in 1..3) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, hSpacing * i),
                        end = Offset(width, hSpacing * i),
                        strokeWidth = lineWidth
                    )
                }
            }
        }
        GridType.DIAGONAL -> {
            Canvas(modifier = modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // 对角线
                drawLine(
                    color = lineColor,
                    start = Offset(0f, 0f),
                    end = Offset(width, height),
                    strokeWidth = lineWidth
                )
                drawLine(
                    color = lineColor,
                    start = Offset(width, 0f),
                    end = Offset(0f, height),
                    strokeWidth = lineWidth
                )
            }
        }
    }
}
