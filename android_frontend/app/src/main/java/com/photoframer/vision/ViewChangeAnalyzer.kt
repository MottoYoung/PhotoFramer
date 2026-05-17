package com.photoframer.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.photoframer.arcore.CameraPoseSample
import com.photoframer.arcore.CameraPoseSource
import com.photoframer.data.api.ShotSpec
import com.photoframer.guidance.canonicalViewDirection
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
    val uiHintY: Float,
    val subjectConfidence: Float
)

private data class SubjectAssessment(
    val hasSubject: Boolean,
    val lockScore: Float,
    val targetAlignmentScore: Float,
    val sourceConsistencyScore: Float,
    val targetOffsetX: Float,
    val targetOffsetY: Float,
    val requiresSubjectGate: Boolean,
    val hasTargetAnchor: Boolean
)

private data class ActionProfile(
    val arrivalThreshold: Float,
    val directionThreshold: Float,
    val subjectThreshold: Float,
    val nearTargetArrivalThreshold: Float,
    val targetSimilarityFloor: Float,
    val targetAlignmentFloor: Float,
    val nearTargetSimilarityFloor: Float,
    val nearTargetAlignmentFloor: Float,
    val nearTargetDirectionFloor: Float,
    val requiredStableFrames: Int
)

/**
 * View-change 分析器（v3）
 *
 * 主信号：
 * 1. 取景器 source -> current 的单应性几何：判断真实画面的绕拍/升降/前后移动趋势
 * 2. ARCore/设备位姿：在可用时辅助确认真实位移，避免只转手腕误判
 * 3. ORB/pHash 到目标图的相似度：判断是否接近候选参考图，但不单独主导方向
 *
 * 安全约束：
 * 4. shot_spec + ML Kit 主体检测：防止主体丢失、切到别的主体，作为闭环安全门而非主评分项
 */
class ViewChangeAnalyzer(
    sourceBitmap: Bitmap,
    targetBitmap: Bitmap,
    private val targetShotSpec: ShotSpec? = null
) {
    private val objectDetector: ObjectDetector
    private val referenceDetector: ObjectDetector
    private var stableCompletionFrames: Int = 0
    private val targetSimHistory: ArrayDeque<Float> = ArrayDeque()

    private var sourceBoundingBox: NormalizedBoundingBox? = null
    private var targetBoundingBox: NormalizedBoundingBox? = null
    private var sourceReferenceDetectionSettled: Boolean = false
    private var targetReferenceDetectionSettled: Boolean = false
    private var targetReferenceDetected: Boolean = false
    private var lastTrackedSubjectBox: NormalizedBoundingBox? = null
    private var lastSubjectLockScore: Float = 0f
    private var consecutiveMissingSubjectFrames: Int = 0
    private val targetSpecCenter = targetShotSpec.parseTargetCenter()
    private val targetSpecSize = targetShotSpec.parseTargetSize()

    companion object {
        private const val HISTORY_CAPACITY = 8
        private const val ARRIVAL_THRESHOLD = 0.52f
        private const val ARRIVAL_THRESHOLD_VERTICAL = 0.40f
        private const val ARRIVAL_THRESHOLD_LOWER_CAMERA = 0.42f
        private const val ARRIVAL_THRESHOLD_ORBIT = 0.46f
        private const val ARRIVAL_THRESHOLD_STEP = 0.45f
        private const val DIRECTION_THRESHOLD = 0.40f
        private const val DIRECTION_THRESHOLD_VERTICAL = 0.28f
        private const val DIRECTION_THRESHOLD_LOWER_CAMERA = 0.20f
        private const val DIRECTION_THRESHOLD_ORBIT = 0.22f
        private const val DIRECTION_THRESHOLD_STEP = 0.22f
        private const val SUBJECT_THRESHOLD = 0.42f
        private const val SUBJECT_THRESHOLD_VERTICAL = 0.36f
        private const val SUBJECT_THRESHOLD_LOWER_CAMERA = 0.32f
        private const val SUBJECT_THRESHOLD_ORBIT = 0.28f
        private const val SUBJECT_THRESHOLD_STEP = 0.24f
        private const val NEAR_TARGET_ARRIVAL_VERTICAL = 0.66f
        private const val NEAR_TARGET_ARRIVAL_LOWER_CAMERA = 0.60f
        private const val NEAR_TARGET_ARRIVAL_ORBIT = 0.88f
        private const val NEAR_TARGET_ARRIVAL_STEP = 0.86f
        private const val NEAR_TARGET_SUBJECT_MARGIN = 0.06f
        private const val NEAR_TARGET_DIRECTION_FLOOR = 0.05f
        private const val NEAR_TARGET_DIRECTION_FLOOR_VERTICAL = 0.01f
        private const val ARCORE_VERTICAL_COMPLETION_FLOOR = 0.38f
        private const val DEVICE_VERTICAL_COMPLETION_FLOOR = 0.46f
        private const val GEOMETRY_COMPLETION_QUALITY_FLOOR = 0.45f
        private const val GEOMETRY_DIRECTION_MARGIN_FLOOR = 0.16f
        private const val VERTICAL_SCENE_ARRIVAL_MARGIN = 0.06f
        private const val TARGET_SIMILARITY_FLOOR_VERTICAL = 0.42f
        private const val TARGET_SIMILARITY_FLOOR_LOWER_CAMERA = 0.40f
        private const val TARGET_SIMILARITY_FLOOR_ORBIT = 0.54f
        private const val TARGET_SIMILARITY_FLOOR_STEP = 0.52f
        private const val TARGET_ALIGNMENT_FLOOR_VERTICAL = 0.32f
        private const val TARGET_ALIGNMENT_FLOOR_LOWER_CAMERA = 0.22f
        private const val TARGET_ALIGNMENT_FLOOR_ORBIT = 0.42f
        private const val TARGET_ALIGNMENT_FLOOR_STEP = 0.40f
        private const val NEAR_TARGET_SIMILARITY_FLOOR_VERTICAL = 0.48f
        private const val NEAR_TARGET_SIMILARITY_FLOOR_LOWER_CAMERA = 0.45f
        private const val NEAR_TARGET_SIMILARITY_FLOOR_ORBIT = 0.62f
        private const val NEAR_TARGET_SIMILARITY_FLOOR_STEP = 0.60f
        private const val NEAR_TARGET_ALIGNMENT_FLOOR_VERTICAL = 0.36f
        private const val NEAR_TARGET_ALIGNMENT_FLOOR_LOWER_CAMERA = 0.26f
        private const val NEAR_TARGET_ALIGNMENT_FLOOR_ORBIT = 0.46f
        private const val NEAR_TARGET_ALIGNMENT_FLOOR_STEP = 0.44f
        private const val STABLE_FRAME_COUNT = 3
        private const val STABLE_FRAME_COUNT_VERTICAL = 2
        private const val STABLE_FRAME_COUNT_LOWER_CAMERA = 3
        private const val STABLE_FRAME_COUNT_MOVING_POSE = 2
        private const val SUBJECT_MISS_GRACE_FRAMES = 4

        private val DEFAULT_ACTION_PROFILE = ActionProfile(
            arrivalThreshold = ARRIVAL_THRESHOLD,
            directionThreshold = DIRECTION_THRESHOLD,
            subjectThreshold = SUBJECT_THRESHOLD,
            nearTargetArrivalThreshold = 1f,
            targetSimilarityFloor = 0f,
            targetAlignmentFloor = 0f,
            nearTargetSimilarityFloor = 0f,
            nearTargetAlignmentFloor = 0f,
            nearTargetDirectionFloor = NEAR_TARGET_DIRECTION_FLOOR,
            requiredStableFrames = STABLE_FRAME_COUNT
        )

        private val ACTION_PROFILES = mapOf(
            "raisecamera" to ActionProfile(
                arrivalThreshold = ARRIVAL_THRESHOLD_VERTICAL,
                directionThreshold = DIRECTION_THRESHOLD_VERTICAL,
                subjectThreshold = SUBJECT_THRESHOLD_VERTICAL,
                nearTargetArrivalThreshold = NEAR_TARGET_ARRIVAL_VERTICAL,
                targetSimilarityFloor = TARGET_SIMILARITY_FLOOR_VERTICAL,
                targetAlignmentFloor = TARGET_ALIGNMENT_FLOOR_VERTICAL,
                nearTargetSimilarityFloor = NEAR_TARGET_SIMILARITY_FLOOR_VERTICAL,
                nearTargetAlignmentFloor = NEAR_TARGET_ALIGNMENT_FLOOR_VERTICAL,
                nearTargetDirectionFloor = NEAR_TARGET_DIRECTION_FLOOR_VERTICAL,
                requiredStableFrames = STABLE_FRAME_COUNT_VERTICAL
            ),
            "lowercamera" to ActionProfile(
                arrivalThreshold = ARRIVAL_THRESHOLD_LOWER_CAMERA,
                directionThreshold = DIRECTION_THRESHOLD_LOWER_CAMERA,
                subjectThreshold = SUBJECT_THRESHOLD_LOWER_CAMERA,
                nearTargetArrivalThreshold = NEAR_TARGET_ARRIVAL_LOWER_CAMERA,
                targetSimilarityFloor = TARGET_SIMILARITY_FLOOR_LOWER_CAMERA,
                targetAlignmentFloor = TARGET_ALIGNMENT_FLOOR_LOWER_CAMERA,
                nearTargetSimilarityFloor = NEAR_TARGET_SIMILARITY_FLOOR_LOWER_CAMERA,
                nearTargetAlignmentFloor = NEAR_TARGET_ALIGNMENT_FLOOR_LOWER_CAMERA,
                nearTargetDirectionFloor = NEAR_TARGET_DIRECTION_FLOOR_VERTICAL,
                requiredStableFrames = STABLE_FRAME_COUNT_LOWER_CAMERA
            ),
            "orbit" to ActionProfile(
                arrivalThreshold = ARRIVAL_THRESHOLD_ORBIT,
                directionThreshold = DIRECTION_THRESHOLD_ORBIT,
                subjectThreshold = SUBJECT_THRESHOLD_ORBIT,
                nearTargetArrivalThreshold = NEAR_TARGET_ARRIVAL_ORBIT,
                targetSimilarityFloor = TARGET_SIMILARITY_FLOOR_ORBIT,
                targetAlignmentFloor = TARGET_ALIGNMENT_FLOOR_ORBIT,
                nearTargetSimilarityFloor = NEAR_TARGET_SIMILARITY_FLOOR_ORBIT,
                nearTargetAlignmentFloor = NEAR_TARGET_ALIGNMENT_FLOOR_ORBIT,
                nearTargetDirectionFloor = NEAR_TARGET_DIRECTION_FLOOR,
                requiredStableFrames = STABLE_FRAME_COUNT_MOVING_POSE
            ),
            "step" to ActionProfile(
                arrivalThreshold = ARRIVAL_THRESHOLD_STEP,
                directionThreshold = DIRECTION_THRESHOLD_STEP,
                subjectThreshold = SUBJECT_THRESHOLD_STEP,
                nearTargetArrivalThreshold = NEAR_TARGET_ARRIVAL_STEP,
                targetSimilarityFloor = TARGET_SIMILARITY_FLOOR_STEP,
                targetAlignmentFloor = TARGET_ALIGNMENT_FLOOR_STEP,
                nearTargetSimilarityFloor = NEAR_TARGET_SIMILARITY_FLOOR_STEP,
                nearTargetAlignmentFloor = NEAR_TARGET_ALIGNMENT_FLOOR_STEP,
                nearTargetDirectionFloor = NEAR_TARGET_DIRECTION_FLOOR,
                requiredStableFrames = STABLE_FRAME_COUNT_MOVING_POSE
            )
        )
    }

    init {
        val streamOptions = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(streamOptions)

        val singleOptions = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        referenceDetector = ObjectDetection.getClient(singleOptions)

        targetBoundingBox = applyShotSpecToBox(null)

        detectReferenceObject(sourceBitmap) { box ->
            sourceBoundingBox = box
            sourceReferenceDetectionSettled = true
            Log.d(TAG, "source subject=$box")
        }
        detectReferenceObject(targetBitmap) { detected ->
            targetReferenceDetected = detected != null
            targetBoundingBox = applyShotSpecToBox(detected)
            targetReferenceDetectionSettled = true
            Log.d(TAG, "target subject=$targetBoundingBox")
        }

        Log.d(TAG, "ViewChangeAnalyzer v3 初始化：全局特征 + 位姿 + 主体锁定")
    }

    fun analyze(
        currentFrame: Bitmap,
        actionType: String,
        direction: String,
        perspectiveScore: Float,
        sourceSimilarityScore: Float,
        targetSimilarityScore: Float,
        pHashSimilarity: Float,
        viewGeometry: ViewChangeGeometry = ViewChangeGeometry.Empty,
        cameraPoseSample: CameraPoseSample?,
        callback: (ViewChangeAnalysisResult) -> Unit
    ) {
        val normalizedActionType = actionType.normalizedActionType()
        val canonicalDirection = direction.canonicalViewDirection()
        val isVerticalCameraAction = normalizedActionType in setOf("raisecamera", "lowercamera")
        val isLowerCameraAction = normalizedActionType == "lowercamera"
        val isOrbitAction = normalizedActionType == "orbit"
        val isStepAction = normalizedActionType == "step"
        val isMovingPoseAction = isOrbitAction || isStepAction
        val profile = actionProfile(normalizedActionType)
        val blendedTargetSimilarity = blendTargetSimilarity(targetSimilarityScore, pHashSimilarity)

        targetSimHistory.addLast(blendedTargetSimilarity)
        while (targetSimHistory.size > HISTORY_CAPACITY) targetSimHistory.removeFirst()

        val targetGainScore = computeTargetGainScore(
            sourceSimilarityScore = sourceSimilarityScore,
            targetSimilarityScore = blendedTargetSimilarity,
            perspectiveScore = perspectiveScore
        )
        val geometryActionScore = computeGeometryActionScore(
            normalizedActionType = normalizedActionType,
            direction = canonicalDirection,
            viewGeometry = viewGeometry
        )
        val geometryOppositeScore = computeOppositeGeometryActionScore(
            normalizedActionType = normalizedActionType,
            direction = canonicalDirection,
            viewGeometry = viewGeometry
        )
        val geometryDirectionMargin = geometryActionScore - geometryOppositeScore
        val baseArrivalScore = computeBaseArrivalScore(
            normalizedActionType = normalizedActionType,
            targetSimilarityScore = blendedTargetSimilarity,
            targetGainScore = targetGainScore,
            perspectiveScore = perspectiveScore,
            geometryActionScore = geometryActionScore
        )

        val poseScore = computePoseDirectionScore(actionType, canonicalDirection, cameraPoseSample)
        val temporalScore = computeTemporalDirectionScore()
        val sensorDirectionScore = if (poseScore != null) {
            if (isVerticalCameraAction) {
                max(
                    temporalScore,
                    (poseScore * 0.45f + temporalScore * 0.55f).coerceIn(0f, 1f)
                )
            } else {
                (poseScore * 0.75f + temporalScore * 0.25f).coerceIn(0f, 1f)
            }
        } else {
            temporalScore
        }
        val directionScore = blendGeometryDirectionScore(
            geometryScore = geometryActionScore,
            oppositeGeometryScore = geometryOppositeScore,
            sensorScore = sensorDirectionScore,
            temporalScore = temporalScore,
            viewGeometry = viewGeometry
        )
        val stepVisualDirectionSatisfied = isStepAction &&
            poseScore == null &&
            targetGainScore >= 0.36f &&
            blendedTargetSimilarity >= 0.52f &&
            baseArrivalScore >= (profile.arrivalThreshold - 0.05f)

        val wristOnlyMotion = isLikelyWristOnlyMotion(actionType, cameraPoseSample)

        val image = InputImage.fromBitmap(currentFrame, 0)
        objectDetector.process(image)
            .addOnSuccessListener { objects ->
                val currentBox = selectCurrentSubjectBox(
                    objects = objects,
                    frameWidth = currentFrame.width,
                    frameHeight = currentFrame.height,
                    actionType = actionType,
                    direction = canonicalDirection,
                    baseArrivalScore = baseArrivalScore
                )

                if (currentBox == null) {
                    if (canUseSceneOnlyValidation()) {
                        val sceneResult = validateSceneOnlyViewChange(
                            normalizedActionType = normalizedActionType,
                            actionType = actionType,
                            direction = canonicalDirection,
                            profile = profile,
                            baseArrivalScore = baseArrivalScore,
                            blendedTargetSimilarity = blendedTargetSimilarity,
                            targetGainScore = targetGainScore,
                            directionScore = directionScore,
                            poseScore = poseScore,
                            stepVisualDirectionSatisfied = stepVisualDirectionSatisfied,
                            wristOnlyMotion = wristOnlyMotion,
                            isMovingPoseAction = isMovingPoseAction,
                            isVerticalCameraAction = isVerticalCameraAction,
                            viewGeometry = viewGeometry,
                            geometryActionScore = geometryActionScore,
                            geometryDirectionMargin = geometryDirectionMargin,
                            cameraPoseSample = cameraPoseSample
                        )
                        callback(sceneResult)
                        return@addOnSuccessListener
                    }

                    consecutiveMissingSubjectFrames += 1
                    val hasRecentSubject =
                        lastTrackedSubjectBox != null &&
                            consecutiveMissingSubjectFrames <= SUBJECT_MISS_GRACE_FRAMES
                    stableCompletionFrames = if (hasRecentSubject) {
                        (stableCompletionFrames - 1).coerceAtLeast(0)
                    } else {
                        0
                    }
                    val reducedArrival = if (hasRecentSubject) {
                        (baseArrivalScore * 0.72f + lastSubjectLockScore * 0.10f).coerceIn(0f, 0.58f)
                    } else {
                        (baseArrivalScore * 0.48f).coerceIn(0f, 0.36f)
                    }
                    val uiHint = computeUiHint(
                        actionType = actionType,
                        direction = canonicalDirection,
                        progress = reducedArrival,
                        subjectAssessment = null
                    )
                    callback(
                        ViewChangeAnalysisResult(
                            isCompleted = false,
                            progress = reducedArrival,
                            feedbackText = if (hasRecentSubject) {
                                "保持当前移动方向，等主体识别稳定一下"
                            } else if (baseArrivalScore > 0.34f) {
                                "主体快丢了，先把主体带回画面再继续"
                            } else {
                                "先稳住主体，再开始改变机位"
                            },
                            hasObject = false,
                            targetAlignmentScore = reducedArrival,
                            directionScore = directionScore,
                            targetSimilarityScore = blendedTargetSimilarity,
                            uiHintX = uiHint.first,
                            uiHintY = uiHint.second,
                            subjectConfidence = 0f
                        )
                    )
                    return@addOnSuccessListener
                }

                consecutiveMissingSubjectFrames = 0
                val subjectAssessment = assessSubject(
                    currentBox = currentBox,
                    actionType = actionType,
                    direction = canonicalDirection,
                    baseArrivalScore = baseArrivalScore
                )
                lastTrackedSubjectBox = currentBox
                lastSubjectLockScore = subjectAssessment.lockScore
                val adjustedArrival = if (subjectAssessment.hasTargetAnchor) {
                    if (isOrbitAction) {
                        (baseArrivalScore * 0.86f + subjectAssessment.lockScore * 0.14f).coerceIn(0f, 1f)
                    } else {
                        (baseArrivalScore * 0.76f + subjectAssessment.lockScore * 0.24f).coerceIn(0f, 1f)
                    }
                } else {
                    if (isOrbitAction) {
                        (baseArrivalScore * 0.90f + subjectAssessment.lockScore * 0.10f).coerceIn(0f, 1f)
                    } else {
                        (baseArrivalScore * 0.84f + subjectAssessment.lockScore * 0.16f).coerceIn(0f, 1f)
                    }
                }
                val subjectGateScore = if (isMovingPoseAction) {
                    softenedMovingPoseSubjectScore(subjectAssessment)
                } else {
                    subjectAssessment.lockScore
                }
                val nearTargetSubjectThreshold =
                    (profile.subjectThreshold - NEAR_TARGET_SUBJECT_MARGIN).coerceAtLeast(0.18f)
                val requiresStrictSubjectGate = if (isMovingPoseAction || isVerticalCameraAction) {
                    targetReferenceDetected
                } else {
                    subjectAssessment.requiresSubjectGate
                }
                val movingPoseVisualReady = isMovingPoseAction &&
                    blendedTargetSimilarity >= profile.nearTargetSimilarityFloor &&
                    adjustedArrival >= profile.arrivalThreshold &&
                    directionScore >= profile.directionThreshold &&
                    subjectGateScore >= profile.subjectThreshold
                val geometryDirectionReliable = hasReliableGeometryDirection(
                    viewGeometry = viewGeometry,
                    geometryActionScore = geometryActionScore,
                    geometryDirectionMargin = geometryDirectionMargin,
                    profile = profile
                ) &&
                    adjustedArrival >= (profile.arrivalThreshold - 0.04f) &&
                    directionScore >= profile.directionThreshold
                val targetResidualReady = blendedTargetSimilarity >= profile.targetSimilarityFloor
                val geometryAssistedResidualReady = geometryDirectionReliable &&
                    blendedTargetSimilarity >= profile.targetSimilarityFloor &&
                    targetGainScore >= 0.24f
                val residualReady = blendedTargetSimilarity >= profile.targetSimilarityFloor &&
                    (
                        !subjectAssessment.hasTargetAnchor ||
                            subjectAssessment.targetAlignmentScore >= profile.targetAlignmentFloor ||
                            movingPoseVisualReady ||
                            geometryAssistedResidualReady
                        )
                val effectiveDirectionThreshold = if (stepVisualDirectionSatisfied) {
                    0.12f
                } else {
                    profile.directionThreshold
                }
                val effectiveNearTargetDirectionFloor = if (stepVisualDirectionSatisfied) {
                    0.02f
                } else {
                    profile.nearTargetDirectionFloor
                }
                val orbitVisualDirectionSatisfied = isOrbitAction &&
                    targetGainScore >= 0.34f &&
                    blendedTargetSimilarity >= (profile.targetSimilarityFloor + 0.04f) &&
                    adjustedArrival >= (profile.arrivalThreshold + 0.08f)
                val normalDirectionSatisfied =
                    directionScore >= effectiveDirectionThreshold ||
                        stepVisualDirectionSatisfied ||
                        orbitVisualDirectionSatisfied
                val nearTargetSatisfied = (isMovingPoseAction || isVerticalCameraAction) &&
                    adjustedArrival >= profile.nearTargetArrivalThreshold &&
                    blendedTargetSimilarity >= profile.nearTargetSimilarityFloor &&
                    (
                        !subjectAssessment.hasTargetAnchor ||
                            subjectAssessment.targetAlignmentScore >= profile.nearTargetAlignmentFloor
                        ) &&
                    subjectGateScore >= nearTargetSubjectThreshold &&
                    (
                        directionScore >= effectiveNearTargetDirectionFloor ||
                            stepVisualDirectionSatisfied ||
                            orbitVisualDirectionSatisfied
                        )
                val directionSatisfied = normalDirectionSatisfied || nearTargetSatisfied
                val verticalPoseSatisfied = !isVerticalCameraAction ||
                    isVerticalPoseCompletionSatisfied(
                        poseScore = poseScore,
                        sample = cameraPoseSample,
                        viewGeometry = viewGeometry,
                        geometryActionScore = geometryActionScore,
                        geometryDirectionMargin = geometryDirectionMargin
                    )
                val verticalSceneCompleted = isVerticalCameraAction &&
                    verticalPoseSatisfied &&
                    adjustedArrival >= (profile.arrivalThreshold - VERTICAL_SCENE_ARRIVAL_MARGIN) &&
                    targetResidualReady &&
                    directionSatisfied &&
                    !wristOnlyMotion &&
                    subjectGateScore >= nearTargetSubjectThreshold

                val rawCompleted = if (isVerticalCameraAction) {
                    verticalSceneCompleted
                } else {
                    adjustedArrival >= profile.arrivalThreshold &&
                        residualReady &&
                        directionSatisfied &&
                        !wristOnlyMotion &&
                        (!requiresStrictSubjectGate || subjectGateScore >= profile.subjectThreshold)
                }
                stableCompletionFrames = if (rawCompleted) {
                    stableCompletionFrames + 1
                } else {
                    0
                }
                val completed = stableCompletionFrames >= profile.requiredStableFrames
                val uiProgress = computeDisplayedArrivalProgress(
                    normalizedActionType = normalizedActionType,
                    baseArrivalScore = baseArrivalScore,
                    adjustedArrival = adjustedArrival,
                    targetSimilarityScore = blendedTargetSimilarity,
                    directionScore = directionScore,
                    poseScore = poseScore,
                    nearTargetSatisfied = nearTargetSatisfied,
                    isCompleted = completed
                )

                val uiHint = computeUiHint(
                    actionType = actionType,
                    direction = canonicalDirection,
                    progress = uiProgress,
                    subjectAssessment = subjectAssessment
                )

                Log.d(
                    TAG,
                    "action=%s arrival=%.2f->%.2f ui=%.2f dir=%.2f geom=%.2f opp=%.2f margin=%.2f gq=%.2f shift=(%.3f,%.3f) scale=%.2f persp=(%.3f,%.3f) proj=(%.3f,%.3f) targetSim=%.2f targetAlign=%.2f residual=%s subject=%.2f gate=%.2f strict=%s nearTarget=%s raw=%s stable=%d/%d".format(
                        normalizedActionType,
                        baseArrivalScore,
                        adjustedArrival,
                        uiProgress,
                        directionScore,
                        geometryActionScore,
                        geometryOppositeScore,
                        geometryDirectionMargin,
                        viewGeometry.quality,
                        viewGeometry.shiftXNorm,
                        viewGeometry.shiftYNorm,
                        viewGeometry.scaleRatio,
                        viewGeometry.horizontalPerspective,
                        viewGeometry.verticalPerspective,
                        viewGeometry.projectiveX,
                        viewGeometry.projectiveY,
                        blendedTargetSimilarity,
                        subjectAssessment.targetAlignmentScore,
                        residualReady,
                        subjectAssessment.lockScore,
                        subjectGateScore,
                        requiresStrictSubjectGate,
                        nearTargetSatisfied,
                        rawCompleted,
                        stableCompletionFrames,
                        profile.requiredStableFrames
                    )
                )

                callback(
                    ViewChangeAnalysisResult(
                        isCompleted = completed,
                        progress = uiProgress,
                        feedbackText = generateFeedback(
                            actionType = actionType,
                            direction = canonicalDirection,
                            targetSimilarityScore = blendedTargetSimilarity,
                            targetGainScore = targetGainScore,
                            arrivalScore = adjustedArrival,
                            directionScore = directionScore,
                            cameraPoseSample = cameraPoseSample,
                            subjectAssessment = subjectAssessment,
                            nearTargetSatisfied = nearTargetSatisfied,
                            isCompleted = completed
                        ),
                        hasObject = true,
                        targetAlignmentScore = adjustedArrival,
                        directionScore = directionScore,
                        targetSimilarityScore = blendedTargetSimilarity,
                        uiHintX = uiHint.first,
                        uiHintY = uiHint.second,
                        subjectConfidence = subjectAssessment.lockScore
                    )
                )
            }
            .addOnFailureListener { error ->
                consecutiveMissingSubjectFrames += 1
                stableCompletionFrames = if (consecutiveMissingSubjectFrames <= SUBJECT_MISS_GRACE_FRAMES) {
                    (stableCompletionFrames - 1).coerceAtLeast(0)
                } else {
                    0
                }
                Log.e(TAG, "主体检测失败: ${error.message}")
                val reducedArrival = (baseArrivalScore * 0.42f).coerceIn(0f, 0.32f)
                val uiHint = computeUiHint(
                    actionType = actionType,
                    direction = canonicalDirection,
                    progress = reducedArrival,
                    subjectAssessment = null
                )
                callback(
                    ViewChangeAnalysisResult(
                        isCompleted = false,
                        progress = reducedArrival,
                        feedbackText = "正在重新识别主体，先稳住手机",
                        hasObject = false,
                        targetAlignmentScore = reducedArrival,
                        directionScore = directionScore,
                        targetSimilarityScore = blendedTargetSimilarity,
                        uiHintX = uiHint.first,
                        uiHintY = uiHint.second,
                        subjectConfidence = 0f
                    )
                )
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

    private fun computeGeometryActionScore(
        normalizedActionType: String,
        direction: String,
        viewGeometry: ViewChangeGeometry
    ): Float {
        return when (normalizedActionType) {
            "orbit" -> if (direction == "right") {
                viewGeometry.orbitRightScore
            } else {
                viewGeometry.orbitLeftScore
            }
            "raisecamera" -> viewGeometry.raiseScore
            "lowercamera" -> viewGeometry.lowerScore
            "step" -> if (direction == "backward") {
                viewGeometry.backwardScore
            } else {
                viewGeometry.forwardScore
            }
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    private fun computeOppositeGeometryActionScore(
        normalizedActionType: String,
        direction: String,
        viewGeometry: ViewChangeGeometry
    ): Float {
        return when (normalizedActionType) {
            "orbit" -> if (direction == "right") {
                viewGeometry.orbitLeftScore
            } else {
                viewGeometry.orbitRightScore
            }
            "raisecamera" -> viewGeometry.lowerScore
            "lowercamera" -> viewGeometry.raiseScore
            "step" -> if (direction == "backward") {
                viewGeometry.forwardScore
            } else {
                viewGeometry.backwardScore
            }
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    private fun blendGeometryDirectionScore(
        geometryScore: Float,
        oppositeGeometryScore: Float,
        sensorScore: Float,
        temporalScore: Float,
        viewGeometry: ViewChangeGeometry
    ): Float {
        if (viewGeometry.quality <= 0.08f) {
            return sensorScore
        }
        val signedGeometryScore = (geometryScore - oppositeGeometryScore * 0.70f).coerceIn(0f, 1f)
        val geometryWeight = (0.45f + viewGeometry.quality * 0.35f).coerceIn(0.45f, 0.80f)
        val blended = signedGeometryScore * geometryWeight +
            sensorScore * (1f - geometryWeight) * 0.75f +
            temporalScore * (1f - geometryWeight) * 0.25f
        return blended.coerceIn(0f, 1f)
    }

    private fun canUseSceneOnlyValidation(): Boolean {
        return sourceReferenceDetectionSettled &&
            targetReferenceDetectionSettled &&
            targetSpecCenter == null &&
            targetSpecSize == null &&
            lastTrackedSubjectBox == null &&
            (
                !targetReferenceDetected ||
                    targetBoundingBox?.isWeakSceneProxyBox() == true
                )
    }

    private fun validateSceneOnlyViewChange(
        normalizedActionType: String,
        actionType: String,
        direction: String,
        profile: ActionProfile,
        baseArrivalScore: Float,
        blendedTargetSimilarity: Float,
        targetGainScore: Float,
        directionScore: Float,
        poseScore: Float?,
        stepVisualDirectionSatisfied: Boolean,
        wristOnlyMotion: Boolean,
        isMovingPoseAction: Boolean,
        isVerticalCameraAction: Boolean,
        viewGeometry: ViewChangeGeometry,
        geometryActionScore: Float,
        geometryDirectionMargin: Float,
        cameraPoseSample: CameraPoseSample?
    ): ViewChangeAnalysisResult {
        consecutiveMissingSubjectFrames = 0
        val sceneArrival = baseArrivalScore
        val sceneResidualReady = blendedTargetSimilarity >= profile.targetSimilarityFloor
        val orbitVisualDirectionSatisfied = normalizedActionType == "orbit" &&
            targetGainScore >= 0.34f &&
            blendedTargetSimilarity >= (profile.targetSimilarityFloor + 0.04f) &&
            sceneArrival >= (profile.arrivalThreshold + 0.08f)
        val directionSatisfied = directionScore >= profile.directionThreshold ||
            stepVisualDirectionSatisfied ||
            orbitVisualDirectionSatisfied
        val nearTargetSatisfied = (isMovingPoseAction || isVerticalCameraAction) &&
            sceneArrival >= profile.nearTargetArrivalThreshold &&
            blendedTargetSimilarity >= profile.nearTargetSimilarityFloor &&
            (
                directionScore >= profile.nearTargetDirectionFloor ||
                    stepVisualDirectionSatisfied ||
                    orbitVisualDirectionSatisfied
                )
        val geometryDirectionReliable = hasReliableGeometryDirection(
            viewGeometry = viewGeometry,
            geometryActionScore = geometryActionScore,
            geometryDirectionMargin = geometryDirectionMargin,
            profile = profile
        )
        val targetReady = sceneResidualReady &&
            (
                targetGainScore >= 0.24f ||
                    blendedTargetSimilarity >= profile.nearTargetSimilarityFloor
                )
        val rawCompleted = sceneArrival >= profile.arrivalThreshold &&
            targetReady &&
            geometryDirectionReliable &&
            (directionSatisfied || nearTargetSatisfied) &&
            (
                !isVerticalCameraAction ||
                    isVerticalPoseCompletionSatisfied(
                        poseScore = poseScore,
                        sample = cameraPoseSample,
                        viewGeometry = viewGeometry,
                        geometryActionScore = geometryActionScore,
                        geometryDirectionMargin = geometryDirectionMargin
                    )
                ) &&
            !wristOnlyMotion
        val effectiveRawCompleted = if (isVerticalCameraAction) {
            sceneArrival >= (profile.arrivalThreshold - VERTICAL_SCENE_ARRIVAL_MARGIN) &&
                targetReady &&
                geometryDirectionReliable &&
                (directionSatisfied || nearTargetSatisfied) &&
                isVerticalPoseCompletionSatisfied(
                    poseScore = poseScore,
                    sample = cameraPoseSample,
                    viewGeometry = viewGeometry,
                    geometryActionScore = geometryActionScore,
                    geometryDirectionMargin = geometryDirectionMargin
                ) &&
                !wristOnlyMotion
        } else {
            rawCompleted
        }

        stableCompletionFrames = if (effectiveRawCompleted) {
            stableCompletionFrames + 1
        } else {
            0
        }
        val completed = stableCompletionFrames >= profile.requiredStableFrames
        val uiProgress = computeDisplayedArrivalProgress(
            normalizedActionType = normalizedActionType,
            baseArrivalScore = baseArrivalScore,
            adjustedArrival = sceneArrival,
            targetSimilarityScore = blendedTargetSimilarity,
            directionScore = directionScore,
            poseScore = poseScore,
            nearTargetSatisfied = nearTargetSatisfied,
            isCompleted = completed
        )
        val uiHint = computeUiHint(
            actionType = actionType,
            direction = direction,
            progress = uiProgress,
            subjectAssessment = null
        )
        val feedback = generateSceneOnlyFeedback(
            actionType = actionType,
            direction = direction,
            targetGainScore = targetGainScore,
            directionScore = directionScore,
            nearTargetSatisfied = nearTargetSatisfied,
            isCompleted = completed,
            cameraPoseSample = cameraPoseSample
        )

        Log.d(
            TAG,
            "scene-only action=%s arrival=%.2f ui=%.2f dir=%.2f geom=%.2f margin=%.2f gq=%.2f shift=(%.3f,%.3f) scale=%.2f persp=(%.3f,%.3f) proj=(%.3f,%.3f) nearTarget=%s stable=%d/%d".format(
                normalizedActionType,
                sceneArrival,
                uiProgress,
                directionScore,
                geometryActionScore,
                geometryDirectionMargin,
                viewGeometry.quality,
                viewGeometry.shiftXNorm,
                viewGeometry.shiftYNorm,
                viewGeometry.scaleRatio,
                viewGeometry.horizontalPerspective,
                viewGeometry.verticalPerspective,
                viewGeometry.projectiveX,
                viewGeometry.projectiveY,
                nearTargetSatisfied,
                stableCompletionFrames,
                profile.requiredStableFrames
            )
        )

        return ViewChangeAnalysisResult(
            isCompleted = completed,
            progress = uiProgress,
            feedbackText = feedback,
            hasObject = false,
            targetAlignmentScore = sceneArrival,
            directionScore = directionScore,
            targetSimilarityScore = blendedTargetSimilarity,
            uiHintX = uiHint.first,
            uiHintY = uiHint.second,
            subjectConfidence = 0f
        )
    }

    private fun actionProfile(normalizedActionType: String): ActionProfile {
        return ACTION_PROFILES[normalizedActionType] ?: DEFAULT_ACTION_PROFILE
    }

    private fun hasReliableGeometryDirection(
        viewGeometry: ViewChangeGeometry,
        geometryActionScore: Float,
        geometryDirectionMargin: Float,
        profile: ActionProfile
    ): Boolean {
        return viewGeometry.quality >= GEOMETRY_COMPLETION_QUALITY_FLOOR &&
            geometryActionScore >= profile.directionThreshold &&
            geometryDirectionMargin >= GEOMETRY_DIRECTION_MARGIN_FLOOR
    }

    private fun blendTargetSimilarity(
        targetSimilarityScore: Float,
        pHashSimilarity: Float
    ): Float {
        return (targetSimilarityScore * 0.6f + pHashSimilarity * 0.4f).coerceIn(0f, 1f)
    }

    private fun computeBaseArrivalScore(
        normalizedActionType: String,
        targetSimilarityScore: Float,
        targetGainScore: Float,
        perspectiveScore: Float,
        geometryActionScore: Float
    ): Float {
        return when (normalizedActionType) {
            "raisecamera" -> (
                geometryActionScore * 0.34f +
                    perspectiveScore * 0.18f +
                    targetGainScore * 0.26f +
                    targetSimilarityScore * 0.22f
                ).coerceIn(0f, 1f)
            "lowercamera" -> (
                geometryActionScore * 0.34f +
                    perspectiveScore * 0.18f +
                    targetGainScore * 0.26f +
                    targetSimilarityScore * 0.22f
                ).coerceIn(0f, 1f)
            "orbit" -> (
                geometryActionScore * 0.36f +
                    perspectiveScore * 0.16f +
                    targetGainScore * 0.26f +
                    targetSimilarityScore * 0.22f
                ).coerceIn(0f, 1f)
            "step" -> (
                geometryActionScore * 0.34f +
                    targetGainScore * 0.30f +
                    targetSimilarityScore * 0.26f +
                    perspectiveScore * 0.10f
                ).coerceIn(0f, 1f)
            else -> (
                targetSimilarityScore * 0.55f +
                    targetGainScore * 0.30f +
                    perspectiveScore * 0.15f
                ).coerceIn(0f, 1f)
        }
    }

    private fun computeTemporalDirectionScore(): Float {
        if (targetSimHistory.size < 4) return 0.22f

        val samples = targetSimHistory.toList()
        val windowSize = minOf(3, samples.size / 2)
        val earlyMean = samples.take(windowSize).average().toFloat()
        val recentMean = samples.takeLast(windowSize).average().toFloat()

        val trend = recentMean - earlyMean
        return (0.5f + trend / 0.20f).coerceIn(0f, 1f)
    }

    private fun computeDisplayedArrivalProgress(
        normalizedActionType: String,
        baseArrivalScore: Float,
        adjustedArrival: Float,
        targetSimilarityScore: Float,
        directionScore: Float,
        poseScore: Float?,
        nearTargetSatisfied: Boolean,
        isCompleted: Boolean
    ): Float {
        if (isCompleted) return 1f

        return when (normalizedActionType) {
            "orbit" -> {
                val poseAwareScore = if (poseScore != null) {
                    (baseArrivalScore * 0.50f +
                        adjustedArrival * 0.22f +
                        targetSimilarityScore * 0.12f +
                        poseScore * 0.16f).coerceIn(0f, 1f)
                } else {
                    (baseArrivalScore * 0.68f +
                        adjustedArrival * 0.22f +
                        targetSimilarityScore * 0.10f).coerceIn(0f, 1f)
                }
                val normalized = ((poseAwareScore - 0.12f) / (NEAR_TARGET_ARRIVAL_ORBIT - 0.12f))
                    .coerceIn(0f, 1f)
                val directionBoost = (directionScore * 0.08f).coerceIn(0f, 0.08f)
                val progress = max(normalized, adjustedArrival * 0.92f) + directionBoost
                if (nearTargetSatisfied) {
                    progress.coerceAtLeast(0.94f).coerceIn(0f, 0.99f)
                } else {
                    progress.coerceIn(0f, 0.98f)
                }
            }
            else -> adjustedArrival
        }
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

    private fun computeVerticalPoseScore(
        sample: CameraPoseSample,
        shouldRaise: Boolean
    ): Float {
        if (!sample.isTracking) {
            return 0f
        }

        if (sample.source != CameraPoseSource.ARCORE) {
            val signedPitchDelta = if (shouldRaise) {
                -sample.pitchDeltaDegrees
            } else {
                sample.pitchDeltaDegrees
            }
            return (0.12f + (signedPitchDelta / 14f) * 0.48f).coerceIn(0f, 0.55f)
        }

        val verticalProgress = if (shouldRaise) {
            sample.verticalMeters / 0.07f
        } else {
            -sample.verticalMeters / 0.06f
        }

        return (0.18f + verticalProgress * 0.82f).coerceIn(0f, 1f)
    }

    private fun isVerticalPoseCompletionSatisfied(
        poseScore: Float?,
        sample: CameraPoseSample?,
        viewGeometry: ViewChangeGeometry,
        geometryActionScore: Float,
        geometryDirectionMargin: Float
    ): Boolean {
        val geometrySatisfied = viewGeometry.quality >= GEOMETRY_COMPLETION_QUALITY_FLOOR &&
            geometryActionScore >= DIRECTION_THRESHOLD_LOWER_CAMERA &&
            geometryDirectionMargin >= GEOMETRY_DIRECTION_MARGIN_FLOOR
        val score = poseScore
        val poseSatisfied = when {
            score == null -> false
            sample?.source == CameraPoseSource.ARCORE && sample.isTracking -> {
                score >= ARCORE_VERTICAL_COMPLETION_FLOOR
            }
            else -> score >= DEVICE_VERTICAL_COMPLETION_FLOOR
        }
        return poseSatisfied || geometrySatisfied
    }

    private fun computeStepPoseScore(
        sample: CameraPoseSample,
        direction: String
    ): Float? {
        if (sample.source != CameraPoseSource.ARCORE || !sample.isTracking) {
            return null
        }

        val forwardProgress = if (direction.canonicalViewDirection() == "backward") {
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
        val moveRight = direction.canonicalViewDirection() == "right"
        // When users orbit around a subject, they often rotate the phone opposite to the walking
        // direction to keep the subject in frame. Treat yaw as weak evidence; lateral ARCore motion
        // is the reliable signed signal for left/right orbit.
        val signedYawDelta = if (moveRight) {
            -sample.yawDeltaDegrees
        } else {
            sample.yawDeltaDegrees
        }
        val yawProgress = (0.08f + (signedYawDelta / 24f) * 0.42f).coerceIn(0f, 0.50f)

        if (sample.source != CameraPoseSource.ARCORE || !sample.isTracking) {
            return yawProgress
        }

        val lateralProgress = if (moveRight) {
            sample.lateralMeters / 0.14f
        } else {
            -sample.lateralMeters / 0.14f
        }
        val lateralScore = (0.15f + lateralProgress * 0.85f).coerceIn(0f, 1f)

        return max(
            lateralScore * 0.88f + yawProgress * 0.12f,
            lateralScore
        ).coerceIn(0f, 1f)
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
        if (!sample.isTracking) return false
        if (sample.source != CameraPoseSource.ARCORE) return false

        return abs(sample.verticalMeters) < 0.03f && tiltMagnitude > 8f
    }

    private fun detectReferenceObject(
        bitmap: Bitmap,
        onDetected: (NormalizedBoundingBox?) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        referenceDetector.process(image)
            .addOnSuccessListener { objects ->
                val candidate = selectMostSalientObject(
                    objects = objects,
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height
                )
                onDetected(candidate)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "reference subject detection failed: ${error.message}")
                onDetected(null)
            }
    }

    private fun selectCurrentSubjectBox(
        objects: List<DetectedObject>,
        frameWidth: Int,
        frameHeight: Int,
        actionType: String,
        direction: String,
        baseArrivalScore: Float
    ): NormalizedBoundingBox? {
        if (objects.isEmpty()) return null

        val boxes = objects.map { normalizeRect(it.boundingBox, frameWidth, frameHeight) }
        val phase = baseArrivalScore.coerceIn(0f, 1f)
        val previousBox = lastTrackedSubjectBox

        return boxes.maxByOrNull { box ->
            val targetScore = computeTargetAlignmentScore(box)
            val sourceScore = computeSourceConsistencyScore(box, actionType, direction)
            val salienceScore = (box.width * box.height / 0.18f).coerceIn(0f, 1f)
            val continuityScore = previousBox?.let { computeBoxContinuityScore(box, it) } ?: 0.55f
            val centerDistance = sqrt(
                (box.centerX - 0.5f).let { it * it } +
                    (box.centerY - 0.5f).let { it * it }
            )
            val centerScore = (1f - centerDistance / 0.72f).coerceIn(0f, 1f)

            val targetWeight = if (targetBoundingBox != null) {
                0.32f + phase * 0.28f
            } else {
                0.16f + phase * 0.10f
            }
            val sourceWeight = 0.40f - phase * 0.12f

            targetScore * targetWeight +
                sourceScore * sourceWeight +
                continuityScore * 0.16f +
                salienceScore * 0.20f +
                centerScore * 0.12f
        }
    }

    private fun computeBoxContinuityScore(
        currentBox: NormalizedBoundingBox,
        referenceBox: NormalizedBoundingBox
    ): Float {
        val centerDistance = sqrt(
            (currentBox.centerX - referenceBox.centerX).let { it * it } +
                (currentBox.centerY - referenceBox.centerY).let { it * it }
        )
        val referenceArea = (referenceBox.width * referenceBox.height).coerceAtLeast(1e-4f)
        val currentArea = (currentBox.width * currentBox.height).coerceAtLeast(1e-4f)
        val areaError = abs(currentArea - referenceArea) / max(currentArea, referenceArea)
        val centerScore = (1f - centerDistance / 0.26f).coerceIn(0f, 1f)
        val areaScore = (1f - areaError / 0.58f).coerceIn(0f, 1f)
        return (centerScore * 0.72f + areaScore * 0.28f).coerceIn(0f, 1f)
    }

    private fun selectMostSalientObject(
        objects: List<DetectedObject>,
        frameWidth: Int,
        frameHeight: Int
    ): NormalizedBoundingBox? {
        return objects.maxByOrNull { obj ->
            obj.boundingBox.width() * obj.boundingBox.height()
        }?.let { normalizeRect(it.boundingBox, frameWidth, frameHeight) }
    }

    private fun assessSubject(
        currentBox: NormalizedBoundingBox,
        actionType: String,
        direction: String,
        baseArrivalScore: Float
    ): SubjectAssessment {
        val targetAlignmentScore = computeTargetAlignmentScore(currentBox)
        val sourceConsistencyScore = computeSourceConsistencyScore(currentBox, actionType, direction)
        val target = targetBoundingBox
        val targetOffsetX = if (target != null) {
            (target.centerX - currentBox.centerX).coerceIn(-0.45f, 0.45f)
        } else {
            0f
        }
        val targetOffsetY = if (target != null) {
            (target.centerY - currentBox.centerY).coerceIn(-0.45f, 0.45f)
        } else {
            0f
        }

        val phase = baseArrivalScore.coerceIn(0f, 1f)
        val targetWeight = if (target != null) {
            0.30f + phase * 0.42f
        } else {
            0.18f + phase * 0.08f
        }
        val sourceWeight = 0.46f - phase * 0.18f
        val visibilityBoost = 0.14f
        val lockScore = (
            targetAlignmentScore * targetWeight +
                sourceConsistencyScore * sourceWeight +
                visibilityBoost
            ).coerceIn(0f, 1f)

        return SubjectAssessment(
            hasSubject = true,
            lockScore = lockScore,
            targetAlignmentScore = targetAlignmentScore,
            sourceConsistencyScore = sourceConsistencyScore,
            targetOffsetX = targetOffsetX,
            targetOffsetY = targetOffsetY,
            requiresSubjectGate = target != null || targetShotSpec?.viewpointRequired == true,
            hasTargetAnchor = target != null
        )
    }

    private fun computeTargetAlignmentScore(currentBox: NormalizedBoundingBox): Float {
        val target = targetBoundingBox ?: return 0.54f

        val centerError = sqrt(
            (target.centerX - currentBox.centerX).let { it * it } +
                (target.centerY - currentBox.centerY).let { it * it }
        )
        val targetArea = target.width * target.height
        val currentArea = currentBox.width * currentBox.height
        val sizeError = abs(targetArea - currentArea) / max(targetArea, currentArea)

        val centerScore = (1f - centerError / 0.38f).coerceIn(0f, 1f)
        val sizeScore = (1f - sizeError / 0.55f).coerceIn(0f, 1f)

        return (centerScore * 0.70f + sizeScore * 0.30f).coerceIn(0f, 1f)
    }

    private fun NormalizedBoundingBox.isWeakSceneProxyBox(): Boolean {
        val area = width * height
        val nearFullWidth = width > 0.72f
        val nearFullHeight = height > 0.48f
        val touchesHorizontalEdge = centerX - width / 2f < 0.06f || centerX + width / 2f > 0.94f
        val touchesVerticalEdge = centerY - height / 2f < 0.06f || centerY + height / 2f > 0.94f
        return area > 0.24f || (nearFullWidth && touchesHorizontalEdge) || (nearFullHeight && touchesVerticalEdge)
    }

    private fun computeSourceConsistencyScore(
        currentBox: NormalizedBoundingBox,
        actionType: String,
        direction: String
    ): Float {
        val source = sourceBoundingBox ?: return 0.52f

        val sourceAspect = (source.width / source.height.coerceAtLeast(1e-3f)).coerceAtLeast(1e-3f)
        val currentAspect = (currentBox.width / currentBox.height.coerceAtLeast(1e-3f)).coerceAtLeast(1e-3f)
        val aspectError = abs(sourceAspect - currentAspect) / max(sourceAspect, currentAspect)
        val aspectScore = (1f - aspectError / 0.55f).coerceIn(0f, 1f)

        val sourceArea = source.width * source.height
        val currentArea = currentBox.width * currentBox.height
        val areaRatio = currentArea / sourceArea.coerceAtLeast(1e-4f)
        val sizeScore = when (actionType.normalizedActionType()) {
            "step" -> {
                if (direction.equals("backward", ignoreCase = true)) {
                    when {
                        areaRatio in 0.20f..1.05f -> 1f
                        areaRatio < 0.20f -> (areaRatio / 0.20f).coerceIn(0f, 1f)
                        else -> (1.45f - areaRatio / 1.05f).coerceIn(0f, 1f)
                    }
                } else {
                    when {
                        areaRatio in 0.95f..3.20f -> 1f
                        areaRatio < 0.95f -> (areaRatio / 0.95f).coerceIn(0f, 1f)
                        else -> (1.90f - areaRatio / 3.20f).coerceIn(0f, 1f)
                    }
                }
            }
            else -> when {
                areaRatio in 0.45f..2.20f -> 1f
                areaRatio < 0.45f -> (areaRatio / 0.45f).coerceIn(0f, 1f)
                else -> (1.85f - areaRatio / 2.20f).coerceIn(0f, 1f)
            }
        }

        return (aspectScore * 0.62f + sizeScore * 0.38f).coerceIn(0f, 1f)
    }

    private fun computeUiHint(
        actionType: String,
        direction: String,
        progress: Float,
        subjectAssessment: SubjectAssessment?
    ): Pair<Float, Float> {
        val decay = (1f - progress).coerceIn(0.1f, 1f)
        val magnitude = 120f * decay

        var hintX = when (actionType.normalizedActionType()) {
            "orbit" -> if (direction.canonicalViewDirection() == "right") magnitude else -magnitude
            else -> when (direction.canonicalViewDirection()) {
                "right" -> magnitude
                "left" -> -magnitude
                else -> 0f
            }
        }
        var hintY = when (actionType.normalizedActionType()) {
            "raisecamera" -> -magnitude
            "lowercamera" -> magnitude
            "step" -> if (direction.equals("backward", ignoreCase = true)) {
                -magnitude * 0.5f
            } else {
                magnitude * 0.5f
            }
            else -> when (direction.lowercase()) {
                "high-angle" -> -magnitude
                "low-angle" -> magnitude
                else -> 0f
            }
        }

        if (subjectAssessment != null && subjectAssessment.hasTargetAnchor) {
            val subjectWeight = (0.10f + subjectAssessment.lockScore * 0.28f).coerceIn(0.10f, 0.38f)
            hintX += subjectAssessment.targetOffsetX * 180f * subjectWeight
            hintY += subjectAssessment.targetOffsetY * 200f * subjectWeight
        }

        return hintX.coerceIn(-150f, 150f) to hintY.coerceIn(-160f, 160f)
    }

    private fun softenedMovingPoseSubjectScore(subjectAssessment: SubjectAssessment): Float {
        val consistencyFloor = (subjectAssessment.sourceConsistencyScore * 0.78f + 0.18f)
            .coerceIn(0f, 1f)
        return max(subjectAssessment.lockScore, consistencyFloor)
    }

    private fun applyShotSpecToBox(detectedBox: NormalizedBoundingBox?): NormalizedBoundingBox? {
        if (targetSpecCenter == null && targetSpecSize == null) {
            return detectedBox
        }

        val fallbackCenter = targetSpecCenter
            ?: detectedBox?.let { it.centerX to it.centerY }
            ?: (0.5f to 0.5f)
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

    private fun normalizeRect(
        rect: android.graphics.Rect,
        imageWidth: Int,
        imageHeight: Int
    ): NormalizedBoundingBox {
        return NormalizedBoundingBox(
            centerX = (rect.left + rect.width() / 2f) / imageWidth,
            centerY = (rect.top + rect.height() / 2f) / imageHeight,
            width = rect.width().toFloat() / imageWidth,
            height = rect.height().toFloat() / imageHeight
        )
    }

    private fun generateFeedback(
        actionType: String,
        direction: String,
        targetSimilarityScore: Float,
        targetGainScore: Float,
        arrivalScore: Float,
        directionScore: Float,
        cameraPoseSample: CameraPoseSample?,
        subjectAssessment: SubjectAssessment,
        nearTargetSatisfied: Boolean,
        isCompleted: Boolean
    ): String {
        if (isCompleted) {
            return "✓ 机位与主体都已接近参考图"
        }

        if (nearTargetSatisfied) {
            return "机位差不多了，准备微调构图"
        }

        if (!subjectAssessment.hasSubject) {
            return "主体快丢了，先把主体带回画面再继续"
        }

        if (subjectAssessment.lockScore < 0.20f) {
            return "先稳住主体，不要切到别的物体"
        }

        if (subjectAssessment.hasTargetAnchor && subjectAssessment.targetAlignmentScore < 0.26f) {
            return subjectPositionCorrection(subjectAssessment)
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

        val normalizedActionType = actionType.normalizedActionType()
        if (
            normalizedActionType in setOf("raisecamera", "lowercamera") &&
            cameraPoseSample?.isTracking == true &&
            directionScore < 0.34f
        ) {
            return when (normalizedActionType) {
                "lowercamera" -> "把手机整体再放低一点，主体别跑出画面"
                "raisecamera" -> "把手机整体再抬高一点，主体别跑出画面"
                else -> movementPrompt(actionType, direction, stronger = true)
            }
        }

        if (directionScore < 0.40f) {
            return movementDirectionCorrection(actionType, direction)
        }

        if (subjectAssessment.lockScore < 0.42f) {
            return "方向对了，先把主体稳在参考图位置附近"
        }

        if (targetSimilarityScore < 0.55f || arrivalScore < 0.58f) {
            return movementPrompt(actionType, direction, stronger = false)
        }

        return "方向对了，继续微调到参考图位置"
    }

    private fun generateSceneOnlyFeedback(
        actionType: String,
        direction: String,
        targetGainScore: Float,
        directionScore: Float,
        nearTargetSatisfied: Boolean,
        isCompleted: Boolean,
        cameraPoseSample: CameraPoseSample?
    ): String {
        if (isCompleted) {
            return "✓ 机位已接近参考视角"
        }

        if (nearTargetSatisfied) {
            return "机位差不多了，准备微调构图"
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

        if (directionScore < 0.40f) {
            return movementDirectionCorrection(actionType, direction)
        }

        return movementPrompt(actionType, direction, stronger = false)
    }

    private fun subjectPositionCorrection(subjectAssessment: SubjectAssessment): String {
        val absX = abs(subjectAssessment.targetOffsetX)
        val absY = abs(subjectAssessment.targetOffsetY)
        return when {
            absX > absY && subjectAssessment.targetOffsetX > 0.04f -> "保持机位方向，同时让主体再往右一点"
            absX > absY && subjectAssessment.targetOffsetX < -0.04f -> "保持机位方向，同时让主体再往左一点"
            absY >= absX && subjectAssessment.targetOffsetY > 0.04f -> "保持机位方向，同时让主体再往下稳一点"
            absY >= absX && subjectAssessment.targetOffsetY < -0.04f -> "保持机位方向，同时让主体再往上稳一点"
            else -> "继续移动，但别让主体跑偏"
        }
    }

    private fun movementPrompt(actionType: String, direction: String, stronger: Boolean): String {
        return when (actionType.normalizedActionType()) {
            "orbit" -> if (direction.canonicalViewDirection() == "right") {
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
            "orbit" -> if (direction.canonicalViewDirection() == "right") {
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

    fun close() {
        objectDetector.close()
        referenceDetector.close()
    }

    fun resetStableFrames() {
        stableCompletionFrames = 0
        consecutiveMissingSubjectFrames = 0
        lastTrackedSubjectBox = null
        lastSubjectLockScore = 0f
        targetSimHistory.clear()
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
