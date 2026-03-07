package com.photoframer.ui.state

import com.photoframer.data.api.CompositionResult

/**
 * 相机界面 UI 状态
 */
sealed class CameraUiState {
    /**
     * 阶段 1：相机预览
     */
    object Preview : CameraUiState()
    
    /**
     * 阶段 2：分析中（显示流光动画）
     */
    object Analyzing : CameraUiState()
    
    /**
     * 阶段 3：候选方案选择 (v3.1 并行化后端格式)
     */
    data class Candidates(
        val totalTechniques: Int,      // 请求的构图技术总数
        val applicableCount: Int,      // 适用的构图方案数量
        val totalTimeMs: Float,        // 并行请求总耗时（毫秒）
        val compositions: List<CompositionResult>
    ) : CameraUiState()
    
    /**
     * 阶段 4：构图引导
     */
    data class Guiding(
        val composition: CompositionResult,
        val currentStepIndex: Int = 0
    ) : CameraUiState() {
        val currentStep get() = composition.steps.getOrNull(currentStepIndex)
        val totalSteps get() = composition.steps.size
        val isLastStep get() = currentStepIndex >= totalSteps - 1
        val isFirstStep get() = currentStepIndex == 0
    }
    
    /**
     * 错误状态
     */
    data class Error(val message: String) : CameraUiState()
}
