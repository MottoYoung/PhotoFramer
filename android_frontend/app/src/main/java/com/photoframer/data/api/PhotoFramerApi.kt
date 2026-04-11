package com.photoframer.data.api

import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * PhotoFramer API 接口定义 (v3.1 并行化后端)
 */
interface PhotoFramerApi {
    
    /**
     * 分析构图 (并行化)
     * POST /api/v1/composition/analyze
     * 
     * @param image 图片文件
     */
    @Multipart
    @POST("api/v1/composition/analyze")
    suspend fun analyzeComposition(
        @Part image: MultipartBody.Part
    ): AnalysisResponse

    /**
     * 画面内构图
     * POST https://crop.312237.xyz/predict?return_preview=0
     */
    @Multipart
    @POST
    suspend fun analyzeInFrameComposition(
        @Url url: String,
        @Part image: MultipartBody.Part
    ): InFrameCompositionResponse
    
    /**
     * 健康检查
     * GET /api/v1/composition/health
     */
    @GET("api/v1/composition/health")
    suspend fun healthCheck(): HealthResponse
}
