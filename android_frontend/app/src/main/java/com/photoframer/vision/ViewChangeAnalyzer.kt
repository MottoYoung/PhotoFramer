package com.photoframer.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.photoframer.arcore.CameraPoseSample
import com.photoframer.arcore.CameraPoseSource
import com.photoframer.data.api.ShotSpec
import com.photoframer.guidance.normalizedActionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "ViewChangeAnalyzer"

data class ViewChangeAnalysisResult(
    val isCompleted: Boolean,
    val progress: Float,
    val feedbackText: String,
    val hasObject: Boolean,
    val targetAlignmentScore: Float,
    val directionScore: Float,
    val targetSimilarityScore: Float,
    val uiHintX: Float,
    val uiHintY: Float
)

/**
 * 视角变化分析器
 *
 * 核心原则：
 * 1. 当前帧要逐渐脱离 source（起始视角）
 * 2. 当前帧要逐渐逼近 target（参考图）
 * 3. 主体位置/尺度仅作为辅助证据，不再主导完成判定
 */
class ViewChangeAnalyzer(
    sourceBitmap: Bitmap,
    targetBitmap: Bitmap,
    private val targetShotSpec: ShotSpec? = null
) {
    private val objectDetector: ObjectDetector

    private var sourceBoundingBox: NormalizedBoundingBox? = null
    private var targetBoundingBox: NormalizedBoundingBox? = null
    private var stableCompletionFrames: Int = 0
    private val targetSpecCenter = targetShotSpec.parseTargetCenter()
    private val targetSpecSize = targetShotSpec.parseTargetSize()

    companion object {
        private const val TARGET_SIM_THRESHOLD = 0.58f
        private const val TARGET_SIM_THRESHOLD_VERTICAL = 0.50f
        private const val TARGET_SIM_THRESHOLD_LOWER_CAMERA = 0.45f
        private const val DIRECTION_THRESHOLD = 0.40f
        private const val DIRECTION_THRESHOLD_VERTICAL = 0.34f
        private const val DIRECTION_THRESHOLD_LOWER_CAMERA = 0.26f
        private const val ALIGNMENT_THRESHOLD = 0.52f
        private const val ALIGNMENT_THRESHOLD_VERTICAL = 0.42f
        private const val ALIGNMENT_THRESHOLD_LOWER_CAMERA = 0.28f
        private const val TARGET_GAIN_THRESHOLD = 0.52f
        private const val TARGET_GAIN_THRESHOLD_VERTICAL = 0.42f
        private const val TARGET_GAIN_THRESHOLD_LOWER_CAMERA = 0.30f
        private const val STABLE_FRAME_COUNT = 3
        private const val STABLE_FRAME_COUNT_VERTICAL = 2
    }

    init {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()

        objectDetector = ObjectDetection.getClient(options)

        targetBoundingBox = applyShotSpecToBox(null)

        detectReferenceObject(sourceBitmap) { box ->
            sourceBoundingBox = box
            if (targetBoundingBox == null) {
                targetBoundingBox = applyShotSpecToBox(box)
            }
            Log.d(TAG, "源视角主体框: $box")
        }
        detectReferenceObject(targetBitmap) { box ->
            targetBoundingBox = applyShotSpecToBox(box)
            Log.d(TAG, "目标视角主体框: ${targetBoundingBox}")
        }
    }

    fun analyze(
        currentFrame: Bitmap,
        actionType: String,
        direction: String,
        perspectiveScore: Float,
        sourceSimilarityScore: Float,
        targetSimilarityScore: Float,
        cameraPoseSample: CameraPoseSample?,
        callback: (ViewChangeAnalysisResult) -> Unit
    ) {
        val image = InputImage.fromBitmap(currentFrame, 0)
        objectDetector.process(image)
            .addOnSuccessListener { objects ->
                val currentObject = objects.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                }

                if (currentObject == null) {
                    stableCompletionFrames = 0
                    val targetGainScore = computeTargetGainScore(
                        sourceSimilarityScore = sourceSimilarityScore,
                        targetSimilarityScore = targetSimilarityScore,
                        perspectiveScore = perspectiveScore
                    )
                    callback(
                        ViewChangeAnalysisResult(
                            isCompleted = false,
                            progress = (
                                targetSimilarityScore * 0.55f +
                                    targetGainScore * 0.25f +
                                    perspectiveScore * 0.20f
                                ).coerceIn(0f, 1f),
                            feedbackText = "请保持主体在画面里，再继续调整机位",
                            hasObject = false,
                            targetAlignmentScore = 0f,
                            directionScore = 0.25f,
                            targetSimilarityScore = targetSimilarityScore,
                            uiHintX = 0f,
                            uiHintY = 0f
                        )
                    )
                    return@addOnSuccessListener
                }

                val currentBox = normalizeRect(
                    currentObject.boundingBox,
                    currentFrame.width,
                    currentFrame.height
                )

                val alignmentScore = computeTargetAlignmentScore(currentBox, targetSimilarityScore)
                val visualDirectionScore = computeDirectionScore(
                    actionType = actionType,
                    direction = direction,
                    currentBox = currentBox
                )
                val poseDirectionScore = computePoseDirectionScore(
                    actionType = actionType,
                    direction = direction,
                    cameraPoseSample = cameraPoseSample
                )
                val directionScore = combineDirectionScores(
                    actionType = actionType,
                    visualDirectionScore = visualDirectionScore,
                    poseDirectionScore = poseDirectionScore
                )
                val targetGainScore = computeTargetGainScore(
                    sourceSimilarityScore = sourceSimilarityScore,
                    targetSimilarityScore = targetSimilarityScore,
                    perspectiveScore = perspectiveScore
                )
                val poseProgressScore = computePoseProgressScore(
                    actionType = actionType,
                    direction = direction,
                    cameraPoseSample = cameraPoseSample
                )
                val wristOnlyMotion = isLikelyWristOnlyMotion(
                    actionType = actionType,
                    cameraPoseSample = cameraPoseSample
                )
                val uiHint = computeUiHint(
                    actionType = actionType,
                    direction = direction,
                    currentBox = currentBox,
                    directionScore = directionScore,
                    frameWidth = currentFrame.width.toFloat(),
                    frameHeight = currentFrame.height.toFloat()
                )

                val progress = (
                    targetSimilarityScore * 0.40f +
                        targetGainScore * 0.20f +
                        alignmentScore * 0.18f +
                        directionScore * 0.12f +
                        poseProgressScore * 0.10f
                    ).coerceIn(0f, 1f)

                val normalizedActionType = actionType.normalizedActionType()
                val isVerticalCameraAction = normalizedActionType in setOf(
                    "raisecamera",
                    "lowercamera"
                )
                val isLowerCameraAction = normalizedActionType == "lowercamera"
                val targetSimThreshold = when {
                    isLowerCameraAction -> TARGET_SIM_THRESHOLD_LOWER_CAMERA
                    isVerticalCameraAction -> TARGET_SIM_THRESHOLD_VERTICAL
                    else -> TARGET_SIM_THRESHOLD
                }
                val targetGainThreshold = when {
                    isLowerCameraAction -> TARGET_GAIN_THRESHOLD_LOWER_CAMERA
                    isVerticalCameraAction -> TARGET_GAIN_THRESHOLD_VERTICAL
                    else -> TARGET_GAIN_THRESHOLD
                }
                val directionThreshold = when {
                    isLowerCameraAction -> DIRECTION_THRESHOLD_LOWER_CAMERA
                    isVerticalCameraAction -> DIRECTION_THRESHOLD_VERTICAL
                    else -> DIRECTION_THRESHOLD
                }
                val alignmentThreshold = when {
                    isLowerCameraAction -> ALIGNMENT_THRESHOLD_LOWER_CAMERA
                    isVerticalCameraAction -> ALIGNMENT_THRESHOLD_VERTICAL
                    else -> ALIGNMENT_THRESHOLD
                }
                val requiredStableFrames = if (isVerticalCameraAction) {
                    STABLE_FRAME_COUNT_VERTICAL
                } else {
                    STABLE_FRAME_COUNT
                }

                val rawCompleted = if (isLowerCameraAction) {
                    targetGainScore >= targetGainThreshold &&
                        directionScore >= directionThreshold &&
                        !wristOnlyMotion &&
                        (
                            targetSimilarityScore >= targetSimThreshold ||
                                alignmentScore >= alignmentThreshold ||
                                perspectiveScore >= 0.28f ||
                                poseProgressScore >= 0.72f
                            )
                } else {
                    targetSimilarityScore >= targetSimThreshold &&
                        targetGainScore >= targetGainThreshold &&
                        directionScore >= directionThreshold &&
                        !wristOnlyMotion &&
                        (
                            alignmentScore >= alignmentThreshold ||
                                targetSimilarityScore >= 0.74f ||
                                poseProgressScore >= 0.76f
                            )
                }

                stableCompletionFrames = if (rawCompleted) {
                    stableCompletionFrames + 1
                } else {
                    0
                }

                val completed = stableCompletionFrames >= requiredStableFrames

                callback(
                    ViewChangeAnalysisResult(
                        isCompleted = completed,
                        progress = progress,
                        feedbackText = generateFeedback(
                            actionType = actionType,
                            direction = direction,
                            currentBox = currentBox,
                            targetSimilarityScore = targetSimilarityScore,
                            targetGainScore = targetGainScore,
                            alignmentScore = alignmentScore,
                            directionScore = directionScore,
                            poseProgressScore = poseProgressScore,
                            cameraPoseSample = cameraPoseSample,
                            isCompleted = completed
                        ),
                        hasObject = true,
                        targetAlignmentScore = alignmentScore,
                        directionScore = directionScore,
                        targetSimilarityScore = targetSimilarityScore,
                        uiHintX = uiHint.first,
                        uiHintY = uiHint.second
                    )
                )
            }
            .addOnFailureListener { error ->
                stableCompletionFrames = 0
                Log.e(TAG, "当前帧主体检测失败: ${error.message}")
                callback(
                    ViewChangeAnalysisResult(
                        isCompleted = false,
                        progress = (
                            targetSimilarityScore * 0.45f +
                                perspectiveScore * 0.20f
                            ).coerceIn(0f, 1f),
                        feedbackText = "正在重新识别主体...",
                        hasObject = false,
                        targetAlignmentScore = 0f,
                        directionScore = 0.25f,
                        targetSimilarityScore = targetSimilarityScore,
                        uiHintX = 0f,
                        uiHintY = 0f
                    )
                )
            }
    }

    private fun computeTargetAlignmentScore(
        currentBox: NormalizedBoundingBox,
        targetSimilarityScore: Float
    ): Float {
        val targetBox = targetBoundingBox ?: return (0.40f + targetSimilarityScore * 0.45f).coerceIn(0f, 1f)

        val positionError = sqrt(
            (targetBox.centerX - currentBox.centerX).let { it * it } +
                (targetBox.centerY - currentBox.centerY).let { it * it }
        ).toFloat()

        val targetArea = targetBox.width * targetBox.height
        val currentArea = currentBox.width * currentBox.height
        val sizeError = abs(targetArea - currentArea) / maxOf(targetArea, currentArea, 1e-3f)

        val positionScore = (1f - positionError / 0.32f).coerceIn(0f, 1f)
        val sizeScore = (1f - sizeError / 0.40f).coerceIn(0f, 1f)

        return (positionScore * 0.65f + sizeScore * 0.35f).coerceIn(0f, 1f)
    }

    private fun computeDirectionScore(
        actionType: String,
        direction: String,
        currentBox: NormalizedBoundingBox
    ): Float {
        return when (actionType.normalizedActionType()) {
            "orbit" -> computeOrbitDirectionScore(currentBox, direction)
            "raisecamera" -> computeVerticalMoveScore(currentBox, shouldMoveDownInFrame = true)
            "lowercamera" -> computeVerticalMoveScore(currentBox, shouldMoveDownInFrame = false)
            "step" -> computeStepDirectionScore(currentBox, direction)
            else -> computeGenericViewpointScore(currentBox, direction)
        }
    }

    private fun computeOrbitDirectionScore(
        currentBox: NormalizedBoundingBox,
        direction: String
    ): Float {
        val sourceBox = sourceBoundingBox ?: return 0.5f
        val targetBox = targetBoundingBox

        if (targetBox != null) {
            val expectedDx = targetBox.centerX - sourceBox.centerX
            if (abs(expectedDx) > 0.015f) {
                val currentDx = currentBox.centerX - sourceBox.centerX
                return projectedScore(currentDx, expectedDx)
            }
        }

        val dx = currentBox.centerX - sourceBox.centerX
        return when (direction.lowercase()) {
            "right", "side-view-right" -> directionalFallback(dx > 0.02f, abs(dx))
            else -> directionalFallback(dx < -0.02f, abs(dx))
        }
    }

    private fun computeVerticalMoveScore(
        currentBox: NormalizedBoundingBox,
        shouldMoveDownInFrame: Boolean
    ): Float {
        val sourceBox = sourceBoundingBox ?: return 0.5f
        val targetBox = targetBoundingBox

        if (targetBox != null) {
            val expectedDy = targetBox.centerY - sourceBox.centerY
            if (abs(expectedDy) > 0.015f) {
                val currentDy = currentBox.centerY - sourceBox.centerY
                return projectedScore(currentDy, expectedDy)
            }
        }

        val dy = currentBox.centerY - sourceBox.centerY
        return if (shouldMoveDownInFrame) {
            directionalFallback(dy > 0.01f, abs(dy))
        } else {
            directionalFallback(dy < -0.01f, abs(dy))
        }
    }

    private fun computeStepDirectionScore(
        currentBox: NormalizedBoundingBox,
        direction: String
    ): Float {
        val sourceBox = sourceBoundingBox ?: return 0.5f
        val targetBox = targetBoundingBox
        val sourceArea = sourceBox.width * sourceBox.height
        val currentArea = currentBox.width * currentBox.height

        if (targetBox != null) {
            val targetArea = targetBox.width * targetBox.height
            val expectedDelta = targetArea - sourceArea
            val currentDelta = currentArea - sourceArea
            if (abs(expectedDelta) > 1e-3f) {
                return projectedScore(currentDelta, expectedDelta)
            }
        }

        val areaDelta = currentArea - sourceArea
        return when (direction.lowercase()) {
            "backward" -> directionalFallback(areaDelta < -0.01f, abs(areaDelta) * 10f)
            else -> directionalFallback(areaDelta > 0.01f, abs(areaDelta) * 10f)
        }
    }

    private fun computeGenericViewpointScore(
        currentBox: NormalizedBoundingBox,
        direction: String
    ): Float {
        val sourceBox = sourceBoundingBox ?: return 0.5f
        val targetBox = targetBoundingBox

        if (targetBox != null) {
            val expectedDx = targetBox.centerX - sourceBox.centerX
            val expectedDy = targetBox.centerY - sourceBox.centerY
            val currentDx = currentBox.centerX - sourceBox.centerX
            val currentDy = currentBox.centerY - sourceBox.centerY
            val expectedNorm = sqrt(expectedDx * expectedDx + expectedDy * expectedDy)
            if (expectedNorm > 1e-3f) {
                val projection = ((currentDx * expectedDx) + (currentDy * expectedDy)) / (expectedNorm * expectedNorm)
                val lateral = abs(currentDx * expectedDy - currentDy * expectedDx) / (expectedNorm * expectedNorm)
                return (projection.coerceIn(0f, 1f) - lateral * 0.45f).coerceIn(0f, 1f)
            }
        }

        val dx = currentBox.centerX - sourceBox.centerX
        val dy = currentBox.centerY - sourceBox.centerY
        return when (direction.lowercase()) {
            "high-angle" -> directionalFallback(dy < -0.02f, abs(dy))
            "low-angle" -> directionalFallback(dy > 0.02f, abs(dy))
            "side-view-right" -> directionalFallback(dx > 0.02f, abs(dx))
            else -> directionalFallback(dx < -0.02f, abs(dx))
        }
    }

    private fun computeTargetGainScore(
        sourceSimilarityScore: Float,
        targetSimilarityScore: Float,
        perspectiveScore: Float
    ): Float {
        val similarityGap = targetSimilarityScore - sourceSimilarityScore
        val gapScore = ((similarityGap + 0.18f) / 0.45f).coerceIn(0f, 1f)
        val departureScore = ((1f - sourceSimilarityScore) * 0.55f + perspectiveScore * 0.45f)
            .coerceIn(0f, 1f)
        return (gapScore * 0.70f + departureScore * 0.30f).coerceIn(0f, 1f)
    }

    private fun computeUiHint(
        actionType: String,
        direction: String,
        currentBox: NormalizedBoundingBox,
        directionScore: Float,
        frameWidth: Float,
        frameHeight: Float
    ): Pair<Float, Float> {
        val targetBox = targetBoundingBox
        if (targetBox != null) {
            val dx = ((targetBox.centerX - currentBox.centerX) * frameWidth * 0.95f)
                .coerceIn(-frameWidth * 0.27f, frameWidth * 0.27f)
            val dy = ((targetBox.centerY - currentBox.centerY) * frameHeight * 0.95f)
                .coerceIn(-frameHeight * 0.35f, frameHeight * 0.35f)
            return when (actionType.normalizedActionType()) {
                "orbit" -> dx to (dy * 0.25f)
                "raisecamera", "lowercamera" -> (dx * 0.45f) to dy
                else -> dx to dy
            }
        }

        val fallbackX = ((1f - directionScore).coerceIn(0f, 1f) * frameWidth * 0.20f + frameWidth * 0.05f)
        val fallbackY = ((1f - directionScore).coerceIn(0f, 1f) * frameHeight * 0.24f + frameHeight * 0.07f)
        return when (actionType.normalizedActionType()) {
            "orbit" -> if (direction.equals("right", ignoreCase = true)) {
                fallbackX to 0f
            } else {
                -fallbackX to 0f
            }
            "raisecamera" -> 0f to -fallbackY
            "lowercamera" -> 0f to fallbackY
            else -> when (direction.lowercase()) {
                "side-view-right" -> fallbackX to 0f
                "side-view-left" -> -fallbackX to 0f
                "high-angle" -> 0f to -fallbackY
                "low-angle" -> 0f to fallbackY
                else -> 0f to 0f
            }
        }
    }

    private fun applyShotSpecToBox(detectedBox: NormalizedBoundingBox?): NormalizedBoundingBox? {
        if (targetSpecCenter == null && targetSpecSize == null) {
            return detectedBox
        }

        val fallbackCenter = targetSpecCenter ?: detectedBox?.let { it.centerX to it.centerY } ?: (0.5f to 0.5f)
        val baseBox = detectedBox ?: sourceBoundingBox
        val aspect = baseBox
            ?.let { (it.width / it.height.coerceAtLeast(1e-3f)).coerceIn(0.45f, 2.2f) }
            ?: 0.85f
        val longSide = targetSpecSize
            ?: baseBox?.let { max(it.width, it.height) }
            ?: 0.28f

        val clampedLongSide = longSide.coerceIn(0.10f, 0.82f)
        val width = if (aspect >= 1f) {
            clampedLongSide
        } else {
            (clampedLongSide * aspect).coerceIn(0.08f, 0.82f)
        }
        val height = if (aspect >= 1f) {
            (clampedLongSide / aspect).coerceIn(0.08f, 0.82f)
        } else {
            clampedLongSide
        }

        return NormalizedBoundingBox(
            centerX = fallbackCenter.first.coerceIn(0.08f, 0.92f),
            centerY = fallbackCenter.second.coerceIn(0.08f, 0.92f),
            width = width,
            height = height
        )
    }

    private fun computePoseDirectionScore(
        actionType: String,
        direction: String,
        cameraPoseSample: CameraPoseSample?
    ): Float? {
        val sample = cameraPoseSample ?: return null
        return when (actionType.normalizedActionType()) {
            "raisecamera" -> computeVerticalPoseScore(sample, shouldRaise = true)
            "lowercamera" -> computeVerticalPoseScore(sample, shouldRaise = false)
            "step" -> computeStepPoseScore(sample, direction)
            "orbit" -> computeOrbitPoseScore(sample, direction)
            else -> null
        }
    }

    private fun computePoseProgressScore(
        actionType: String,
        direction: String,
        cameraPoseSample: CameraPoseSample?
    ): Float {
        return computePoseDirectionScore(actionType, direction, cameraPoseSample) ?: 0f
    }

    private fun combineDirectionScores(
        actionType: String,
        visualDirectionScore: Float,
        poseDirectionScore: Float?
    ): Float {
        val poseScore = poseDirectionScore ?: return visualDirectionScore
        return when (actionType.normalizedActionType()) {
            "raisecamera", "lowercamera" -> {
                max(visualDirectionScore * 0.60f + poseScore * 0.40f, poseScore * 0.92f)
                    .coerceIn(0f, 1f)
            }
            else -> (visualDirectionScore * 0.78f + poseScore * 0.22f).coerceIn(0f, 1f)
        }
    }

    private fun computeVerticalPoseScore(
        sample: CameraPoseSample,
        shouldRaise: Boolean
    ): Float {
        if (sample.source != CameraPoseSource.ARCORE || !sample.isTracking) {
            return 0f
        }

        val verticalProgress = if (shouldRaise) {
            sample.verticalMeters / 0.09f
        } else {
            -sample.verticalMeters / 0.09f
        }

        return (0.18f + verticalProgress * 0.82f).coerceIn(0f, 1f)
    }

    private fun computeStepPoseScore(
        sample: CameraPoseSample,
        direction: String
    ): Float {
        if (sample.source != CameraPoseSource.ARCORE || !sample.isTracking) {
            return 0f
        }

        val forwardProgress = if (direction.equals("backward", ignoreCase = true)) {
            -sample.forwardMeters / 0.16f
        } else {
            sample.forwardMeters / 0.16f
        }

        return (0.18f + forwardProgress * 0.82f).coerceIn(0f, 1f)
    }

    private fun computeOrbitPoseScore(
        sample: CameraPoseSample,
        direction: String
    ): Float {
        if (sample.source != CameraPoseSource.ARCORE || !sample.isTracking) {
            return 0f
        }

        val lateralProgress = if (direction.equals("right", ignoreCase = true)) {
            sample.lateralMeters / 0.14f
        } else {
            -sample.lateralMeters / 0.14f
        }

        return (0.15f + lateralProgress * 0.85f).coerceIn(0f, 1f)
    }

    private fun isLikelyWristOnlyMotion(
        actionType: String,
        cameraPoseSample: CameraPoseSample?
    ): Boolean {
        val sample = cameraPoseSample ?: return false
        if (actionType.normalizedActionType() !in setOf("raisecamera", "lowercamera")) {
            return false
        }

        val tiltMagnitude = max(abs(sample.pitchDeltaDegrees), abs(sample.rollDeltaDegrees))
        return sample.source == CameraPoseSource.ARCORE &&
            sample.isTracking &&
            abs(sample.verticalMeters) < 0.03f &&
            tiltMagnitude > 8f
    }

    private fun projectedScore(currentDelta: Float, expectedDelta: Float): Float {
        val projection = (currentDelta / expectedDelta).coerceIn(-1.2f, 1.2f)
        return when {
            projection <= 0f -> 0.12f
            projection >= 1f -> 1f - ((projection - 1f) * 0.20f)
            else -> projection
        }.coerceIn(0f, 1f)
    }

    private fun directionalFallback(inCorrectDirection: Boolean, magnitude: Float): Float {
        return if (inCorrectDirection) {
            (0.45f + magnitude * 1.8f).coerceIn(0f, 0.88f)
        } else {
            0.18f
        }
    }

    private fun generateFeedback(
        actionType: String,
        direction: String,
        currentBox: NormalizedBoundingBox,
        targetSimilarityScore: Float,
        targetGainScore: Float,
        alignmentScore: Float,
        directionScore: Float,
        poseProgressScore: Float,
        cameraPoseSample: CameraPoseSample?,
        isCompleted: Boolean
    ): String {
        if (isCompleted) {
            return "✓ 机位已接近参考图"
        }

        if (isLikelyWristOnlyMotion(actionType, cameraPoseSample)) {
            return when (actionType.normalizedActionType()) {
                "lowercamera" -> "不是只低头拍，把手机整体放低一点"
                "raisecamera" -> "不是只仰头拍，把手机整体抬高一点"
                else -> "继续带着手机整体移动，不是只转手腕"
            }
        }

        if (targetGainScore < 0.34f) {
            return movementPrompt(actionType, direction, stronger = true)
        }

        if (
            actionType.normalizedActionType() in setOf("raisecamera", "lowercamera") &&
            cameraPoseSample?.source == CameraPoseSource.ARCORE &&
            cameraPoseSample.isTracking &&
            poseProgressScore < 0.34f
        ) {
            return when (actionType.normalizedActionType()) {
                "lowercamera" -> "把手机整体再放低一点，主体别跑出画面"
                "raisecamera" -> "把手机整体再抬高一点，主体别跑出画面"
                else -> movementPrompt(actionType, direction, stronger = true)
            }
        }

        if (directionScore < 0.40f) {
            return movementDirectionCorrection(actionType, direction)
        }

        if (actionType.normalizedActionType() == "lowercamera" && alignmentScore < 0.46f) {
            return "把手机整体再放低一点，同时轻轻上扬镜头"
        }

        if (actionType.normalizedActionType() == "raisecamera" && alignmentScore < 0.46f) {
            return "把手机整体再抬高一点，同时轻轻下压镜头"
        }

        if (alignmentScore < 0.54f) {
            val targetBox = targetBoundingBox
            if (targetBox != null) {
                val dx = targetBox.centerX - currentBox.centerX
                val dy = targetBox.centerY - currentBox.centerY
                return when {
                    abs(dx) > abs(dy) && dx > 0.03f -> "主体还需再往右贴近参考图"
                    abs(dx) > abs(dy) && dx < -0.03f -> "主体还需再往左贴近参考图"
                    abs(dy) >= abs(dx) && dy > 0.03f -> "画面还要再低一点，贴近参考图"
                    abs(dy) >= abs(dx) && dy < -0.03f -> "画面还要再高一点，贴近参考图"
                    else -> "继续微调，让主体更贴近参考图"
                }
            }
        }

        if (targetSimilarityScore < 0.55f) {
            return movementPrompt(actionType, direction, stronger = false)
        }

        return "方向对了，继续微调到参考图位置"
    }

    private fun movementPrompt(actionType: String, direction: String, stronger: Boolean): String {
        return when (actionType.normalizedActionType()) {
            "orbit" -> if (direction.equals("right", ignoreCase = true)) {
                if (stronger) "继续向右绕主体移动" else "方向对了，再向右绕一点"
            } else {
                if (stronger) "继续向左绕主体移动" else "方向对了，再向左绕一点"
            }
            "raisecamera" -> if (stronger) {
                "先把手机整体抬高，再轻轻下压镜头"
            } else {
                "再抬高一点，保持主体别跑掉"
            }
            "lowercamera" -> if (stronger) {
                "先把手机整体放低，不是只把镜头往下压"
            } else {
                "再放低一点，并轻轻上扬镜头"
            }
            "step" -> if (direction.equals("backward", ignoreCase = true)) {
                if (stronger) "继续后退一点" else "再后退半步"
            } else {
                if (stronger) "继续靠近主体" else "再靠近一点"
            }
            else -> "继续改变机位，逼近参考图"
        }
    }

    private fun movementDirectionCorrection(actionType: String, direction: String): String {
        return when (actionType.normalizedActionType()) {
            "orbit" -> if (direction.equals("right", ignoreCase = true)) {
                "方向还不对，再往主体右侧绕"
            } else {
                "方向还不对，再往主体左侧绕"
            }
            "raisecamera" -> "不是只仰头拍，把手机整体抬高一点"
            "lowercamera" -> "不是只低头拍，把手机整体放低一点"
            "step" -> if (direction.equals("backward", ignoreCase = true)) {
                "方向还不对，带着主体再后退一点"
            } else {
                "方向还不对，带着主体再靠近一点"
            }
            else -> "方向还不对，再调整机位"
        }
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

private fun ShotSpec?.parseTargetCenter(): Pair<Float, Float>? {
    val center = this?.targetSubjectCenter ?: return null
    if (center.size < 2) return null
    val x = center[0]
    val y = center[1]
    if (!x.isFinite() || !y.isFinite()) return null
    return x.coerceIn(0.05f, 0.95f) to y.coerceIn(0.05f, 0.95f)
}

private fun ShotSpec?.parseTargetSize(): Float? {
    val size = this?.targetSubjectSize ?: return null
    if (!size.isFinite()) return null
    return size.coerceIn(0.08f, 0.82f)
}
