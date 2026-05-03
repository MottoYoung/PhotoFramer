package com.photoframer.vision

/**
 * 物体边界框（归一化坐标）
 */
data class NormalizedBoundingBox(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)
