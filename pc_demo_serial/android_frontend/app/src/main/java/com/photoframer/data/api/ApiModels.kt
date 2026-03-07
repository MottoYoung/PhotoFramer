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
 * 单个构图方案
 */
data class CompositionResult(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("aesthetic_desc")
    val aestheticDesc: String,  // 美学描述
    
    @SerializedName("steps")
    val steps: List<CompositionStep>,
    
    @SerializedName("image_base64")
    val imageBase64: String?    // Base64 编码的图片
)

/**
 * 分析头部信息
 */
data class AnalysisHeader(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("total_count")
    val totalCount: Int,
    
    @SerializedName("analysis")
    val analysis: String  // 原图分析描述
)

/**
 * API 响应
 */
data class AnalysisResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("header")
    val header: AnalysisHeader?,
    
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
    val model: String
)
