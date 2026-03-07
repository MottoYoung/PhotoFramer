package com.photoframer.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

private const val TAG = "ObjectMatcher"

/**
 * 物体匹配结果
 */
data class ObjectMatchResult(
    val isMatched: Boolean,           // 物体位置是否接近目标
    val hasObject: Boolean,           // 是否检测到物体
    val positionError: Float,         // 位置误差 (0-1, 相对画面尺寸)
    val sizeError: Float,             // 大小误差 (比例差异)
    val feedbackText: String          // 引导文本
)

/**
 * 物体边界框（归一化坐标）
 */
data class NormalizedBoundingBox(
    val centerX: Float,   // 中心点 X (0-1)
    val centerY: Float,   // 中心点 Y (0-1)
    val width: Float,     // 宽度 (0-1)
    val height: Float     // 高度 (0-1)
)

/**
 * 物体检测匹配器
 * 用于 View-change 验证：比较目标图和实时帧中的主要物体位置
 */
class ObjectMatcher(targetBitmap: Bitmap) {
    
    // ML Kit 物体检测器 (单图模式，检测多个物体)
    private val objectDetector: ObjectDetector
    
    // 目标图片中检测到的主要物体边界框
    private var targetBoundingBox: NormalizedBoundingBox? = null
    private var targetImageWidth: Int = 0
    private var targetImageHeight: Int = 0
    
    // 阈值设定
    companion object {
        const val POSITION_THRESHOLD = 0.15f    // 位置误差阈值 (15% 画面尺寸)
        const val SIZE_THRESHOLD = 0.25f        // 大小误差阈值 (25%)
    }
    
    init {
        // 配置物体检测器
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()  // 检测多个物体，选最大的
            .enableClassification()   // 启用分类
            .build()
        
        objectDetector = ObjectDetection.getClient(options)
        
        // 检测目标图片中的物体
        targetImageWidth = targetBitmap.width
        targetImageHeight = targetBitmap.height
        detectTargetObject(targetBitmap)
        
        Log.d(TAG, "ObjectMatcher 初始化完成")
    }
    
    /**
     * 检测目标图片中的主要物体
     */
    private fun detectTargetObject(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        objectDetector.process(image)
            .addOnSuccessListener { objects ->
                if (objects.isNotEmpty()) {
                    // 选择最大的物体
                    val largestObject = objects.maxByOrNull { 
                        it.boundingBox.width() * it.boundingBox.height() 
                    }
                    
                    largestObject?.let {
                        targetBoundingBox = normalizeRect(it.boundingBox, targetImageWidth, targetImageHeight)
                        Log.d(TAG, "目标物体检测成功: ${targetBoundingBox}")
                    }
                } else {
                    Log.w(TAG, "目标图片中未检测到物体")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "目标物体检测失败: ${e.message}")
            }
    }
    
    /**
     * 比较实时帧与目标图的物体位置
     * 使用挂起函数以支持协程
     */
    suspend fun compareObjects(currentFrame: Bitmap): ObjectMatchResult {
        val target = targetBoundingBox
        
        if (target == null) {
            return ObjectMatchResult(
                isMatched = false,
                hasObject = false,
                positionError = 1f,
                sizeError = 1f,
                feedbackText = "未能识别目标物体"
            )
        }
        
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(currentFrame, 0)
            
            objectDetector.process(image)
                .addOnSuccessListener { objects ->
                    if (objects.isNotEmpty()) {
                        // 选择最大的物体
                        val largestObject = objects.maxByOrNull { 
                            it.boundingBox.width() * it.boundingBox.height() 
                        }
                        
                        if (largestObject != null) {
                            val currentBox = normalizeRect(
                                largestObject.boundingBox, 
                                currentFrame.width, 
                                currentFrame.height
                            )
                            
                            val result = calculateMatch(target, currentBox)
                            continuation.resume(result)
                        } else {
                            continuation.resume(createNoObjectResult())
                        }
                    } else {
                        continuation.resume(createNoObjectResult())
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "当前帧物体检测失败: ${e.message}")
                    continuation.resume(createNoObjectResult())
                }
        }
    }
    
    /**
     * 同步比较方法（用于非协程环境）
     * 注意：此方法会阻塞，仅供兼容使用
     */
    fun compareObjectsSync(currentFrame: Bitmap, callback: (ObjectMatchResult) -> Unit) {
        val target = targetBoundingBox
        
        if (target == null) {
            callback(ObjectMatchResult(
                isMatched = false,
                hasObject = false,
                positionError = 1f,
                sizeError = 1f,
                feedbackText = "未能识别目标物体"
            ))
            return
        }
        
        val image = InputImage.fromBitmap(currentFrame, 0)
        
        objectDetector.process(image)
            .addOnSuccessListener { objects ->
                if (objects.isNotEmpty()) {
                    val largestObject = objects.maxByOrNull { 
                        it.boundingBox.width() * it.boundingBox.height() 
                    }
                    
                    if (largestObject != null) {
                        val currentBox = normalizeRect(
                            largestObject.boundingBox, 
                            currentFrame.width, 
                            currentFrame.height
                        )
                        callback(calculateMatch(target, currentBox))
                    } else {
                        callback(createNoObjectResult())
                    }
                } else {
                    callback(createNoObjectResult())
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "物体检测失败: ${e.message}")
                callback(createNoObjectResult())
            }
    }
    
    /**
     * 计算目标框和当前框的匹配程度
     */
    private fun calculateMatch(
        target: NormalizedBoundingBox, 
        current: NormalizedBoundingBox
    ): ObjectMatchResult {
        // 计算位置误差（中心点距离）
        val positionError = kotlin.math.sqrt(
            (target.centerX - current.centerX).let { it * it } +
            (target.centerY - current.centerY).let { it * it }
        ).toFloat()
        
        // 计算大小误差（面积比例差异）
        val targetArea = target.width * target.height
        val currentArea = current.width * current.height
        val sizeError = abs(targetArea - currentArea) / maxOf(targetArea, currentArea)
        
        val isMatched = positionError < POSITION_THRESHOLD && sizeError < SIZE_THRESHOLD
        
        val feedbackText = if (isMatched) {
            "✓ 构图已对齐"
        } else {
            generateFeedback(target, current, positionError, sizeError)
        }
        
        Log.d(TAG, "匹配结果: posErr=$positionError, sizeErr=$sizeError, matched=$isMatched")
        
        return ObjectMatchResult(
            isMatched = isMatched,
            hasObject = true,
            positionError = positionError,
            sizeError = sizeError,
            feedbackText = feedbackText
        )
    }
    
    /**
     * 生成引导反馈文本
     */
    private fun generateFeedback(
        target: NormalizedBoundingBox,
        current: NormalizedBoundingBox,
        positionError: Float,
        sizeError: Float
    ): String {
        // 优先处理位置误差
        if (positionError >= POSITION_THRESHOLD) {
            val dx = target.centerX - current.centerX
            val dy = target.centerY - current.centerY
            
            return when {
                abs(dx) > abs(dy) && dx > 0.05f -> "向右侧移 →"
                abs(dx) > abs(dy) && dx < -0.05f -> "向左侧移 ←"
                abs(dy) > abs(dx) && dy > 0.05f -> "再低一点 ↓"
                abs(dy) > abs(dx) && dy < -0.05f -> "再高一点 ↑"
                else -> "微调角度"
            }
        }
        
        // 处理大小误差
        if (sizeError >= SIZE_THRESHOLD) {
            val targetArea = target.width * target.height
            val currentArea = current.width * current.height
            
            return if (currentArea > targetArea) {
                "距离太近，后退一步"
            } else {
                "距离太远，前进一步"
            }
        }
        
        return "微调角度"
    }
    
    /**
     * 将 Rect 归一化到 0-1 范围
     */
    private fun normalizeRect(rect: Rect, imageWidth: Int, imageHeight: Int): NormalizedBoundingBox {
        return NormalizedBoundingBox(
            centerX = (rect.left + rect.width() / 2f) / imageWidth,
            centerY = (rect.top + rect.height() / 2f) / imageHeight,
            width = rect.width().toFloat() / imageWidth,
            height = rect.height().toFloat() / imageHeight
        )
    }
    
    /**
     * 创建未检测到物体的结果
     */
    private fun createNoObjectResult(): ObjectMatchResult {
        return ObjectMatchResult(
            isMatched = false,
            hasObject = false,
            positionError = 1f,
            sizeError = 1f,
            feedbackText = "未检测到主体，请对准目标"
        )
    }
    
    /**
     * 释放资源
     */
    fun close() {
        objectDetector.close()
    }
}
