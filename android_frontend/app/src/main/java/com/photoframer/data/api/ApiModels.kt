package com.photoframer.data.api

import com.google.gson.annotations.SerializedName

/**
 * 构图步骤
 */
data class CompositionStep(
    @SerializedName("step_order")
    val stepOrder: Int,
    
    @SerializedName("action_type")
    val actionType: String,  // "Shift", "Zoom", "View-change"
    
    @SerializedName("direction")
    val direction: String,   // "Left", "Right", "In", "Out", etc.
    
    @SerializedName("guide_text")
    val guideText: String    // 中文指导文本
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
