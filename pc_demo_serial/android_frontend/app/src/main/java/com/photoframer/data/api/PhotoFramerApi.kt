package com.photoframer.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * PhotoFramer API 接口定义
 */
interface PhotoFramerApi {
    
    /**
     * 分析构图
     * POST /api/v1/composition/analyze
     * 
     * @param image 图片文件
     * @param prompt 可选的自定义提示词
     */
    @Multipart
    @POST("api/v1/composition/analyze")
    suspend fun analyzeComposition(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody? = null
    ): AnalysisResponse
    
    /**
     * 健康检查
     * GET /api/v1/composition/health
     */
    @GET("api/v1/composition/health")
    suspend fun healthCheck(): HealthResponse
}
