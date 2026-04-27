package com.photoframer.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photoframer.arcore.ArCoreRuntimeState
import com.photoframer.arcore.ArCorePoseTracker
import com.photoframer.arcore.ArCoreStatus
import com.photoframer.arcore.ArCoreSupport
import com.photoframer.arcore.findActivity
import com.photoframer.guidance.isViewpointAction
import com.photoframer.ui.components.AllStepsCompletedBanner
import com.photoframer.ui.components.AspectRatioOption
import com.photoframer.ui.components.CameraBottomBar
import com.photoframer.ui.components.CameraEntryMode
import com.photoframer.ui.components.CameraMode
import com.photoframer.ui.components.CameraTopBar
import com.photoframer.ui.components.CameraVisualStyle
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
import com.photoframer.utils.ImageFileDecoder
import com.photoframer.utils.ImageSaver
import com.photoframer.viewmodel.CameraViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "CameraScreen"
private const val FOCUS_UI_AUTO_HIDE_DELAY_MS = 1800L

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val allStepsCompleted by viewModel.allStepsCompleted.collectAsState()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val arCorePoseTracker = remember(context.applicationContext) {
        ArCorePoseTracker(context.applicationContext)
    }

    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var gridEnabled by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var lastPhotoThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    var touchScreenPhotoEnabled by remember { mutableStateOf(false) }
    var backgroundBlurEnabled by remember { mutableStateOf(false) }
    var selectedRatio by remember { mutableStateOf(AspectRatioOption.FULL) }
    var selectedTimer by remember { mutableStateOf(CaptureTimer.OFF) }
    var countdownValue by remember { mutableStateOf<Int?>(null) }
    var burstCount by remember { mutableIntStateOf(0) }
    var isBursting by remember { mutableStateOf(false) }
    var activeEntryMode by remember { mutableStateOf(CameraEntryMode.NONE) }
    var visualStyle by remember { mutableStateOf(CameraVisualStyle.DEFAULT) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var isFocusUiVisible by remember { mutableStateOf(false) }
    var focusUiInteractionVersion by remember { mutableIntStateOf(0) }
    var isAdjustingExposure by remember { mutableStateOf(false) }
    var exposureCompensationRange: IntRange by remember {
        mutableStateOf(IntRange(0, 0))
    }
    var exposureCompensationIndex by remember { mutableIntStateOf(0) }

    var frameCounter by remember { mutableIntStateOf(0) }
    var lastAnalysisTime by remember { mutableStateOf(0L) }
    var arCoreStatus by remember { mutableStateOf(ArCoreSupport.idleStatus()) }
    var hasRequestedArCoreInstall by remember { mutableStateOf(false) }

    val guidingStep = (uiState as? CameraUiState.Guiding)?.currentStep
    val needsArCore = guidingStep?.isViewpointAction() == true
    val analyzeEveryNFrames = if (needsArCore) 6 else 4
    val minAnalysisInterval = if (needsArCore) 90L else 66L

    androidx.compose.runtime.DisposableEffect(
        lifecycleOwner,
        needsArCore,
        guidingStep?.stepOrder
    ) {
        if (!needsArCore) {
            arCoreStatus = ArCoreSupport.idleStatus()
            hasRequestedArCoreInstall = false
            arCorePoseTracker.stop()
            return@DisposableEffect onDispose {}
        }

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

        refreshArCoreStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshArCoreStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            arCorePoseTracker.stop()
        }
    }

    LaunchedEffect(
        needsArCore,
        guidingStep?.stepOrder,
        arCoreStatus.state,
        previewView?.width,
        previewView?.height,
        useFrontCamera
    ) {
        if (!needsArCore || useFrontCamera) {
            arCorePoseTracker.stop()
            return@LaunchedEffect
        }

        val currentPreview = previewView
        arCorePoseTracker.start(
            activity = context.findActivity(),
            preferArCore = arCoreStatus.isReady,
            viewportWidth = currentPreview?.width ?: 0,
            viewportHeight = currentPreview?.height ?: 0,
            displayRotation = currentPreview?.display?.rotation ?: Surface.ROTATION_0
        )
    }

    LaunchedEffect(uiState) {
        if (uiState is CameraUiState.Preview) {
            activeEntryMode = CameraEntryMode.NONE
            countdownValue = null
            isBursting = false
            burstCount = 0
        }
    }

    fun refreshFocusUiAutoHide() {
        focusUiInteractionVersion += 1
    }

    fun syncExposureState(info: CameraInfo?) {
        val exposureState = info?.exposureState
        exposureCompensationRange = if (exposureState != null) {
            exposureState.exposureCompensationRange.lower..
                exposureState.exposureCompensationRange.upper
        } else {
            IntRange(0, 0)
        }
        exposureCompensationIndex = exposureState?.exposureCompensationIndex ?: 0
    }

    fun showFocusUi(tapOffset: Offset) {
        focusPoint = tapOffset
        isFocusUiVisible = true
        refreshFocusUiAutoHide()
    }

    LaunchedEffect(isFocusUiVisible, focusUiInteractionVersion, isAdjustingExposure) {
        if (!isFocusUiVisible || isAdjustingExposure) {
            return@LaunchedEffect
        }

        delay(FOCUS_UI_AUTO_HIDE_DELAY_MS)
        isFocusUiVisible = false
    }

    fun saveCapturedPhoto(file: File, namePrefix: String) {
        val adjustedFile = applyAspectRatioToCapturedFile(context, file, selectedRatio)
        val bitmap = ImageFileDecoder.decodeBitmapRespectingExif(adjustedFile) ?: return
        scope.launch {
            lastPhotoThumbnail = bitmap
            ImageSaver.saveImageToGallery(context, bitmap, namePrefix)
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
        captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
            saveCapturedPhoto(file, "photoframer_final")
            scope.launch {
                viewModel.backToPreview()
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
                .onSizeChanged { previewSize = it }
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val currentZoom = zoomRatio
                        val newZoom = (currentZoom * zoom).coerceIn(1f, 8f)
                        if (newZoom != currentZoom) {
                            cameraControl?.setZoomRatio(newZoom)
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
                .pointerInput(
                    previewView,
                    cameraControl,
                    uiState,
                    touchScreenPhotoEnabled,
                    countdownValue,
                    isBursting
                ) {
                    if (
                        uiState is CameraUiState.Preview &&
                        !touchScreenPhotoEnabled &&
                        countdownValue == null &&
                        !isBursting
                    ) {
                        detectTapGestures { tapOffset ->
                            val currentPreviewView = previewView ?: return@detectTapGestures
                            val currentCameraControl = cameraControl ?: return@detectTapGestures
                            val meteringPoint = currentPreviewView.meteringPointFactory
                                .createPoint(tapOffset.x, tapOffset.y)
                            val focusAction = FocusMeteringAction.Builder(
                                meteringPoint,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            )
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()

                            currentCameraControl.startFocusAndMetering(focusAction)
                            syncExposureState(cameraInfo)
                            showFocusUi(tapOffset)
                        }
                    }
                }
        ) {
            key(useFrontCamera, flashMode) {
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
                                            val currentState = viewModel.uiState.value
                                            if (currentState is CameraUiState.Guiding) {
                                                frameCounter += 1
                                                val currentTime = System.currentTimeMillis()
                                                if (frameCounter % analyzeEveryNFrames == 0 &&
                                                    currentTime - lastAnalysisTime > minAnalysisInterval
                                                ) {
                                                    lastAnalysisTime = currentTime
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
                                                        viewModel.validateCurrentFrame(
                                                            currentFrame = scaledBitmap,
                                                            currentZoomRatio = zoomRatio,
                                                            cameraPoseSample = arCorePoseTracker.latestPoseSample()
                                                        )
                                                    }
                                                }
                                            }
                                            imageProxy.close()
                                        }
                                    }

                                val cameraSelector = if (useFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageCapture,
                                        imageAnalyzer
                                    )
                                    previewView = cameraPreviewView
                                    cameraControl = camera.cameraControl
                                    cameraInfo = camera.cameraInfo
                                    syncExposureState(camera.cameraInfo)
                                    camera.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                                        zoomRatio = state.zoomRatio
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "相机绑定失败", e)
                                }
                                Unit
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

            var focusOverlaySettled by remember(focusUiInteractionVersion) { mutableStateOf(false) }
            LaunchedEffect(focusUiInteractionVersion, isFocusUiVisible) {
                if (isFocusUiVisible && focusPoint != null) {
                    focusOverlaySettled = false
                    delay(85)
                    if (isFocusUiVisible) {
                        focusOverlaySettled = true
                    }
                }
            }

            val showFocusOverlay = uiState is CameraUiState.Preview && focusPoint != null
            val focusOverlayAlpha by animateFloatAsState(
                targetValue = when {
                    !showFocusOverlay || !isFocusUiVisible -> 0f
                    focusOverlaySettled -> 1f
                    else -> 0.58f
                },
                animationSpec = tween(durationMillis = if (isFocusUiVisible) 180 else 220),
                label = "focus_overlay_alpha"
            )
            val focusOverlayScale by animateFloatAsState(
                targetValue = when {
                    !showFocusOverlay || !isFocusUiVisible -> 0.97f
                    focusOverlaySettled -> 1f
                    else -> 1.1f
                },
                animationSpec = tween(durationMillis = if (isFocusUiVisible) 180 else 220),
                label = "focus_overlay_scale"
            )

            if (showFocusOverlay) {
                focusPoint?.let { point ->
                    FocusExposureOverlay(
                        tapPoint = point,
                        containerSize = previewSize,
                        currentExposureIndex = exposureCompensationIndex,
                        exposureRange = exposureCompensationRange,
                        onExposureChange = { newIndex ->
                            exposureCompensationIndex = newIndex
                            cameraControl?.setExposureCompensationIndex(newIndex)
                        },
                        onExposureAdjustStart = {
                            isAdjustingExposure = true
                        },
                        onExposureAdjustEnd = {
                            isAdjustingExposure = false
                            refreshFocusUiAutoHide()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = focusOverlayAlpha
                                scaleX = focusOverlayScale
                                scaleY = focusOverlayScale
                            }
                    )
                }
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
                            onNextStep = {
                                if (!guidingState.isLastStep) {
                                    viewModel.nextStep()
                                }
                            },
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

                if (!allStepsCompleted && guidingStep?.isViewpointAction() == true) {
                    ArCoreStatusChip(
                        status = arCoreStatus,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 156.dp)
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
                    onRetry = { viewModel.backToPreview() }
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
                    backgroundBlurEnabled = backgroundBlurEnabled,
                    onBackgroundBlurToggle = { backgroundBlurEnabled = !backgroundBlurEnabled },
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
                        currentZoom = zoomRatio,
                        onZoomChange = { cameraControl?.setZoomRatio(it) },
                        onShutterClick = { startCountdownCapture() },
                        onCameraSwitch = { useFrontCamera = !useFrontCamera },
                        lastPhotoThumbnail = lastPhotoThumbnail,
                        isGalleryAvailable = false,
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
                        applicableCount = state.applicableCount,
                        totalTimeMs = state.totalTimeMs,
                        compositions = state.compositions,
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
                        currentZoom = zoomRatio,
                        onZoomChange = { cameraControl?.setZoomRatio(it) },
                        onShutterClick = {},
                        onCameraSwitch = { useFrontCamera = !useFrontCamera },
                        lastPhotoThumbnail = lastPhotoThumbnail,
                        isGalleryAvailable = false
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
private fun ArCoreStatusChip(
    status: ArCoreStatus,
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
                status.detail?.takeIf { it.isNotBlank() }?.let { detail ->
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
            AspectRatioOption.RATIO_4_3 -> 3f / 4f
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

@Composable
private fun FocusExposureOverlay(
    tapPoint: Offset,
    containerSize: IntSize,
    currentExposureIndex: Int,
    exposureRange: IntRange,
    onExposureChange: (Int) -> Unit,
    onExposureAdjustStart: () -> Unit,
    onExposureAdjustEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val focusFrameSize = 54.dp
    val sliderWidth = 30.dp
    val sliderHeight = 104.dp
    val focusSizePx = with(density) { focusFrameSize.roundToPx() }
    val sliderWidthPx = with(density) { sliderWidth.roundToPx() }
    val sliderHeightPx = with(density) { sliderHeight.roundToPx() }
    val sliderGapPx = with(density) { 8.dp.roundToPx() }
    val sliderSupported = exposureRange.first != exposureRange.last

    Box(modifier = modifier) {
        val focusOffsetX = (tapPoint.x - focusSizePx / 2f)
            .coerceIn(0f, (containerSize.width - focusSizePx).coerceAtLeast(0).toFloat())
        val focusOffsetY = (tapPoint.y - focusSizePx / 2f)
            .coerceIn(0f, (containerSize.height - focusSizePx).coerceAtLeast(0).toFloat())

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = focusOffsetX.toInt(),
                        y = focusOffsetY.toInt()
                    )
                }
                .size(focusFrameSize)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.2.dp.toPx()
                val cornerLength = size.minDimension * 0.24f
                val color = Color.White

                drawLine(color, Offset(0f, cornerLength), Offset(0f, 0f), strokeWidth)
                drawLine(color, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth)
                drawLine(color, Offset(size.width - cornerLength, 0f), Offset(size.width, 0f), strokeWidth)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, cornerLength), strokeWidth)
                drawLine(color, Offset(0f, size.height - cornerLength), Offset(0f, size.height), strokeWidth)
                drawLine(color, Offset(0f, size.height), Offset(cornerLength, size.height), strokeWidth)
                drawLine(
                    color,
                    Offset(size.width - cornerLength, size.height),
                    Offset(size.width, size.height),
                    strokeWidth
                )
                drawLine(
                    color,
                    Offset(size.width, size.height - cornerLength),
                    Offset(size.width, size.height),
                    strokeWidth
                )
            }
        }

        if (sliderSupported && containerSize != IntSize.Zero) {
            val rawSliderX = if (tapPoint.x + focusSizePx / 2f + sliderGapPx + sliderWidthPx <= containerSize.width) {
                tapPoint.x + focusSizePx / 2f + sliderGapPx
            } else {
                tapPoint.x - focusSizePx / 2f - sliderGapPx - sliderWidthPx
            }
            val sliderX = rawSliderX
                .coerceIn(0f, (containerSize.width - sliderWidthPx).coerceAtLeast(0).toFloat())
            val sliderY = (tapPoint.y - sliderHeightPx / 2f)
                .coerceIn(0f, (containerSize.height - sliderHeightPx).coerceAtLeast(0).toFloat())

            ExposureAdjustmentSlider(
                currentValue = currentExposureIndex,
                valueRange = exposureRange,
                onValueChange = onExposureChange,
                onInteractionStart = onExposureAdjustStart,
                onInteractionEnd = onExposureAdjustEnd,
                modifier = Modifier.offset {
                    IntOffset(sliderX.toInt(), sliderY.toInt())
                }
            )
        }
    }
}

@Composable
private fun ExposureAdjustmentSlider(
    currentValue: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val sliderWidth = 30.dp
    val sliderHeight = 104.dp
    val topPadding = 10.dp
    val bottomPadding = 8.dp
    val iconSize = 11.dp
    val thumbSize = 10.dp
    val trackWidth = 2.dp
    val sliderHeightPx = with(density) { sliderHeight.toPx() }
    val topPaddingPx = with(density) { topPadding.toPx() }
    val bottomPaddingPx = with(density) { bottomPadding.toPx() }
    val iconSizePx = with(density) { iconSize.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val trackTopPx = topPaddingPx + iconSizePx + with(density) { 8.dp.toPx() }
    val trackBottomPx = sliderHeightPx - bottomPaddingPx
    val thumbTravelPx = (trackBottomPx - trackTopPx - thumbSizePx).coerceAtLeast(1f)

    fun thumbOffsetToValue(offset: Float): Int {
        val normalized = 1f - (offset / thumbTravelPx).coerceIn(0f, 1f)
        val rawValue = valueRange.first + normalized * (valueRange.last - valueRange.first)
        return rawValue.toInt().coerceIn(valueRange.first, valueRange.last)
    }

    val normalizedValue = if (valueRange.first == valueRange.last) {
        0.5f
    } else {
        (currentValue - valueRange.first).toFloat() / (valueRange.last - valueRange.first).toFloat()
    }.coerceIn(0f, 1f)
    val thumbOffset = thumbTravelPx * (1f - normalizedValue)

    Box(
        modifier = modifier
            .size(width = sliderWidth, height = sliderHeight)
            .border(1.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .pointerInput(valueRange) {
                var dragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { startOffset ->
                        onInteractionStart()
                        dragY = (startOffset.y - trackTopPx - thumbSizePx / 2f)
                            .coerceIn(0f, thumbTravelPx)
                        onValueChange(thumbOffsetToValue(dragY))
                    },
                    onVerticalDrag = { _, dragAmount ->
                        dragY = (dragY + dragAmount).coerceIn(0f, thumbTravelPx)
                        onValueChange(thumbOffsetToValue(dragY))
                    },
                    onDragEnd = onInteractionEnd,
                    onDragCancel = onInteractionEnd
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(
            modifier = Modifier
                .padding(top = topPadding)
                .size(iconSize)
        ) {
            val strokeWidth = 1.35.dp.toPx()
            val coreRadius = size.minDimension * 0.19f
            val rayInnerRadius = size.minDimension * 0.34f
            val rayOuterRadius = size.minDimension * 0.48f
            val c = center

            drawCircle(
                color = Color.White.copy(alpha = 0.98f),
                radius = coreRadius,
                center = c
            )

            repeat(8) { index ->
                val angle = (index * 45f) * (Math.PI / 180f).toFloat()
                val start = Offset(
                    x = c.x + kotlin.math.cos(angle) * rayInnerRadius,
                    y = c.y + kotlin.math.sin(angle) * rayInnerRadius
                )
                val end = Offset(
                    x = c.x + kotlin.math.cos(angle) * rayOuterRadius,
                    y = c.y + kotlin.math.sin(angle) * rayOuterRadius
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.98f),
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(top = topPadding + iconSize + 10.dp)
                .width(trackWidth)
                .height(with(density) { (trackBottomPx - trackTopPx).toDp() })
                .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(999.dp))
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = 0,
                        y = (trackTopPx + thumbOffset).toInt()
                    )
                }
                .size(thumbSize)
                .background(Color.White, RoundedCornerShape(999.dp))
        )
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
