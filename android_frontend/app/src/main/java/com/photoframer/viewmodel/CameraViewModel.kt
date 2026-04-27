package com.photoframer.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoframer.arcore.CameraPoseSample
import com.photoframer.data.api.CompositionStep
import com.photoframer.data.api.CompositionResult
import com.photoframer.data.api.InFrameCompositionResponse
import com.photoframer.data.api.ShotSpec
import com.photoframer.data.repository.CompositionRepository
import com.photoframer.guidance.isShiftAction
import com.photoframer.guidance.isViewpointAction
import com.photoframer.guidance.isZoomAction
import com.photoframer.inframe.InFrameCompositionGuideBuilder
import com.photoframer.inframe.InFrameCompositionGuide
import com.photoframer.inframe.InFrameGuideValidationConfig
import com.photoframer.ui.state.CameraUiState
import com.photoframer.utils.AnalysisImagePreprocessor
import com.photoframer.utils.ImageFileDecoder
import com.photoframer.vision.StepValidationResult
import com.photoframer.vision.StepValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 相机 ViewModel
 * 管理 UI 状态和业务逻辑
 */
class CameraViewModel : ViewModel() {
    
    private val repository = CompositionRepository()
    
    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Preview)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    
    // 步骤验证结果
    private val _validationResult = MutableStateFlow<StepValidationResult?>(null)
    val validationResult: StateFlow<StepValidationResult?> = _validationResult.asStateFlow()
    
    // 是否自动跳转（步骤完成后）
    private val _autoAdvanceEnabled = MutableStateFlow(true)
    val autoAdvanceEnabled: StateFlow<Boolean> = _autoAdvanceEnabled.asStateFlow()
    
    // 步骤完成状态（用于显示完成动画）
    private val _stepCompleted = MutableStateFlow(false)
    val stepCompleted: StateFlow<Boolean> = _stepCompleted.asStateFlow()
    
    // 所有步骤完成状态
    private val _allStepsCompleted = MutableStateFlow(false)
    val allStepsCompleted: StateFlow<Boolean> = _allStepsCompleted.asStateFlow()
    
    // 缓存解码后的图片 (使用 technique 作为 key)
    private val imageCache = mutableStateMapOf<String, Bitmap>()

    // 用于本地验证的参考图；AI 构图与缩略图一致，画面内构图则使用上传分析图
    private val validationImageCache = mutableMapOf<String, Bitmap>()

    // 画面内构图的引导参数（center / target zoom）
    private val inFrameGuideConfigCache = mutableMapOf<String, InFrameGuideValidationConfig>()
    
    // 缓存候选方案状态（用于返回）
    private var cachedCandidatesState: CameraUiState.Candidates? = null
    
    // 当前步骤验证器
    private var stepValidator: StepValidator? = null
    
    // 防止重复触发自动跳转
    private var isAutoAdvancing = false
    
    // 当前分析任务
    private var analysisJob: Job? = null

    // 引导会话与验证请求版本号，确保只有“当前步骤、当前请求”的结果可以生效
    private var guidanceSessionId = 0L
    private var latestValidationRequestId = 0L
    
    /**
     * 分析图片构图 (v3.1 并行化后端)
     */
    fun analyzeImage(imageFile: File) {
        analysisJob?.cancel()  // 取消之前的分析任务
        analysisJob = viewModelScope.launch {
            _uiState.value = CameraUiState.Analyzing

            val preparedImage = try {
                withContext(Dispatchers.IO) {
                    AnalysisImagePreprocessor.prepare(imageFile)
                }
            } catch (error: Exception) {
                _uiState.value = CameraUiState.Error(
                    error.message ?: "图片预处理失败"
                )
                return@launch
            }

            try {
                repository.analyzeComposition(preparedImage.file)
                    .onSuccess { response ->
                        if (response.applicableCount > 0 && response.compositions.isNotEmpty()) {
                            imageCache.clear()
                            validationImageCache.clear()
                            inFrameGuideConfigCache.clear()
                            val candidatesState = CameraUiState.Candidates(
                                totalTechniques = response.totalTechniques,
                                applicableCount = response.applicableCount,
                                totalTimeMs = response.totalTimeMs,
                                compositions = response.compositions
                            )
                            cachedCandidatesState = candidatesState
                            _uiState.value = candidatesState
                            warmCandidateImagesAsync(response.compositions)
                        } else {
                            _uiState.value = CameraUiState.Error(
                                response.message ?: "未能生成适用的构图方案"
                            )
                        }
                    }
                    .onFailure { error ->
                        _uiState.value = CameraUiState.Error(
                            error.message ?: "网络错误，请检查连接"
                        )
                    }
            } finally {
                preparedImage.cleanup()
            }
        }
    }

    /**
     * 画面内构图分析
     */
    fun analyzeInFrameComposition(imageFile: File) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.value = CameraUiState.Analyzing

            val preparedImage = try {
                withContext(Dispatchers.IO) {
                    AnalysisImagePreprocessor.prepare(
                        sourceFile = imageFile,
                        requireBitmap = true
                    )
                }
            } catch (error: Exception) {
                _uiState.value = CameraUiState.Error(
                    error.message ?: "图片预处理失败"
                )
                return@launch
            }

            try {
                val result = repository.analyzeInFrameComposition(preparedImage.file)
                val response = result.getOrElse { error ->
                    _uiState.value = CameraUiState.Error(
                        message = error.message ?: "画面内构图分析失败",
                        title = "画面内分析不可用",
                        actionText = "返回"
                    )
                    return@launch
                }

                if (isNonTargetInFrameScene(response)) {
                    _uiState.value = CameraUiState.Error(
                        title = "当前场景不适合",
                        message = "当前场景无明显主体，换个场景试试吧～",
                        actionText = "返回"
                    )
                    return@launch
                }

                val guides = withContext(Dispatchers.Default) {
                    val sourceBitmap = preparedImage.bitmap ?: ImageFileDecoder.decodeBitmapRespectingExif(preparedImage.file)
                        ?: return@withContext emptyList()
                    InFrameCompositionGuideBuilder.buildAll(sourceBitmap, response)
                }

                if (guides.isNotEmpty()) {
                    imageCache.clear()
                    validationImageCache.clear()
                    inFrameGuideConfigCache.clear()
                    cacheInFrameGuides(guides)

                    if (guides.size == 1) {
                        cachedCandidatesState = null
                        selectComposition(guides.first().composition)
                    } else {
                        val candidatesState = CameraUiState.Candidates(
                            totalTechniques = guides.size,
                            applicableCount = guides.size,
                            totalTimeMs = 0f,
                            compositions = guides.map { it.composition }
                        )
                        cachedCandidatesState = candidatesState
                        _uiState.value = candidatesState
                    }
                } else {
                    _uiState.value = CameraUiState.Error("未能生成有效的画面内参考构图")
                }
            } finally {
                preparedImage.cleanup()
            }
        }
    }
    
    /**
     * 取消当前分析
     */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        invalidateGuidanceSession()
        _uiState.value = CameraUiState.Preview
    }
    
    /**
     * 选择构图方案，进入引导阶段
     */
    fun selectComposition(composition: CompositionResult) {
        stepValidator?.close()
        val guidanceComposition = prepareCompositionForGuidance(composition)

        // 获取目标图片并创建验证器 (使用 technique 作为 key)
        val targetBitmap = ensureCompositionBitmapCached(guidanceComposition)
        val inFrameGuideConfig = inFrameGuideConfigCache[guidanceComposition.technique]
        if (targetBitmap != null) {
            stepValidator = StepValidator(
                targetBitmap = targetBitmap,
                inFrameGuideConfig = inFrameGuideConfig,
                shotSpec = guidanceComposition.shotSpec
            )
        } else {
            stepValidator = null
        }

        beginGuidanceSession()
        _validationResult.value = null
        _stepCompleted.value = false
        _allStepsCompleted.value = false
        
        _uiState.value = CameraUiState.Guiding(
            composition = guidanceComposition,
            currentStepIndex = 0
        )
    }

    private fun prepareCompositionForGuidance(composition: CompositionResult): CompositionResult {
        val normalizedSteps = normalizeGuidanceSteps(
            steps = composition.steps,
            shotSpec = composition.shotSpec
        )
        return composition.copy(steps = normalizedSteps)
    }

    private fun normalizeGuidanceSteps(
        steps: List<CompositionStep>,
        shotSpec: ShotSpec?
    ): List<CompositionStep> {
        if (steps.isEmpty()) return emptyList()

        val firstViewpointIndex = steps.indexOfFirst { it.isViewpointAction() }
        if (firstViewpointIndex == -1) {
            val shiftLike = steps.firstOrNull { it.isShiftAction() }
            val zoomLike = steps.firstOrNull { it.isZoomAction() }
            val normalized = buildList {
                if (shiftLike != null) add(simplifyRefineStep(shiftLike))
                if (zoomLike != null) add(simplifyRefineStep(zoomLike))
                if (isEmpty()) addAll(steps.take(2))
            }
            return renumberSteps(normalized.take(2))
        }

        val viewpointStep = steps[firstViewpointIndex].copy(
            guideText = simplifyViewpointGuideText(steps[firstViewpointIndex])
        )
        val trailingSteps = steps.drop(firstViewpointIndex + 1)
        val shiftRefine = trailingSteps
            .firstOrNull { it.isShiftAction() }
            ?.let { simplifyRefineStep(it) }
            ?: buildSyntheticShiftRefineStep(shotSpec)
        val zoomRefine = trailingSteps
            .firstOrNull { it.isZoomAction() }
            ?.let { simplifyRefineStep(it) }

        val normalized = mutableListOf(viewpointStep)
        if (shiftRefine != null) {
            normalized += shiftRefine
        }
        if (zoomRefine != null) {
            normalized += zoomRefine
        }
        return renumberSteps(normalized.take(3))
    }

    private fun simplifyViewpointGuideText(step: CompositionStep): String {
        return when (step.actionType.trim().lowercase()) {
            "orbit" -> if (step.direction.equals("right", ignoreCase = true)) {
                "先向右绕一点"
            } else {
                "先向左绕一点"
            }
            "raisecamera" -> "先抬高机位"
            "lowercamera" -> "先降低机位"
            "step" -> if (step.direction.equals("backward", ignoreCase = true)) {
                "先后退一点"
            } else {
                "先靠近一点"
            }
            else -> step.guideText
        }
    }

    private fun simplifyRefineStep(step: CompositionStep): CompositionStep {
        val guideText = when {
            step.isShiftAction() -> "再把主体对进圆圈"
            step.isZoomAction() -> "再把画面缩放到参考大小"
            else -> step.guideText
        }
        return step.copy(guideText = guideText)
    }

    private fun buildSyntheticShiftRefineStep(shotSpec: ShotSpec?): CompositionStep? {
        val targetCenter = shotSpec?.targetSubjectCenter ?: return null
        if (targetCenter.size < 2) return null

        val centerX = targetCenter[0]
        val centerY = targetCenter[1]
        val deltaX = centerX - 0.5f
        val deltaY = centerY - 0.5f
        val direction = if (kotlin.math.abs(deltaX) >= kotlin.math.abs(deltaY)) {
            if (deltaX >= 0f) "Right" else "Left"
        } else {
            if (deltaY >= 0f) "Down" else "Up"
        }

        return CompositionStep(
            stepOrder = 2,
            actionType = "Shift",
            direction = direction,
            guideText = "再把主体对进圆圈"
        )
    }

    private fun renumberSteps(steps: List<CompositionStep>): List<CompositionStep> {
        return steps.mapIndexed { index, step ->
            step.copy(stepOrder = index + 1)
        }
    }
    
    /**
     * 验证当前帧是否完成当前步骤
     * 应在相机帧回调中调用
     */
    fun validateCurrentFrame(
        currentFrame: Bitmap,
        currentZoomRatio: Float = 1.0f,
        cameraPoseSample: CameraPoseSample? = null
    ) {
        val currentState = _uiState.value
        if (currentState !is CameraUiState.Guiding) return
        
        val step = currentState.currentStep ?: return
        val validator = stepValidator ?: return
        
        // 如果正在自动跳转，跳过验证
        if (isAutoAdvancing) return

        val sessionId = guidanceSessionId
        val stepIndex = currentState.currentStepIndex
        val requestId = nextValidationRequestId()
        
        viewModelScope.launch {
            // 根据操作类型选择验证方式
            if (step.isViewpointAction()) {
                // 视角类动作使用 source/target 双相似度 + 主体辅助分析（异步）
                validator.validateViewChangeAsync(
                    currentFrame = currentFrame,
                    stepKey = step.stepOrder,
                    actionType = step.actionType,
                    direction = step.direction,
                    cameraPoseSample = cameraPoseSample
                ) { result ->
                    applyValidationResultIfCurrent(
                        result = result,
                        validator = validator,
                        sessionId = sessionId,
                        stepIndex = stepIndex,
                        requestId = requestId
                    )
                }
            } else {
                // Shift 和 Zoom 使用 Homography 验证
                val result = withContext(Dispatchers.Default) {
                    validator.validateStep(
                        currentFrame = currentFrame,
                        stepKey = step.stepOrder,
                        actionType = step.actionType,
                        direction = step.direction,
                        currentZoomRatio = currentZoomRatio
                    )
                }

                applyValidationResultIfCurrent(
                    result = result,
                    validator = validator,
                    sessionId = sessionId,
                    stepIndex = stepIndex,
                    requestId = requestId
                )
            }
        }
    }

    
    /**
     * 处理步骤完成
     */
    private fun handleStepCompleted(sessionId: Long, stepIndex: Int) {
        isAutoAdvancing = true
        _stepCompleted.value = true
        invalidatePendingValidation()
        
        viewModelScope.launch {
            // 显示完成动画
            delay(800)  // 等待动画显示

            if (!isGuidanceStepCurrent(sessionId, stepIndex)) {
                _stepCompleted.value = false
                isAutoAdvancing = false
                return@launch
            }
            
            _stepCompleted.value = false

            val currentState = _uiState.value as? CameraUiState.Guiding
            if (currentState == null) {
                isAutoAdvancing = false
                return@launch
            }

            if (currentState.isLastStep) {
                // 所有步骤完成
                _allStepsCompleted.value = true
                invalidatePendingValidation()
            } else {
                // 跳转到下一步
                nextStep()
            }
            
            delay(200)
            isAutoAdvancing = false
        }
    }
    
    /**
     * 切换自动跳转
     */
    fun toggleAutoAdvance() {
        _autoAdvanceEnabled.value = !_autoAdvanceEnabled.value
    }
    
    /**
     * 下一步
     */
    fun nextStep() {
        val currentState = _uiState.value
        if (currentState is CameraUiState.Guiding && !currentState.isLastStep) {
            invalidatePendingValidation()
            isAutoAdvancing = false
            _validationResult.value = null
            _stepCompleted.value = false
            _allStepsCompleted.value = false
            _uiState.value = currentState.copy(
                currentStepIndex = currentState.currentStepIndex + 1
            )
        }
    }
    
    /**
     * 上一步
     */
    fun previousStep() {
        val currentState = _uiState.value
        if (currentState is CameraUiState.Guiding && !currentState.isFirstStep) {
            invalidatePendingValidation()
            isAutoAdvancing = false
            _validationResult.value = null
            _stepCompleted.value = false
            _allStepsCompleted.value = false
            _uiState.value = currentState.copy(
                currentStepIndex = currentState.currentStepIndex - 1
            )
        }
    }
    
    /**
     * 返回预览状态
     */
    fun backToPreview() {
        invalidateGuidanceSession()
        imageCache.clear()
        validationImageCache.clear()
        inFrameGuideConfigCache.clear()
        stepValidator?.close()  // 释放 ML Kit 资源
        stepValidator = null
        _validationResult.value = null
        _stepCompleted.value = false
        _allStepsCompleted.value = false
        cachedCandidatesState = null
        _uiState.value = CameraUiState.Preview
    }
    
    /**
     * 返回候选方案选择
     */
    fun backToCandidates() {
        val cached = cachedCandidatesState
        if (cached != null) {
            invalidateGuidanceSession()
            stepValidator?.close()  // 释放 ML Kit 资源
            stepValidator = null
            _validationResult.value = null
            _stepCompleted.value = false
            _allStepsCompleted.value = false
            _uiState.value = cached
        } else {
            backToPreview()
        }
    }
    
    /**
     * 获取缓存的图片 (使用 technique 作为 key)
     */
    fun getCachedImage(technique: String): Bitmap? {
        return imageCache[technique]
    }
    
    /**
     * 解码 Base64 图片
     */
    private fun decodeBase64Image(base64String: String): Bitmap? {
        return try {
            val pureBase64 = base64String.substringAfter("base64,", base64String)
            val imageBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun warmCandidateImagesAsync(compositions: List<CompositionResult>) {
        viewModelScope.launch(Dispatchers.Default) {
            compositions.forEach { composition ->
                if (imageCache.containsKey(composition.technique)) return@forEach
                val bitmap = composition.imageBase64?.let(::decodeBase64Image) ?: return@forEach
                withContext(Dispatchers.Main) {
                    imageCache[composition.technique] = bitmap
                    validationImageCache[composition.technique] = bitmap
                }
            }
        }
    }

    private fun ensureCompositionBitmapCached(composition: CompositionResult): Bitmap? {
        val cached = validationImageCache[composition.technique] ?: imageCache[composition.technique]
        if (cached != null) return cached

        val decoded = composition.imageBase64?.let(::decodeBase64Image) ?: return null
        imageCache[composition.technique] = decoded
        validationImageCache[composition.technique] = decoded
        return decoded
    }

    private fun cacheInFrameGuides(guides: List<InFrameCompositionGuide>) {
        guides.forEach { guide ->
            imageCache[guide.composition.technique] = guide.previewBitmap
            validationImageCache[guide.composition.technique] = guide.validationBitmap
            inFrameGuideConfigCache[guide.composition.technique] = guide.validationConfig
        }
    }

    private fun isNonTargetInFrameScene(response: InFrameCompositionResponse): Boolean {
        val sceneType = response.sceneType
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return false

        return sceneType in setOf("notarget", "no_target", "no-target")
    }

    private fun beginGuidanceSession() {
        guidanceSessionId += 1
        latestValidationRequestId = 0L
        isAutoAdvancing = false
    }

    private fun invalidateGuidanceSession() {
        guidanceSessionId += 1
        invalidatePendingValidation()
        isAutoAdvancing = false
    }

    private fun nextValidationRequestId(): Long {
        latestValidationRequestId += 1
        return latestValidationRequestId
    }

    private fun invalidatePendingValidation() {
        latestValidationRequestId += 1
    }

    private fun isGuidanceStepCurrent(
        sessionId: Long,
        stepIndex: Int,
        validator: StepValidator? = null
    ): Boolean {
        if (sessionId != guidanceSessionId) return false
        if (validator != null && validator !== stepValidator) return false

        val state = _uiState.value as? CameraUiState.Guiding ?: return false
        return state.currentStepIndex == stepIndex
    }

    private fun applyValidationResultIfCurrent(
        result: StepValidationResult,
        validator: StepValidator,
        sessionId: Long,
        stepIndex: Int,
        requestId: Long
    ) {
        if (requestId != latestValidationRequestId) return
        if (!isGuidanceStepCurrent(sessionId, stepIndex, validator)) return

        _validationResult.value = result

        if (result.isCompleted && _autoAdvanceEnabled.value && !isAutoAdvancing) {
            handleStepCompleted(sessionId, stepIndex)
        }
    }
}
