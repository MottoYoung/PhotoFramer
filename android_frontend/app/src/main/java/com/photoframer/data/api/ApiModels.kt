package com.photoframer.data.api

import com.google.gson.annotations.SerializedName

/**
 * 构图步骤
 */
data class CompositionStep(
    @SerializedName("step_order")
    val stepOrder: Int,
    
    @SerializedName("action_type")
    val actionType: String,  // "Shift", "Level", "Zoom", "Orbit", "RaiseCamera"...
    
    @SerializedName("direction")
    val direction: String,   // "Left", "Right", "In", "Out", "Forward", "CW", etc.
    
    @SerializedName("guide_text")
    val guideText: String    // 中文指导文本
)

data class ShotSpec(
    @SerializedName("subject_hint")
    val subjectHint: String? = null,

    @SerializedName("viewpoint_required")
    val viewpointRequired: Boolean = false,

    @SerializedName("target_subject_center")
    val targetSubjectCenter: List<Float>? = null,

    @SerializedName("target_subject_size")
    val targetSubjectSize: Float? = null,

    @SerializedName("camera_move_summary")
    val cameraMoveSummary: String? = null,

    @SerializedName("validation_notes")
    val validationNotes: String? = null
)

/**
 * 单个构图方案 (v3.1 并行化后端格式)
 */
data class CompositionResult(
    @SerializedName("technique")
    val technique: String,           // 构图技术ID，如 "rule_of_thirds"
    
    @SerializedName("technique_name")
    val techniqueName: String,       // 构图技术中文名称，如 "三分构图"
    
    @SerializedName("aesthetic_desc")
    val aestheticDesc: String,       // 美学描述
    
    @SerializedName("steps")
    val steps: List<CompositionStep>,

    @SerializedName("shot_spec")
    val shotSpec: ShotSpec? = null,
    
    @SerializedName("image_base64")
    val imageBase64: String?,        // Base64 编码的图片
    
    @SerializedName("response_time_ms")
    val responseTimeMs: Float? = null,  // 该方案的响应时间（毫秒）
    
    @SerializedName("is_recommended")
    val isRecommended: Boolean = false  // 是否为推荐方案
)

/**
 * API 响应 (v3.1 并行化后端格式)
 */
data class AnalysisResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("total_techniques")
    val totalTechniques: Int = 0,       // 请求的构图技术总数
    
    @SerializedName("applicable_count")
    val applicableCount: Int = 0,       // 适用的构图方案数量
    
    @SerializedName("total_time_ms")
    val totalTimeMs: Float = 0f,        // 并行请求总耗时（毫秒）
    
    @SerializedName("compositions")
    val compositions: List<CompositionResult>
)

/**
 * 健康检查响应
 */
data class HealthResponse(
    @SerializedName("status")
    val status: String,
    
    @SerializedName("model")
    val model: String,
    
    @SerializedName("techniques")
    val techniques: List<String>? = null  // 支持的构图技术列表
)

/**
 * 画面内构图响应
 */
data class InFrameCropCandidate(
    @SerializedName("rank")
    val rank: Int? = null,

    @SerializedName("score")
    val score: Float? = null,

    @SerializedName("query_index")
    val queryIndex: Int? = null,

    @SerializedName("box")
    val box: List<Int> = emptyList(),

    @SerializedName("center")
    val center: List<Float> = emptyList(),

    @SerializedName("crop_size")
    val cropSize: List<Int> = emptyList(),

    @SerializedName("normalized_box")
    val normalizedBox: List<Float>? = null,

    @SerializedName("normalized_center")
    val normalizedCenter: List<Float>? = null,

    @SerializedName("cropped_preview_jpeg_base64")
    val croppedPreviewJpegBase64: String? = null
)

data class InFrameCompositionResponse(
    @SerializedName("file_name")
    val fileName: String? = null,

    @SerializedName("scene_type")
    val sceneType: String? = null,

    @SerializedName("used_model")
    val usedModel: String? = null,

    @SerializedName("image_size")
    val imageSize: List<Int> = emptyList(),

    @SerializedName("stage1_box")
    val stage1Box: List<Int>? = null,

    @SerializedName("stage1_score")
    val stage1Score: Float? = null,

    @SerializedName("crops")
    val crops: List<InFrameCropCandidate> = emptyList(),

    @SerializedName("score")
    val score: Float? = null,

    @SerializedName("box")
    val box: List<Int> = emptyList(),

    @SerializedName("center")
    val center: List<Float> = emptyList(),

    @SerializedName("crop_size")
    val cropSize: List<Int> = emptyList(),

    @SerializedName("normalized_box")
    val normalizedBox: List<Float>? = null,

    @SerializedName("normalized_center")
    val normalizedCenter: List<Float>? = null,

    @SerializedName("cropped_preview_jpeg_base64")
    val croppedPreviewJpegBase64: String? = null
)
