package com.photoframer.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photoframer.ui.components.*
import com.photoframer.ui.state.CameraUiState
import com.photoframer.ui.theme.*
import com.photoframer.viewmodel.CameraViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.util.concurrent.Executors

private const val TAG = "CameraScreen"

/**
 * 相机主界面 - 专业相机风格
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val allStepsCompleted by viewModel.allStepsCompleted.collectAsState()
    
    // 相机相关
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // ========== 相机功能状态 ==========
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var gridEnabled by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var lastPhotoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    
    // 帧分析计数器（每隔N帧分析一次，避免过于频繁）
    var frameCounter by remember { mutableStateOf(0) }
    val analyzeEveryNFrames = 10  // 每10帧分析一次
    
    // 上次分析时间（限制分析频率）
    var lastAnalysisTime by remember { mutableStateOf(0L) }
    val minAnalysisInterval = 100L  // 提升到 100ms 以增强实时性

    val captureGuidedPhoto = {
        captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                lastPhotoThumbnail = bitmap
                kotlinx.coroutines.GlobalScope.launch {
                    com.photoframer.utils.ImageSaver.saveImageToGallery(context, bitmap, "photoframer_final")
                }
            }
            viewModel.backToPreview()
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. 顶部栏（纯黑背景，独立区域）
        // 在分析中时也显示顶部栏，但隐藏 AI 按钮
        if (uiState !is CameraUiState.Guiding) {
            CameraTopBar(
                flashMode = flashMode,
                onFlashModeChange = { newMode ->
                    flashMode = newMode
                    imageCapture?.flashMode = newMode.toImageCaptureFlashMode()
                },
                gridEnabled = gridEnabled,
                onGridToggle = { gridEnabled = !gridEnabled },
                showAnalysisButtons = uiState !is CameraUiState.Analyzing,
                onInFrameClick = {
                    captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
                        viewModel.analyzeInFrameComposition(file)
                    }
                },
                onAiClick = {
                    captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
                        viewModel.analyzeImage(file)
                    }
                }
            )
        }
        
        // 2. 中间部分：相机预览区域 (权重 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()  // 防止预览内容溢出遮挡顶部栏
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val currentZoom = zoomRatio
                        val newZoom = (currentZoom * zoom).coerceIn(1f, 8f)
                        if (newZoom != currentZoom) {
                            cameraControl?.setZoomRatio(newZoom)
                        }
                    }
                }
        ) {
            // A. 相机预览 View
            // 使用 key() 强制重组：当 useFrontCamera 或 flashMode 变化时，重新创建 AndroidView 并绑定相机
            key(useFrontCamera, flashMode) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            // 使用 TextureView (兼容模式) 避免黑屏问题
                            implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
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
                                                frameCounter++
                                                val currentTime = System.currentTimeMillis()
                                                if (frameCounter % analyzeEveryNFrames == 0 &&
                                                    currentTime - lastAnalysisTime > minAnalysisInterval) {
                                                    lastAnalysisTime = currentTime
                                                    val bitmap = com.photoframer.vision.ImageConverter.imageProxyToBitmap(imageProxy)
                                                    if (bitmap != null) {
                                                        // 统一到固定宽度坐标系，保证验证阈值与引导 UI 一致
                                                        val scaledBitmap = com.photoframer.vision.ImageConverter.scaleBitmapToWidth(
                                                            bitmap,
                                                            com.photoframer.vision.StepValidator.VALIDATION_FRAME_WIDTH
                                                        )
                                                        viewModel.validateCurrentFrame(scaledBitmap, zoomRatio)
                                                    }
                                                }
                                            }
                                            imageProxy.close()
                                        }
                                    }
                                
                                val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer)
                                    cameraControl = camera.cameraControl
                                    camera.cameraInfo.zoomState.observe(lifecycleOwner) { state -> zoomRatio = state.zoomRatio }
                                } catch (e: Exception) {
                                    Log.e(TAG, "相机绑定失败", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // 网格线叠加层
            if (gridEnabled) {
                GridOverlay(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // B. 覆盖层 (Guidance Arrows, Glow) - 只在预览区显示
            if (uiState is CameraUiState.Guiding) {
                val guidingState = uiState as CameraUiState.Guiding
                
                // 动态箭头
                if (!allStepsCompleted) {
                    guidingState.currentStep?.let { step -> 
                        GuidanceOverlay(
                            step = step,
                            validationResult = validationResult
                        ) 
                    }
                }
                
                // 顶部引导条 (悬浮在预览区顶部)
                if (!allStepsCompleted) {
                    guidingState.currentStep?.let { step ->
                        TopGuidanceBar(
                            step = step,
                            currentStepIndex = guidingState.currentStepIndex,
                            totalSteps = guidingState.totalSteps,
                            onPreviousStep = { viewModel.previousStep() },
                            onNextStep = { 
                                if (guidingState.isLastStep) {
                                    /* Handle in bottom panel */
                                } else {
                                    viewModel.nextStep()
                                }
                            },
                            isFirstStep = guidingState.isFirstStep,
                            isLastStep = guidingState.isLastStep,
                            validationResult = validationResult,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                        )
                    }
                } else {
                     AllStepsCompletedBanner(
                        onTakePhoto = captureGuidedPhoto,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                    )
                }

                // 左下角缩略图（按原比例显示）
                val targetBitmap = viewModel.getCachedImage(guidingState.composition.technique)
                androidx.compose.animation.AnimatedVisibility(
                    visible = targetBitmap != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 80.dp)
                ) {
                    if (targetBitmap != null) {
                        val aspectRatio = targetBitmap.width.toFloat() / targetBitmap.height.toFloat()
                        val thumbnailHeight = 120.dp
                        val thumbnailWidth = (120 * aspectRatio).dp
                        
                        Card(
                            modifier = Modifier
                                .width(thumbnailWidth)
                                .height(thumbnailHeight)
                                .border(1.5.dp, PurplePrimary, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.8f))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = targetBitmap.asImageBitmap(),
                                    contentDescription = "参考构图",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                
                                // 底部标签
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
            } else if (uiState is CameraUiState.Analyzing) {
                LoadingOverlay()
            } else if (uiState is CameraUiState.Error) {
                val errorState = uiState as CameraUiState.Error
                ErrorOverlay(
                    message = errorState.message,
                    onRetry = { viewModel.backToPreview() }
                )
            }
            
            // 变焦控制（悬浮在预览区底部）- 仅在预览和候选模式显示
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState is CameraUiState.Preview || uiState is CameraUiState.Candidates,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                ZoomSelector(
                    currentZoom = zoomRatio,
                    onZoomChange = { cameraControl?.setZoomRatio(it) }
                )
            }
        }

        // 3. 底部控制栏 — 使用 AnimatedContent 实现过渡动画
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
                    // 预览模式：专业相机底部栏
                    CameraBottomBar(
                        currentMode = CameraMode.PHOTO,
                        onShutterClick = {
                            // 普通拍照
                            captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
                                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                if (bitmap != null) {
                                    lastPhotoThumbnail = bitmap
                                    kotlinx.coroutines.GlobalScope.launch {
                                        com.photoframer.utils.ImageSaver.saveImageToGallery(context, bitmap, "photoframer_${System.currentTimeMillis()}")
                                    }
                                }
                            }
                        },
                        onCameraSwitch = { useFrontCamera = !useFrontCamera },
                        lastPhotoThumbnail = lastPhotoThumbnail,
                        isGalleryAvailable = false
                    )
                }
                
                is CameraUiState.Analyzing -> {
                    // 分析中：使用与 Preview 完全相同的布局结构
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .navigationBarsPadding()
                            .padding(top = 20.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. 取消按钮区域
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
                        
                        // 2. 主控制行：与快门行完全相同的布局
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
                            ) { }
                            Box(modifier = Modifier.size(48.dp))
                        }
                    }
                }
                
                is CameraUiState.Candidates -> {
                    // 候选方案模式
                    CandidatesBottomPanel(
                        applicableCount = state.applicableCount,
                        totalTimeMs = state.totalTimeMs,
                        compositions = state.compositions,
                        onStartGuidance = { comp -> viewModel.selectComposition(comp) },
                        getCompositionBitmap = { technique -> viewModel.getCachedImage(technique) },
                        onSaveComposition = { technique ->
                            val bitmap = viewModel.getCachedImage(technique)
                            if (bitmap != null) {
                                kotlinx.coroutines.GlobalScope.launch {
                                    com.photoframer.utils.ImageSaver.saveImageToGallery(context, bitmap, "ai_ref_$technique")
                                }
                            }
                        },
                        onRescan = { viewModel.backToPreview() }
                    )
                }
                
                is CameraUiState.Guiding -> {
                    // 引导模式
                    GuidingBottomBar(
                        onCaptureClick = captureGuidedPhoto,
                        onBackClick = { viewModel.backToCandidates() },
                        isCaptureEnabled = allStepsCompleted
                    )
                }
                
                else -> {
                    // 错误或其他状态：显示基础底部栏
                    CameraBottomBar(
                        currentMode = CameraMode.PHOTO,
                        onShutterClick = { },
                        onCameraSwitch = { useFrontCamera = !useFrontCamera },
                        lastPhotoThumbnail = lastPhotoThumbnail,
                        isGalleryAvailable = false
                    )
                }
            }
        }
    }
}

// 移除未使用的旧 Overlay 函数
// private fun PreviewOverlay(...)
// private fun CandidatesOverlay(...)
// private fun GuidingOverlay(...)

/**
 * 错误覆盖层
 */
@Composable
private fun ErrorOverlay(
    message: String,
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
                text = "出错了",
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurplePrimary
                )
            ) {
                Text("重试")
            }
        }
    }
}

/**
 * 拍照并分析
 */
private fun captureAndAnalyze(
    context: Context,
    imageCapture: ImageCapture?,
    executor: java.util.concurrent.ExecutorService,
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
