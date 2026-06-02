package com.photoframer.viewmodel

import com.photoframer.data.api.CompositionResult
import com.photoframer.ui.state.CameraUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CameraViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectComposition_withoutReferenceImage_doesNotEnterGuiding() {
        val viewModel = CameraViewModel()
        val composition = CompositionResult(
            technique = "rule_of_thirds",
            techniqueName = "三分构图",
            aestheticDesc = "desc",
            steps = emptyList(),
            imageBase64 = null
        )

        viewModel.selectComposition(composition)

        assertTrue(viewModel.uiState.value is CameraUiState.Error)
    }
}
