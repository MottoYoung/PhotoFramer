package com.photoframer.vision

import org.opencv.core.Mat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 单应性矩阵分析结果
 */
data class HomographyComponents(
    val translationX: Double,    // X方向平移（像素）
    val translationY: Double,    // Y方向平移（像素）
    val translationDistance: Double,  // 平移距离
    val scaleFactor: Double,     // 缩放因子（1.0表示无缩放）
    val rotationAngle: Double    // 旋转角度（度）
)

/**
 * 单应性矩阵分析器
 * 从单应性矩阵中提取平移、缩放、旋转分量
 */
class HomographyAnalyzer {
    
    /**
     * 分解单应性矩阵
     * 
     * H ≈ | s·cosθ  -s·sinθ  tx |
     *     | s·sinθ   s·cosθ  ty |
     *     |   0        0      1  |
     * 
     * @param homography 3x3 单应性矩阵
     * @return 分解后的组件
     */
    /**
     * 分解变换矩阵 (支持 3x3 单应性矩阵 或 2x3 仿射矩阵)
     * 
     * 对于 2x3 仿射矩阵 (Partial Affine / Similarity):
     * [ s*cosθ  -s*sinθ  tx ]
     * [ s*sinθ   s*cosθ  ty ]
     */
    fun decompose(matrix: Mat): HomographyComponents? {
        if (matrix.empty()) return null
        
        var tx = 0.0
        var ty = 0.0
        var scaleFactor = 1.0
        var rotationDeg = 0.0
        
        if (matrix.rows() == 2 && matrix.cols() == 3) {
            // 2x3 仿射矩阵 (estimateAffinePartial2D 返回此格式)
            // m00 m01 m02 (tx)
            // m10 m11 m12 (ty)
            val m00 = matrix.get(0, 0)[0] // s * cos
            val m10 = matrix.get(1, 0)[0] // s * sin
            val m02 = matrix.get(0, 2)[0] // tx
            val m12 = matrix.get(1, 2)[0] // ty
            
            tx = m02
            ty = m12
            
            // 计算缩放 s = sqrt((s*cos)^2 + (s*sin)^2)
            scaleFactor = sqrt(m00 * m00 + m10 * m10)
            
            // 计算旋转 atan2(s*sin, s*cos)
            val rotationRad = atan2(m10, m00)
            rotationDeg = Math.toDegrees(rotationRad)
            
        } else if (matrix.rows() == 3 && matrix.cols() == 3) {
            // 3x3 单应性矩阵 (旧逻辑兼容)
            val h00 = matrix.get(0, 0)[0]
            val h01 = matrix.get(0, 1)[0]
            val h02 = matrix.get(0, 2)[0]
            val h10 = matrix.get(1, 0)[0]
            val h11 = matrix.get(1, 1)[0]
            val h12 = matrix.get(1, 2)[0]
            val h22 = matrix.get(2, 2)[0]
            
            // 归一化
            val norm = if (abs(h22) > 1e-6) h22 else 1.0
            
            tx = h02 / norm
            ty = h12 / norm
            val normalizedH00 = h00 / norm
            val normalizedH10 = h10 / norm
            
            scaleFactor = sqrt(normalizedH00 * normalizedH00 + normalizedH10 * normalizedH10)
            
            val rotationRad = atan2(normalizedH10, normalizedH00)
            rotationDeg = Math.toDegrees(rotationRad)
        } else {
            return null
        }
        
        return HomographyComponents(
            translationX = tx,
            translationY = ty,
            translationDistance = sqrt(tx * tx + ty * ty),
            scaleFactor = scaleFactor,
            rotationAngle = rotationDeg
        )
    }
}
