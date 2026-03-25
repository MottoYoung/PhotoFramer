package com.photoframer.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoframer.data.api.CompositionResult
import com.photoframer.data.repository.CompositionRepository
import com.photoframer.ui.state.CameraUiState
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
    private val imageCache = mutableMapOf<String, Bitmap>()
    
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
            
            repository.analyzeComposition(imageFile)
                .onSuccess { response ->
                    if (response.applicableCount > 0 && response.compositions.isNotEmpty()) {
                        // 预解码图片 (使用 technique 作为 key)
                        response.compositions.forEach { comp ->
                            comp.imageBase64?.let { base64 ->
                                decodeBase64Image(base64)?.let { bitmap ->
                                    imageCache[comp.technique] = bitmap
                                }
                            }
                        }
                        val candidatesState = CameraUiState.Candidates(
                            totalTechniques = response.totalTechniques,
                            applicableCount = response.applicableCount,
                            totalTimeMs = response.totalTimeMs,
                            compositions = response.compositions
                        )
                        cachedCandidatesState = candidatesState
                        _uiState.value = candidatesState
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

        // 获取目标图片并创建验证器 (使用 technique 作为 key)
        val targetBitmap = imageCache[composition.technique]
        if (targetBitmap != null) {
            stepValidator = StepValidator(targetBitmap)
        } else {
            stepValidator = null
        }

        beginGuidanceSession()
        _validationResult.value = null
        _stepCompleted.value = false
        _allStepsCompleted.value = false
        
        _uiState.value = CameraUiState.Guiding(
            composition = composition,
            currentStepIndex = 0
        )
    }
    
    /**
     * 验证当前帧是否完成当前步骤
     * 应在相机帧回调中调用
     */
    fun validateCurrentFrame(currentFrame: Bitmap, currentZoomRatio: Float = 1.0f) {
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
            if (step.actionType.lowercase() == "view-change") {
                // View-change 使用 ML Kit 物体检测（异步）
                validator.validateViewChangeAsync(
                    currentFrame = currentFrame,
                    stepKey = step.stepOrder,
                    direction = step.direction
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
