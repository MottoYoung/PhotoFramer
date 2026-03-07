package com.photoframer.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframer.data.api.CompositionResult
import com.photoframer.data.api.CompositionStep
import com.photoframer.ui.components.*
import com.photoframer.ui.theme.*
import com.photoframer.vision.StepValidationResult

// ==================== 模拟数据 ====================

private val mockStep = CompositionStep(
    stepOrder = 1,
    actionType = "Shift",
    direction = "Left",
    guideText = "将相机向左平移约20厘米，使主体位于画面右侧三分之一处"
)

private val mockComposition = CompositionResult(
    id = 1,
    aestheticDesc = "使用三分法构图，将主体放置在左侧三分之一处，增强画面平衡感",
    steps = listOf(mockStep),
    imageBase64 = null
)

private val mockValidationResult = StepValidationResult(
    isCompleted = false,
    progress = 0.65f,
    feedbackText = "向左移动 ←",
    shiftDistance = 45f,
    scaleFactor = 1.05f,
    rotationAngle = 2f,
    matchQuality = 0.8f
)

private val mockCompletedResult = StepValidationResult(
    isCompleted = true,
    progress = 1f,
    feedbackText = "✓ 位置已对齐",
    shiftDistance = 15f,
    scaleFactor = 1.0f,
    rotationAngle = 0f,
    matchQuality = 1f
)

// ==================== 组件预览 ====================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
fun LoadingOverlayPreview() {
    PhotoFramerTheme {
        LoadingOverlay()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E, widthDp = 200)
@Composable
fun CompositionCardPreview() {
    PhotoFramerTheme {
        CompositionCard(
            composition = mockComposition,
            bitmap = null,
            onClick = {},
            onSaveClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
fun GuidancePanelPreview() {
    PhotoFramerTheme {
        GuidancePanel(
            step = mockStep,
            currentStepIndex = 0,
            totalSteps = 3,
            onPreviousStep = {},
            onNextStep = {},
            isFirstStep = true,
            isLastStep = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
fun ValidationFeedbackPreview() {
    PhotoFramerTheme {
        ValidationFeedback(
            result = mockValidationResult,
            stepCompleted = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
fun ValidationFeedbackCompletedPreview() {
    PhotoFramerTheme {
        ValidationFeedback(
            result = mockCompletedResult,
            stepCompleted = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
fun AllStepsCompletedBannerPreview() {
    PhotoFramerTheme {
        AllStepsCompletedBanner(
            onTakePhoto = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
fun ButtonsPreview() {
    PhotoFramerTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            AnalysisButton(onClick = {})
            CaptureButton(onClick = {})
        }
    }
}

// ==================== 完整页面预览 ====================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E, widthDp = 360, heightDp = 800)
@Composable
fun AllComponentsPreview() {
    PhotoFramerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("📱 组件预览", fontSize = 20.sp, color = TextPrimary)
            
            Text("分析/拍照按钮", fontSize = 14.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AnalysisButton(onClick = {})
                CaptureButton(onClick = {})
            }
            
            Text("验证反馈 - 进行中", fontSize = 14.sp, color = TextSecondary)
            ValidationFeedback(result = mockValidationResult)
            
            Text("验证反馈 - 已完成", fontSize = 14.sp, color = TextSecondary)
            ValidationFeedback(result = mockCompletedResult, stepCompleted = true)
            
            Text("完成横幅", fontSize = 14.sp, color = TextSecondary)
            AllStepsCompletedBanner(onTakePhoto = {})
            
            Text("构图卡片", fontSize = 14.sp, color = TextSecondary)
            CompositionCard(
                composition = mockComposition,
                bitmap = null,
                onClick = {},
                onSaveClick = {}
            )
            
            Text("引导面板", fontSize = 14.sp, color = TextSecondary)
            GuidancePanel(
                step = mockStep,
                currentStepIndex = 0,
                totalSteps = 3,
                onPreviousStep = {},
                onNextStep = {},
                isFirstStep = true,
                isLastStep = false
            )
        }
    }
}
