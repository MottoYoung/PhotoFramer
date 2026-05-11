package com.photoframer.vision

import android.graphics.Bitmap
import android.util.Log
import com.photoframer.arcore.CameraPoseSample
import com.photoframer.data.api.ShotSpec
import com.photoframer.guidance.isViewpointActionType
import com.photoframer.guidance.normalizedActionType
import com.photoframer.inframe.InFrameGuideValidationConfig
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "StepValidator"

/**
 * 步骤验证结果
 */
data class StepValidationResult(
    val isCompleted: Boolean,       // 步骤是否完成
    val progress: Float,            // 完成进度 0-1
    val feedbackText: String,       // 反馈文本（中文）
    val shiftDistance: Float?,      // 平移距离（像素）
    val tx: Float?,                 // X轴平移量（辅助UI指示方向）
    val ty: Float?,                 // Y轴平移量（辅助UI指示方向）
    val scaleFactor: Float?,        // 缩放因子
    val rotationAngle: Float?,      // 旋转角度（度）
    val matchQuality: Float,        // 匹配质量 0-1
    val uiHintX: Float? = null,     // 机位变化步骤的目标偏移提示
    val uiHintY: Float? = null,
    val directionConfidence: Float? = null,
    val subjectConfidence: Float? = null,
    val hasSubject: Boolean? = null,
    val uiSpaceWidth: Float? = null,
    val uiSpaceHeight: Float? = null
)

/**
 * 步骤验证器
 */
class StepValidator(
    targetBitmap: Bitmap,
    private val inFrameGuideConfig: InFrameGuideValidationConfig? = null,
    private val shotSpec: ShotSpec? = null
) {
    private val featureMatcher = FeatureMatcher(FeatureMatcherProfile.REALTIME_ORB)
    private val homographyAnalyzer = HomographyAnalyzer()
    private val targetViewMatcher = FeatureMatcher(FeatureMatcherProfile.REALTIME_ORB)
    private val targetReferenceBitmap: Bitmap
    private var viewChangeSourceMatcher: FeatureMatcher? = null
    private var viewChangeAnalyzer: ViewChangeAnalyzer? = null
    private var viewChangeBaselineStepKey: Int? = null
    private var targetPHash: Long = 0L
    private var cachedCurrentPHash: Long = 0L
    private var pHashFrameCounter: Int = 0
    
    // 阈值设定
    companion object {
        const val VALIDATION_FRAME_WIDTH = 360
        const val SHIFT_THRESHOLD = 25.0       // 平移阈值 (25px on 360px frame ≈ 7% 帧宽)
        const val ZOOM_THRESHOLD = 0.10        // 缩放阈值 (10%，更严格)
        const val LEVEL_ROTATION_THRESHOLD = 2.4 // 水平校正阈值（度）
        const val SCALE_GUIDANCE_THRESHOLD = 0.15 // 触发缩放建议的阈值
        const val ROTATION_THRESHOLD = 5.0     // 旋转阈值 (5度，用于 View-change)
        const val ROLL_THRESHOLD = 3.0         // Roll 水平校正阈值 (3度，用于 Shift)
        const val MIN_MATCH_QUALITY = 6        // 最小匹配点数
        private const val PHASH_COMPUTE_INTERVAL = 3
    }
    
    init {
        // 将目标图片缩放到与分析帧相同的固定宽度坐标系
        // 确保 Scale=1.0 代表完美匹配，消除分辨率差异带来的伪误差
        val scaledTarget = if (targetBitmap.width != VALIDATION_FRAME_WIDTH) {
            ImageConverter.scaleBitmapToWidth(targetBitmap, VALIDATION_FRAME_WIDTH)
        } else {
            targetBitmap
        }
        
        targetReferenceBitmap = scaledTarget
        val sharpenedTarget = sharpenForMatching(scaledTarget)
        featureMatcher.setTargetImage(sharpenedTarget)
        targetViewMatcher.setTargetImage(sharpenedTarget)
        targetPHash = PerceptualHashMatcher.computeHash(targetReferenceBitmap)
        
        Log.d(TAG, "StepValidator 初始化完成 (Target Resized to ${scaledTarget.width}px)")
    }
    
    // 状态保持
    private var lastValidComponents: HomographyComponents? = null
    private var lastValidTime: Long = 0
    private val PERSISTENCE_DURATION = 280L // 丢失目标后短暂保持，避免引导“黏住”
    
    // 平滑滤波器
    private val smoothingFilter = SmoothingFilter(alpha = 0.80f) // 提高跟手性，减少“明明对齐却迟迟不结束”
    private val feedbackDebouncer = FeedbackDebouncer()
    private var motionBaseline: MotionBaseline? = null
    private var motionStableFrames: Int = 0

    private fun uiSpaceWidth(): Float = targetReferenceBitmap.width.toFloat()

    private fun uiSpaceHeight(): Float = targetReferenceBitmap.height.toFloat()

    /**
     * 验证当前帧是否完成指定步骤
     */
    /**
     * 验证当前帧是否完成指定步骤
     */
    fun validateStep(
        currentFrame: Bitmap,
        stepKey: Int,
        actionType: String,
        direction: String,
        currentZoomRatio: Float = 1.0f,
        cameraPoseSample: CameraPoseSample? = null
    ): StepValidationResult {
        Log.d(TAG, "开始验证: actionType=$actionType, direction=$direction, zoom=$currentZoomRatio")
        val normalizedActionType = actionType.normalizedActionType()
        val isInFrameZoomStep = inFrameGuideConfig != null && normalizedActionType == "zoom"
        feedbackDebouncer.minDurationMs = if (normalizedActionType.isViewpointActionType()) 300L else 180L
        val pHashSimilarity = getOrComputePHash(currentFrame)
        
        // 计算单应性矩阵
        val result = featureMatcher.computeHomography(currentFrame)

        try {
            Log.d(TAG, "匹配结果: matchCount=${result.matchCount}, message=${result.message}")

            val currentTime = System.currentTimeMillis()
            var components: HomographyComponents? = null
            var matchQuality = 0f
            var usingFallbackComponents = false

            if (result.homography != null && result.matchCount >= MIN_MATCH_QUALITY) {
                // 分解单应性矩阵
                components = homographyAnalyzer.decompose(result.homography)
                val orbQuality = computeOrbQuality(result.matchCount)
                matchQuality = (orbQuality * 0.6f + pHashSimilarity * 0.4f).coerceIn(0f, 1f)
            } else {
                matchQuality = (pHashSimilarity * 0.55f).coerceIn(0f, 1f)
            }

            // 策略：如果当前帧识别失败，但在有效期内，则使用上一次的有效数据
            if (components == null) {
                if (isInFrameZoomStep) {
                    return validateInFrameZoom(direction, matchQuality, currentZoomRatio)
                }
                if (pHashSimilarity > 0.75f && lastValidComponents != null) {
                    components = lastValidComponents
                    usingFallbackComponents = true
                    matchQuality = (pHashSimilarity * 0.5f).coerceIn(0f, 1f)
                    Log.d(TAG, "ORB 匹配不足，使用 pHash 兜底缓存")
                } else if (currentTime - lastValidTime < PERSISTENCE_DURATION && lastValidComponents != null) {
                    // 使用缓存数据，但稍微降低匹配质量
                    components = lastValidComponents
                    usingFallbackComponents = true
                    matchQuality = maxOf(matchQuality, pHashSimilarity * 0.35f, 0.24f)
                    Log.d(TAG, "处于保持期，使用缓存数据")
                } else {
                    // 确实丢失了
                    return StepValidationResult(
                        isCompleted = false,
                        progress = 0f,
                        feedbackText = "", // 丢失时返回空，UI显示静态引导
                        shiftDistance = null,
                        tx = null,
                        ty = null,
                        scaleFactor = null,
                        rotationAngle = null,
                        matchQuality = 0f,
                        uiSpaceWidth = uiSpaceWidth(),
                        uiSpaceHeight = uiSpaceHeight()
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
            return when (normalizedActionType) {
                "shift" -> {
                    if (inFrameGuideConfig != null) {
                        validateInFrameShift(
                            components = components,
                            direction = direction,
                            matchQuality = matchQuality
                        )
                    } else {
                        validateShift(
                            stepKey = stepKey,
                            actionType = normalizedActionType,
                            components = components,
                            direction = direction,
                            matchQuality = matchQuality,
                            usingFallbackComponents = usingFallbackComponents
                        )
                    }
                }
                "level" -> validateLevel(
                    stepKey = stepKey,
                    components = components,
                    direction = direction,
                    matchQuality = matchQuality,
                    cameraPoseSample = cameraPoseSample
                )
                "zoom" -> {
                    if (inFrameGuideConfig != null) {
                        validateInFrameZoom(direction, matchQuality, currentZoomRatio)
                    } else {
                        validateZoom(
                            stepKey = stepKey,
                            components = components,
                            direction = direction,
                            matchQuality = matchQuality,
                            currentZoom = currentZoomRatio
                        )
                    }
                }
                else -> {
                    if (normalizedActionType.isViewpointActionType()) {
                        validateViewChange(components, actionType, direction, matchQuality)
                    } else {
                        validateShift(
                            stepKey = stepKey,
                            actionType = normalizedActionType,
                            components = components,
                            direction = direction,
                            matchQuality = matchQuality,
                            usingFallbackComponents = usingFallbackComponents
                        )
                    }
                }
            }
        } finally {
            result.release()
        }
    }
    
    /**
     * 验证平移步骤
     * 使用 sigmoid 函数计算进度，确保进度始终在0-1之间且有意义
     */
    private fun validateShift(
        stepKey: Int,
        actionType: String,
        components: HomographyComponents,
        direction: String,
        matchQuality: Float,
        usingFallbackComponents: Boolean = false
    ): StepValidationResult {
        val distance = components.translationDistance
        val tx = components.translationX
        val ty = components.translationY
        val rollAngle = components.rotationAngle  // Roll 角度（水平校正）
        val normalizedDirection = direction.lowercase()
        val isRotationStep = normalizedDirection in setOf("rotate-cw", "rotate-ccw", "cw", "ccw")
        val primaryError = computePrimaryShiftError(
            tx = tx,
            ty = ty,
            direction = direction
        )
        val uiVector = stabilizeShiftVectorForUi(
            tx = tx,
            ty = ty,
            direction = direction
        )

        // Shift 以位置对齐为主，不再被轻微 roll 卡住；
        // 真正需要转正时由 Level / rotate 步骤负责。
        val completionDistanceThreshold = if (isRotationStep) {
            SHIFT_THRESHOLD
        } else {
            SHIFT_THRESHOLD * 1.16
        }
        val completionRollThreshold = if (isRotationStep) {
            ROLL_THRESHOLD * 2
        } else {
            ROLL_THRESHOLD * 4
        }
        val alignedNow = distance < completionDistanceThreshold &&
            abs(rollAngle) < completionRollThreshold

        // 普通 shift 更强调平移进度，rotation step 才把 roll 权重拉高
        val rollProgress = (1.0 / (1.0 + exp((abs(rollAngle) - ROLL_THRESHOLD) / 2.0)))
        val shiftProgress = (1.0 / (1.0 + exp((distance - SHIFT_THRESHOLD) / 15.0)))
        val currentAlignmentProgress = if (isRotationStep) {
            ((rollProgress + shiftProgress) / 2.0).toFloat()
        } else {
            (shiftProgress * 0.82 + rollProgress * 0.18).toFloat()
        }

        val baseline = ensureMotionBaseline(
            stepKey = stepKey,
            actionType = actionType,
            direction = direction,
            components = components
        )
        val primaryImprovement = baseline.initialPrimaryError - primaryError
        val distanceImprovement = baseline.initialDistance - distance
        val improvementScore = computeImprovementScore(
            improvement = maxOf(primaryImprovement, distanceImprovement),
            baselineError = maxOf(baseline.initialPrimaryError, baseline.initialDistance),
            targetThreshold = completionDistanceThreshold
        )
        val movedTowardTarget = primaryImprovement > 8.0 ||
            distanceImprovement > 10.0 ||
            improvementScore >= 0.18f
        val strongAlignmentHold = alignedNow &&
            distance < completionDistanceThreshold * 0.34 &&
            abs(rollAngle) < completionRollThreshold * 0.62
        val nearPerfectHold = alignedNow &&
            distance < completionDistanceThreshold * 0.42 &&
            abs(rollAngle) < completionRollThreshold * 0.72 &&
            matchQuality >= 0.22f
        val alignedFrames = if (alignedNow) motionStableFrames + 1 else 0
        val sustainedHold = alignedNow &&
            alignedFrames >= 4 &&
            (matchQuality >= 0.12f || strongAlignmentHold)
        val requiredStableFrames = when {
            strongAlignmentHold -> 2
            nearPerfectHold && !movedTowardTarget -> 3
            else -> 2
        }
        val rawCompleted = alignedNow && (movedTowardTarget || nearPerfectHold || sustainedHold)
        motionStableFrames = alignedFrames
        val isCompleted = rawCompleted &&
            (motionStableFrames >= requiredStableFrames || sustainedHold)
        val progress = (currentAlignmentProgress * 0.74f + improvementScore * 0.26f).coerceIn(0f, 1f)

        val feedbackText = if (isCompleted) {
            "✓ 位置已对齐"
        } else if (alignedNow && !movedTowardTarget && !nearPerfectHold) {
            if (strongAlignmentHold || sustainedHold) {
                "保持一下，正在确认对齐"
            } else if (isRotationStep) {
                "再轻轻转一点，不要停在原地"
            } else {
                "基本对齐了，再微调一点"
            }
        } else {
            when {
                isRotationStep ->
                    generateRotationFeedback(rollAngle, distance, normalizedDirection)
                // 普通 shift 只有明显歪斜时才打断位置引导
                abs(rollAngle) > ROLL_THRESHOLD * 4 ->
                    if (rollAngle > 0) "手机稍微向右转，把画面放平" else "手机稍微向左转，把画面放平"
                // 常规情况：优先提示步骤主方向
                else -> generateShiftFeedback(tx, ty, distance, direction)
            }
        }
        
        return StepValidationResult(
            isCompleted = isCompleted,
            progress = progress,
            feedbackText = feedbackDebouncer.debounce(feedbackText),
            shiftDistance = distance.toFloat(),
            tx = uiVector.first.toFloat(),
            ty = uiVector.second.toFloat(),
            scaleFactor = components.scaleFactor.toFloat(),
            rotationAngle = rollAngle.toFloat(),
            matchQuality = matchQuality,
            uiSpaceWidth = uiSpaceWidth(),
            uiSpaceHeight = uiSpaceHeight()
        )
    }

    private fun validateLevel(
        stepKey: Int,
        components: HomographyComponents,
        direction: String,
        matchQuality: Float,
        cameraPoseSample: CameraPoseSample? = null
    ): StepValidationResult {
        val rollAngle = components.rotationAngle
        val rollError = abs(rollAngle)
        val translationDistance = components.translationDistance
        val alignedNow = rollError < LEVEL_ROTATION_THRESHOLD
        val rotationProgress = (
            1.0 / (1.0 + exp((rollError - LEVEL_ROTATION_THRESHOLD) / 0.95))
            ).toFloat()
        val deviceMotionScore = computeLevelDeviceMotionScore(direction, cameraPoseSample)

        val baseline = ensureMotionBaseline(
            stepKey = stepKey,
            actionType = "level",
            direction = direction,
            components = components
        )
        val rotationImprovement = baseline.initialPrimaryError - rollError
        val improvementScore = computeImprovementScore(
            improvement = rotationImprovement,
            baselineError = baseline.initialPrimaryError,
            targetThreshold = LEVEL_ROTATION_THRESHOLD
        )
        val movedTowardTarget = rotationImprovement > 0.6 || improvementScore >= 0.16f
        val strongAlignmentHold = alignedNow && rollError < LEVEL_ROTATION_THRESHOLD * 0.55
        val nearPerfectHold = alignedNow && matchQuality >= 0.18f
        val alignedFrames = if (alignedNow) motionStableFrames + 1 else 0
        val sustainedHold = alignedNow &&
            alignedFrames >= 3 &&
            (matchQuality >= 0.10f || strongAlignmentHold)
        val requiredStableFrames = if (strongAlignmentHold) 2 else 3
        val rawCompleted = alignedNow && (movedTowardTarget || nearPerfectHold || sustainedHold)
        motionStableFrames = alignedFrames
        val isCompleted = rawCompleted &&
            (motionStableFrames >= requiredStableFrames || sustainedHold)
        val progress = if (deviceMotionScore != null) {
            (rotationProgress * 0.72f + improvementScore * 0.16f + deviceMotionScore * 0.12f).coerceIn(0f, 1f)
        } else {
            (rotationProgress * 0.82f + improvementScore * 0.18f).coerceIn(0f, 1f)
        }

        val feedbackText = if (isCompleted) {
            "✓ 画面已放平"
        } else if (strongAlignmentHold || sustainedHold) {
            "保持一下，正在确认画面水平"
        } else if (deviceMotionScore != null && deviceMotionScore < 0.18f) {
            generateRotationFeedback(rollAngle, translationDistance, direction.lowercase())
        } else {
            generateRotationFeedback(rollAngle, translationDistance, direction.lowercase())
        }

        return StepValidationResult(
            isCompleted = isCompleted,
            progress = progress,
            feedbackText = feedbackDebouncer.debounce(feedbackText),
            shiftDistance = translationDistance.toFloat(),
            tx = 0f,
            ty = 0f,
            scaleFactor = components.scaleFactor.toFloat(),
            rotationAngle = rollAngle.toFloat(),
            matchQuality = matchQuality,
            directionConfidence = deviceMotionScore,
            uiSpaceWidth = uiSpaceWidth(),
            uiSpaceHeight = uiSpaceHeight()
        )
    }

    /**
     * 画面内构图的平移验证：
     * 使用“原始整图 -> 当前帧”的相似变换，把推荐裁切中心投影到当前帧，
     * 再判断它与画面中心的偏差。
     */
    private fun validateInFrameShift(
        components: HomographyComponents,
        direction: String,
        matchQuality: Float
    ): StepValidationResult {
        val guide = inFrameGuideConfig ?: return validateShift(
            stepKey = -1,
            actionType = "shift",
            components = components,
            direction = direction,
            matchQuality = matchQuality
        )

        val projectedTargetCenter = projectNormalizedPoint(
            xNorm = guide.targetCenterXNormalized,
            yNorm = guide.targetCenterYNormalized,
            components = components
        )

        val frameCenterX = targetReferenceBitmap.width / 2.0
        val frameCenterY = targetReferenceBitmap.height / 2.0
        val offsetX = projectedTargetCenter.first - frameCenterX
        val offsetY = projectedTargetCenter.second - frameCenterY
        val distance = sqrt(offsetX * offsetX + offsetY * offsetY)
        val rollAngle = components.rotationAngle

        val isCompleted = distance < SHIFT_THRESHOLD &&
            abs(rollAngle) < ROLL_THRESHOLD * 2

        val rollProgress = (1.0 / (1.0 + exp((abs(rollAngle) - ROLL_THRESHOLD) / 2.0)))
        val shiftProgress = (1.0 / (1.0 + exp((distance - SHIFT_THRESHOLD) / 15.0)))
        val progress = ((rollProgress + shiftProgress) / 2.0).toFloat()

        val feedbackText = if (isCompleted) {
            "✓ 中心已对齐"
        } else if (abs(rollAngle) > ROLL_THRESHOLD * 2) {
            if (rollAngle > 0) "手机稍微向右转，把画面放平" else "手机稍微向左转，把画面放平"
        } else {
            generateShiftFeedback(offsetX, offsetY, distance, direction)
        }

        return StepValidationResult(
            isCompleted = isCompleted,
            progress = progress,
            feedbackText = feedbackDebouncer.debounce(feedbackText),
            shiftDistance = distance.toFloat(),
            tx = offsetX.toFloat(),
            ty = offsetY.toFloat(),
            scaleFactor = components.scaleFactor.toFloat(),
            rotationAngle = rollAngle.toFloat(),
            matchQuality = matchQuality,
            uiSpaceWidth = uiSpaceWidth(),
            uiSpaceHeight = uiSpaceHeight()
        )
    }
    
    /**
     * 验证缩放步骤
     * feedbackText 参考 direction 参数，确保与 TopGuidanceBar 大字方向一致
     */
    private fun validateZoom(
        stepKey: Int,
        components: HomographyComponents,
        direction: String,
        matchQuality: Float,
        currentZoom: Float
    ): StepValidationResult {
        // scaleFactor 来自“当前帧 vs 目标图”的仿射匹配，当前帧已经包含系统变焦效果。
        // 这里不要再乘 currentZoom，否则普通 AI Zoom 步骤会把同一次缩放重复计算。
        val visualScale = components.scaleFactor.coerceAtLeast(1e-3)
        val scaleError = abs(1.0 - visualScale)
        
        val alignedNow = scaleError < ZOOM_THRESHOLD
        val currentAlignmentProgress = (1.0 / (1.0 + exp((scaleError - ZOOM_THRESHOLD) / 0.1))).toFloat()
        val baseline = ensureMotionBaseline(
            stepKey = stepKey,
            actionType = "zoom",
            direction = direction,
            components = components
        )
        val scaleImprovement = baseline.initialScaleError - scaleError
        val improvementScore = computeImprovementScore(
            improvement = scaleImprovement,
            baselineError = baseline.initialScaleError,
            targetThreshold = ZOOM_THRESHOLD
        )
        val movedTowardTarget = scaleImprovement > 0.035 || improvementScore >= 0.20f
        val strongAlignmentHold = alignedNow &&
            scaleError < ZOOM_THRESHOLD * 0.28
        val nearPerfectHold = alignedNow &&
            scaleError < ZOOM_THRESHOLD * 0.45 &&
            matchQuality >= 0.22f
        val alignedFrames = if (alignedNow) motionStableFrames + 1 else 0
        val sustainedHold = alignedNow &&
            alignedFrames >= 4 &&
            (matchQuality >= 0.12f || strongAlignmentHold)
        val requiredStableFrames = when {
            strongAlignmentHold -> 2
            nearPerfectHold && !movedTowardTarget -> 3
            else -> 2
        }
        val rawCompleted = alignedNow && (movedTowardTarget || nearPerfectHold || sustainedHold)
        motionStableFrames = alignedFrames
        val isCompleted = rawCompleted &&
            (motionStableFrames >= requiredStableFrames || sustainedHold)
        val progress = (currentAlignmentProgress * 0.72f + improvementScore * 0.28f).coerceIn(0f, 1f)
        
        // targetZoom = 让 visualScale 变为 1.0 时，用户应该设置的数码变焦
        val targetZoom = (currentZoom / visualScale).coerceIn(1.0, 8.0)
        
        val feedbackText = if (isCompleted) {
            "✓ 焦距已对齐"
        } else if (alignedNow && !movedTowardTarget && !nearPerfectHold) {
            if (strongAlignmentHold || sustainedHold) {
                "保持一下，正在确认焦距"
            } else if (direction.lowercase() == "in") {
                "继续放大一点，别停在原地"
            } else {
                "继续缩小一点，别停在原地"
            }
        } else {
            // 根据步骤 direction 生成与大字一致的文案
            val needZoomIn = direction.lowercase() == "in"
            when {
                // 步骤要求放大
                needZoomIn && visualScale < 1.0 - ZOOM_THRESHOLD -> 
                    "放大变焦至 %.1fx".format(targetZoom)
                needZoomIn && visualScale > 1.0 + ZOOM_THRESHOLD -> 
                    "已超过目标，缩回至 %.1fx".format(targetZoom)
                needZoomIn -> "再放大一点"
                // 步骤要求缩小
                !needZoomIn && visualScale > 1.0 + ZOOM_THRESHOLD -> 
                    "缩小变焦至 %.1fx".format(targetZoom)
                !needZoomIn && visualScale < 1.0 - ZOOM_THRESHOLD -> 
                    "已超过目标，放大至 %.1fx".format(targetZoom)
                !needZoomIn -> "再缩小一点"
                else -> "微调焦距"
            }
        }
        
        return StepValidationResult(
            isCompleted = isCompleted,
            progress = progress,
            feedbackText = feedbackDebouncer.debounce(feedbackText),
            shiftDistance = components.translationDistance.toFloat(),
            tx = visualScale.toFloat(),  // 传给 UI：当前视觉缩放比（用于绘制缩放圆环）
            ty = null,
            scaleFactor = components.scaleFactor.toFloat(),
            rotationAngle = components.rotationAngle.toFloat(),
            matchQuality = matchQuality,
            uiSpaceWidth = uiSpaceWidth(),
            uiSpaceHeight = uiSpaceHeight()
        )
    }

    /**
     * 画面内构图的缩放验证：
     * 直接比较当前系统变焦与推荐裁切所需的目标变焦，避免裁切框带来的几何耦合。
     */
    private fun validateInFrameZoom(
        direction: String,
        matchQuality: Float,
        currentZoom: Float
    ): StepValidationResult {
        val guide = inFrameGuideConfig ?: return StepValidationResult(
            isCompleted = false,
            progress = 0f,
            feedbackText = "正在准备变焦参考...",
            shiftDistance = null,
            tx = null,
            ty = null,
            scaleFactor = null,
            rotationAngle = null,
            matchQuality = matchQuality
        )

        val targetZoom = guide.targetZoomRatio.coerceIn(1.0f, 8.0f)
        val zoomRatio = (currentZoom / targetZoom).coerceAtLeast(1e-3f)
        val relativeError = abs(1.0f - zoomRatio)

        val isCompleted = relativeError < ZOOM_THRESHOLD
        val progress = (1.0 / (1.0 + exp((relativeError - ZOOM_THRESHOLD) / 0.1))).toFloat()

        val feedbackText = if (isCompleted) {
            "✓ 焦距已对齐"
        } else {
            val needZoomIn = direction.lowercase() == "in"
            when {
                needZoomIn && currentZoom < targetZoom - 0.05f ->
                    "放大变焦至 %.1fx".format(targetZoom)
                needZoomIn && currentZoom > targetZoom + 0.05f ->
                    "已超过目标，缩回至 %.1fx".format(targetZoom)
                needZoomIn -> "再放大一点"
                !needZoomIn && currentZoom > targetZoom + 0.05f ->
                    "缩小变焦至 %.1fx".format(targetZoom)
                !needZoomIn && currentZoom < targetZoom - 0.05f ->
                    "已超过目标，放大至 %.1fx".format(targetZoom)
                else -> "微调焦距"
            }
        }

        return StepValidationResult(
            isCompleted = isCompleted,
            progress = progress,
            feedbackText = feedbackDebouncer.debounce(feedbackText),
            shiftDistance = null,
            tx = zoomRatio,
            ty = null,
            scaleFactor = targetZoom,
            rotationAngle = null,
            matchQuality = matchQuality,
            uiSpaceWidth = uiSpaceWidth(),
            uiSpaceHeight = uiSpaceHeight()
        )
    }
    
    /**
     * 验证视角变换步骤
     * 真正完成判定由 validateViewChangeAsync 中的异步融合逻辑给出
     */
    private fun validateViewChange(
        components: HomographyComponents,
        actionType: String,
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
            progress = progress * 0.5f,  // 初始进度减半，等待主体/位姿融合信号
            feedbackText = initialViewChangeFeedback(actionType, direction),
            shiftDistance = components.translationDistance.toFloat(),
            tx = null, // View-change 不使用矢量UI
            ty = null,
            scaleFactor = components.scaleFactor.toFloat(),
            rotationAngle = rotation.toFloat(),
            matchQuality = matchQuality,
            uiSpaceWidth = uiSpaceWidth(),
            uiSpaceHeight = uiSpaceHeight()
        )
    }
    
    /**
     * 异步验证 View-change 步骤
     * 融合全局特征、透视变化、ARCore 位姿与主体连续性
     */
    fun validateViewChangeAsync(
        currentFrame: Bitmap,
        stepKey: Int,
        actionType: String,
        direction: String,
        cameraPoseSample: CameraPoseSample? = null,
        callback: (StepValidationResult) -> Unit
    ) {
        feedbackDebouncer.minDurationMs = 300L
        val baselineJustInitialized = ensureViewChangeBaseline(stepKey, currentFrame)

        if (baselineJustInitialized) {
            callback(
                StepValidationResult(
                    isCompleted = false,
                    progress = 0.05f,
                    feedbackText = initialViewChangeFeedback(actionType, direction),
                    shiftDistance = null,
                    tx = 0.05f,
                    ty = null,
                    scaleFactor = null,
                    rotationAngle = null,
                    matchQuality = 0f,
                    subjectConfidence = 0f,
                    uiSpaceWidth = currentFrame.width.toFloat(),
                    uiSpaceHeight = currentFrame.height.toFloat()
                )
            )
            return
        }

        val sourceMatcher = viewChangeSourceMatcher
        val analyzer = viewChangeAnalyzer
        if (sourceMatcher == null || analyzer == null) {
            callback(
                StepValidationResult(
                    isCompleted = false,
                    progress = 0f,
                    feedbackText = "正在初始化视角参考...",
                    shiftDistance = null,
                    tx = 0f,
                    ty = null,
                    scaleFactor = null,
                    rotationAngle = null,
                    matchQuality = 0f,
                    subjectConfidence = 0f,
                    uiSpaceWidth = currentFrame.width.toFloat(),
                    uiSpaceHeight = currentFrame.height.toFloat()
                )
            )
            return
        }

        val sourcePerspectiveResult = sourceMatcher.computePerspectiveHomography(currentFrame)
        val targetPerspectiveResult = targetViewMatcher.computePerspectiveHomography(currentFrame)
        val perspectiveDepartureScore: Float
        val targetFeatureScore: Float

        try {
            perspectiveDepartureScore = computePerspectiveDepartureScore(
                result = sourcePerspectiveResult,
                frameWidth = currentFrame.width,
                frameHeight = currentFrame.height
            )
            targetFeatureScore = (targetPerspectiveResult.matchCount.toFloat() / 28f).coerceIn(0f, 1f)
            val sourceFeatureScore = (sourcePerspectiveResult.matchCount.toFloat() / 28f).coerceIn(0f, 1f)
            val pHashSimilarity = getOrComputePHash(currentFrame)

            analyzer.analyze(
                currentFrame = currentFrame,
                actionType = actionType,
                direction = direction,
                perspectiveScore = perspectiveDepartureScore,
                sourceSimilarityScore = sourceFeatureScore,
                targetSimilarityScore = targetFeatureScore,
                pHashSimilarity = pHashSimilarity,
                cameraPoseSample = cameraPoseSample
            ) { result ->
                callback(
                    StepValidationResult(
                        isCompleted = result.isCompleted,
                        progress = result.progress,
                        feedbackText = feedbackDebouncer.debounce(result.feedbackText),
                        shiftDistance = null,
                        tx = null,
                        ty = null,
                        scaleFactor = result.targetAlignmentScore,
                        rotationAngle = null,
                        matchQuality = result.targetSimilarityScore,
                        uiHintX = result.uiHintX,
                        uiHintY = result.uiHintY,
                        directionConfidence = result.directionScore,
                        subjectConfidence = result.subjectConfidence,
                        hasSubject = result.hasObject,
                        uiSpaceWidth = currentFrame.width.toFloat(),
                        uiSpaceHeight = currentFrame.height.toFloat()
                    )
                )
            }
        } finally {
            sourcePerspectiveResult.release()
            targetPerspectiveResult.release()
        }
    }

    private fun ensureViewChangeBaseline(stepKey: Int, currentFrame: Bitmap): Boolean {
        if (viewChangeBaselineStepKey == stepKey && viewChangeSourceMatcher != null && viewChangeAnalyzer != null) {
            return false
        }

        viewChangeBaselineStepKey = stepKey
        viewChangeSourceMatcher?.close()
        viewChangeSourceMatcher = FeatureMatcher(FeatureMatcherProfile.REALTIME_ORB).apply {
            setTargetImage(currentFrame)
        }
        viewChangeAnalyzer?.close()
        viewChangeAnalyzer = ViewChangeAnalyzer(
            sourceBitmap = currentFrame,
            targetBitmap = targetReferenceBitmap,
            targetShotSpec = shotSpec
        )
        return true
    }

    private fun ensureMotionBaseline(
        stepKey: Int,
        actionType: String,
        direction: String,
        components: HomographyComponents
    ): MotionBaseline {
        val normalizedAction = actionType.normalizedActionType()
        val normalizedDirection = direction.lowercase()
        val current = motionBaseline
        if (
            current != null &&
            current.stepKey == stepKey &&
            current.actionType == normalizedAction &&
            current.direction == normalizedDirection
        ) {
            if (current.framesSeen < 3) {
                current.initialDistance = minOf(current.initialDistance, components.translationDistance)
                current.initialPrimaryError = minOf(
                    current.initialPrimaryError,
                    computePrimaryShiftError(
                        tx = components.translationX,
                        ty = components.translationY,
                        direction = direction
                    )
                )
                current.initialScaleError = minOf(
                    current.initialScaleError,
                    abs(1.0 - components.scaleFactor)
                )
                current.framesSeen += 1
            }
            return current
        }

        motionStableFrames = 0
        return MotionBaseline(
            stepKey = stepKey,
            actionType = normalizedAction,
            direction = normalizedDirection,
            initialDistance = components.translationDistance,
            initialPrimaryError = computePrimaryShiftError(
                tx = components.translationX,
                ty = components.translationY,
                direction = direction
            ),
            initialScaleError = abs(1.0 - components.scaleFactor),
            framesSeen = 1
        ).also {
            motionBaseline = it
        }
    }

    private fun computeLevelDeviceMotionScore(
        direction: String,
        cameraPoseSample: CameraPoseSample?
    ): Float? {
        val sample = cameraPoseSample ?: return null
        if (!sample.isTracking) return null

        val normalizedDirection = direction.lowercase()
        val signedRollDelta = when (normalizedDirection) {
            "cw", "rotate-cw" -> sample.rollDeltaDegrees
            "ccw", "rotate-ccw" -> -sample.rollDeltaDegrees
            else -> return null
        }

        return (0.18f + (signedRollDelta / 10f) * 0.82f).coerceIn(0f, 1f)
    }

    private fun computeOrbQuality(matchCount: Int): Float {
        val baseQuality = (matchCount.toFloat() / 50f).coerceIn(0f, 1f)
        return if (matchCount < 10) {
            (baseQuality * 0.6f).coerceIn(0f, 1f)
        } else {
            baseQuality
        }
    }

    private fun getOrComputePHash(currentFrame: Bitmap): Float {
        pHashFrameCounter += 1
        if (pHashFrameCounter % PHASH_COMPUTE_INTERVAL == 1 || cachedCurrentPHash == 0L) {
            cachedCurrentPHash = PerceptualHashMatcher.computeHash(currentFrame)
        }
        return PerceptualHashMatcher.similarity(cachedCurrentPHash, targetPHash)
    }

    private fun sharpenForMatching(bitmap: Bitmap): Bitmap {
        val src = Mat()
        val blurred = Mat()
        val sharpened = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), 3.0)
            Core.addWeighted(src, 1.5, blurred, -0.5, 0.0, sharpened)
            Bitmap.createBitmap(
                bitmap.width,
                bitmap.height,
                bitmap.config ?: Bitmap.Config.ARGB_8888
            ).also { result ->
                Utils.matToBitmap(sharpened, result)
            }
        } catch (_: Exception) {
            bitmap
        } finally {
            src.release()
            blurred.release()
            sharpened.release()
        }
    }

    private fun computePrimaryShiftError(
        tx: Double,
        ty: Double,
        direction: String
    ): Double {
        return when (direction.lowercase()) {
            "cw", "ccw", "rotate-cw", "rotate-ccw" -> 0.0
            "left", "right", "rotate-cw", "rotate-ccw", "cw", "ccw" -> abs(tx)
            "up", "down" -> abs(ty)
            else -> sqrt(tx * tx + ty * ty)
        }
    }

    private fun stabilizeShiftVectorForUi(
        tx: Double,
        ty: Double,
        direction: String
    ): Pair<Double, Double> {
        val deadZone = 8.0
        val uiTx = if (abs(tx) < deadZone) 0.0 else tx
        val uiTy = if (abs(ty) < deadZone) 0.0 else ty
        return uiTx to uiTy
    }

    private fun computeImprovementScore(
        improvement: Double,
        baselineError: Double,
        targetThreshold: Double
    ): Float {
        val effectiveBaseline = (baselineError - targetThreshold).coerceAtLeast(targetThreshold * 0.5)
        if (effectiveBaseline <= 1e-6) return 0f
        return (improvement / effectiveBaseline).toFloat().coerceIn(0f, 1f)
    }

    private fun initialViewChangeFeedback(actionType: String, direction: String): String {
        return when (actionType.normalizedActionType()) {
            "orbit" -> if (direction.equals("right", ignoreCase = true)) {
                "保持主体稳定，开始向右绕拍"
            } else {
                "保持主体稳定，开始向左绕拍"
            }
            "raisecamera" -> "保持主体稳定，开始抬高机位"
            "lowercamera" -> "保持主体稳定，先把手机整体放低"
            "step" -> if (direction.equals("backward", ignoreCase = true)) {
                "保持主体稳定，开始后退一点"
            } else {
                "保持主体稳定，开始靠近主体"
            }
            else -> when (direction.lowercase()) {
                "high-angle" -> "保持主体稳定，开始抬高机位俯拍"
                "low-angle" -> "保持主体稳定，开始降低机位仰拍"
                "side-view-left" -> "保持主体稳定，开始绕主体向左移动"
                "side-view-right" -> "保持主体稳定，开始绕主体向右移动"
                else -> "开始围绕主体改变视角"
            }
        }
    }

    private fun computePerspectiveDepartureScore(
        result: HomographyResult,
        frameWidth: Int,
        frameHeight: Int
    ): Float {
        val homography = result.homography ?: return 0f
        if (homography.rows() != 3 || homography.cols() != 3 || homography.empty()) {
            return 0f
        }

        val h22 = homography.get(2, 2)[0].takeIf { abs(it) > 1e-6 } ?: 1.0
        val h20 = homography.get(2, 0)[0] / h22
        val h21 = homography.get(2, 1)[0] / h22

        val projectiveMagnitude = kotlin.math.sqrt(h20 * h20 + h21 * h21)
        val projectiveScore = (projectiveMagnitude * frameWidth * 12.0).toFloat().coerceIn(0f, 1f)

        val corners = arrayOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(frameWidth.toDouble(), 0.0),
            doubleArrayOf(frameWidth.toDouble(), frameHeight.toDouble()),
            doubleArrayOf(0.0, frameHeight.toDouble())
        ).map { point ->
            projectPoint(homography, point[0], point[1], h22)
        }

        val topWidth = distance(corners[0], corners[1])
        val bottomWidth = distance(corners[3], corners[2])
        val leftHeight = distance(corners[0], corners[3])
        val rightHeight = distance(corners[1], corners[2])

        val horizontalSkew = (abs(topWidth - bottomWidth) / maxOf(topWidth, bottomWidth, 1e-3)).toFloat()
        val verticalSkew = (abs(leftHeight - rightHeight) / maxOf(leftHeight, rightHeight, 1e-3)).toFloat()
        val trapezoidScore = ((horizontalSkew + verticalSkew) * 1.4f).coerceIn(0f, 1f)

        val inlierScore = (result.matchCount.toFloat() / 35f).coerceIn(0f, 1f)

        return (
            projectiveScore * 0.50f +
                trapezoidScore * 0.35f +
                inlierScore * 0.15f
            ).coerceIn(0f, 1f)
    }

    private fun projectPoint(
        homography: org.opencv.core.Mat,
        x: Double,
        y: Double,
        h22: Double
    ): Pair<Double, Double> {
        val h00 = homography.get(0, 0)[0] / h22
        val h01 = homography.get(0, 1)[0] / h22
        val h02 = homography.get(0, 2)[0] / h22
        val h10 = homography.get(1, 0)[0] / h22
        val h11 = homography.get(1, 1)[0] / h22
        val h12 = homography.get(1, 2)[0] / h22
        val h20 = homography.get(2, 0)[0] / h22
        val h21 = homography.get(2, 1)[0] / h22

        val denominator = (h20 * x + h21 * y + 1.0).takeIf { abs(it) > 1e-6 } ?: 1.0
        val px = (h00 * x + h01 * y + h02) / denominator
        val py = (h10 * x + h11 * y + h12) / denominator

        return px to py
    }

    private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun projectNormalizedPoint(
        xNorm: Float,
        yNorm: Float,
        components: HomographyComponents
    ): Pair<Double, Double> {
        val sourceX = xNorm.toDouble() * targetReferenceBitmap.width.toDouble()
        val sourceY = yNorm.toDouble() * targetReferenceBitmap.height.toDouble()
        val radians = Math.toRadians(components.rotationAngle)
        val scale = components.scaleFactor

        val projectedX = scale * cos(radians) * sourceX -
            scale * sin(radians) * sourceY +
            components.translationX
        val projectedY = scale * sin(radians) * sourceX +
            scale * cos(radians) * sourceY +
            components.translationY

        return projectedX to projectedY
    }
    
    /**
     * 生成二维平移反馈
     * 用户只需要把准星带回中心圈，因此反馈直接描述当前二维偏差。
     */
    private fun generateShiftFeedback(tx: Double, ty: Double, distance: Double, direction: String): String {
        val absX = abs(tx)
        val absY = abs(ty)
        
        val distanceHint = when {
            distance > 150 -> " (较远)"
            distance > 80 -> ""
            else -> " (接近)"
        }

        val axisThreshold = 20.0
        val horizontalText = when {
            absX < axisThreshold -> null
            tx > 0 -> "向右"
            else -> "向左"
        }
        val verticalText = when {
            absY < axisThreshold -> null
            ty > 0 -> "向下"
            else -> "向上"
        }

        return when {
            horizontalText != null && verticalText != null ->
                "${horizontalText}${verticalText}移动$distanceHint"
            horizontalText != null -> "${horizontalText}移动$distanceHint"
            verticalText != null -> "${verticalText}移动$distanceHint"
            distance > 25 -> "微调位置$distanceHint"
            else -> "接近目标"
        }
    }

    private fun generateRotationFeedback(
        rollAngle: Double,
        distance: Double,
        direction: String
    ): String {
        return when {
            abs(rollAngle) > ROLL_THRESHOLD * 2 -> {
                if (direction == "rotate-cw" || direction == "cw") {
                    "手机稍微向右转"
                } else {
                    "手机稍微向左转"
                }
            }
            distance > SHIFT_THRESHOLD * 1.4 -> "角度差不多了，再微调位置"
            else -> "继续微调，让画面放平"
        }
    }

    /**
     * 反馈文字防抖器
     * 同一反馈至少保持 minDurationMs 才能被替换，避免文字快速跳变
     */
    private class FeedbackDebouncer(var minDurationMs: Long = 250L) {
        private var lastText: String = ""
        private var lastChangeTime: Long = 0

        fun debounce(newText: String): String {
            val now = System.currentTimeMillis()
            if (newText != lastText && now - lastChangeTime > minDurationMs) {
                lastText = newText
                lastChangeTime = now
            }
            return lastText
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
            val deltaRot = normalizeAngleDelta(input.rotationAngle - last.rotationAngle)
            val newRot = normalizeAngle(last.rotationAngle + alpha * deltaRot)
            
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

        private fun normalizeAngleDelta(angleDegrees: Double): Double {
            var normalized = angleDegrees
            while (normalized > 180.0) normalized -= 360.0
            while (normalized < -180.0) normalized += 360.0
            return normalized
        }

        private fun normalizeAngle(angleDegrees: Double): Double {
            var normalized = angleDegrees
            while (normalized > 180.0) normalized -= 360.0
            while (normalized < -180.0) normalized += 360.0
            return normalized
        }
    }

    private data class MotionBaseline(
        val stepKey: Int,
        val actionType: String,
        val direction: String,
        var initialDistance: Double,
        var initialPrimaryError: Double,
        var initialScaleError: Double,
        var framesSeen: Int = 0
    )

    /**
     * 释放资源
     */
    fun close() {
        featureMatcher.close()
        targetViewMatcher.close()
        viewChangeSourceMatcher?.close()
        viewChangeSourceMatcher = null
        viewChangeAnalyzer?.close()
        viewChangeAnalyzer = null
        motionBaseline = null
        motionStableFrames = 0
        pHashFrameCounter = 0
        cachedCurrentPHash = 0L
    }

    fun resetStableFrames() {
        motionStableFrames = 0
        motionBaseline = null
        pHashFrameCounter = 0
        cachedCurrentPHash = 0L
        viewChangeAnalyzer?.resetStableFrames()
    }
}
