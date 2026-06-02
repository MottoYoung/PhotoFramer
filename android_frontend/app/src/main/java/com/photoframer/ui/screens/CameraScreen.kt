package com.photoframer.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Surface
import android.view.HapticFeedbackConstants
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photoframer.arcore.ArCoreRuntimeState
import com.photoframer.arcore.CameraPoseSample
import com.photoframer.arcore.CameraPoseSource
import com.photoframer.arcore.ArCorePoseTracker
import com.photoframer.arcore.ArCoreStatus
import com.photoframer.arcore.ArCoreSupport
import com.photoframer.arcore.findActivity
import com.photoframer.guidance.isLevelAction
import com.photoframer.guidance.isViewpointAction
import com.photoframer.ui.components.AllStepsCompletedBanner
import com.photoframer.ui.components.AspectRatioOption
import com.photoframer.ui.components.CameraBottomBar
import com.photoframer.ui.components.CameraEntryMode
import com.photoframer.ui.components.CameraMode
import com.photoframer.ui.components.CameraTopBar
import com.photoframer.ui.components.CameraVisualStyle
import com.photoframer.ui.components.CameraZoomUiState
import com.photoframer.ui.components.CandidatesBottomPanel
import com.photoframer.ui.components.CaptureTimer
import com.photoframer.ui.components.FlashMode
import com.photoframer.ui.components.GridOverlay
import com.photoframer.ui.components.GuidanceOverlay
import com.photoframer.ui.components.GuidingBottomBar
import com.photoframer.ui.components.LoadingOverlay
import com.photoframer.ui.components.SideToolBar
import com.photoframer.ui.components.TopGuidanceBar
import com.photoframer.ui.state.CameraUiState
import com.photoframer.ui.theme.BackgroundDark
import com.photoframer.ui.theme.ErrorRed
import com.photoframer.ui.theme.PurplePrimary
import com.photoframer.ui.theme.SurfaceDark
import com.photoframer.ui.theme.TextSecondary
import com.photoframer.utils.CameraLensManager
import com.photoframer.utils.CameraLensOption
import com.photoframer.utils.CameraZoomController
import com.photoframer.utils.GalleryUtils
import com.photoframer.utils.ImageFileDecoder
import com.photoframer.utils.ImageSaver
import com.photoframer.viewmodel.CameraViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CameraScreen"
private const val ZOOM_RULER_AUTO_HIDE_DELAY_MS = 1500L
private const val ZOOM_DRAG_SENSITIVITY = 0.0075f

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
    val stepCompleted by viewModel.stepCompleted.collectAsStateWithLifecycle()
    val allStepsCompleted by viewModel.allStepsCompleted.collectAsStateWithLifecycle()
    val showStepSkip by viewModel.showStepSkip.collectAsStateWithLifecycle()
    val postCapturePrompt by viewModel.postCapturePrompt.collectAsStateWithLifecycle()
    val newCandidatePrompt by viewModel.newCandidatePrompt.collectAsStateWithLifecycle()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var rawZoomRatio by remember { mutableFloatStateOf(1f) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val arCorePoseTracker = remember(context.applicationContext) {
        ArCorePoseTracker(context.applicationContext)
    }

    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var gridEnabled by remember { mutableStateOf(false) }
    var lastPhotoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var availableLenses by remember { mutableStateOf(CameraLensManager.getAvailableLenses(context)) }
    var selectedLensIndex by remember { mutableIntStateOf(0) }
    var desiredEffectiveZoom by remember { mutableFloatStateOf(1f) }
    var isZoomRulerVisible by remember { mutableStateOf(false) }
    var zoomInteractionVersion by remember { mutableIntStateOf(0) }

    var touchScreenPhotoEnabled by remember { mutableStateOf(false) }
    var selectedRatio by remember { mutableStateOf(AspectRatioOption.FULL) }
    var selectedTimer by remember { mutableStateOf(CaptureTimer.OFF) }
    var countdownValue by remember { mutableStateOf<Int?>(null) }
    var burstCount by remember { mutableIntStateOf(0) }
    var isBursting by remember { mutableStateOf(false) }
    var activeEntryMode by remember { mutableStateOf(CameraEntryMode.NONE) }
    var visualStyle by remember { mutableStateOf(CameraVisualStyle.DEFAULT) }

    val frameCounter = remember { AtomicInteger(0) }
    val lastAnalysisTime = remember { AtomicLong(0L) }
    var arCoreStatus by remember { mutableStateOf(ArCoreSupport.idleStatus()) }
    var hasRequestedArCoreInstall by remember { mutableStateOf(false) }
    var latestPoseSample by remember { mutableStateOf<CameraPoseSample?>(null) }
    var arExperimentEnabled by remember { mutableStateOf(ArCoreSupport.isEnabled(context)) }

    val guidingStep = (uiState as? CameraUiState.Guiding)?.currentStep
    val needsPoseTracking = guidingStep?.let { it.isViewpointAction() || it.isLevelAction() } == true
    val isViewpointStep = guidingStep?.isViewpointAction() == true
    val shouldUseArCore = isViewpointStep && arExperimentEnabled
    val analyzeEveryNFrames = if (needsPoseTracking) 6 else 4
    val minAnalysisInterval = if (needsPoseTracking) 90L else 66L
    val canOpenGallery = remember(context) {
        GalleryUtils.canOpenGallery(context)
    }
    val selectedLens = availableLenses.getOrNull(selectedLensIndex)
    val selectedFacing = selectedLens?.lensFacing ?: CameraSelector.LENS_FACING_BACK
    val currentFacingLenses = availableLenses.mapIndexedNotNull { index, lens ->
        if (lens.lensFacing == selectedFacing) {
            index to lens
        } else {
            null
        }
    }
    val zoomPresets = CameraZoomController.buildPresets(currentFacingLenses)
    val actualEffectiveZoom = CameraZoomController.effectiveZoomRatio(selectedLens, rawZoomRatio)
    val displayedZoom = if (isZoomRulerVisible) desiredEffectiveZoom else actualEffectiveZoom
    val zoomUiState = CameraZoomUiState(
        presets = zoomPresets,
        selectedLensIndex = selectedLensIndex,
        displayedZoomText = CameraZoomController.formatZoomRatio(displayedZoom),
        isRulerVisible = isZoomRulerVisible
    )

    fun performHaptic(constant: Int) {
        view.performHapticFeedback(constant)
    }

    fun performCompletionVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(180, 160))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(VibrationEffect.createOneShot(180, 160))
        }
    }

    DisposableEffect(cameraExecutor, arCorePoseTracker) {
        onDispose {
            arCorePoseTracker.close()
            cameraExecutor.shutdownNow()
        }
    }

    DisposableEffect(
        lifecycleOwner,
        needsPoseTracking,
        shouldUseArCore,
        guidingStep?.stepOrder
    ) {
        if (!needsPoseTracking) {
            arCoreStatus = ArCoreSupport.idleStatus()
            hasRequestedArCoreInstall = false
            arCorePoseTracker.stop()
            return@DisposableEffect onDispose {}
        }
        if (!isViewpointStep) {
            arCoreStatus = ArCoreSupport.disabledStatus()
            hasRequestedArCoreInstall = false
            return@DisposableEffect onDispose {}
        }
        if (!shouldUseArCore) {
            arCoreStatus = ArCoreSupport.disabledStatus()
            hasRequestedArCoreInstall = false
            return@DisposableEffect onDispose {}
        }

        var checkingJob: Job? = null

        fun refreshArCoreStatus() {
            val activity = context.findActivity()
            arCoreStatus = if (activity != null) {
                ArCoreSupport.resolveForActivity(
                    activity = activity,
                    requestInstall = !hasRequestedArCoreInstall
                ).also { status ->
                    if (status.state == ArCoreRuntimeState.Installing) {
                        hasRequestedArCoreInstall = true
                    }
                }
            } else {
                ArCoreStatus(
                    state = ArCoreRuntimeState.Failed,
                    message = "无法启动 ARCore",
                    detail = "当前继续使用视觉估计"
                )
            }
        }

        fun scheduleCheckingPoll() {
            checkingJob?.cancel()
            val activity = context.findActivity() ?: return
            checkingJob = scope.launch {
                repeat(6) { attempt ->
                    if (arCoreStatus.state != ArCoreRuntimeState.Checking) return@launch
                    delay(1200)
                    refreshArCoreStatus()
                    if (arCoreStatus.state != ArCoreRuntimeState.Checking) return@launch
                    if (attempt == 5) {
                        arCoreStatus = ArCoreSupport.checkingTimeoutStatus(activity)
                    }
                }
            }
        }

        refreshArCoreStatus()
        if (arCoreStatus.state == ArCoreRuntimeState.Checking) {
            scheduleCheckingPoll()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshArCoreStatus()
                if (arCoreStatus.state == ArCoreRuntimeState.Checking) {
                    scheduleCheckingPoll()
                } else {
                    checkingJob?.cancel()
                    checkingJob = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            checkingJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            arCorePoseTracker.stop()
        }
    }

    LaunchedEffect(
        needsPoseTracking,
        shouldUseArCore,
        guidingStep?.stepOrder,
        arCoreStatus.state,
        selectedFacing
    ) {
        if (!needsPoseTracking || selectedFacing == CameraSelector.LENS_FACING_FRONT) {
            arCorePoseTracker.stop()
            latestPoseSample = null
            return@LaunchedEffect
        }

        val currentPreview = previewView
        arCorePoseTracker.start(
            activity = context.findActivity(),
            preferArCore = shouldUseArCore && arCoreStatus.isReady,
            viewportWidth = currentPreview?.width ?: 0,
            viewportHeight = currentPreview?.height ?: 0,
            displayRotation = currentPreview?.display?.rotation ?: Surface.ROTATION_0,
            resetBaseline = true
        )
    }

    fun defaultLensIndex(lenses: List<CameraLensOption>): Int {
        val mainBackLensIndex = lenses.indexOfFirst { lens ->
            lens.lensFacing == CameraSelector.LENS_FACING_BACK &&
                (lens.targetZoomRatio?.let { kotlin.math.abs(it - 1f) < 0.15f } == true)
        }
        if (mainBackLensIndex >= 0) {
            return mainBackLensIndex
        }

        return lenses.indexOfFirst { lens ->
            lens.lensFacing == CameraSelector.LENS_FACING_BACK
        }.takeIf { it >= 0 } ?: 0
    }

    fun refreshLatestThumbnail() {
        scope.launch {
            val latestThumbnail = withContext(Dispatchers.IO) {
                GalleryUtils.loadLatestThumbnail(context)
            }
            if (latestThumbnail != null) {
                lastPhotoThumbnail = latestThumbnail
            }
        }
    }

    fun openGallery() {
        GalleryUtils.openGallery(context)
    }

    fun refreshZoomAutoHide() {
        zoomInteractionVersion += 1
    }

    fun supportedEffectiveZoomRange(
        lens: CameraLensOption?,
        info: CameraInfo?,
        presets: List<com.photoframer.utils.CameraZoomPreset>
    ): ClosedFloatingPointRange<Float> {
        val zoomState = info?.zoomState?.value
        val minPresetZoom = presets.minOfOrNull { it.effectiveZoomRatio } ?: 1f
        val maxPresetZoom = presets.maxOfOrNull { it.effectiveZoomRatio } ?: 1f
        val maxZoom = maxOf(maxPresetZoom, maxPresetZoom * (zoomState?.maxZoomRatio ?: 8f))
        val minZoom = minPresetZoom
        return minZoom..maxZoom
    }

    fun applyTargetEffectiveZoom(
        targetZoom: Float,
        revealRuler: Boolean = false
    ) {
        if (zoomPresets.isEmpty()) return

        val zoomRange = supportedEffectiveZoomRange(selectedLens, cameraInfo, zoomPresets)
        val clampedZoom = targetZoom.coerceIn(zoomRange.start, zoomRange.endInclusive)
        val targetPreset = CameraZoomController.chooseLensForZoom(zoomPresets, clampedZoom)
            ?: return

        desiredEffectiveZoom = clampedZoom
        if (revealRuler) {
            isZoomRulerVisible = true
            refreshZoomAutoHide()
        }

        if (targetPreset.lensIndex != selectedLensIndex) {
            selectedLensIndex = targetPreset.lensIndex
            return
        }

        val rawTargetZoom = CameraZoomController.computeRawZoomRatio(
            lens = selectedLens,
            targetEffectiveZoom = clampedZoom,
            cameraInfo = cameraInfo
        )
        cameraControl?.setZoomRatio(rawTargetZoom)
    }

    fun revealZoomRuler() {
        desiredEffectiveZoom = actualEffectiveZoom
        isZoomRulerVisible = true
        refreshZoomAutoHide()
    }

    fun finishZoomInteraction() {
        val snappedZoom = CameraZoomController.snapZoomRatio(desiredEffectiveZoom, zoomPresets)
        applyTargetEffectiveZoom(snappedZoom, revealRuler = true)
    }

    fun switchCameraFacing() {
        val targetFacing = if (selectedFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val targetLenses = availableLenses.mapIndexedNotNull { index, lens ->
            if (lens.lensFacing == targetFacing) {
                index to lens
            } else {
                null
            }
        }
        val targetIndex = CameraZoomController.findDefaultLensIndex(targetLenses) ?: return
        selectedLensIndex = targetIndex
        desiredEffectiveZoom = availableLenses.getOrNull(targetIndex)?.targetZoomRatio ?: 1f
        isZoomRulerVisible = false
    }

    LaunchedEffect(context) {
        availableLenses = CameraLensManager.getAvailableLenses(context)
        selectedLensIndex = defaultLensIndex(availableLenses)
        desiredEffectiveZoom =
            availableLenses.getOrNull(selectedLensIndex)?.targetZoomRatio ?: 1f
        refreshLatestThumbnail()
    }

    LaunchedEffect(selectedLensIndex, cameraInfo, desiredEffectiveZoom) {
        if (selectedLens == null) return@LaunchedEffect
        val rawTargetZoom = CameraZoomController.computeRawZoomRatio(
            lens = selectedLens,
            targetEffectiveZoom = desiredEffectiveZoom,
            cameraInfo = cameraInfo
        )
        cameraControl?.setZoomRatio(rawTargetZoom)
    }

    LaunchedEffect(isZoomRulerVisible, zoomInteractionVersion) {
        if (!isZoomRulerVisible) {
            return@LaunchedEffect
        }

        delay(ZOOM_RULER_AUTO_HIDE_DELAY_MS)
        isZoomRulerVisible = false
    }

    LaunchedEffect(uiState) {
        if (uiState is CameraUiState.Preview) {
            activeEntryMode = CameraEntryMode.NONE
            countdownValue = null
            isBursting = false
            burstCount = 0
        }
    }

    LaunchedEffect((uiState as? CameraUiState.Guiding)?.composition?.technique) {
        if (uiState is CameraUiState.Guiding) {
            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    LaunchedEffect(stepCompleted) {
        if (stepCompleted) {
            performHaptic(HapticFeedbackConstants.CONFIRM)
        }
    }

    LaunchedEffect(allStepsCompleted) {
        if (allStepsCompleted) {
            performCompletionVibration()
        }
    }

    fun saveCapturedPhoto(file: File, namePrefix: String) {
        scope.launch {
            val adjustedFile = withContext(Dispatchers.IO) {
                applyAspectRatioToCapturedFile(context, file, selectedRatio)
            }
            val thumbnail = withContext(Dispatchers.IO) {
                ImageFileDecoder.decodeThumbnailRespectingExif(adjustedFile, maxDimension = 512)
            }
            if (thumbnail != null) {
                lastPhotoThumbnail = thumbnail
            }
            ImageSaver.saveImageFileToGallery(context, adjustedFile, namePrefix)
        }
    }

    fun capturePreviewPhoto() {
        captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
            saveCapturedPhoto(file, "photoframer")
        }
    }

    fun startCountdownCapture() {
        if (countdownValue != null) return
        val timerSeconds = selectedTimer.seconds
        if (timerSeconds <= 0) {
            performHaptic(HapticFeedbackConstants.LONG_PRESS)
            capturePreviewPhoto()
            return
        }

        countdownValue = timerSeconds
        scope.launch {
            while ((countdownValue ?: 0) > 0) {
                delay(1000)
                val next = (countdownValue ?: 0) - 1
                countdownValue = if (next > 0) next else null
            }
            if (uiState is CameraUiState.Preview) {
                capturePreviewPhoto()
            }
        }
    }

    val startBurst = startBurst@{
        if (isBursting) {
            return@startBurst
        }
        isBursting = true
        burstCount = 0
        countdownValue = null
        scope.launch {
            while (isBursting) {
                captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
                    saveCapturedPhoto(file, "photoframer_burst")
                }
                burstCount += 1
                delay(200)
            }
        }
    }

    val stopBurst = {
        isBursting = false
    }

    val captureGuidedPhoto = {
        performHaptic(HapticFeedbackConstants.LONG_PRESS)
        captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
            saveCapturedPhoto(file, "photoframer_final")
            scope.launch {
                viewModel.handleGuidedCaptureCompleted()
            }
        }
    }

    val startInFrameComposition = {
        activeEntryMode = CameraEntryMode.IN_FRAME
        countdownValue = null
        captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
            val adjustedFile = applyAspectRatioToCapturedFile(context, file, selectedRatio)
            viewModel.analyzeInFrameComposition(adjustedFile)
        }
    }

    val startAiAnalysis = {
        activeEntryMode = CameraEntryMode.AI
        countdownValue = null
        captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
            val adjustedFile = applyAspectRatioToCapturedFile(context, file, selectedRatio)
            viewModel.analyzeImage(adjustedFile)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState !is CameraUiState.Guiding) {
            CameraTopBar(
                flashMode = flashMode,
                onFlashModeChange = { newMode ->
                    flashMode = newMode
                    imageCapture?.flashMode = newMode.toImageCaptureFlashMode()
                },
                gridEnabled = gridEnabled,
                onGridToggle = { gridEnabled = !gridEnabled },
                visualStyle = visualStyle,
                onVisualStyleChange = { visualStyle = it },
                activeEntryMode = activeEntryMode,
                showInFrameButton = uiState !is CameraUiState.Analyzing,
                onInFrameClick = startInFrameComposition,
                showAiButton = uiState !is CameraUiState.Analyzing,
                onAiClick = startAiAnalysis
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val currentZoom = desiredEffectiveZoom
                        val zoomRange = supportedEffectiveZoomRange(selectedLens, cameraInfo, zoomPresets)
                        val newZoom = (currentZoom * zoom).coerceIn(
                            zoomRange.start,
                            zoomRange.endInclusive
                        )
                        if (kotlin.math.abs(newZoom - currentZoom) > 0.001f) {
                            desiredEffectiveZoom = newZoom
                            applyTargetEffectiveZoom(newZoom)
                        }
                    }
                }
                .pointerInput(touchScreenPhotoEnabled, uiState, countdownValue, isBursting) {
                    if (touchScreenPhotoEnabled &&
                        uiState is CameraUiState.Preview &&
                        countdownValue == null &&
                        !isBursting
                    ) {
                        detectTapGestures {
                            startCountdownCapture()
                        }
                    }
                }
        ) {
            key(selectedLens?.logicalCameraId, selectedLens?.physicalCameraId, flashMode) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            previewView = this
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = surfaceProvider
                                }
                                imageCapture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .setFlashMode(flashMode.toImageCaptureFlashMode())
                                    .build()

                                val imageAnalyzer = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                            try {
                                                val currentState = viewModel.uiState.value
                                                if (currentState is CameraUiState.Guiding) {
                                                    val currentFrameCount = frameCounter.incrementAndGet()
                                                    val currentTime = System.currentTimeMillis()
                                                    val previousAnalysisTime = lastAnalysisTime.get()
                                                    if (currentFrameCount % analyzeEveryNFrames == 0 &&
                                                        currentTime - previousAnalysisTime > minAnalysisInterval &&
                                                        lastAnalysisTime.compareAndSet(previousAnalysisTime, currentTime)
                                                    ) {
                                                        val bitmap =
                                                            com.photoframer.vision.ImageConverter.imageProxyToBitmap(
                                                                imageProxy
                                                            )
                                                        if (bitmap != null) {
                                                            val croppedBitmap =
                                                                selectedRatio.toTargetAspectRatio()?.let { targetRatio ->
                                                                    com.photoframer.vision.ImageConverter.centerCropToAspectRatio(
                                                                        bitmap,
                                                                        targetRatio
                                                                    )
                                                                } ?: bitmap
                                                            val scaledBitmap =
                                                                com.photoframer.vision.ImageConverter.scaleBitmapToWidth(
                                                                    croppedBitmap,
                                                                    com.photoframer.vision.StepValidator.VALIDATION_FRAME_WIDTH
                                                                )
                                                            val currentPreview = previewView
                                                            arCorePoseTracker.updateViewport(
                                                                width = currentPreview?.width ?: scaledBitmap.width,
                                                                height = currentPreview?.height ?: scaledBitmap.height,
                                                                rotation = currentPreview?.display?.rotation
                                                                    ?: Surface.ROTATION_0
                                                            )
                                                            val poseSample = arCorePoseTracker.latestPoseSample()
                                                            scope.launch {
                                                                latestPoseSample = poseSample
                                                            }
                                                            viewModel.validateCurrentFrame(
                                                                currentFrame = scaledBitmap,
                                                                currentZoomRatio = CameraZoomController.effectiveZoomRatio(
                                                                    selectedLens,
                                                                    rawZoomRatio
                                                                ),
                                                                cameraPoseSample = poseSample
                                                            )
                                                        }
                                                    }
                                                }
                                            } catch (error: Exception) {
                                                Log.e(TAG, "相机帧分析失败", error)
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                    }

                                val cameraSelector = selectedLens?.let(
                                    CameraLensManager::buildCameraSelector
                                ) ?: CameraSelector.DEFAULT_BACK_CAMERA

                                try {
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageCapture,
                                        imageAnalyzer
                                    )
                                    cameraControl = camera.cameraControl
                                    cameraInfo = camera.cameraInfo
                                    camera.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                                        rawZoomRatio = state.zoomRatio
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "相机绑定失败", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    update = { previewView = it },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (gridEnabled) {
                GridOverlay(modifier = Modifier.fillMaxSize())
            }

            if (selectedRatio != AspectRatioOption.FULL) {
                AspectRatioMaskOverlay(
                    selectedRatio = selectedRatio,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState is CameraUiState.Guiding) {
                val guidingState = uiState as CameraUiState.Guiding

                if (!allStepsCompleted) {
                    guidingState.currentStep?.let { step ->
                        GuidanceOverlay(
                            step = step,
                            validationResult = validationResult
                        )
                    }
                }

                if (!allStepsCompleted) {
                    guidingState.currentStep?.let { step ->
                        TopGuidanceBar(
                            step = step,
                            currentStepIndex = guidingState.currentStepIndex,
                            totalSteps = guidingState.totalSteps,
                            onPreviousStep = { viewModel.previousStep() },
                            onNextStep = { viewModel.nextStep() },
                            isFirstStep = guidingState.isFirstStep,
                            isLastStep = guidingState.isLastStep,
                            validationResult = validationResult,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        )
                    }
                } else {
                    AllStepsCompletedBanner(
                        onTakePhoto = captureGuidedPhoto,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                    )
                }

                if (allStepsCompleted && postCapturePrompt != null) {
                    PostCaptureChoiceOverlay(
                        message = postCapturePrompt?.message.orEmpty(),
                        onViewCandidates = { viewModel.viewOtherCandidatesAfterCapture() },
                        onDismiss = { viewModel.dismissPostCapturePrompt() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 20.dp, end = 20.dp, bottom = 156.dp)
                    )
                }

                if (!allStepsCompleted && needsPoseTracking) {
                    ArCoreStatusChip(
                        status = arCoreStatus,
                        poseSample = latestPoseSample,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 156.dp)
                    )
                }

                if (!allStepsCompleted && showStepSkip) {
                    StepSkipOverlay(
                        onSkip = { viewModel.skipCurrentStep() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 156.dp)
                    )
                }

                if (!allStepsCompleted && newCandidatePrompt != null) {
                    NewCandidateOverlay(
                        message = newCandidatePrompt?.message.orEmpty(),
                        onViewCandidates = { viewModel.viewUpdatedCandidates() },
                        onDismiss = { viewModel.dismissNewCandidatePrompt() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 20.dp, end = 20.dp, bottom = 96.dp)
                    )
                }

                val targetBitmap = viewModel.getCachedImage(guidingState.composition.technique)

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 80.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = targetBitmap != null,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f)
                    ) {
                        if (targetBitmap != null) {
                            val aspectRatio =
                                targetBitmap.width.toFloat() / targetBitmap.height.toFloat()
                            val thumbnailHeight = 120.dp
                            val thumbnailWidth = (120 * aspectRatio).dp

                            Card(
                                modifier = Modifier
                                    .width(thumbnailWidth)
                                    .height(thumbnailHeight)
                                    .border(1.5.dp, PurplePrimary, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = SurfaceDark.copy(alpha = 0.8f)
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        bitmap = targetBitmap.asImageBitmap(),
                                        contentDescription = "参考构图",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "参考图",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (uiState is CameraUiState.Analyzing) {
                LoadingOverlay()
            } else if (uiState is CameraUiState.Error) {
                val errorState = uiState as CameraUiState.Error
                ErrorOverlay(
                    title = errorState.title,
                    message = errorState.message,
                    actionText = errorState.actionText,
                    onRetry = { viewModel.handleErrorAction() }
                )
            }

            countdownValue?.let { value ->
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.displayLarge
                    )
                }
            }

            if (uiState is CameraUiState.Preview) {
                SideToolBar(
                    selectedRatio = selectedRatio,
                    onRatioChange = { selectedRatio = it },
                    selectedTimer = selectedTimer,
                    onTimerChange = { selectedTimer = it },
                    touchScreenPhotoEnabled = touchScreenPhotoEnabled,
                    onTouchScreenPhotoToggle = { touchScreenPhotoEnabled = !touchScreenPhotoEnabled },
                    arExperimentEnabled = arExperimentEnabled,
                    onArExperimentToggle = {
                        val newValue = !arExperimentEnabled
                        arExperimentEnabled = newValue
                        ArCoreSupport.setEnabled(context, newValue)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp, top = 32.dp, bottom = 96.dp)
                )
            }

        }

        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn())
                    .togetherWith(slideOutVertically { it / 3 } + fadeOut())
            },
            contentKey = { it::class },
            label = "bottom_bar"
        ) { state ->
            when (state) {
                is CameraUiState.Preview -> {
                    CameraBottomBar(
                        currentMode = CameraMode.PHOTO,
                        visualStyle = visualStyle,
                        zoomUiState = zoomUiState,
                        onZoomPresetClick = { index ->
                            val preset = zoomPresets.firstOrNull { it.lensIndex == index }
                            if (preset != null) {
                                isZoomRulerVisible = false
                                desiredEffectiveZoom = preset.effectiveZoomRatio
                                if (selectedLensIndex != index) {
                                    selectedLensIndex = index
                                } else {
                                    applyTargetEffectiveZoom(preset.effectiveZoomRatio)
                                }
                            }
                        },
                        onZoomRulerReveal = { revealZoomRuler() },
                        onZoomRulerDrag = { dragAmount ->
                            if (!isZoomRulerVisible) {
                                revealZoomRuler()
                            }
                            val zoomRange = supportedEffectiveZoomRange(selectedLens, cameraInfo, zoomPresets)
                            val nextZoom = (desiredEffectiveZoom + dragAmount * ZOOM_DRAG_SENSITIVITY)
                                .coerceIn(zoomRange.start, zoomRange.endInclusive)
                            applyTargetEffectiveZoom(nextZoom, revealRuler = true)
                        },
                        onZoomRulerDragEnd = { finishZoomInteraction() },
                        onShutterClick = { startCountdownCapture() },
                        lastPhotoThumbnail = lastPhotoThumbnail,
                        isGalleryAvailable = canOpenGallery,
                        onGalleryClick = ::openGallery,
                        canSwitchFacing = availableLenses.any { it.lensFacing != selectedFacing },
                        onFacingSwitch = ::switchCameraFacing,
                        onLongPressStart = startBurst,
                        onLongPressEnd = stopBurst,
                        burstCount = burstCount
                    )
                }

                is CameraUiState.Analyzing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .navigationBarsPadding()
                            .padding(top = 20.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextButton(
                            onClick = { viewModel.cancelAnalysis() },
                            modifier = Modifier.padding(bottom = 24.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "取消",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(48.dp))
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {}
                            Box(modifier = Modifier.size(48.dp))
                        }
                    }
                }

                is CameraUiState.Candidates -> {
                    CandidatesBottomPanel(
                        totalTechniques = state.totalTechniques,
                        completedCount = state.completedCount,
                        applicableCount = state.applicableCount,
                        totalTimeMs = state.totalTimeMs,
                        compositions = state.compositions,
                        postCaptureHint = state.postCaptureHint,
                        onStartGuidance = { composition -> viewModel.selectComposition(composition) },
                        getCompositionBitmap = { technique -> viewModel.getCachedImage(technique) },
                        onSaveComposition = { technique ->
                            val bitmap = viewModel.getCachedImage(technique)
                            if (bitmap != null) {
                                scope.launch {
                                    ImageSaver.saveImageToGallery(context, bitmap, "ai_ref_$technique")
                                }
                            }
                        },
                        onRescan = { viewModel.backToPreview() }
                    )
                }

                is CameraUiState.Guiding -> {
                    GuidingBottomBar(
                        onCaptureClick = captureGuidedPhoto,
                        onBackClick = { viewModel.backToCandidates() },
                        isCaptureEnabled = allStepsCompleted
                    )
                }

                else -> {
                    CameraBottomBar(
                        currentMode = CameraMode.PHOTO,
                        visualStyle = visualStyle,
                        zoomUiState = zoomUiState,
                        onZoomPresetClick = { index ->
                            val preset = zoomPresets.firstOrNull { it.lensIndex == index }
                            if (preset != null) {
                                isZoomRulerVisible = false
                                desiredEffectiveZoom = preset.effectiveZoomRatio
                                if (selectedLensIndex != index) {
                                    selectedLensIndex = index
                                } else {
                                    applyTargetEffectiveZoom(preset.effectiveZoomRatio)
                                }
                            }
                        },
                        onZoomRulerReveal = { revealZoomRuler() },
                        onZoomRulerDrag = { dragAmount ->
                            if (!isZoomRulerVisible) {
                                revealZoomRuler()
                            }
                            val zoomRange = supportedEffectiveZoomRange(selectedLens, cameraInfo, zoomPresets)
                            val nextZoom = (desiredEffectiveZoom + dragAmount * ZOOM_DRAG_SENSITIVITY)
                                .coerceIn(zoomRange.start, zoomRange.endInclusive)
                            applyTargetEffectiveZoom(nextZoom, revealRuler = true)
                        },
                        onZoomRulerDragEnd = { finishZoomInteraction() },
                        onShutterClick = {},
                        lastPhotoThumbnail = lastPhotoThumbnail,
                        isGalleryAvailable = canOpenGallery,
                        onGalleryClick = ::openGallery,
                        canSwitchFacing = availableLenses.any { it.lensFacing != selectedFacing },
                        onFacingSwitch = ::switchCameraFacing
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    title: String,
    message: String,
    actionText: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = ErrorRed
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun StepSkipOverlay(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "这一步有点卡住了",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "可以先跳过，后面继续拍摄",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = onSkip,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("跳过此步")
            }
        }
    }
}

@Composable
private fun PostCaptureChoiceOverlay(
    message: String,
    onViewCandidates: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "当前方案已拍完",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("留在当前方案", color = Color.White)
                }
                Button(
                    onClick = onViewCandidates,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看其他方案", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun NewCandidateOverlay(
    message: String,
    onViewCandidates: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark.copy(alpha = 0.90f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f, fill = false)
            )
            TextButton(onClick = onDismiss) {
                Text("稍后", color = TextSecondary)
            }
            Button(
                onClick = onViewCandidates,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("查看", color = Color.White)
            }
        }
    }
}

@Composable
private fun ArCoreStatusChip(
    status: ArCoreStatus,
    poseSample: CameraPoseSample?,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (status.state) {
        ArCoreRuntimeState.Ready -> PurplePrimary.copy(alpha = 0.22f) to Color.White
        ArCoreRuntimeState.Installing,
        ArCoreRuntimeState.Checking -> SurfaceDark.copy(alpha = 0.92f) to Color.White
        ArCoreRuntimeState.Unsupported,
        ArCoreRuntimeState.Failed -> ErrorRed.copy(alpha = 0.18f) to Color.White
        ArCoreRuntimeState.Idle -> SurfaceDark.copy(alpha = 0.80f) to Color.White.copy(alpha = 0.82f)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statusDot = when (status.state) {
                ArCoreRuntimeState.Ready -> PurplePrimary
                ArCoreRuntimeState.Installing,
                ArCoreRuntimeState.Checking -> Color(0xFFFFC857)
                ArCoreRuntimeState.Unsupported,
                ArCoreRuntimeState.Failed -> ErrorRed
                ArCoreRuntimeState.Idle -> Color.White.copy(alpha = 0.45f)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusDot, RoundedCornerShape(999.dp))
            )
            Column {
                Text(
                    text = status.message,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium
                )
                val trackingDetail = poseSample?.let { sample ->
                    when (sample.source) {
                        CameraPoseSource.ARCORE -> "追踪源: ARCore"
                        CameraPoseSource.DEVICE_MOTION -> "追踪源: DeviceMotion"
                    }
                }
                listOfNotNull(status.detail?.takeIf { it.isNotBlank() }, trackingDetail).forEach { detail ->
                    Text(
                        text = detail,
                        color = textColor.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AspectRatioMaskOverlay(
    selectedRatio: AspectRatioOption,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val targetRatio = when (selectedRatio) {
            AspectRatioOption.RATIO_4_3 -> 4f / 3f
            AspectRatioOption.RATIO_1_1 -> 1f
            AspectRatioOption.FULL -> maxWidth.value / maxHeight.value
        }
        val containerRatio = maxWidth.value / maxHeight.value
        val maskColor = Color.Black.copy(alpha = 0.36f)
        val borderColor = Color.White.copy(alpha = 0.45f)

        if (containerRatio > targetRatio) {
            val visibleWidth: Dp = maxHeight * targetRatio
            val sideMask = (maxWidth - visibleWidth) / 2f

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sideMask)
                    .background(maskColor)
                    .align(Alignment.CenterStart)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sideMask)
                    .background(maskColor)
                    .align(Alignment.CenterEnd)
            )
            Box(
                modifier = Modifier
                    .width(visibleWidth)
                    .fillMaxHeight()
                    .border(1.dp, borderColor)
                    .align(Alignment.Center)
            )
        } else {
            val visibleHeight: Dp = maxWidth / targetRatio
            val topMask = (maxHeight - visibleHeight) / 2f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topMask)
                    .background(maskColor)
                    .align(Alignment.TopCenter)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topMask)
                    .background(maskColor)
                    .align(Alignment.BottomCenter)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(visibleHeight)
                    .border(1.dp, borderColor)
                    .align(Alignment.Center)
            )
        }
    }
}

private fun captureAndAnalyze(
    context: Context,
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    onImageCaptured: (File) -> Unit
) {
    if (imageCapture == null) {
        Log.e(TAG, "ImageCapture 未初始化")
        return
    }

    val photoFile = File(
        context.cacheDir,
        "capture_${System.currentTimeMillis()}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d(TAG, "图片保存成功: ${photoFile.absolutePath}")
                onImageCaptured(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "拍照失败", exception)
            }
        }
    )
}

private fun applyAspectRatioToCapturedFile(
    context: Context,
    sourceFile: File,
    ratioOption: AspectRatioOption
): File {
    val targetRatio = ratioOption.toTargetAspectRatio() ?: return sourceFile

    if (ratioOption == AspectRatioOption.FULL) {
        return sourceFile
    }

    val bitmap = ImageFileDecoder.decodeBitmapRespectingExif(sourceFile) ?: return sourceFile
    val croppedBitmap = try {
        com.photoframer.vision.ImageConverter.centerCropToAspectRatio(bitmap, targetRatio)
    } catch (_: Exception) {
        return sourceFile
    }

    val outFile = File(context.cacheDir, "capture_ratio_${System.currentTimeMillis()}.jpg")
    return try {
        FileOutputStream(outFile).use { output ->
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        outFile
    } catch (_: Exception) {
        sourceFile
    } finally {
        if (croppedBitmap !== bitmap) {
            bitmap.recycle()
            croppedBitmap.recycle()
        }
    }
}

private fun AspectRatioOption.toTargetAspectRatio(): Float? {
    return when (this) {
        AspectRatioOption.RATIO_4_3 -> 4f / 3f
        AspectRatioOption.RATIO_1_1 -> 1f
        AspectRatioOption.FULL -> null
    }
}
