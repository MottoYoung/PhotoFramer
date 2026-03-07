package com.photoframer.ui.state

import com.photoframer.data.api.AnalysisHeader
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
     * 阶段 3：候选方案选择
     */
    data class Candidates(
        val header: AnalysisHeader,
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
