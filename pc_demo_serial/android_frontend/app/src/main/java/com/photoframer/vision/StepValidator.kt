package com.photoframer.vision

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.exp

private const val TAG = "StepValidator"

/**
 * 步骤验证结果
 */
data class StepValidationResult(
    val isCompleted: Boolean,       // 步骤是否完成
    val progress: Float,            // 完成进度 0-1
    val feedbackText: String,       // 反馈文本（中文）
    val shiftDistance: Float?,      // 平移距离（像素）
    val scaleFactor: Float?,        // 缩放因子
    val rotationAngle: Float?,      // 旋转角度（度）
    val matchQuality: Float         // 匹配质量 0-1
)

/**
 * 步骤验证器
 */
class StepValidator(
    targetBitmap: Bitmap
) {
    private val featureMatcher = FeatureMatcher()
    private val homographyAnalyzer = HomographyAnalyzer()
    private val objectMatcher: ObjectMatcher  // 用于 View-change 验证
    
    // 阈值设定
    companion object {
        const val SHIFT_THRESHOLD = 35.0       // 平移阈值 (约10%屏幕宽度，更严格)
        const val ZOOM_THRESHOLD = 0.10        // 缩放阈值 (10%，更严格)
        const val SCALE_GUIDANCE_THRESHOLD = 0.15 // 触发缩放建议的阈值
        const val ROTATION_THRESHOLD = 5.0     // 旋转阈值 (5度，用于 View-change)
        const val ROLL_THRESHOLD = 3.0         // Roll 水平校正阈值 (3度，用于 Shift)
        const val MIN_MATCH_QUALITY = 8        // 最小匹配点数
    }
    
    init {
        // 关键修复：将目标图片缩放到与分析帧相同的宽度 (360px)
        // 确保 Scale=1.0 代表完美匹配，消除分辨率差异带来的伪误差
        val scaledTarget = if (targetBitmap.width != 360) {
            val ratio = 360f / targetBitmap.width
            val newHeight = (targetBitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(targetBitmap, 360, newHeight, true)
        } else {
            targetBitmap
        }
        
        featureMatcher.setTargetImage(scaledTarget)
        
        // 初始化 ObjectMatcher 用于 View-change 验证
        objectMatcher = ObjectMatcher(targetBitmap)
        
        Log.d(TAG, "StepValidator 初始化完成 (Target Resized to 360px, ObjectMatcher Ready)")
    }
    
    // 状态保持
    private var lastValidComponents: HomographyComponents? = null
    private var lastValidTime: Long = 0
    private val PERSISTENCE_DURATION = 800L // 丢失目标后保持显示的时间 (ms)
    
    // 平滑滤波器
    private val smoothingFilter = SmoothingFilter(alpha = 0.3f) // alpha越小越平滑，延迟越高

    /**
     * 验证当前帧是否完成指定步骤
     */
    /**
     * 验证当前帧是否完成指定步骤
     */
    fun validateStep(
        currentFrame: Bitmap,
        actionType: String,
        direction: String,
        currentZoomRatio: Float = 1.0f
    ): StepValidationResult {
        Log.d(TAG, "开始验证: actionType=$actionType, direction=$direction, zoom=$currentZoomRatio")
        
        // 计算单应性矩阵
        val result = featureMatcher.computeHomography(currentFrame)
        
        Log.d(TAG, "匹配结果: matchCount=${result.matchCount}, message=${result.message}")
        
        val currentTime = System.currentTimeMillis()
        var components: HomographyComponents? = null
        var matchQuality = 0f

        if (result.homography != null && result.matchCount >= MIN_MATCH_QUALITY) {
             // 分解单应性矩阵
            components = homographyAnalyzer.decompose(result.homography)
            matchQuality = (result.matchCount.toFloat() / 50f).coerceIn(0f, 1f)
        }

        // 策略：如果当前帧识别失败，但在有效期内，则使用上一次的有效数据
        if (components == null) {
            if (currentTime - lastValidTime < PERSISTENCE_DURATION && lastValidComponents != null) {
                // 使用缓存数据，但稍微降低匹配质量
                components = lastValidComponents
                matchQuality = 0.3f // 标记为缓存数据
                Log.d(TAG, "处于保持期，使用缓存数据")
            } else {
                // 确实丢失了
                return StepValidationResult(
                    isCompleted = false,
                    progress = 0f,
                    feedbackText = "", // 丢失时返回空，UI显示静态引导
                    shiftDistance = null,
                    scaleFactor = null,
                    rotationAngle = null,
                    matchQuality = 0f
                )
            }
        } else {
            // 当前帧有效，更新缓存和时间
            // 应用平滑滤波
            val smoothed = smoothingFilter.filter(components)
            components = smoothed
            
            lastValidComponents = smoothed
            lastValidTime = currentTime
        }
        
        Log.d(TAG, "验证数据: tx=${components!!.translationX}, ty=${components.translationY}, " +
                "scale=${components.scaleFactor}, rotation=${components.rotationAngle}")
        
        // 根据操作类型判定
        return when (actionType.lowercase()) {
            "shift" -> validateShift(components, direction, matchQuality, currentZoomRatio)
            "zoom" -> validateZoom(components, direction, matchQuality, currentZoomRatio)
            "view-change" -> validateViewChange(components, direction, matchQuality)
            else -> {
                validateShift(components, direction, matchQuality, currentZoomRatio)
            }
        }
    }
    
    /**
     * 验证平移步骤
     * 使用 sigmoid 函数计算进度，确保进度始终在0-1之间且有意义
     */
    private fun validateShift(
        components: HomographyComponents,
        direction: String,
        matchQuality: Float,
        currentZoom: Float
    ): StepValidationResult {
        val distance = components.translationDistance
        val tx = components.translationX
        val ty = components.translationY
        val rollAngle = components.rotationAngle  // Roll 角度（水平校正）

        // 计算视觉缩放因子 (Sensor Scale * Digital Zoom)
        val visualScale = components.scaleFactor * currentZoom
        
        // 完成条件：平移 + 缩放 + Roll 三者同时满足
        val isCompleted = distance < SHIFT_THRESHOLD && 
                          abs(1.0 - visualScale) < SCALE_GUIDANCE_THRESHOLD &&
                          abs(rollAngle) < ROLL_THRESHOLD  // 新增 Roll 验证
        
        // 使用 sigmoid 函数计算进度（综合考虑平移和 Roll）
        val rollProgress = (1.0 / (1.0 + exp((abs(rollAngle) - ROLL_THRESHOLD) / 2.0)))
        val shiftProgress = (1.0 / (1.0 + exp((distance - SHIFT_THRESHOLD) / 30.0)))
        val progress = ((rollProgress + shiftProgress) / 2.0).toFloat()
        
        val feedbackText = if (isCompleted) {
            "✓ 位置已对齐"
        } else {
            // 优先检查 Roll 角度（水平校正）
            if (abs(rollAngle) > ROLL_THRESHOLD) {
                if (rollAngle > 0) "向右转动手机 ↻" else "向左转动手机 ↺"
            }
            // 然后检查缩放
            else if (visualScale > (1.0 + SCALE_GUIDANCE_THRESHOLD)) {
                 val targetZoom = 1.0 / components.scaleFactor
                 "主体太大 (建议变焦至 %.1fx)".format(targetZoom)
            } else if (visualScale < (1.0 - SCALE_GUIDANCE_THRESHOLD)) {
                 val targetZoom = 1.0 / components.scaleFactor
                 "主体太小 (建议变焦至 %.1fx)".format(targetZoom)
            }
            // 最后检查平移
            else {
                 generateShiftFeedback(tx, ty, distance)
            }
        }
        
        return StepValidationResult(
            isCompleted = isCompleted,
            progress = progress,
            feedbackText = feedbackText,
            shiftDistance = distance.toFloat(),
            scaleFactor = components.scaleFactor.toFloat(),
            rotationAngle = rollAngle.toFloat(),
            matchQuality = matchQuality
        )
    }
    
    /**
     * 验证缩放步骤
     */
    private fun validateZoom(
        components: HomographyComponents,
        direction: String,
        matchQuality: Float,
        currentZoom: Float
    ): StepValidationResult {
        // 计算视觉缩放因子
        val visualScale = components.scaleFactor * currentZoom
        val scaleError = abs(1.0 - visualScale)
        
        val isCompleted = scaleError < ZOOM_THRESHOLD
        val progress = (1.0 / (1.0 + exp((scaleError - ZOOM_THRESHOLD) / 0.1))).toFloat()
        
        // TargetZoom = 1.0 / SensorScale
        val targetZoom = 1.0 / components.scaleFactor
        
        val feedbackText = if (isCompleted) {
            "✓ 焦距已对齐"
        } else {
            when {
                visualScale < 0.85 -> "主体太小 (变焦至 %.1fx) ↑".format(targetZoom)
                visualScale > 1.15 -> "主体太大 (变焦至 %.1fx) ↓".format(targetZoom)
                visualScale < 1.0 -> "再放大一点"
                else -> "再缩小一点"
            }
        }
        
        return StepValidationResult(
            isCompleted = isCompleted,
            progress = progress,
            feedbackText = feedbackText,
            shiftDistance = components.translationDistance.toFloat(),
            scaleFactor = components.scaleFactor.toFloat(),
            rotationAngle = components.rotationAngle.toFloat(),
            matchQuality = matchQuality
        )
    }
    
    /**
     * 验证视角变换步骤 - 使用 ML Kit 物体检测
     * 注意：ML Kit 是异步的，此方法返回当前状态，并通过回调更新
     */
    private fun validateViewChange(
        components: HomographyComponents,
        direction: String,
        matchQuality: Float
    ): StepValidationResult {
        // View-change 验证通过 validateViewChangeAsync 异步处理
        // 此处返回基于 Homography 的初始结果作为备用
        val rotation = components.rotationAngle
        val rotationError = abs(rotation)
        
        // Homography 作为辅助判断
        val progress = (1.0 / (1.0 + exp((rotationError - ROTATION_THRESHOLD) / 5.0))).toFloat()
        
        return StepValidationResult(
            isCompleted = false,  // 需要异步验证确认
            progress = progress * 0.5f,  // 初始进度减半，等待物体检测
            feedbackText = "正在分析视角...",
            shiftDistance = components.translationDistance.toFloat(),
            scaleFactor = components.scaleFactor.toFloat(),
            rotationAngle = rotation.toFloat(),
            matchQuality = matchQuality
        )
    }
    
    /**
     * 异步验证 View-change 步骤
     * 使用 ML Kit 物体检测比较主体位置
     */
    fun validateViewChangeAsync(
        currentFrame: Bitmap,
        callback: (StepValidationResult) -> Unit
    ) {
        objectMatcher.compareObjectsSync(currentFrame) { result ->
            val validationResult = StepValidationResult(
                isCompleted = result.isMatched,
                progress = 1f - result.positionError,
                feedbackText = result.feedbackText,
                shiftDistance = null,
                scaleFactor = null,
                rotationAngle = null,
                matchQuality = if (result.hasObject) 0.8f else 0f
            )
            callback(validationResult)
        }
    }
    
    /**
     * 生成平移方向反馈
     */
    private fun generateShiftFeedback(tx: Double, ty: Double, distance: Double): String {
        val absX = abs(tx)
        val absY = abs(ty)
        
        val distanceHint = when {
            distance > 150 -> " (较远)"
            distance > 80 -> ""
            else -> " (接近)"
        }
        
        return when {
            absX > absY && tx > 30 -> "向右移动$distanceHint →"
            absX > absY && tx < -30 -> "向左移动$distanceHint ←"
            absY > absX && ty > 30 -> "向下移动$distanceHint ↓"
            absY > absX && ty < -30 -> "向上移动$distanceHint ↑"
            absX > 10 || absY > 10 -> "微调位置$distanceHint"
            else -> "接近目标"
        }
    }

    /**
     * 简单的低通滤波器 (Exponential Moving Average)
     */
    private class SmoothingFilter(private val alpha: Float) {
        private var lastVal: HomographyComponents? = null
        
        fun filter(input: HomographyComponents): HomographyComponents {
            val last = lastVal
            if (last == null) {
                lastVal = input
                return input
            }
            
            // Apply EMA for each component
            // y[n] = alpha * x[n] + (1 - alpha) * y[n-1]
            val newTx = alpha * input.translationX + (1 - alpha) * last.translationX
            val newTy = alpha * input.translationY + (1 - alpha) * last.translationY
            // Distance recalculate from new tx, ty roughly
            val newDist = kotlin.math.sqrt(newTx * newTx + newTy * newTy) 
            
            val newScale = alpha * input.scaleFactor + (1 - alpha) * last.scaleFactor
            val newRot = alpha * input.rotationAngle + (1 - alpha) * last.rotationAngle
            
            val result = HomographyComponents(
                translationX = newTx,
                translationY = newTy,
                translationDistance = newDist,
                scaleFactor = newScale,
                rotationAngle = newRot
            )
            lastVal = result
            return result
        }
    }
    
    /**
     * 释放资源
     */
    fun close() {
        objectMatcher.close()
    }
}
