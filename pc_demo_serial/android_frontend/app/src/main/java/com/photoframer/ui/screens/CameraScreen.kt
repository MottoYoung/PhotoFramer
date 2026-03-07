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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 相机主界面
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    
    // 相机相关
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // 帧分析计数器（每隔N帧分析一次，避免过于频繁）
    var frameCounter by remember { mutableStateOf(0) }
    val analyzeEveryNFrames = 10  // 每10帧分析一次
    
    // 上次分析时间（限制分析频率）
    var lastAnalysisTime by remember { mutableStateOf(0L) }
    val minAnalysisInterval = 100L  // 提升到 100ms 以增强实时性
    
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. 上半部分：相机预览区域 (权重 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val currentZoom = zoomRatio
                        // zoom is a multiplier (e.g. 1.1f for 10% zoom in)
                        // Adjust sensitivity if needed, but passing directly usually works well for cameras
                        val newZoom = (currentZoom * zoom).coerceIn(1f, 8f) // Assuming max zoom 8x
                        if (newZoom != currentZoom) {
                            cameraControl?.setZoomRatio(newZoom)
                            // zoomRatio will be updated via observation, but strictly we can optimistically update
                        }
                    }
                }
        ) {
            // A. 相机预览 View
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
                                                    // 降低分辨率以提升处理速度 (480 -> 360)
                                                    val scaledBitmap = com.photoframer.vision.ImageConverter.scaleBitmap(bitmap, 360)
                                                    viewModel.validateCurrentFrame(scaledBitmap, zoomRatio)
                                                }
                                            }
                                        }
                                        imageProxy.close()
                                    }
                                }
                            
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
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

            // B. 覆盖层 (Guidance Arrows, Glow) - 只在预览区显示
            if (uiState is CameraUiState.Guiding) {
                val guidingState = uiState as CameraUiState.Guiding
                val allStepsCompleted by viewModel.allStepsCompleted.collectAsState()
                
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
                        onTakePhoto = {}, // Handled by bottom shutter
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                    )
                }

                // 左下角缩略图（按原比例显示）
                val targetBitmap = viewModel.getCachedImage(guidingState.composition.id)
                if (targetBitmap != null) {
                    // 计算按比例的宽度（固定高度 100dp）
                    val aspectRatio = targetBitmap.width.toFloat() / targetBitmap.height.toFloat()
                    val thumbnailHeight = 100.dp
                    val thumbnailWidth = (100 * aspectRatio).dp
                    
                     Card(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 80.dp) // Leave space for zoom pills
                            .width(thumbnailWidth)
                            .height(thumbnailHeight),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.8f))
                    ) {
                        Image(
                            bitmap = targetBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit  // 改为 Fit 保持完整显示
                        )
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

            // C. 浮动在预览区内的通用控件
            // 变焦药丸 (底部中间)
            ZoomPills(
                currentZoom = zoomRatio,
                onZoomChanged = { ratio -> cameraControl?.setZoomRatio(ratio) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )

            // 分析按钮 (在 Preview 和 Candidates 模式显示)
            if (uiState is CameraUiState.Preview || uiState is CameraUiState.Candidates) {
                AnalysisButton(
                    onClick = {
                         captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
                            viewModel.analyzeImage(file)
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()
                )
            }
        }

        // 2. 下半部分：黑色控制面板 (Dashboard)
        // 根据状态切换显示内容
        val controlMode = when (uiState) {
            is CameraUiState.Preview -> ControlMode.PREVIEW
            is CameraUiState.Analyzing -> ControlMode.ANALYZING
            is CameraUiState.Candidates -> ControlMode.CANDIDATES
            is CameraUiState.Guiding -> ControlMode.GUIDING
            else -> ControlMode.PREVIEW
        }
        
        BottomControlPanel(
            mode = controlMode,
            onCaptureClick = {
                if (controlMode == ControlMode.PREVIEW) {
                     // 暂定预览模式下点击快门也是分析 (或者普通拍照)
                     // captureAndAnalyze(...) 
                } else if (controlMode == ControlMode.GUIDING) {
                    // 引导完成拍照 -> 保存并返回
                    captureAndAnalyze(context, imageCapture, cameraExecutor) { file ->
                        // 这里我们实际上已经拍到了最终照片 file
                        // 1. 保存到相册 (captureAndAnalyze 内部其实只是存了 cache，我们需要存到 Gallery)
                        if (android.graphics.BitmapFactory.decodeFile(file.absolutePath) != null) {
                             val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                             kotlinx.coroutines.GlobalScope.launch {
                                com.photoframer.utils.ImageSaver.saveImageToGallery(context, bitmap, "photoframer_final")
                             }
                        }
                        viewModel.backToPreview()
                    }
                }
            },
            onCancelClick = {
                if (controlMode == ControlMode.GUIDING) viewModel.backToCandidates()
                if (controlMode == ControlMode.CANDIDATES) viewModel.backToPreview()
            },
            
            // Candidates Data
            analysisHeader = (uiState as? CameraUiState.Candidates)?.header,
            compositions = (uiState as? CameraUiState.Candidates)?.compositions ?: emptyList(),
            onCompositionSelected = { comp -> viewModel.selectComposition(comp) },
            getCompositionBitmap = { id -> viewModel.getCachedImage(id) },
            onSaveComposition = { id ->
                val bitmap = viewModel.getCachedImage(id)
                if (bitmap != null) {
                    kotlinx.coroutines.GlobalScope.launch {
                        com.photoframer.utils.ImageSaver.saveImageToGallery(context, bitmap, "ai_ref_$id")
                    }
                }
            }
        )
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
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = message,
                fontSize = 14.sp,
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
