package com.photoframer.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "ViewChangeAnalyzer"

data class ViewChangeAnalysisResult(
    val isCompleted: Boolean,
    val progress: Float,
    val feedbackText: String,
    val hasObject: Boolean,
    val targetAlignmentScore: Float,
    val directionScore: Float
)

/**
 * View-change 专用分析器
 * 同时关注：
 * 1. 当前画面是否已脱离初始视角
 * 2. 当前主体位置/大小是否逼近目标视角
 * 3. 当前运动方向是否与目标视角一致
 */
class ViewChangeAnalyzer(
    sourceBitmap: Bitmap,
    targetBitmap: Bitmap
) {
    private val objectDetector: ObjectDetector

    private var sourceBoundingBox: NormalizedBoundingBox? = null
    private var targetBoundingBox: NormalizedBoundingBox? = null

    companion object {
        private const val MIN_PERSPECTIVE_SCORE = 0.45f
        private const val MIN_ALIGNMENT_SCORE = 0.62f
        private const val MIN_DIRECTION_SCORE = 0.35f
        private const val MIN_COMPLETION_SCORE = 0.72f
    }

    init {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()

        objectDetector = ObjectDetection.getClient(options)

        detectReferenceObject(sourceBitmap) { box ->
            sourceBoundingBox = box
            Log.d(TAG, "源视角主体框: $box")
        }
        detectReferenceObject(targetBitmap) { box ->
            targetBoundingBox = box
            Log.d(TAG, "目标视角主体框: $box")
        }
    }

    fun analyze(
        currentFrame: Bitmap,
        direction: String,
        perspectiveScore: Float,
        targetFeatureScore: Float,
        callback: (ViewChangeAnalysisResult) -> Unit
    ) {
        val image = InputImage.fromBitmap(currentFrame, 0)
        objectDetector.process(image)
            .addOnSuccessListener { objects ->
                val currentObject = objects.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                }

                if (currentObject == null) {
                    callback(
                        ViewChangeAnalysisResult(
                            isCompleted = false,
                            progress = (perspectiveScore * 0.35f).coerceIn(0f, 1f),
                            feedbackText = "请保持主体在画面中，再继续改变视角",
                            hasObject = false,
                            targetAlignmentScore = 0f,
                            directionScore = 0.2f
                        )
                    )
                    return@addOnSuccessListener
                }

                val currentBox = normalizeRect(
                    currentObject.boundingBox,
                    currentFrame.width,
                    currentFrame.height
                )

                val alignmentScore = computeTargetAlignmentScore(currentBox)
                val directionScore = computeDirectionScore(currentBox, direction)
                val combinedFeatureScore = targetFeatureScore.coerceIn(0f, 1f)
                val progress = (
                    perspectiveScore * 0.40f +
                        alignmentScore * 0.30f +
                        directionScore * 0.20f +
                        combinedFeatureScore * 0.10f
                    ).coerceIn(0f, 1f)

                val completed = perspectiveScore >= MIN_PERSPECTIVE_SCORE &&
                    alignmentScore >= MIN_ALIGNMENT_SCORE &&
                    directionScore >= MIN_DIRECTION_SCORE &&
                    progress >= MIN_COMPLETION_SCORE

                callback(
                    ViewChangeAnalysisResult(
                        isCompleted = completed,
                        progress = progress,
                        feedbackText = generateFeedback(
                            direction = direction,
                            perspectiveScore = perspectiveScore,
                            alignmentScore = alignmentScore,
                            directionScore = directionScore,
                            currentBox = currentBox,
                            isCompleted = completed
                        ),
                        hasObject = true,
                        targetAlignmentScore = alignmentScore,
                        directionScore = directionScore
                    )
                )
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "View-change 当前帧主体检测失败: ${error.message}")
                callback(
                    ViewChangeAnalysisResult(
                        isCompleted = false,
                        progress = (perspectiveScore * 0.25f).coerceIn(0f, 1f),
                        feedbackText = "正在重新识别主体...",
                        hasObject = false,
                        targetAlignmentScore = 0f,
                        directionScore = 0.2f
                    )
                )
            }
    }

    private fun computeTargetAlignmentScore(currentBox: NormalizedBoundingBox): Float {
        val targetBox = targetBoundingBox ?: return 0.45f

        val positionError = sqrt(
            (targetBox.centerX - currentBox.centerX).let { it * it } +
                (targetBox.centerY - currentBox.centerY).let { it * it }
        ).toFloat()

        val targetArea = targetBox.width * targetBox.height
        val currentArea = currentBox.width * currentBox.height
        val sizeError = abs(targetArea - currentArea) / maxOf(targetArea, currentArea, 1e-3f)

        val positionScore = (1f - positionError / 0.35f).coerceIn(0f, 1f)
        val sizeScore = (1f - sizeError / 0.45f).coerceIn(0f, 1f)

        return (positionScore * 0.65f + sizeScore * 0.35f).coerceIn(0f, 1f)
    }

    private fun computeDirectionScore(
        currentBox: NormalizedBoundingBox,
        direction: String
    ): Float {
        val sourceBox = sourceBoundingBox
        val targetBox = targetBoundingBox

        if (sourceBox != null && targetBox != null) {
            val expectedDx = targetBox.centerX - sourceBox.centerX
            val expectedDy = targetBox.centerY - sourceBox.centerY
            val currentDx = currentBox.centerX - sourceBox.centerX
            val currentDy = currentBox.centerY - sourceBox.centerY

            val expectedNormSq = expectedDx * expectedDx + expectedDy * expectedDy
            if (expectedNormSq > 1e-4f) {
                val projection = ((currentDx * expectedDx) + (currentDy * expectedDy)) / expectedNormSq
                val lateral = abs(currentDx * expectedDy - currentDy * expectedDx) / expectedNormSq
                return (projection.coerceIn(0f, 1f) - lateral * 0.45f).coerceIn(0f, 1f)
            }
        }

        return fallbackDirectionScore(sourceBox, currentBox, direction)
    }

    private fun fallbackDirectionScore(
        sourceBox: NormalizedBoundingBox?,
        currentBox: NormalizedBoundingBox,
        direction: String
    ): Float {
        if (sourceBox == null) return 0.5f

        val dx = currentBox.centerX - sourceBox.centerX
        val dy = currentBox.centerY - sourceBox.centerY

        return when (direction.lowercase()) {
            "side-view-left" -> if (dx < -0.03f) 0.65f else 0.25f
            "side-view-right" -> if (dx > 0.03f) 0.65f else 0.25f
            "high-angle" -> if (dy > 0.03f) 0.60f else 0.30f
            "low-angle" -> if (dy < -0.03f) 0.60f else 0.30f
            else -> 0.5f
        }
    }

    private fun generateFeedback(
        direction: String,
        perspectiveScore: Float,
        alignmentScore: Float,
        directionScore: Float,
        currentBox: NormalizedBoundingBox,
        isCompleted: Boolean
    ): String {
        if (isCompleted) {
            return "✓ 新视角已接近目标"
        }

        if (perspectiveScore < 0.22f) {
            return when (direction.lowercase()) {
                "high-angle" -> "保持主体稳定，稍微抬高机位俯拍"
                "low-angle" -> "保持主体稳定，稍微降低机位仰拍"
                "side-view-left" -> "围绕主体向左侧移动，制造侧视角"
                "side-view-right" -> "围绕主体向右侧移动，制造侧视角"
                else -> "继续围绕主体改变视角"
            }
        }

        if (directionScore < 0.35f) {
            return when (direction.lowercase()) {
                "high-angle" -> "方向还不对，机位再抬高一点"
                "low-angle" -> "方向还不对，机位再降低一点"
                "side-view-left" -> "方向还不对，再往主体左侧绕一点"
                "side-view-right" -> "方向还不对，再往主体右侧绕一点"
                else -> "改变方向，再绕主体移动一点"
            }
        }

        if (alignmentScore < 0.60f) {
            val targetBox = targetBoundingBox
            if (targetBox != null) {
                val dx = targetBox.centerX - currentBox.centerX
                val dy = targetBox.centerY - currentBox.centerY
                return when {
                    abs(dx) > abs(dy) && dx > 0.03f -> "主体还需向右靠近目标视角"
                    abs(dx) > abs(dy) && dx < -0.03f -> "主体还需向左靠近目标视角"
                    abs(dy) >= abs(dx) && dy > 0.03f -> "机位再低一点，接近目标视角"
                    abs(dy) >= abs(dx) && dy < -0.03f -> "机位再高一点，接近目标视角"
                    else -> "继续微调视角，让主体更贴近参考图"
                }
            }
        }

        return "视角方向正确，继续微调到参考图位置"
    }

    private fun detectReferenceObject(
        bitmap: Bitmap,
        onDetected: (NormalizedBoundingBox?) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        objectDetector.process(image)
            .addOnSuccessListener { objects ->
                val largestObject = objects.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                }
                onDetected(
                    largestObject?.boundingBox?.let {
                        normalizeRect(it, bitmap.width, bitmap.height)
                    }
                )
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "参考主体检测失败: ${error.message}")
                onDetected(null)
            }
    }

    private fun normalizeRect(rect: Rect, imageWidth: Int, imageHeight: Int): NormalizedBoundingBox {
        return NormalizedBoundingBox(
            centerX = (rect.left + rect.width() / 2f) / imageWidth,
            centerY = (rect.top + rect.height() / 2f) / imageHeight,
            width = rect.width().toFloat() / imageWidth,
            height = rect.height().toFloat() / imageHeight
        )
    }

    fun close() {
        objectDetector.close()
    }
}
